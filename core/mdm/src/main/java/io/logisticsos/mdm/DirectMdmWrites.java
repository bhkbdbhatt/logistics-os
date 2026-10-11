package io.logisticsos.mdm;

import org.apache.avro.generic.GenericRecord;

/**
 * In-memory write path: persist to the {@link MdmStore} and publish straight to Kafka via the
 * {@link ReferenceSink}. Used when no database is configured (dev/tests), and the original scaffold
 * behavior.
 */
public final class DirectMdmWrites implements MdmWrites {

    private final MdmStore store;
    private final ReferenceSink publisher;
    private final ReferenceCodec codec;

    public DirectMdmWrites(MdmStore store, ReferenceSink publisher, ReferenceCodec codec) {
        this.store = store;
        this.publisher = publisher;
        this.codec = codec;
    }

    @Override
    public MdmRegistry.Entry upsert(MdmEntities entity, String id, String tenantId, String canonicalJson,
            GenericRecord record, String sourceSystem) {
        long priorVersion = store.get(entity, id).map(MdmRegistry.Entry::version).orElse(0L);
        MdmRegistry.Entry entry = store.next(entity, id, tenantId, canonicalJson, true);
        if (entry.version() != priorVersion) {
            publisher.publishUpsert(entity, entry, record, true, sourceSystem);
        }
        return entry;
    }

    @Override
    public MdmRegistry.Entry deactivate(MdmEntities entity, String id, String sourceSystem) {
        MdmRegistry.Entry entry = store.deactivate(entity, id);
        GenericRecord record = codec.recordFromJson(entity, entry.canonicalJson());
        publisher.publishUpsert(entity, entry, record, false, sourceSystem);
        return entry;
    }
}
