package io.logisticsos.mdm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MdmServiceTest {

    private static final String LOCATION = """
            {"locationId":"LOC-1","tenantId":"tenant-1","name":"Amsterdam DC","type":"DC",
             "address":{"line1":"1 Way","city":"Amsterdam","postalCode":"1000","country":"NL"},
             "timezone":"Europe/Amsterdam","status":"ACTIVE"}""";

    private final List<MdmRegistry.Entry> published = new ArrayList<>();
    private MdmService service;

    @BeforeEach
    void setUp() {
        ReferenceSink sink = (entity, entry, record, active, sourceSystem) -> published.add(entry);
        ReferenceCodec codec = new ReferenceCodec(new ResourceSchemaResolver());
        service = new MdmService(new MdmRegistry(), sink, codec, new ObjectMapper());
    }

    @Test
    void firstUpsertPublishesVersionOne() {
        MdmRegistry.Entry entry = service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        assertThat(entry.version()).isEqualTo(1);
        assertThat(entry.active()).isTrue();
        assertThat(published).hasSize(1);
    }

    @Test
    void unchangedUpsertIsIdempotent() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        MdmRegistry.Entry again = service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        assertThat(again.version()).isEqualTo(1);
        assertThat(published).hasSize(1);
    }

    @Test
    void changedRecordBumpsVersionAndPublishes() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        String renamed = LOCATION.replace("Amsterdam DC", "Amsterdam Hub");
        MdmRegistry.Entry entry = service.upsert(MdmEntities.LOCATION, "LOC-1", renamed, "tenant-1", "test");
        assertThat(entry.version()).isEqualTo(2);
        assertThat(published).hasSize(2);
    }

    @Test
    void deactivateFlipsActiveAndPublishes() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        MdmRegistry.Entry entry = service.deactivate(MdmEntities.LOCATION, "LOC-1", "test");
        assertThat(entry.active()).isFalse();
        assertThat(entry.version()).isEqualTo(2);
        assertThat(published).hasSize(2);
    }

    @Test
    void upsertAfterDeactivateReactivatesWithFreshVersion() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        service.deactivate(MdmEntities.LOCATION, "LOC-1", "test");
        MdmRegistry.Entry again = service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "tenant-1", "test");
        assertThat(again.active()).isTrue();
        assertThat(again.version()).isEqualTo(3);
        assertThat(published).hasSize(3);
    }

    @Test
    void rejectsRecordWithWrongEntityShape() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                service.upsert(MdmEntities.LOCATION, "X", "{\"name\":\"no id\"}", "tenant-1", "test")))
                .isInstanceOf(Exception.class);
    }
}