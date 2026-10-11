package io.logisticsos.bffops;

import com.fasterxml.jackson.databind.JsonNode;

/** Upstream read/compose ports for the ops BFF. Implemented by {@link OpsClient}. */
public interface OpsPort {

    java.util.Optional<JsonNode> shipments(String status);

    java.util.Optional<JsonNode> shipment(String shipmentId);

    java.util.Optional<JsonNode> inventory();

    java.util.Optional<JsonNode> towerAlerts();

    java.util.Optional<JsonNode> towerHealth();

    java.util.Optional<JsonNode> mdmLocation(String locationId);

    java.util.Optional<JsonNode> mdmLocations();

    java.util.Optional<JsonNode> entitlement();

    java.util.Optional<JsonNode> notifyCustomer(String shipmentId, String caseId);
}