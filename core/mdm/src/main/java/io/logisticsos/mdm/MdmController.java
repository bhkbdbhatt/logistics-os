package io.logisticsos.mdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Golden-record REST API. Upserts are idempotent on {@code entityId + canonical JSON}: an unchanged
 * record is a no-op (no new ReferenceUpserted), a changed record increments the aggregate version.
 */
@RestController
@RequestMapping("/mdm")
public class MdmController {

    private final MdmService service;
    private final ObjectMapper json;

    public MdmController(MdmService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/{entity}")
    public ResponseEntity<JsonNode> upsert(@PathVariable String entity,
            @RequestBody String body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem) {
        MdmEntities def = MdmEntities.fromPath(entity);
        String id = idFromBody(def, body);
        MdmRegistry.Entry entry = service.upsert(def, id, body, tenantId, sourceSystem);
        return ResponseEntity.ok(service.asJson(def, entry));
    }

    @PostMapping("/{entity}/{id}/deactivate")
    public ResponseEntity<JsonNode> deactivate(@PathVariable String entity, @PathVariable String id,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem) {
        MdmEntities def = MdmEntities.fromPath(entity);
        return ResponseEntity.ok(service.asJson(def, service.deactivate(def, id, sourceSystem)));
    }

    @GetMapping("/{entity}/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String entity, @PathVariable String id) {
        MdmEntities def = MdmEntities.fromPath(entity);
        return service.get(def, id)
                .map(entry -> ResponseEntity.ok(service.asJson(def, entry)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{entity}")
    public List<JsonNode> list(@PathVariable String entity) {
        MdmEntities def = MdmEntities.fromPath(entity);
        return service.list(def).stream().map(e -> service.asJson(def, e)).toList();
    }

    private String idFromBody(MdmEntities def, String body) {
        try {
            JsonNode node = json.readTree(body);
            JsonNode id = node.get(def.idField());
            if (id == null || id.asText().isBlank()) {
                throw new IllegalArgumentException("missing '" + def.idField() + "'");
            }
            return id.asText();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("body must be canonical '" + def.subject() + "' JSON", ex);
        }
    }
}