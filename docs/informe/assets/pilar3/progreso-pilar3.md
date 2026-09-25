# Pilar 3 — Progreso de Despliegue

## Estado actual: Plataforma completa desplegada en GKE, con 2 réplicas por servicio ✅

---

## Lo que se hizo

### 1. Dockerización de servicios

Todos los servicios del Pilar 2 fueron dockerizados con imágenes optimizadas:

- Base de build: `maven:3.9.9-eclipse-temurin-21`
- Base de runtime: `eclipse-temurin:21-jre-jammy`
- Frontend: `node:22-alpine` + `nginx:alpine`, con nginx haciendo de reverse
  proxy hacia `blockchain-api` y `transaction-pool` (rutas relativas, sin
  URLs de `localhost` hardcodeadas — el mismo build sirve para Docker
  Compose, minikube o GKE sin cambios).

### 2. Docker Compose local

Validado con un smoke test end-to-end automatizado
(`pilar2-services/scripts/smoke-test.ps1`): salud de los 8 servicios,
proxy de nginx (REST + WebSocket con STOMP), validaciones de entrada,
2 rondas de minado con integridad de cadena verificada, y persistencia
de Redis ante una caída abrupta (`docker kill`). Este último punto llevó
a activar AOF en Redis (`--appendonly yes`): sin él, un `SIGKILL` perdía
la cadena completa (3→0 bloques); con AOF, la sobrevive (3→3).

### 3. Google Cloud Platform

- Proyecto: `sdypp-rocco` (cuenta prestada, ver nota abajo)
- Región: `us-central1` / Zona: `us-central1-a`
- Billing vinculado, con alerta de presupuesto configurada
- APIs habilitadas: container, compute, iam, artifactregistry, cloudresourcemanager

> **Nota sobre el proyecto GCP:** el despliegue arrancó en un proyecto
> propio (`blockchain-unlu-2026`), pero el trial de esa cuenta venció
> antes de la fecha de entrega. Se migró todo a `sdypp-rocco`, un
> proyecto en una cuenta prestada, replicando la misma infraestructura
> con OpenTofu. El proceso quedó documentado como parte del trabajo con
> infraestructura como código: reconstruir el entorno completo en un
> proyecto nuevo fue cuestión de cambiar una variable y volver a correr
> `tofu apply`.

### 4. Service Account OpenTofu

- SA: `opentofu-sa@sdypp-rocco.iam.gserviceaccount.com`
- Roles: `container.admin`, `compute.admin`, `iam.serviceAccountUser`,
  `artifactregistry.admin`, `iam.serviceAccountAdmin`,
  `resourcemanager.projectIamAdmin` (los dos últimos se agregaron cuando
  hizo falta que el propio OpenTofu pudiera crear un Service Account
  nuevo para las VMs mineras externas, ver punto 6)
- Credenciales guardadas en `~/.gcp/opentofu-key.json` (NO en el repo)

### 5. Infraestructura GKE con OpenTofu

Archivos en `pilar3-infra/opentofu/`:

- `versions.tf` — provider hashicorp/google ~> 6.0
- `variables.tf` — variables del proyecto, incluidas las de las VMs
  mineras externas
- `provider.tf` — configuración del provider
- `main.tf` — VPC, subnet (con Private Google Access habilitado), clúster
  GKE, node pools
- `outputs.tf` — outputs del clúster y comandos útiles (credenciales,
  resize de las VMs mineras)
- `external-miner.tf` — VMs externas al clúster (ver punto 6)

Recursos del clúster:

| Recurso | Detalle |
| ---------------- | ------------------------------------------------- |
| VPC | blockchain-vpc |
| Subnet | blockchain-subnet (10.0.0.0/24, Private Google Access) |
| Clúster GKE | blockchain-cluster (v1.35.8) |
| Node Pool infra | 1 nodo e2-medium, taint `pool=infra:NoSchedule` (Redis + RabbitMQ) |
| Node Pool apps | 2-4 nodos e2-medium, autoscaling min=2 max=4 |

### 6. VMs mineras externas al clúster (sección 3.1)

La guía pide explícitamente "máquinas virtuales externas al clúster
dedicadas a tareas de procesamiento intensivo", como pieza de
arquitectura separada de Kubernetes. Se implementó con:

- Un `google_compute_instance_template` sobre Container-Optimized OS,
  sin IP pública, que levanta la misma imagen Docker del `worker` vía
  startup-script.
- Un `google_compute_instance_group_manager` con `target_size`
  controlable por variable (arranca en 0).
