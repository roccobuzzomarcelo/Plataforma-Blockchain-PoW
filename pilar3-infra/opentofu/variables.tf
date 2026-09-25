variable "project_id" {
  description = "ID del proyecto GCP"
  type        = string
  default     = "sdypp-rocco"
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

# ── VMs externas al clúster (sección 3.1) ────────────

variable "external_miner_count" {
  description = "Cantidad de VMs mineras externas al clúster. 0 = apagadas. Se sube/baja a mano para simular el ingreso y egreso de nodos (sección 3.3 de la guía)."
  type        = number
  default     = 0
}

variable "external_miner_machine_type" {
  description = "Tipo de máquina de las VMs mineras externas"
  type        = string
  default     = "e2-medium"
}

variable "worker_image" {
  description = "Tag de la imagen del worker en Artifact Registry (repo blockchain-repo)"
  type        = string
  default     = "worker:latest"
}

# IP fija dentro de blockchain-subnet para el Service de RabbitMQ
# (Internal Load Balancer). Se define acá para que las VMs mineras y el
# manifiesto de Kubernetes (infra/rabbitmq.yaml, loadBalancerIP) usen
# siempre el mismo valor. 10.0.0.100 queda lejos del rango bajo que GKE
# usa para las IPs primarias de los nodos.
variable "rabbitmq_internal_ip" {
  description = "IP interna fija para el Service de RabbitMQ"
  type        = string
  default     = "10.0.0.100"
}

variable "rabbitmq_user" {
  description = "Usuario de RabbitMQ (debe coincidir con el Secret de Kubernetes)"
  type        = string
  default     = "admin"
}

variable "rabbitmq_password" {
  description = "Password de RabbitMQ (debe coincidir con el Secret de Kubernetes)"
  type        = string
  default     = "admin123"
  sensitive   = true
}
