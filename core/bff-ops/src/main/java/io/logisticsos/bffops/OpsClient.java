package io.logisticsos.bffops;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client over the (stateless, read-mostly) upstreams. A 404/connection failure surfaces
 * as {@code Optional.empty()} so the BFF can compose partial views instead of failing the page.
 */
@Component
public class OpsClient implements OpsPort {

    private static final Logger log = LoggerFactory.getLogger(OpsClient.class);

    private final RestClient readModel;
    private final RestClient tower;
    private final RestClient mdm;

    public OpsClient(BffProperties properties) {
        this.readModel = RestClient.builder().baseUrl(properties.getReadModelBaseUrl()).build();
        this.tower = RestClient.builder().baseUrl(properties.getTowerBaseUrl()).build();
        this.mdm = RestClient.builder().baseUrl(properties.getMdmBaseUrl()).build();
    }

    @Override
    public Optional<JsonNode> shipments(String status) {
        return get(readModel, "/views/shipments" + (status == null || status.isBlank() ? "" : "?status=" + status));
    }

    @Override
    public Optional<JsonNode> shipment(String shipmentId) {
        return get(readModel, "/views/shipments/" + shipmentId);
    }

    @Override
    public Optional<JsonNode> inventory() {
        return get(readModel, "/views/inventory");
    }

    @Override
    public Optional<JsonNode> towerAlerts() {
        return get(tower, "/tower/alerts?status=open");
    }

    @Override
    public Optional<JsonNode> towerHealth() {
        return get(tower, "/tower/health");
    }

    @Override
    public Optional<JsonNode> mdmLocation(String locationId) {
        return get(mdm, "/mdm/location/" + locationId);
    }

    @Override
    public Optional<JsonNode> mdmLocations() {
        return get(mdm, "/mdm/location");
    }

    @Override
    public Optional<JsonNode> notifyCustomer(String shipmentId, String caseId) {
        try {
            JsonNode ack = tower.post().uri("/tower/actions/notify-customer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("shipmentId", shipmentId, "caseId", caseId == null ? "" : caseId))
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(ack);
        } catch (RestClientException ex) {
            log.warn("tower action notify-customer failed for {}", shipmentId, ex);
            return Optional.empty();
        }
    }

    private Optional<JsonNode> get(RestClient client, String uri) {
        try {
            return Optional.ofNullable(client.get().uri(uri).retrieve().body(JsonNode.class));
        } catch (RestClientException ex) {
            log.warn("upstream GET {} failed: {}", uri, ex.getMessage());
            return Optional.empty();
        }
    }
}