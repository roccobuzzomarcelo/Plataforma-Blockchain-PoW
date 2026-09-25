# ── VMs externas al clúster para procesamiento intensivo (sección 3.1) ──
#
# La guía pide máquinas separadas del clúster de Kubernetes, dedicadas a
# minar. Viven en la misma VPC que el clúster GKE y hablan con RabbitMQ
# por una IP interna fija dentro de la subred (sin exponer nada a
# Internet). Corren la MISMA imagen Docker del worker que usan los pods
# de apps-pool; el comportamiento (competir por tareas, ganar o perder
# el CAS del consenso) es idéntico, solo cambia dónde corre el proceso.
#
# Arrancan apagadas (external_miner_count = 0). Subir y bajar ese número
# es la forma de "iniciar/destruir instancias cuando sea necesario"
# (P5) y de simular el ingreso y egreso de nodos (sección 3.3).

# Service Account dedicado: solo necesita LEER imágenes de Artifact
# Registry, nada más (principio de mínimo privilegio, separado del
# opentofu-sa que administra todo el proyecto).
resource "google_service_account" "external_miner" {
  account_id   = "external-miner-vm"
  display_name = "Service Account - VMs mineras externas"
}

resource "google_project_iam_member" "external_miner_artifact_reader" {
  project = var.project_id
  role    = "roles/artifactregistry.reader"
  member  = "serviceAccount:${google_service_account.external_miner.email}"
}

# RabbitMQ (dentro del clúster) y las VMs mineras (fuera del clúster)
# comparten la misma subred. Este firewall abre solo el puerto de
# RabbitMQ, solo dentro de esa subred: nada de esto llega a Internet.
resource "google_compute_firewall" "allow_rabbitmq_internal" {
  name    = "allow-rabbitmq-internal"
  network = google_compute_network.blockchain_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["5672"]
  }

  source_ranges = [google_compute_subnetwork.blockchain_subnet.ip_cidr_range]
}

# SSH vía Identity-Aware Proxy: permite `gcloud compute ssh --tunnel-through-iap`
# para debuggear la VM sin darle IP pública ni abrir el 22 a Internet.
resource "google_compute_firewall" "allow_iap_ssh" {
  name    = "allow-iap-ssh"
  network = google_compute_network.blockchain_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  # Rango fijo de Google para el relay de IAP (no es "abrir a cualquiera").
  source_ranges = ["35.235.240.0/20"]
}

resource "google_compute_instance_template" "external_miner" {
  name_prefix  = "external-miner-"
  machine_type = var.external_miner_machine_type
  region       = var.region

  disk {
    source_image = "projects/cos-cloud/global/images/family/cos-stable"
    auto_delete  = true
    boot         = true
    disk_size_gb = 20
  }

  network_interface {
    subnetwork = google_compute_subnetwork.blockchain_subnet.id
    # Sin access_config: la VM NO tiene IP pública. Llega a Artifact
    # Registry por Private Google Access (habilitado en la subred, ver
    # main.tf) y a RabbitMQ por la IP interna fija de abajo.
  }

  service_account {
    email  = google_service_account.external_miner.email
    scopes = ["cloud-platform"]
  }

  # Container-Optimized OS trae Docker y docker-credential-gcr
  # preinstalados: no hace falta instalar nada, solo autenticar contra
  # Artifact Registry y correr el contenedor del worker.
  metadata = {
    startup-script = <<-EOT
      #!/bin/bash
      set -e
      docker-credential-gcr configure-docker --registries=${var.region}-docker.pkg.dev

      docker run -d \
        --name worker \
        --restart=always \
        -p 8083:8083 \
        -e RABBITMQ_HOST=${var.rabbitmq_internal_ip} \
        -e RABBITMQ_PORT=5672 \
        -e RABBITMQ_USER=${var.rabbitmq_user} \
        -e RABBITMQ_PASS=${var.rabbitmq_password} \
        -e WORKER_ID=external-vm-$(hostname) \
        -e WORKER_THREADS=8 \
        ${var.region}-docker.pkg.dev/${var.project_id}/blockchain-repo/${var.worker_image}
    EOT
  }

  tags = ["external-miner"]

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_instance_group_manager" "external_miner" {
  name               = "external-miner-mig"
  zone               = var.zone
  base_instance_name = "external-miner"
  target_size        = var.external_miner_count

  version {
    instance_template = google_compute_instance_template.external_miner.id
  }
}
