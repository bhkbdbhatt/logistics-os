package io.logisticsos.persistence;

import io.logisticsos.outbox.OutboxRecord;
import io.logisticsos.outbox.OutboxRepository;
import io.logisticsos.outbox.OutboxState;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * JDBC {@link OutboxRepository} for the shared {@code outbox_record} table (ADR-0007, ADR-0021).
 *
 * <p>Duplicate detection relies on the {@code UNIQUE(dedupe_key)} index: inserting a row with an
 * existing dedupe key raises SQLState {@code 23505} (PostgreSQL and H2 in PostgreSQL mode) and is
 * reported as a silent no-op via {@code false}, matching {@code InMemoryOutboxRepository}.
 *
 * <p>Uses plain {@code java.sql} only (no framework imports) so it is portable across services and
 * usable in unit tests with H2.
 */
public final class JdbcOutboxRepository implements OutboxRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private static final String INSERT = """
            insert into outbox_record
                (id, dedupe_key, aggregate_type, aggregate_id, aggregate_version, topic,
                 partition_key, headers_json, payload, created_at_ms, state, attempt_count)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_PENDING = """
            select id, dedupe_key, aggregate_type, aggregate_id, aggregate_version, topic,
                   partition_key, headers_json, payload, created_at_ms, state, attempt_count
              from outbox_record
             where state = 'PENDING'
             order by created_at_ms asc, id asc
             limit ?
            """;

    private static final String SELECT_BY_ID = """
            select id, dedupe_key, aggregate_type, aggregate_id, aggregate_version, topic,
                   partition_key, headers_json, payload, created_at_ms, state, attempt_count
              from outbox_record
             where id = ?
            """;

    private static final String MARK = """
            update outbox_record
               set attempt_count = attempt_count + ?,
                   state = case when ? = 1 then 'DISPATCHED'
                                when attempt_count + 1 >= ? then 'FAILED'
                                else 'PENDING' end
             where id = ?
            """;

    private static final String COUNT_PENDING = "select count(*) from outbox_record where state = 'PENDING'";

    private final DataSource dataSource;
    private final int maxAttempts;

    public JdbcOutboxRepository(DataSource dataSource) {
        this(dataSource, 3);
    }

    public JdbcOutboxRepository(DataSource dataSource, int maxAttempts) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean save(OutboxRecord record) {
        Objects.requireNonNull(record, "record");
        try (Connection connection = dataSource.getConnection()) {
            return save(connection, record);
        } catch (SQLException e) {
            throw new IllegalStateException("outbox save failed for id=" + record.id(), e);
        }
    }

    /**
     * Insert the row using a caller-managed connection, so the state change and the outbox row share
     * one transaction (ADR-0007/0021). The connection is not committed or closed here.
     */
    public boolean save(Connection connection, OutboxRecord record) {
        Objects.requireNonNull(record, "record");
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, record.id());
            statement.setString(2, record.dedupeKey());
            statement.setString(3, record.aggregateType());
            statement.setString(4, record.aggregateId());
            statement.setLong(5, record.aggregateVersion());
            statement.setString(6, record.topic());
            statement.setString(7, record.partitionKey());
            statement.setString(8, encodeHeaders(record.headers()));
            statement.setBytes(9, record.payload());
            statement.setLong(10, record.createdAtMs());
            statement.setString(11, record.state().name());
            statement.setInt(12, record.attemptCount());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                return false;
            }
            throw new IllegalStateException("outbox save failed for id=" + record.id(), e);
        }
    }

    @Override
    public List<OutboxRecord> pollPending(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_PENDING)) {
            statement.setInt(1, batchSize);
            try (ResultSet rs = statement.executeQuery()) {
                List<OutboxRecord> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("outbox poll failed", e);
        }
    }

    @Override
    public void markDispatched(String recordId, boolean success) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(MARK)) {
            statement.setInt(1, success ? 0 : 1);
            statement.setInt(2, success ? 1 : 0);
            statement.setInt(3, maxAttempts);
            statement.setString(4, recordId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("outbox markDispatched failed for id=" + recordId, e);
        }
    }

    @Override
    public Optional<OutboxRecord> findById(String recordId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setString(1, recordId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("outbox findById failed for id=" + recordId, e);
        }
    }

    @Override
    public long countPending() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(COUNT_PENDING);
                ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("outbox countPending failed", e);
        }
    }

    private static OutboxRecord map(ResultSet rs) throws SQLException {
        OutboxRecord.Builder builder = OutboxRecord.builder()
                .id(rs.getString("id"))
                .dedupeKey(rs.getString("dedupe_key"))
                .aggregateType(rs.getString("aggregate_type"))
                .aggregateId(rs.getString("aggregate_id"))
                .aggregateVersion(rs.getLong("aggregate_version"))
                .topic(rs.getString("topic"))
                .headers(decodeHeaders(rs.getString("headers_json")))
                .payload(rs.getBytes("payload"))
                .createdAtMs(rs.getLong("created_at_ms"))
                .state(OutboxState.valueOf(rs.getString("state")))
                .attemptCount(rs.getInt("attempt_count"));
        String partitionKey = rs.getString("partition_key");
        if (partitionKey != null) {
            builder.partitionKey(partitionKey);
        }
        return builder.build();
    }

    private static boolean isUniqueViolation(SQLException e) {
        return UNIQUE_VIOLATION.equals(e.getSQLState());
    }

    static String encodeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    static Map<String, String> decodeHeaders(String encoded) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return headers;
        }
        for (String line : encoded.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(line.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(line.substring(eq + 1), StandardCharsets.UTF_8);
            headers.put(key, value);
        }
        return headers;
    }
}
