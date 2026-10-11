# Baselines

`baselines/latest` is a frozen copy of `contracts/avro` used to detect unintended contract drift.

- `bash contracts/scripts/snapshot-baselines.sh` — verify `latest` matches the working tree (CI gate).
- `bash contracts/scripts/snapshot-baselines.sh --write` — regenerate `latest` after an intentional contract change.

Compatibility with the **deployed** Schema Registry (BACKWARD per `payload-registry.json`) is enforced separately at
deploy time; this baseline only guards working-tree drift between developers and CI.