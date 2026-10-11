# Terraform (AWS MSK)

Provision the Phase-1 Kafka backbone as **Amazon MSK**. This is intentionally modest — broker
definition only; topics come from the shared catalog (see below).

## Layout

- `providers.tf` — AWS provider (`~> 5.0`), temporary local backend.
- `main.tf` — `terraform-aws-modules/msk/aws` `~> 4.0`, MSK 3.7.1, 3× `kafka.m5.large`, 100 GB EBS.
- `variables.tf` / `outputs.tf` — subnet/SG/KMS in, plaintext+TLS bootstrap brokers out.

## Usage

```bash
terraform -chdir=ops/terraform init
# supply subnet_ids + security_group_id via a tfvars file (see example.tfvars)
terraform -chdir=ops/terraform apply -var-file=example.tfvars
terraform -chdir=ops/terraform output bootstrap_brokers
```

Then bootstrap topics against the returned brokers using the **same catalog** as local
development — the catalog is the single source of truth and must stay in sync:

```bash
KAFKA_BOOTSTRAP="$(terraform -chdir=ops/terraform output -raw bootstrap_brokers)" \
  bash ops/local-compose/scripts/bootstrap-topics.sh
```

## Reproducibility, destroy & lockfile hygiene

- **Provider/module pins**: AWS provider `~> 5.0` (`providers.tf`); the MSK module is pinned
  `~> 4.0` (`main.tf`). Do not bump without a review and a fresh `terraform plan`.
- **Lockfile**: commit `ops/terraform/.terraform.lock.hcl` after `terraform init` — it pins provider
  + module checksums so plan/apply are reproducible. The lockfile is not git-ignored (only
  `.terraform/`, `*.tfstate*`, and `*.tfvars` are).
- **Destroy**: `terraform -chdir=ops/terraform destroy -var-file=example.tfvars` tears down only this
  cluster. The backend is `local` (state lives in `ops/terraform/terraform.tfstate`, which is
  git-ignored) — switch to S3 + DynamoDB locking before any real usage, per the comment in
  `providers.tf`.
- **Input validation**: required inputs (`subnet_ids`, `security_group_id`) are enforced in
  `variables.tf`; apply must always pass an explicit `-var-file` so defaults never mask a mistake.

## Decisions & gaps (documented, deferred)

- **No Schema Registry yet** — Phase-1 services run `schema.resource=true` (avsc shipped on the
  classpath), so MSK alone is sufficient. Registry + Confluent-format serde is a roadmap item.
- **No client auth configured** in the scaffold; production must enable SASL (SCRAM/IAM) + TLS and
  tighten the security group (example config is commented in `main.tf`).
- Services run in the same VPC/subnets as the cluster (no public access).