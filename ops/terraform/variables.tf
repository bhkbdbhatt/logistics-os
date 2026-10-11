variable "region" {
  description = "AWS region for the Kafka cluster"
  type        = string
  default     = "eu-west-1"
}

variable "environment" {
  description = "Deployment environment (dev/staging/prod)"
  type        = string
  default     = "dev"
}

variable "cluster_name" {
  description = "MSK cluster name"
  type        = string
  default     = "logistics-os-kafka"
}

variable "kafka_version" {
  description = "Apache Kafka version running on MSK (must stay in lockstep with the project kafka BOM)"
  type        = string
  default     = "3.7.1"
}

variable "broker_nodes" {
  description = "Number of broker nodes (MSK minimum is 2, production uses 3+ AZ spread)"
  type        = number
  default     = 3
}

variable "broker_instance_type" {
  description = "Broker instance type"
  type        = string
  default     = "kafka.m5.large"
}

variable "broker_ebs_size_gb" {
  description = "Per-broker EBS volume size in GB (tiered to mirror events.md retention)"
  type        = number
  default     = 100
}

variable "subnet_ids" {
  description = "Private subnet IDs for the broker nodes (one per AZ in use)"
  type        = list(string)
  nullable    = false
}

variable "security_group_id" {
  description = "Security group allowed to reach the cluster (producers/consumers group)"
  type        = string
  nullable    = false
}

variable "kms_key_arn" {
  description = "Optional KMS key ARN for at-rest encryption; defaults to the AWS-managed key when empty"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Common resource tags"
  type        = map(string)
  default = {
    project     = "logistics-os"
    managed-by  = "terraform"
  }
}