- Una IP interna fija (`10.0.0.100`) para que estas VMs -que no pueden
  resolver DNS interno de Kubernetes- lleguen a RabbitMQ.
- Un Service Account propio (`external-miner-vm`), con el único permiso
  de leer imágenes de Artifact Registry.
- Firewall para RabbitMQ (solo dentro de la VPC) y para SSH vía
  Identity-Aware Proxy (sin exponer el puerto 22).

Subir y bajar `target_size` (`gcloud compute instance-groups managed
resize external-miner-mig --size=N`) es la forma de "iniciar/destruir
instancias cuando sea necesario" (P5) y de simular el ingreso y egreso
de mineros para las pruebas de la sección 3.3.

### 7. Artifact Registry

- Repositorio: `blockchain-repo` en `us-central1`
- URL: `us-central1-docker.pkg.dev/sdypp-rocco/blockchain-repo`

Imágenes publicadas (`docker push`, todas actualizadas tras la
migración de proyecto):

| Imagen              |
| ------------------- |
| blockchain-api      |
| coordinator         |
| transaction-pool    |
| worker              |
| blockchain-frontend |

### 8. Manifiestos de Kubernetes (`pilar3-infra/k8s/`)

| Archivo | Contenido |
| -------------------------- | --------- |
| `namespaces.yaml` | `blockchain-infra`, `blockchain-apps` |
| `secrets.yaml` | Credenciales de Redis y RabbitMQ, duplicadas en ambos namespaces (un Secret no cruza namespaces) |
| `infra/redis.yaml` | StatefulSet con `--appendonly yes` + PVC 2Gi, Service headless |
| `infra/rabbitmq.yaml` | StatefulSet + PVC 2Gi; Service `LoadBalancer` interno (`10.0.0.100`) que sirve DNS normal para los pods **y** acceso directo desde las VMs mineras externas |
| `apps/blockchain-api.yaml` | Deployment 2 réplicas + Service |
| `apps/coordinator.yaml` | Deployment 2 réplicas + Service |
| `apps/transaction-pool.yaml` | Deployment 2 réplicas + Service (con `RABBITMQ_VHOST=/` explícito, ver incidencias) |
| `apps/worker.yaml` | Deployment 2 réplicas + Service; `WORKER_ID` tomado del nombre del pod vía Downward API |
| `apps/frontend.yaml` | Deployment 2 réplicas + Service `ClusterIP` (acceso vía `kubectl port-forward`, sin balanceador público) |

Redis y RabbitMQ quedan fijados a `infra-pool` con `nodeSelector` +
`toleration` sobre el taint que ese node pool tiene en OpenTofu; las
apps usan `nodeSelector: pool: apps`.

Validación antes de aplicar: los 9 manifiestos (20 recursos) pasaron
`kubeconform` contra el esquema real de Kubernetes 1.35, la misma
versión del clúster.

---

## Incidencias resueltas durante el despliegue

Documentadas porque son evidencia real de troubleshooting en
Kubernetes, no solo del "camino feliz":

### RabbitMQ en `CrashLoopBackOff`

**Síntoma:** el pod de RabbitMQ arrancaba, aparecía `Running`, y se
reiniciaba solo unos ~106 segundos después, en loop.

**Diagnóstico:** los logs (`kubectl logs --previous`) mostraban
`Server startup complete` sin ningún error, seguido de un `SIGTERM`
limpio — no era el proceso cayéndose, era Kubernetes matándolo.

**Causa:** el `livenessProbe` usaba `rabbitmq-diagnostics ping`, un
chequeo que levanta un nodo Erlang efímero para consultar al
principal y rutinariamente tarda más de 1 segundo. El
`timeoutSeconds` no estaba fijado, así que tomaba el default de
Kubernetes (1s), y el probe fallaba por timeout aunque el servidor
estuviera sano. 3 fallos seguidos → reinicio.

**Arreglo:** `timeoutSeconds: 10` en ambos probes (`readinessProbe` y
`livenessProbe`), más `-q` en los comandos de diagnóstico.

### `ErrImagePull` en los 5 servicios de `apps/`

**Síntoma:** los 10 pods de `blockchain-apps` quedaban en
`ImagePullBackOff`.

**Diagnóstico:** `kubectl describe pod` mostraba `403 Forbidden` al
pedir el token OAuth contra Artifact Registry — no un `404` de
"la imagen no existe".

**Causa:** los *nodos* de GKE (no `opentofu-sa`) corren con la cuenta
de servicio default de Compute Engine del proyecto. El scope
`cloud-platform` que se les dio en `main.tf` abre la puerta a pedir
cualquier API, pero no alcanza sin el rol de IAM correspondiente —
nunca se le dio `artifactregistry.reader` a esa cuenta.

