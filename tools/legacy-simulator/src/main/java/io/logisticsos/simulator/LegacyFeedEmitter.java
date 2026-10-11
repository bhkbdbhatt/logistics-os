package io.logisticsos.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fake legacy TMS/WMS vendors. Emits provider JSON (no envelopes, no Avro) onto the legacy topics
 * that {@code acl-tms} / {@code acl-wms} consume. Cycles a working set so downstream topology
 * sees ongoing, coherent lifecycle flows.
 */
@Component
public class LegacyFeedEmitter {

    private static final Logger log = LoggerFactory.getLogger(LegacyFeedEmitter.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KafkaTemplate<String, String> template;
    private final SimulatorProperties properties;
    private final AtomicLong round = new AtomicLong();

    private static final String TMS_TOPIC = "legacy.tms.events.v1";
    private static final String WMS_TOPIC = "legacy.wms.events.v1";

    public LegacyFeedEmitter(KafkaTemplate<String, String> template, SimulatorProperties properties) {
        this.template = template;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${simulator.interval-millis:2000}")
    public void emitRound() {
        if (!properties.isEnabled()) {
            return;
        }
        long r = round.getAndIncrement();
        emitTms(r);
        emitWms(r);
        log.debug("emitted round {} ({})", r, getClass().getSimpleName());
    }

    private void emitTms(long r) {
        String shipmentId = "SH-" + (r % properties.getWorkingSet());
        int phase = (int) (r % 3);
        ObjectNode event = JSON.createObjectNode();
        String type;
        if (phase == 0) {
            type = "ShipmentCreated";
            event.put("type", type);
            event.put("shipmentId", shipmentId);
            event.put("proNumber", "PRO-" + r);
            event.put("customerRef", "PO-" + r);
            event.put("shipperId", "CUST-" + (r % 17));
            event.put("shipperName", "Northwind " + (r % 17));
            event.put("consigneeId", "CUST-" + (r % 13));
            event.put("pickupLocationId", "WH-AMS");
            event.put("pickupFacilityName", "AMS DC");
            event.put("deliveryLocationId", "STORE-BER");
            event.put("pickupEarliest", System.currentTimeMillis());
            event.put("pickupLatest", System.currentTimeMillis() + 86_400_000L);
            event.put("deliveryEarliest", System.currentTimeMillis() + 172_800_000L);
            event.put("deliveryLatest", System.currentTimeMillis() + 259_200_000L);
            event.put("serviceLevel", "STANDARD");
            event.put("mode", "FTL");
            event.put("quantity", 12.0);
            event.put("uom", "PLT");
            event.put("totalWeightKg", 2400.0);
        } else if (phase == 1) {
            type = "ShipmentPlanned";
            event.put("type", type);
            event.put("shipmentId", shipmentId);
            event.put("planId", "PLAN-" + r);
            event.put("mode", "FTL");
            event.put("plannedPickup", System.currentTimeMillis() + 86_400_000L);
            event.put("plannedDelivery", System.currentTimeMillis() + 259_200_000L);
            var stops = event.putArray("stops");
            var pickup = stops.addObject();
            pickup.put("sequence", 1);
            pickup.put("locationId", "WH-AMS");
            pickup.put("type", "PICKUP");
            pickup.put("start", System.currentTimeMillis() + 86_400_000L);
            pickup.put("end", System.currentTimeMillis() + 172_800_000L);
            pickup.put("legMode", "FTL");
            var delivery = stops.addObject();
            delivery.put("sequence", 2);
            delivery.put("locationId", "STORE-BER");
            delivery.put("type", "DELIVERY");
            delivery.put("start", System.currentTimeMillis() + 172_800_000L);
            delivery.put("end", System.currentTimeMillis() + 259_200_000L);
            delivery.put("legMode", "FTL");
        } else {
            type = "ShipmentDispatched";
            event.put("type", type);
            event.put("shipmentId", shipmentId);
            event.put("dispatchedAt", System.currentTimeMillis());
            event.put("originLocationId", "WH-AMS");
            event.put("vehicleId", "TRK-" + (r % 20));
            event.put("driverId", "DRV-" + (r % 10));
            event.put("sealNumber", "SEAL-" + r);
        }
        send(TMS_TOPIC, shipmentId, event, type);
    }

    private void emitWms(long r) {
        String nodeId = r % 2 == 0 ? "WH-AMS" : "WH-BER";
        String inventoryId = "INV-" + (r % properties.getWorkingSet());
        int phase = (int) (r % 3);

        if (phase == 0) {
            ObjectNode scan = JSON.createObjectNode();
            scan.put("type", "ScanEvent");
            scan.put("scanId", "SCN-" + r);
            scan.put("locationId", nodeId);
            scan.put("deviceId", "SCANNER-" + (r % 8));
            scan.put("scanAt", System.currentTimeMillis());
            scan.put("scanType", "PICK");
            scan.put("refType", "INVENTORY");
            scan.put("refId", inventoryId);
            scan.put("operatorId", "OP-" + (r % 6));
            scan.putObject("quantity").put("value", 1 + r % 5).put("uom", "EA");
            send(WMS_TOPIC, inventoryId, scan, "ScanEvent");
        } else if (phase == 1) {
            ObjectNode task = JSON.createObjectNode();
            task.put("type", "WmsTaskUpdate");
            task.put("taskId", "TASK-" + r);
            task.put("taskType", "PICK");
            task.put("status", "COMPLETED");
            task.put("nodeId", nodeId);
            task.put("orderId", "SO-" + r);
            task.put("locationId", "A-01-" + r % 5);
            task.putObject("quantity").put("value", 1 + r % 5).put("uom", "CS");
            task.put("assignedWorkerId", "W-" + (r % 7));
            task.put("startedAt", System.currentTimeMillis() - 600_000L);
            task.put("completedAt", System.currentTimeMillis());
            task.put("occurredAt", System.currentTimeMillis());
            send(WMS_TOPIC, nodeId, task, "WmsTaskUpdate");
        } else {
            ObjectNode move = JSON.createObjectNode();
            move.put("type", "StockMove");
            move.put("inventoryId", inventoryId);
            move.put("sku", "SKU-" + (r % properties.getWorkingSet()));
            move.put("moveType", "TRANSFER");
            move.put("moveSequence", r);
            move.put("fromLocation", "A-01-" + r % 5);
            move.put("toLocation", "C-09-" + r % 3);
            move.putObject("quantity").put("value", 1 + r % 5).put("uom", "PLT");
            move.put("reasonCode", "REBALANCE");
            move.put("occurredAt", System.currentTimeMillis());
            send(WMS_TOPIC, inventoryId, move, "StockMove");
        }
    }

    private void send(String topic, String key, ObjectNode event, String type) {
        try {
            event.put("_fedAt", System.currentTimeMillis());
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic, key, JSON.writeValueAsString(event));
            record.headers().add("X-Tenant-Id", properties.getTenantId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("X-Legacy-Type", type.getBytes(StandardCharsets.UTF_8));
            template.send(record);
        } catch (Exception ex) {
            log.error("simulator failed to emit {} to {}", type, topic, ex);
        }
    }
}