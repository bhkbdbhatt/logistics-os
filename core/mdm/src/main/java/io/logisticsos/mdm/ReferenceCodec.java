package io.logisticsos.mdm;

import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Converts between canonical record JSON, the reference-record payload (io.logisticsos.mdm.record.v1),
 * and the {@code ReferenceUpserted} wrapper (io.logisticsos.mdm.v1). Pure schema-codec; no state.
 */
public final class ReferenceCodec {

    private static final String REFERENCE_UPSERTED_SUBJECT = "io.logisticsos.mdm.v1.ReferenceUpserted-value";

    private final SchemaResolver schemas;

    public ReferenceCodec(SchemaResolver schemas) {
        this.schemas = schemas;
    }

    public Schema recordSchema(MdmEntities entity) {
        return schemas.resolve(entity.subject());
    }

    public Schema upsertSchema() {
        return schemas.resolve(REFERENCE_UPSERTED_SUBJECT);
    }

    public GenericRecord recordFromJson(MdmEntities entity, String json) {
        return PayloadAvro.jsonToGeneric(json, recordSchema(entity));
    }

    public String recordToCanonicalJson(MdmEntities entity, GenericRecord record) {
        return PayloadAvro.toJson(PayloadAvro.toBytes(record), recordSchema(entity));
    }

    /** Wraps a decoded golden record into the ReferenceUpserted payload. */
    public GenericRecord toUpsert(MdmEntities entity, GenericRecord record, boolean active,
            String sourceSystem) {
        Schema schema = upsertSchema();
        GenericRecord upsert = new GenericData.Record(schema);
        upsert.put("entityType", new GenericData.EnumSymbol(
                schema.getField("entityType").schema(), entity.enumSymbol()));
        upsert.put("record", PayloadAvro.toBytes(record));
        upsert.put("active", active);
        upsert.put("sourceSystem", sourceSystem == null ? "mdm-api" : sourceSystem);
        upsert.put("mergedFrom", null);
        return upsert;
    }

    /** Extracts the canonical record bytes from a ReferenceUpserted payload. */
    public GenericRecord recordFromUpsert(MdmEntities entity, GenericRecord upsert) {
        byte[] recordBytes = (byte[]) upsert.get("record");
        return PayloadAvro.fromBytes(recordBytes, recordSchema(entity));
    }

    public String entityId(MdmEntities entity, GenericRecord record) {
        return String.valueOf(record.get(entity.idField()));
    }
}