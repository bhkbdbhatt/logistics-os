package io.logisticsos.persistence;

import io.logisticsos.idempotency.DedupeStore;
import io.logisticsos.idempotency.IdempotencyKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import javax.sql.DataSource;

/**
 * JDBC {@link DedupeStore} over the shared {@code idempotency_key} table (ADR-0007, ADR-0021).
 *
 * <p>Claiming is atomic: expired rows are deleted first, then a row is inserted; the primary key on
 * {@code key_value} turns a concurrent/duplicate claim into SQLState {@code 23505}, reported as
 * {@code false}. TTL is refreshed on claim and on {@link #recordOutcome}.
 */
public final class JdbcDedupeStore implements DedupeStore {

    private static final String UNIQUE_VIOLATION = "23505";

    private static final String DELETE_EXPIRED =
            "delete from idempotency_key where key_value = ? and expires_at_ms < ?";

    private static final String INSERT_CLAIM =
            "insert into idempotency_key (key_value, processing, expires_at_ms) values (?, true, ?)";

    private static final String UPDATE_OUTCOME = """
            update idempotency_key
               set processing = false, result_ref = ?, expires_at_ms = ?
             where key_value = ?
            """;

    private static final String INSERT_OUTCOME = """
            insert into idempotency_key (key_value, processing, result_ref, expires_at_ms)
            values (?, false, ?, ?)
            """;

    private static final String DELETE_CLAIM =
            "delete from idempotency_key where key_value = ? and processing = true";

    private static final String SELECT_PROCESSED = """
            select 1 from idempotency_key
             where key_value = ? and processing = false and expires_at_ms >= ?
            """;

    private static final String SELECT_RESULT = """
            select result_ref from idempotency_key
             where key_value = ? and processing = false and expires_at_ms >= ?
            """;

    private final DataSource dataSource;
    private final long ttlMillis;
    private final LongSupplier clock;

    public JdbcDedupeStore(DataSource dataSource, Duration ttl) {
        this(dataSource, ttl, System::currentTimeMillis);
    }

    public JdbcDedupeStore(DataSource dataSource, Duration ttl, LongSupplier clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMillis = ttl.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean claim(IdempotencyKey key) {
        long now = clock.getAsLong();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement expire = connection.prepareStatement(DELETE_EXPIRED)) {
                expire.setString(1, key.value());
                expire.setLong(2, now);
                expire.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(INSERT_CLAIM)) {
                insert.setString(1, key.value());
                insert.setLong(2, now + ttlMillis);
                insert.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    return false;
                }
                throw new IllegalStateException("dedupe claim failed for key=" + key.value(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("dedupe claim failed for key=" + key.value(), e);
        }
    }

    @Override
    public void recordOutcome(IdempotencyKey key, String resultRef) {
        long expiresAt = clock.getAsLong() + ttlMillis;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(UPDATE_OUTCOME)) {
                update.setString(1, resultRef);
                update.setLong(2, expiresAt);
                update.setString(3, key.value());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(INSERT_OUTCOME)) {
                insert.setString(1, key.value());
                insert.setString(2, resultRef);
                insert.setLong(3, expiresAt);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                // Raced with a concurrent recordOutcome/claim: retry the update once.
                updateOutcomeOnly(key, resultRef, expiresAt);
                return;
            }
            throw new IllegalStateException("dedupe recordOutcome failed for key=" + key.value(), e);
        }
    }

    private void updateOutcomeOnly(IdempotencyKey key, String resultRef, long expiresAt) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement(UPDATE_OUTCOME)) {
            update.setString(1, resultRef);
            update.setLong(2, expiresAt);
            update.setString(3, key.value());
            update.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("dedupe recordOutcome failed for key=" + key.value(), e);
        }
    }

    @Override
    public void release(IdempotencyKey key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(DELETE_CLAIM)) {
            statement.setString(1, key.value());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("dedupe release failed for key=" + key.value(), e);
        }
    }

    @Override
    public boolean isProcessed(IdempotencyKey key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_PROCESSED)) {
            statement.setString(1, key.value());
            statement.setLong(2, clock.getAsLong());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("dedupe isProcessed failed for key=" + key.value(), e);
        }
    }

    @Override
    public Optional<String> resultRef(IdempotencyKey key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_RESULT)) {
            statement.setString(1, key.value());
            statement.setLong(2, clock.getAsLong());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("dedupe resultRef failed for key=" + key.value(), e);
        }
    }
}