**Arreglo:**

```bash
gcloud projects add-iam-policy-binding sdypp-rocco \
  --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" \
  --role="roles/artifactregistry.reader"
```

### Permisos insuficientes en `opentofu-sa` (dos veces)

Al crear el Service Account de las VMs mineras externas, `tofu apply`
falló dos veces por permisos que el SA de OpenTofu no tenía:
`iam.serviceAccounts.create` (le faltaba `roles/iam.serviceAccountAdmin`)
y, después, el permiso para escribir la política de IAM del proyecto
(le faltaba `roles/resourcemanager.projectIamAdmin`). Ninguno de los
roles asignados originalmente los cubría, porque en el diseño inicial
no se había previsto que OpenTofu tuviera que crear *otro* Service
Account además de administrar el clúster.

---

## Soporte para 2 réplicas por servicio

La guía exige al menos 2 réplicas por servicio (sección 3, plataforma
escalable). Antes de desplegar, se revisó qué estado compartido rompía
con más de una réplica:

- **`GenesisService`** (coordinator): sin protección, 2 réplicas
  arrancando a la vez podían crear cada una su propio bloque génesis.
  Se agregó un lock distribuido en Redis (`SET NX`, 30s) antes de crear
  el génesis.
- **`BlockSchedulerService`** (transaction-pool): el `@Scheduled` de
  60s corría independiente en cada réplica, con riesgo de armar el
  mismo bloque dos veces. Mismo patrón de lock, esta vez compartido
  también con el endpoint manual `/flush`.
- **`ConsensusService`** (coordinator) — el hallazgo más importante:
  el estado del consenso (qué tarea está activa, si ya se resolvió)
  vivía en un `ConcurrentHashMap` en memoria, local a cada instancia.
  Como las 2 réplicas del coordinator escuchan la MISMA cola de
  RabbitMQ (`mining.results`) como consumidores en competencia, un
  resultado podía llegarle a una réplica distinta de la que registró la
  tarea — esa réplica no encontraba la tarea en su mapa local y
  descartaba el resultado en silencio, sin confirmar el bloque. Se
  migró todo el estado de consenso a Redis (CAS distribuido con
  `SET NX` sobre `task:resolved:<id>`, tarea completa guardada en
  `task:active:<id>`), así cualquier réplica puede confirmar el bloque
  sin importar cuál registró la tarea originalmente.

## Evidencia de funcionamiento (demo en GKE, 25/9)

Con los 10 pods (`blockchain-api` x2, `coordinator` x2,
`transaction-pool` x2, `worker` x2, `frontend` x2) en `1/1 Running`:

- **Bloque #1**: minado a pedido (botón "Forzar minado"), ganador
  `worker-5ddd75f488-cr99k`.
- **Bloque #2**: minado **automáticamente** por el scheduler de 60s,
  sin intervención manual — confirma que el lock del
  `BlockSchedulerService` funciona entre las 2 réplicas de
  `transaction-pool` sin duplicar el ciclo. Ganador:
  `worker-5ddd75f488-fwjzx` (un pod distinto al del bloque anterior,
  confirmando que la competencia entre réplicas de worker realmente
  alterna).
- Cadena encadenada correctamente (`previousHash` de cada bloque
  coincide con el `blockHash` del anterior), WebSocket en vivo (panel
  "Eventos en tiempo real" mostrando la notificación push del bloque
  recién minado), sin recargar la página.
- Un único bloque génesis (no dos en conflicto), pese a que las 2
  réplicas del coordinator arrancaron juntas — evidencia práctica de
  que el lock de `GenesisService` funcionó en el arranque real.

---

## Próximos pasos

- [ ] Activar la VM minera externa para la demo (`external_miner_count = 1`)
      y verificar que participa del pool igual que los workers en pods
- [ ] Bandera de "GPU simulada" en el worker: enviar keep-alive a
      `/api/pool/miners/keepalive` para poder demostrar el ajuste de
      dificultad (P5/3.3) sin depender de una GPU real
- [ ] Pruebas de la sección 3.3: prefijo de 1 a 8 caracteres, tamaño de
      fragmentación del pool, ingreso/egreso de mineros — con gráficos
      comparativos
- [ ] Pipelines CI/CD (`pilar3-infra/pipelines/`, hoy vacíos)
- [ ] `test-load/load_test.sh` para las pruebas de carga de 1 a 100.000
      transacciones
