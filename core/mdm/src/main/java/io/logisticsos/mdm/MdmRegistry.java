package io.logisticsos.mdm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process golden-record registry (scaffold; a compacted-topic-backed store replaces this in
 * production). Version-guarded: an unchanged canonical JSON is an idempotent no-op (no new event,
 * no version bump); a changed record increments the version.
 */
public final class MdmRegistry {

    public record Entry(String entity, String id, String tenantId, long version, boolean active, String canonicalJson) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public Optional<Entry> get(MdmEntities entity, String id) {
        return Optional.ofNullable(store.get(key(entity, id)));
    }

    public List<Entry> list(MdmEntities entity) {
        return store.values().stream()
                .filter(e -> e.entity().equals(entity.path()))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    /**
     * Returns the entry to persist. A deep-equal, equally-active canonical JSON keeps the existing
     * version (idempotent replay); any change (content or active flag) bumps the version.
     */
    public Entry next(MdmEntities entity, String id, String tenantId, String canonicalJson, boolean active) {
        Optional<Entry> existing = get(entity, id);
        if (existing.isPresent() && existing.get().canonicalJson().equals(canonicalJson)
                && existing.get().active() == active) {
            return existing.get();
        }
        long version = existing.map(Entry::version).orElse(0L) + 1;
        Entry entry = new Entry(entity.path(), id, tenantId, version, active, canonicalJson);
        store.put(key(entity, id), entry);
        return entry;
    }

    public Entry deactivate(MdmEntities entity, String id) {
        Entry existing = store.get(key(entity, id));
        if (existing == null) {
            throw new IllegalArgumentException("no golden record " + entity.path() + ":" + id);
        }
        Entry entry = new Entry(existing.entity(), existing.id(), existing.tenantId(),
                existing.version() + 1, false, existing.canonicalJson());
        store.put(key(entity, id), entry);
        return entry;
    }

    public int size() {
        return store.size();
    }

    private static String key(MdmEntities entity, String id) {
        return entity.path() + ":" + id;
    }
}