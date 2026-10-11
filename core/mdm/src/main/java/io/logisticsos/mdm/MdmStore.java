package io.logisticsos.mdm;

import java.util.List;
import java.util.Optional;

/**
 * Golden-record store port (ADR-0021). Implemented by {@link MdmRegistry} (in-memory, dev/tests) and
 * {@link JdbcMdmStore} (PostgreSQL system-of-record). Version-guarded: an unchanged, equally-active
 * canonical JSON keeps the version (idempotent replay); any change bumps it.
 */
public interface MdmStore {

    Optional<MdmRegistry.Entry> get(MdmEntities entity, String id);

    List<MdmRegistry.Entry> list(MdmEntities entity);

    MdmRegistry.Entry next(MdmEntities entity, String id, String tenantId, String canonicalJson, boolean active);

    MdmRegistry.Entry deactivate(MdmEntities entity, String id);

    int size();
}
