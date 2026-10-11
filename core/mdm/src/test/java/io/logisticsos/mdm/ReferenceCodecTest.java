package io.logisticsos.mdm;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.kafka.ResourceSchemaResolver;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class ReferenceCodecTest {

    private final ReferenceCodec codec = new ReferenceCodec(new ResourceSchemaResolver());

    private static final String LOCATION_JSON = """
            {
              "locationId": "LOC-1",
              "tenantId": "tenant-1",
              "name": "Amsterdam DC",
              "type": "DC",
              "address": {"line1": "1 Way", "city": "Amsterdam", "postalCode": "1000", "country": "NL"},
              "timezone": "Europe/Amsterdam",
              "status": "ACTIVE"
            }""";

    @Test
    void wrapsAndUnwrapsLocationGoldenRecord() {
        GenericRecord record = codec.recordFromJson(MdmEntities.LOCATION, LOCATION_JSON);
        assertThat(codec.entityId(MdmEntities.LOCATION, record)).isEqualTo("LOC-1");

        GenericRecord upsert = codec.toUpsert(MdmEntities.LOCATION, record, true, "unit-test");
        assertThat(upsert.get("entityType").toString()).isEqualTo("LOCATION");
        assertThat(upsert.get("active")).isEqualTo(true);
        assertThat(upsert.get("sourceSystem")).isEqualTo("unit-test");

        GenericRecord decoded = codec.recordFromUpsert(MdmEntities.LOCATION, upsert);
        assertThat(codec.recordToCanonicalJson(MdmEntities.LOCATION, decoded))
                .isEqualTo(codec.recordToCanonicalJson(MdmEntities.LOCATION, record));
    }

    @Test
    void canonicalJsonRoundTripsThroughBytes() {
        GenericRecord record = codec.recordFromJson(MdmEntities.LOCATION, LOCATION_JSON);
        String canonical = codec.recordToCanonicalJson(MdmEntities.LOCATION, record);
        GenericRecord reparsed = codec.recordFromJson(MdmEntities.LOCATION, canonical);
        assertThat(codec.recordToCanonicalJson(MdmEntities.LOCATION, reparsed)).isEqualTo(canonical);
    }
}