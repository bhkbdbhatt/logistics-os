region            = "eu-west-1"
environment       = "dev"
cluster_name      = "logistics-os-kafka"
kafka_version     = "3.7.1"
broker_nodes      = 3
broker_instance_type = "kafka.m5.large"

# Replace with your dev VPC values.
subnet_ids = [
  "subnet-0aaaa0000aaaa0000",
  "subnet-0bbbb0000bbbb0000",
  "subnet-0cccc0000cccc0000",
]
security_group_id = "sg-0dddd0000dddd0000"

# Client authentication (M4). Defaults are all off (scaffold). For real environments enable
# exactly one of SCRAM / IAM, optionally add mutual TLS, and tighten the security group.
# client_auth_sasl_scram = true
# client_auth_sasl_iam   = false
# client_auth_tls        = false
# client_auth_tls_ca_arns = ["arn:aws:acm-pca:eu-west-1:000000000000:certificate-authority/xxxx"]