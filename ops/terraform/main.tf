# Phase-1 Kafka cluster via AWS MSK.
#
# Topics are NOT created here: a deploy-time bootstrap opens kafka-topics against the MSK
# bootstrap brokers using the same topic catalog as local-compose (ops/local-compose/topics.lst).
# Phase-1 services run with schema.resource=true, so no Schema Registry is provisioned yet
# (roadmap: registry + Confluent-format serde in a later tranche).

module "msk" {
  source  = "terraform-aws-modules/msk/aws"
  version = "~> 4.0"

  name           = var.cluster_name
  kafka_version  = var.kafka_version
  number_of_broker_nodes = var.broker_nodes

  broker_node_client_subnets  = var.subnet_ids
  broker_node_instance_type   = var.broker_instance_type
  broker_node_security_groups = [var.security_group_id]
  broker_node_ebs_volume_size = var.broker_ebs_size_gb

  # At-rest encryption; defaults to AWS-managed key unless a customer key is supplied.
  encryption_info_encryption_at_rest_kms_key_arn = var.kms_key_arn

  # Client authentication is intentionally left unconfigured in the scaffold. Production
  # deployments must enable SASL/IAM + TLS client auth and restrict the security group.
  # e.g.
  #   client_authentication_sasl_scram = { enabled = true }
  #   client_authentication_sasl_iam    = { enabled = true }

  tags = var.tags
}