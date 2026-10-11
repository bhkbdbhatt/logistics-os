package io.logisticsos.mdm;

import io.logisticsos.persistence.JdbcOutboxRepository;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.avro.generic.GenericRecord;

/**
 * Durable write path (ADR-0007/0021): the golden record and its {@code ReferenceUpserted} event are
 * written in one transaction; a relay ({@link MdmOutboxRelay}) publishes later. Replaces the direct
 * {@code KafkaTemplate} send when a database is configured.
 */
public final class OutboxMdmWrites implements MdmWrites {

    private final DataSource dataSource;
    private final JdbcMdmStore store;
    private final JdbcOutboxRepository outbox;
    private final MdmEventFactory events;
    private final ReferenceCodec codec;

    public OutboxMdmWrites(DataSource dataSource, JdbcMdmStore store, JdbcOutboxRepository outbox,
            MdmEventFactory events, ReferenceCodec codec) {
        this.dataSource = dataSource;
        this.store = store;
        this.outbox = outbox;
        this.events = events;
        this.codec = codec;
    }

    @Override
    public MdmRegistry.Entry upsert(MdmEntities entity, String id, String tenantId, String canonicalJson,
            GenericRecord record, String sourceSystem) {
        return inTransaction(connection -> {
            JdbcMdmStore.UpsertResult result = store.upsert(connection, entity, id, tenantId, canonicalJson, true);
            if (result.changed()) {
                outbox.save(connection, events.toOutbox(entity, result.entry(), record, true, sourceSystem));
            }
            return result.entry();
        });
    }

    @Override
    public MdmRegistry.Entry deactivate(MdmEntities entity, String id, String sourceSystem) {
        return inTransaction(connection -> {
            MdmRegistry.Entry entry = store.deactivate(connection, entity, id);
            GenericRecord record = codec.recordFromJson(entity, entry.canonicalJson());
            outbox.save(connection, events.toOutbox(entity, entry, record, false, sourceSystem));
            return entry;
        });
    }

    private MdmRegistry.Entry inTransaction(SqlWork work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MdmRegistry.Entry entry = work.run(connection);
                connection.commit();
                return entry;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mdm transactional write failed", e);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        MdmRegistry.Entry run(Connection connection) throws SQLException;
    }
}
