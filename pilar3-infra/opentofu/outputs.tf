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