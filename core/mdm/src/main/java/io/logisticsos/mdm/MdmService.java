package io.logisticsos.mdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.kafka.PayloadAvro;
import java.util.List;
import org.apache.avro.generic.GenericRecord;

/** Facade over the golden-record store + write path; also produces JSON response structures for the REST API. */
public final class MdmService {

    private final MdmStore store;
    private final MdmWrites writes;
    private final ReferenceCodec codec;
    private final ObjectMapper json;

    public MdmService(MdmStore store, MdmWrites writes, ReferenceCodec codec, ObjectMapper json) {
        this.store = store;
        this.writes = writes;
        this.codec = codec;
        this.json = json;
    }

    public MdmRegistry.Entry upsert(MdmEntities entity, String id, String canonicalJson, String tenantId,
            String sourceSystem) {
        PayloadAvro.validate(canonicalJson, codec.recordSchema(entity));
        GenericRecord record = codec.recordFromJson(entity, canonicalJson);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? "tenant-1" : tenantId;
        return writes.upsert(entity, id, effectiveTenant, canonicalJson, record, sourceSystem);
    }

    public MdmRegistry.Entry deactivate(MdmEntities entity, String id, String sourceSystem) {
        return writes.deactivate(entity, id, sourceSystem);
    }

    public java.util.Optional<MdmRegistry.Entry> get(MdmEntities entity, String id) {
        return store.get(entity, id);
    }

    public List<MdmRegistry.Entry> list(MdmEntities entity) {
        return store.list(entity);
    }

    public JsonNode asJson(MdmEntities entity, MdmRegistry.Entry entry) {
        return json.valueToTree(entry);
    }
}
