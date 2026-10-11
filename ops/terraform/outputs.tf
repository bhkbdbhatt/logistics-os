output "cluster_name" {
  description = "MSK cluster name"
  value       = module.msk.cluster_name
}

output "cluster_arn" {
  description = "MSK cluster ARN"
  value       = module.msk.cluster_arn
}

output "bootstrap_brokers" {
  description = "Plaintext bootstrap broker string (for ServiceConnector/kafka-topics tooling inside the VPC)"
  value       = module.msk.bootstrap_brokers
}

output "bootstrap_brokers_tls" {
  description = "TLS bootstrap broker string"
  value       = module.msk.bootstrap_brokers_tls
}

output "zookeeper_connect_string" {
  description = "ZooKeeper connect string (management only; cluster runs Kafka via the MSK control plane)"
  value       = try(module.msk.zookeeper_connect_string, "")
}