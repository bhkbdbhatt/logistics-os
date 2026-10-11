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

## Decisions & gaps (documented, deferred)

- **No Schema Registry yet** — Phase-1 services run `schema.resource=true` (avsc shipped on the
  classpath), so MSK alone is sufficient. Registry + Confluent-format serde is a roadmap item.
- **No client auth configured** in the scaffold; production must enable SASL (SCRAM/IAM) + TLS and
  tighten the security group (example config is commented in `main.tf`).
- Services run in the same VPC/subnets as the cluster (no public access).