package io.logisticsos.mdm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * PostgreSQL/H2 golden-record store (ADR-0021). Reads use the datasource directly; the write mutations
 * expose connection-scoped variants so a caller can commit them in the same transaction as an outbox
 * row ({@link OutboxMdmWrites}). Version-guarded like {@link MdmRegistry}.
 */
public final class JdbcMdmStore implements MdmStore {

    public record UpsertResult(MdmRegistry.Entry entry, boolean changed) {
    }

    private static final String SELECT_ONE = """
            select entity, id, tenant_id, version, active, canonical_json
              from mdm_golden_record
             where entity = ? and id = ?
            """;

    private static final String SELECT_ONE_FOR_UPDATE = SELECT_ONE + " for update";

    private static final String SELECT_ENTITY = """
            select entity, id, tenant_id, version, active, canonical_json
              from mdm_golden_record
             where entity = ?
             order by id asc
            """;

    private static final String COUNT = "select count(*) from mdm_golden_record";

    private static final String UPDATE = """
            update mdm_golden_record
               set tenant_id = ?, version = ?, active = ?, canonical_json = ?
             where entity = ? and id = ?
            """;

    private static final String INSERT = """
            insert into mdm_golden_record (entity, id, tenant_id, version, active, canonical_json)
            values (?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public JdbcMdmStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<MdmRegistry.Entry> get(MdmEntities entity, String id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setString(1, entity.path());
            statement.setString(2, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mdm get failed for " + entity.path() + ":" + id, e);
        }
    }

    @Override
    public List<MdmRegistry.Entry> list(MdmEntities entity) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ENTITY)) {
            statement.setString(1, entity.path());
            try (ResultSet rs = statement.executeQuery()) {
                List<MdmRegistry.Entry> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mdm list failed for " + entity.path(), e);
        }
    }

    @Override
    public int size() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(COUNT);
                ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("mdm size failed", e);
        }
    }

    @Override
    public MdmRegistry.Entry next(MdmEntities entity, String id, String tenantId, String canonicalJson, boolean active) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UpsertResult result = upsert(connection, entity, id, tenantId, canonicalJson, active);
                connection.commit();
                return result.entry();
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mdm upsert failed for " + entity.path() + ":" + id, e);
        }
    }

    @Override
    public MdmRegistry.Entry deactivate(MdmEntities entity, String id) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MdmRegistry.Entry entry = deactivate(connection, entity, id);
                connection.commit();
                return entry;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mdm deactivate failed for " + entity.path() + ":" + id, e);
        }
    }

    /** Connection-scoped upsert; the caller owns the transaction (used by {@link OutboxMdmWrites}). */
    public UpsertResult upsert(Connection connection, MdmEntities entity, String id, String tenantId,
            String canonicalJson, boolean active) throws SQLException {
        Optional<MdmRegistry.Entry> existing = selectForUpdate(connection, entity, id);
        if (existing.isPresent()
                && existing.get().canonicalJson().equals(canonicalJson)
                && existing.get().active() == active) {
            return new UpsertResult(existing.get(), false);
        }
        long version = existing.map(MdmRegistry.Entry::version).orElse(0L) + 1;
        MdmRegistry.Entry entry = new MdmRegistry.Entry(entity.path(), id, tenantId, version, active, canonicalJson);
        write(connection, entry, existing.isPresent());
        return new UpsertResult(entry, true);
    }

    /** Connection-scoped deactivate; the caller owns the transaction. */
    public MdmRegistry.Entry deactivate(Connection connection, MdmEntities entity, String id) throws SQLException {
        MdmRegistry.Entry existing = selectForUpdate(connection, entity, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no golden record " + entity.path() + ":" + id));
        MdmRegistry.Entry entry = new MdmRegistry.Entry(existing.entity(), existing.id(), existing.tenantId(),
                existing.version() + 1, false, existing.canonicalJson());
        write(connection, entry, true);
        return entry;
    }

    private Optional<MdmRegistry.Entry> selectForUpdate(Connection connection, MdmEntities entity, String id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ONE_FOR_UPDATE)) {
            statement.setString(1, entity.path());
            statement.setString(2, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private void write(Connection connection, MdmRegistry.Entry entry, boolean update) throws SQLException {
        String sql = update ? UPDATE : INSERT;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (update) {
                statement.setString(1, entry.tenantId());
                statement.setLong(2, entry.version());
                statement.setBoolean(3, entry.active());
                statement.setString(4, entry.canonicalJson());
                statement.setString(5, entry.entity());
                statement.setString(6, entry.id());
            } else {
                statement.setString(1, entry.entity());
                statement.setString(2, entry.id());
                statement.setString(3, entry.tenantId());
                statement.setLong(4, entry.version());
                statement.setBoolean(5, entry.active());
                statement.setString(6, entry.canonicalJson());
            }
            statement.executeUpdate();
        }
    }

    private static MdmRegistry.Entry map(ResultSet rs) throws SQLException {
        return new MdmRegistry.Entry(
                rs.getString("entity"),
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getLong("version"),
                rs.getBoolean("active"),
                rs.getString("canonical_json"));
    }
}
