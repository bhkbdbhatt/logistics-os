package io.logisticsos.ingest;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final IngestService service;

    public IngestController(IngestService service) {
        this.service = service;
    }

    @PostMapping("/{kind}")
    public ResponseEntity<IngestResponse> ingest(
            @PathVariable String kind,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Source", required = false) String source,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody String body) {

        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(IngestResponse.rejected("X-Tenant-Id header is required"));
        }
        IngestKind parsed;
        try {
            parsed = IngestKind.fromPath(kind);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(IngestResponse.rejected(ex.getMessage()));
        }
        try {
            IngestService.Result result = service.ingest(parsed, tenantId, source, idempotencyKey, body);
            log.info("ingest kind={} tenant={} status={} eventId={}", kind, tenantId,
                    result.status(), result.eventId());
            return ResponseEntity.ok(new IngestResponse(result.status(), Optional.ofNullable(result.eventId()),
                    Optional.empty()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(IngestResponse.rejected(ex.getMessage()));
        }
    }

    public record IngestResponse(String status, Optional<String> eventId, Optional<String> message) {

        static IngestResponse rejected(String message) {
            return new IngestResponse("REJECTED", Optional.empty(), Optional.of(message));
        }
    }
}