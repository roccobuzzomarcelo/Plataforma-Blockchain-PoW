# ── Red VPC ──────────────────────────────────────────

resource "google_compute_network" "blockchain_vpc" {
  name                    = "blockchain-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "blockchain_subnet" {
  name          = "blockchain-subnet"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.blockchain_vpc.id

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.1.0.0/16"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.2.0.0/20"
  }
}

# ── Clúster GKE ──────────────────────────────────────

resource "google_container_cluster" "blockchain_cluster" {
  name     = var.cluster_name
  location = var.zone

  network    = google_compute_network.blockchain_vpc.name
  subnetwork = google_compute_subnetwork.blockchain_subnet.name

  remove_default_node_pool = true
  initial_node_count       = 1

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  deletion_protection = false
}

# ── Node Pool: Infraestructura (Redis + RabbitMQ) ────

resource "google_container_node_pool" "infra_pool" {
  name       = "infra-pool"
  location   = var.zone
  cluster    = google_container_cluster.blockchain_cluster.name
  node_count = 1

  node_config {
    machine_type = "e2-medium"
    disk_size_gb = 30
    disk_type    = "pd-standard"

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    labels = {
      pool = "infra"
    }

    taint {
      key    = "pool"
      value  = "infra"
      effect = "NO_SCHEDULE"
    }
  }
}

# ── Node Pool: Aplicaciones ──────────────────────────

resource "google_container_node_pool" "apps_pool" {
  name       = "apps-pool"
  location   = var.zone
  cluster    = google_container_cluster.blockchain_cluster.name
  node_count = 2

  node_config {
    machine_type = "e2-medium"
    disk_size_gb = 30
    disk_type    = "pd-standard"

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    labels = {
      pool = "apps"
    }
  }

  autoscaling {
    min_node_count = 1
    max_node_count = 4
  }
}