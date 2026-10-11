package io.logisticsos.mdm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.persistence.JdbcOutboxRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

class MdmOutboxWritesTest {

    private static final String MDM_MIGRATION = "/db/migration/V1__mdm.sql";
    private static final String OUTBOX_MIGRATION = "/io/logisticsos/persistence/migration/V900__outbox.sql";

    private static final String LOCATION = """
            {"locationId":"LOC-1","tenantId":"acme","name":"Amsterdam DC","type":"DC",
             "address":{"line1":"1 Way","city":"Amsterdam","postalCode":"1000","country":"NL"},
             "timezone":"Europe/Amsterdam","status":"ACTIVE"}""";

    private DataSource dataSource;
    private JdbcMdmStore store;
    private JdbcOutboxRepository outbox;
    private MdmService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        dataSource = H2Support.h2();
        H2Support.apply(dataSource, MDM_MIGRATION, OUTBOX_MIGRATION);
        ReferenceCodec codec = new ReferenceCodec(new ResourceSchemaResolver());
        KafkaTemplate<String, EventEnvelope> template = Mockito.mock(KafkaTemplate.class);
        MdmPublisher publisher = new MdmPublisher(template, codec);
        MdmEventFactory events = new MdmEventFactory(publisher);
        store = new JdbcMdmStore(dataSource);
        outbox = new JdbcOutboxRepository(dataSource);
        OutboxMdmWrites writes = new OutboxMdmWrites(dataSource, store, outbox, events, codec);
        service = new MdmService(store, writes, codec, new ObjectMapper());
    }

    @Test
    void upsertPersistsRecordAndEnqueuesOutboxRowAtomically() {
        MdmRegistry.Entry entry = service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");

        assertThat(entry.version()).isEqualTo(1);
        assertThat(store.get(MdmEntities.LOCATION, "LOC-1")).isPresent();
        assertThat(outbox.countPending()).isEqualTo(1);
        assertThat(outbox.pollPending(10).get(0).topic())
                .isEqualTo(MdmEntities.LOCATION.topic());
    }

    @Test
    void unchangedUpsertIsIdempotentNoOutboxRow() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");
        MdmRegistry.Entry again = service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");

        assertThat(again.version()).isEqualTo(1);
        assertThat(outbox.countPending()).isEqualTo(1);
    }

    @Test
    void changedRecordBumpsVersionAndEnqueuesAgain() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");
        String renamed = LOCATION.replace("Amsterdam DC", "Amsterdam Hub");

        MdmRegistry.Entry entry = service.upsert(MdmEntities.LOCATION, "LOC-1", renamed, "acme", "test");

        assertThat(entry.version()).isEqualTo(2);
        assertThat(outbox.countPending()).isEqualTo(2);
    }

    @Test
    void deactivateFlipsActiveAndEnqueues() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");

        MdmRegistry.Entry entry = service.deactivate(MdmEntities.LOCATION, "LOC-1", "test");

        assertThat(entry.active()).isFalse();
        assertThat(entry.version()).isEqualTo(2);
        assertThat(outbox.countPending()).isEqualTo(2);
    }

    @Test
    void recordsSurviveANewStoreInstance() {
        service.upsert(MdmEntities.LOCATION, "LOC-1", LOCATION, "acme", "test");

        JdbcMdmStore secondStore = new JdbcMdmStore(dataSource);

        assertThat(secondStore.get(MdmEntities.LOCATION, "LOC-1")).isPresent();
        assertThat(secondStore.size()).isEqualTo(1);
    }
}
