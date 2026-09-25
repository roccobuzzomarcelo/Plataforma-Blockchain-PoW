output "cluster_name" {
  value = google_container_cluster.blockchain_cluster.name
}

output "cluster_endpoint" {
  value     = google_container_cluster.blockchain_cluster.endpoint
  sensitive = true
}

output "region" {
  value = var.region
}

output "zone" {
  value = var.zone
}

output "get_credentials_command" {
  value = "gcloud container clusters get-credentials ${var.cluster_name} --zone ${var.zone} --project ${var.project_id}"
}
output "rabbitmq_internal_ip" {
  description = "IP interna fija para el Service de RabbitMQ (usar como loadBalancerIP)"
  value       = var.rabbitmq_internal_ip
}

output "external_miner_resize_command" {
  description = "Comando para prender/apagar VMs mineras en vivo (demo de 3.3)"
  value       = "gcloud compute instance-groups managed resize external-miner-mig --zone=${var.zone} --project=${var.project_id} --size=<N>"
}

output "external_miner_ssh_command" {
  description = "Conectarse a una VM minera vía IAP (no tiene IP pública)"
  value       = "gcloud compute instance-groups managed list-instances external-miner-mig --zone=${var.zone} --project=${var.project_id}  # para ver el nombre, luego: gcloud compute ssh <nombre> --zone=${var.zone} --project=${var.project_id} --tunnel-through-iap"
}
