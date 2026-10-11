package io.logisticsos.tower;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.logisticsos.entitlement.EntitlementGate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-mostly control-tower API: live ops state + alert queue + one-click idempotent commands.
 * No domain logic — the tower reads projections and writes only orch.command.v1 (ADR-0003).
 */
@RestController
@RequestMapping("/tower")
public class TowerController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotifyCustomerRequest(String shipmentId, String caseId) {
    }

    private final TowerCache cache;
    private final CommandDispatcher dispatcher;
    private final EntitlementGate entitlementGate;

    public TowerController(TowerCache cache, CommandDispatcher dispatcher, EntitlementGate entitlementGate) {
        this.cache = cache;
        this.dispatcher = dispatcher;
        this.entitlementGate = entitlementGate;
    }

    @GetMapping("/shipments")
    public List<ShipmentView> shipments(@RequestParam(value = "status", required = false) String status) {
        return cache.shipments(status);
    }

    @GetMapping("/shipments/{shipmentId}")
    public ResponseEntity<ShipmentView> shipment(@PathVariable String shipmentId) {
        return cache.shipment(shipmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/inventory")
    public List<InventoryView> inventory() {
        return cache.inventory();
    }

    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestParam(value = "status", required = false) String status) {
        return cache.alerts("resolved".equalsIgnoreCase(status)
                ? Alert.Status.RESOLVED : "open".equalsIgnoreCase(status) ? Alert.Status.OPEN : null);
    }

    @PostMapping("/actions/notify-customer")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String, String> notifyCustomer(@RequestBody NotifyCustomerRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        CommandBuilder.Dispatch dispatch = dispatcher.notifyCustomer(request.shipmentId(), tenantId,
                request.caseId(), System.currentTimeMillis());
        return Map.of(
                "commandId", dispatch.commandId(),
                "eventId", dispatch.eventId(),
                "topic", dispatch.topic(),
                "key", request.shipmentId());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "shipmentCount", cache.shipmentCount(),
                "openAlerts", cache.openAlertCount(),
                "lastSnapshotAt", cache.lastSnapshotMillis(),
                "status", "ok");
    }

    @GetMapping("/entitlement")
    public Map<String, Object> entitlement() {
        var e = entitlementGate.entitlement();
        return Map.of(
                "edition", e.edition(),
                "version", e.version(),
                "tenant", e.tenant(),
                "mode", e.mode().name().toLowerCase(java.util.Locale.ROOT),
                "expiresAt", e.expiresAt() == null ? "" : e.expiresAt().toString(),
                "features", e.features());
    }
}