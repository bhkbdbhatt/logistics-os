rootProject.name = "logistics-os"

include("platform:event-envelope")
include("platform:kafka-commons")
include("platform:outbox")
include("platform:idempotency")
include("platform:tenant")
include("platform:observability")

include("core:ingest-gateway")
include("core:acl-tms")
include("core:acl-wms")
include("core:mdm")
include("core:read-model-builder")
include("core:control-tower")
include("core:bff-ops")
include("core:phase2-stubs:orchestrator")

include("tools:legacy-simulator")

include("contracts:validation")