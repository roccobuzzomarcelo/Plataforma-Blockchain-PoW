# Pilar 3 — Progreso de Despliegue

## Estado actual: Infraestructura GKE lista ✅

---

## Lo que se hizo

### 1. Dockerización de servicios

Todos los servicios del Pilar 2 fueron dockerizados con imágenes optimizadas:

- Base de build: `maven:3.9.8-eclipse-temurin-21`
- Base de runtime: `eclipse-temurin:21-jre-jammy`
- Frontend: `node:22-alpine` + `nginx:alpine`

Imágenes generadas:

| Imagen              | Tamaño |
| ------------------- | ------ |
| blockchain-api      | 467MB  |
| coordinator         | 470MB  |
| transaction-pool    | 470MB  |
| worker              | 449MB  |
| blockchain-frontend | 93.6MB |

### 2. Docker Compose local

Validado localmente con `docker compose up -d`.
8 contenedores corriendo: redis, rabbitmq, blockchain-api, coordinator,
transaction-pool, worker-1, worker-2, frontend.

### 3. Google Cloud Platform

- Proyecto: `blockchain-unlu-2026`
- Región: `us-central1` / Zona: `us-central1-a`
- Billing vinculado
- APIs habilitadas: container, compute, iam, artifactregistry

### 4. Service Account OpenTofu

- SA: `opentofu-sa@blockchain-unlu-2026.iam.gserviceaccount.com`
- Roles: container.admin, compute.admin, iam.serviceAccountUser, artifactregistry.admin
- Credenciales guardadas en `~/.gcp/opentofu-key.json` (NO en el repo)

### 5. Infraestructura GKE con OpenTofu

Archivos en `pilar3-infra/opentofu/`:

- `versions.tf` — provider hashicorp/google v6.50.0
- `variables.tf` — variables del proyecto
- `provider.tf` — configuración del provider
- `main.tf` — VPC, subnet, clúster GKE, node pools
- `outputs.tf` — outputs del clúster

Recursos creados (`tofu apply`):

| Recurso         | Detalle                               |
| --------------- | ------------------------------------- |
| VPC             | blockchain-vpc                        |
| Subnet          | blockchain-subnet (10.0.0.0/24)       |
| Clúster GKE     | blockchain-cluster (v1.35.3)          |
| Node Pool infra | 1 nodo e2-medium (Redis + RabbitMQ)   |
| Node Pool apps  | 2 nodos e2-medium con autoscaling 1-4 |

### 6. Artifact Registry

- Repositorio: `blockchain-repo` en `us-central1`
- URL: `us-central1-docker.pkg.dev/blockchain-unlu-2026/blockchain-repo`

Imágenes publicadas:

| Imagen              | Digest             |
| ------------------- | ------------------ |
| blockchain-api      | sha256:654373b...  |
| coordinator         | sha256:86883308... |
| transaction-pool    | sha256:f196c9b...  |
| worker              | sha256:aedfad07... |
| blockchain-frontend | sha256:f8708af...  |

---

## Próximos pasos

- [ ] Manifiestos Kubernetes (`pilar3-infra/k8s/`)
- [ ] Secrets de Kubernetes para Redis y RabbitMQ
- [ ] Despliegue de Redis y RabbitMQ en infra-pool
- [ ] Despliegue de microservicios en apps-pool
- [ ] Pipelines CI/CD con GitHub Actions
- [ ] Pruebas de carga y análisis de resultados
