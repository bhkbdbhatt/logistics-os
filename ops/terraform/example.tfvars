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