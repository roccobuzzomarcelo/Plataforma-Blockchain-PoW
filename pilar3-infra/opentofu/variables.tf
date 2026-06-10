variable "project_id" {
  description = "ID del proyecto GCP"
  type        = string
  default     = "blockchain-unlu-2026"
}

variable "region" {
  description = "Región de GCP"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona de GCP"
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  description = "Nombre del clúster GKE"
  type        = string
  default     = "blockchain-cluster"
}

variable "credentials_file" {
  description = "Path al archivo de credenciales del Service Account"
  type        = string
  default     = "~/.gcp/opentofu-key.json"
}