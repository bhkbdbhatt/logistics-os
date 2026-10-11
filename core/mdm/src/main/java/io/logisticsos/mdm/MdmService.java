package io.logisticsos.mdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.kafka.PayloadAvro;
import java.util.List;
import java.util.Optional;
import org.apache.avro.generic.GenericRecord;

/** Facade over registry + publisher; also produces JSON response structures for the REST API. */
public final class MdmService {

    private final MdmRegistry registry;
    private final ReferenceSink publisher;
    private final ReferenceCodec codec;
    private final ObjectMapper json;

    public MdmService(MdmRegistry registry, ReferenceSink publisher, ReferenceCodec codec, ObjectMapper json) {
        this.registry = registry;
        this.publisher = publisher;
        this.codec = codec;
        this.json = json;
    }

    public MdmRegistry.Entry upsert(MdmEntities entity, String id, String canonicalJson, String tenantId,
            String sourceSystem) {
        PayloadAvro.validate(canonicalJson, codec.recordSchema(entity));
        Optional<MdmRegistry.Entry> existing = registry.get(entity, id);
        long priorVersion = existing.map(MdmRegistry.Entry::version).orElse(0L);
        GenericRecord record = codec.recordFromJson(entity, canonicalJson);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? "tenant-1" : tenantId;
        MdmRegistry.Entry entry = registry.next(entity, id, effectiveTenant, canonicalJson, true);
        if (entry.version() == priorVersion) {
            return entry; // idempotent replay of an unchanged golden record
        }
        publisher.publishUpsert(entity, entry, record, true, sourceSystem);
        return entry;
    }

    public MdmRegistry.Entry deactivate(MdmEntities entity, String id, String sourceSystem) {
        MdmRegistry.Entry entry = registry.deactivate(entity, id);
        GenericRecord record = codec.recordFromJson(entity, entry.canonicalJson());
        publisher.publishUpsert(entity, entry, record, false, sourceSystem);
        return entry;
    }

    public java.util.Optional<MdmRegistry.Entry> get(MdmEntities entity, String id) {
        return registry.get(entity, id);
    }

    public List<MdmRegistry.Entry> list(MdmEntities entity) {
        return registry.list(entity);
    }

    public JsonNode asJson(MdmEntities entity, MdmRegistry.Entry entry) {
        return json.valueToTree(entry);
    }
}