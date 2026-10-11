# Customer overlays

Per-customer deployment units built on the [base](../README.md). A customer runs in its **own
namespace** and is sold an **edition** (`editions/`); the overlay is where those two facts meet.

| Overlay | Customer | Edition | Namespace | L5 orchestrator |
|---------|----------|---------|-----------|-----------------|
| [`dhl/`](dhl/) | DHL | enterprise | `logistics-os-dhl` | yes |
| [`fedex/`](fedex/) | FedEx | professional | `logistics-os-fedex` | no |

Each overlay sets:

- **namespace** — isolation boundary (ADR-0006 dedicated tier).
- **images** — customer/edition image tag (`<customer>-<version>`), overriding the base tag.
- **commonLabels / commonAnnotations** — `customer` and `logistics-os.io/edition` for visibility.
- **config patch** — `DEFAULT_TENANT`, `COMPANY`, and `ENTITLEMENT_LICENSE` (the soft-phase license the
  services read via `ENTITLEMENT_LICENSE`; see [`platform/entitlement`](../../../platform/entitlement)).
- **resources** — edition-specific services; Enterprise adds `../../apps/orchestrator.yaml`, which the
  base intentionally does not include.

## Deploy

```bash
kubectl apply -k ops/k8s/overlays/dhl      # or: fedex
kubectl -n logistics-os-dhl get pods
```

Verify the license is live:

```bash
kubectl -n logistics-os-dhl port-forward svc/control-tower 8087:8087
curl localhost:8087/tower/entitlement
# {"edition":"enterprise","mode":"licensed", ... }
```

## Add a customer

1. Pick an edition from [`editions/`](../../../editions) and a namespace `<customer>`.
2. Copy an existing overlay dir to `ops/k8s/overlays/<customer>/`.
3. Set `namespace`, the `images` tags, and `config-patch.yaml` (`DEFAULT_TENANT`, `COMPANY`,
   `ENTITLEMENT_LICENSE`).
4. Add `../../apps/orchestrator.yaml` to `resources` only if the edition includes L5 orchestration.

> Editions and licensing are catalogued in [`editions/`](../../../editions) and
> [`docs/product/`](../../../docs/product). This is the preview slice; service-subset templating and
> signed licenses are follow-ups (see [`docs/releases/0.2.0.md`](../../../docs/releases/0.2.0.md)).
