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

  # Client authentication (M4). All default to disabled in the scaffold; enable for real
  # environments. SCRAM and IAM are mutually exclusive -- set only one. Pair with a tightened
  # security group (var.security_group_id) and TLS in-transit.
  client_authentication_sasl_scram = var.client_auth_sasl_scram ? { enabled = true } : null
  client_authentication_sasl_iam   = var.client_auth_sasl_iam ? { enabled = true } : null
  client_authentication_tls = var.client_auth_tls ? {
    enabled                   = true
    certificate_authority_arns = var.client_auth_tls_ca_arns
  } : null

  tags = var.tags
}