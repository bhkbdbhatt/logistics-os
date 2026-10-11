package io.logisticsos.mdm;

import org.apache.avro.generic.GenericRecord;

/**
 * Write path for golden records (ADR-0021). The in-memory path ({@link DirectMdmWrites}) persists and
 * publishes directly; the durable path ({@link OutboxMdmWrites}) writes the record and its event to the
 * transactional outbox so a relay publishes it.
 */
public interface MdmWrites {

    MdmRegistry.Entry upsert(MdmEntities entity, String id, String tenantId, String canonicalJson,
            GenericRecord record, String sourceSystem);

    MdmRegistry.Entry deactivate(MdmEntities entity, String id, String sourceSystem);
}
