# Edition catalog

Machine-readable, single source of truth for the **sellable editions** of Logistics OS. Human-facing
product docs live in [../docs/product/](../docs/product/); this directory is the contract the build,
release, and deployment tooling read from.

## Files

- `standard.yaml`, `professional.yaml`, `enterprise.yaml` — one SKU per file. Each lists the deployed
  **service subset** and the **feature flags** the edition entitles.
- `versions.yaml` — per-edition current / LTS / supported versions and the release **channel** policy.
- `schema.json` — JSON Schema for the `*.yaml` entries; validated in CI (contracts gate).

## Model

An **edition** is a named feature bundle + service set + support tier, versioned with semver. A
customer buys an **edition@version** and runs it (hosted or self-hosted) under a dedicated namespace
with a customer overlay (`ops/k8s/overlays/<customer>`). Licensing is **soft** today (feature flags +
contract) with a path to signed-license enforcement via `platform:entitlement`.

```
Edition (standard | professional | enterprise)
   └─ version (semver, channel: stable | lts | beta)
        └─ Release  (signed images + SBOM + EULA)
             └─ Customer deployment (namespace + overlay + license)
```

## Adding an edition or customer

1. Add/extend an `editions/<name>.yaml` (keep `schema.json` valid).
2. Bump `versions.yaml` at release time.
3. Create the customer overlay under `ops/k8s/overlays/<customer>` referencing the edition.
4. Ship via `.github/workflows/release.yml` (edition-scoped build, sign, publish).

See [../docs/product/packaging-delivery.md](../docs/product/packaging-delivery.md).
