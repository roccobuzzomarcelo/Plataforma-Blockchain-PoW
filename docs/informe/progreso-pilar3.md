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

## Distribución real del trabajo entre workers

Con las 2 réplicas ya validadas, apareció un problema más de fondo: los
workers competían todos por **el mismo rango exacto de nonces**
(`TaskPublisher` usaba un exchange *fanout*, cada worker con su propia
cola exclusiva recibía copia de toda la tarea). Con esto, agregar más
workers no distribuía trabajo, lo duplicaba — el que tuviera más CPU
disponible en su nodo ganaba casi siempre, sin importar cuántos
mineros hubiera.

Se migró a una **cola compartida** (`mining.tasks`, durable, con
`prefetchCount=1`): el coordinator publica varios *chunks* del mismo
bloque, con rangos de nonce disjuntos, y cualquier worker libre toma
el próximo. Esto trajo un segundo hallazgo: con varios chunks por
bloque, más de uno podía resolver casi al mismo tiempo, así que el CAS
del consenso pasó de estar indexado por `taskId` a estarlo por
`blockIndex` — solo el primer chunk en llegar confirma el bloque, sin
importar cuál de los chunks fue.

**Validado en local**: 20 bloques, reparto 10/10 entre 2 workers,
~300ms por bloque (antes del fix, un worker ganaba 8 de 10 con
tiempos de 19-38s por la cola formada). **Validado en GKE** con 3
workers reales (2 pods + la VM minera externa): reparto parejo,
confirmado con nonces de rangos distintos por worker.

## Configuración de dificultad y fragmentación por pedido

`/api/pool/flush` y `/api/coordinator/mine-block` aceptan ahora
`prefix`, `chunks` y `range` opcionales (antes fijos por variable de
entorno), y `transaction-pool` suma `/api/pool/test/generate` y
`/api/pool/test/clear` (apagados por defecto,
`POOL_TEST_ENDPOINTS=false`) para cargar N transacciones sintéticas
sin pasar una por una por `blockchain-api`. Es lo que permite variar
la fragmentación y el rango bloque a bloque para las pruebas de 3.3,
sin reiniciar pods entre mediciones.

## VM minera externa: puesta en marcha

Activarla reveló dos bugs de infraestructura, ninguno relacionado con
el código de la aplicación:

- **`docker-credential-gcr` fallaba en el arranque**: Container-Optimized
  OS monta `/` como solo lectura, y el script intentaba escribir en
  `/root/.docker`. Arreglo: `export HOME=/tmp` antes de configurar las
  credenciales, en el `startup-script`.
- **Redeployment del template**: al cambiar el `startup-script` hizo
  falta un `tofu apply` (el template es inmutable, se reemplaza
  completo) antes de que la VM pudiera arrancar bien.

Con eso resuelto, la VM (`external-vm-<nombre>`) se conecta a
`mining.tasks` como un worker más, indistinguible para el sistema de
un pod. Confirmado ganando bloques reales contra los workers en pods.

## Pruebas de carga y escalabilidad (sección 3.3)

Herramienta: `pilar3-infra/test-load/run_experiments.ps1` +
`plot_results.py`, con 4 escenarios (prefijo, fragmentación, bulk,
GPU simulada). El runner abre sus propios túneles, verifica que
apunta al clúster real (no a datos viejos de otra sesión), y detecta
si el pool o el coordinator corren una imagen vieja antes de medir
nada.

**Experimento de prefijos — completo, 1 a 7 caracteres, sin
timeouts.** Confirma la curva esperada: tiempos por debajo de 2,6s
para 1-4 caracteres (dominado por latencia de red/mensajería, no por
cómputo), y a partir de 5 empieza a notarse la dificultad real (x16
por carácter): 5s, 7,6s, 293s para el 7. El 8 queda para cuando haya
más margen de tiempo (puede tardar mucho más, con alta varianza).

**Fragmentación, bulk y GPU simulada**: todavía sin una corrida
limpia — las primeras dos ventanas de prueba coincidieron con
incidentes reales de infraestructura (ver abajo), no con fallas del
código. Pendientes para retomar.

### Hallazgo: RabbitMQ reencola trabajo obsoleto sin límite

Cuando `kubectl rollout restart` mata un worker a mitad de un chunk,
RabbitMQ reencola automáticamente el mensaje no confirmado — protocolo
AMQP estándar. El problema: ese chunk puede pertenecer a un bloque ya
descartado (de una corrida de prueba anterior), y un worker nuevo lo
retoma igual, quemando CPU en un resultado que el coordinator va a
rechazar apenas llegue (`"tarea no encontrada"`). Sin un
`x-message-ttl` en la cola, no hay nada que lo mate por sí solo —
puede sobrevivir a varios reinicios, reencolándose cada vez. Detectado
con `kubectl top pods` (CPU al 99% con la cola aparentemente
consumida) y confirmado en los logs del coordinator (chunks de un
bloque descartado publicados minutos antes de que el problema
apareciera). Mitigación aplicada en el momento: escalar
`deployment/worker` (y la VM) a 0, recién ahí `purge_queue` (que no
toca mensajes ya entregados a un consumidor, solo los que esperan en
cola), y reconectar. Pendiente como mejora de código: `x-message-ttl`
en la declaración de `mining.tasks`.

### Incidente real de infraestructura: caída de nodo en pleno experimento

En medio de una corrida, un nodo de `apps-pool` pasó a
`NotReady,SchedulingDisabled` (taint automático
`node.kubernetes.io/unreachable`) — autoreparación normal de GKE, no
causada por nada del proyecto. El pod nuevo del rollout quedó
`Pending` un rato (`Insufficient cpu`, `FailedScheduling`) hasta que
GKE terminó de reemplazar el nodo. El resultado de esa ventana de
tiempo se descartó por no ser confiable, pero queda como evidencia
real (no buscada) de tolerancia a fallas de infraestructura en un
sistema distribuido.

## Pipelines de CI/CD (sección 3.2)

Implementadas con GitHub Actions (`.github/workflows/`, documentadas
también en `pilar3-infra/pipelines/`), dos identidades distintas por
mínimo privilegio: `opentofu-sa` (ya existente) solo para Pipeline 1;
`github-actions-ci` (nueva, `container.developer` +
`artifactregistry.writer` + `compute.instanceAdmin.v1`) para las
otras tres.

| Pipeline | Qué hace | Dispara con |
| --- | --- | --- |
| 1 — Infraestructura | `tofu plan`/`apply` de VPC, cluster, node pools | push → solo plan; botón manual → plan + apply |
| 2 — Servicios | `kubectl apply` de namespaces, secrets, Redis, RabbitMQ | push a `main` |
| 3 — Aplicaciones | build + push de las 5 imágenes, apply, rollout restart | push a `main` |
| 4 — VMs mineras | ajusta o recrea la VM minera externa | solo botón manual, `size` como parámetro |

Pipeline 1 nunca aplica sola (solo `plan` en push/PR) y Pipeline 4 es
deliberadamente manual, no un autoscaler — las dos son decisiones de
diseño documentadas en `pilar3-infra/pipelines/README.md`, no
limitaciones.

**Dos bugs de autenticación encontrados poniendo Pipeline 1 en
marcha**, ninguno relacionado con el resto del código:

1. `provider.tf` lee las credenciales con `file(var.credentials_file)`,
   cuyo default es una ruta local de Windows (`~/.gcp/opentofu-key.json`)
   — no existe en el runner de GitHub. Arreglo: un paso que escribe el
   secret en esa misma ruta antes de correr OpenTofu.
2. **Estado de OpenTofu sin backend remoto**: el `.tfstate` vivía solo
   en el disco local (en `.gitignore`), invisible para CI. El primer
   `tofu plan` en GitHub Actions mostró *"11 to add"* — quería recrear
   toda la infraestructura ya existente, porque no tenía forma de saber
   que ya estaba creada. Se migró el estado a un bucket de GCS
   (`sdypp-rocco-tofu-state`, versionado) con `tofu init -migrate-state`,
   validado con un `plan` limpio tanto en local como en CI antes de
   permitir ningún `apply` real desde la pipeline. El backend `gcs` no
   reutiliza las credenciales del `provider` (son dos sistemas de auth
   separados en OpenTofu) y los bloques `backend` no aceptan variables,
   así que la ruta de credenciales quedó literal en `versions.tf`.

**Las 4 pipelines corrieron verdes y se verificaron contra el estado
real del clúster** (no solo el check verde de GitHub): Pipeline 2 con
`kubectl get pods` sobre `blockchain-infra`, Pipeline 3 con la edad de
los 10 pods coincidiendo con la hora de la corrida, Pipeline 1 con un
`tofu plan` idéntico en CI y en local, Pipeline 4 con
`gcloud compute instance-groups managed list-instances` confirmando
el tamaño pedido.

---

## Próximos pasos

- [x] ~~Activar la VM minera externa~~ — hecho, valida como worker real
- [x] ~~Pipelines CI/CD~~ — las 4 implementadas y verificadas
- [ ] Bandera de "GPU simulada" en el worker (keep-alive automático,
      hoy solo se simula a mano con un loop de `curl`)
- [ ] Retomar 3.3: fragmentación, bulk y GPU simulada (sin corrida
      limpia todavía); repetir `prefix` con las repeticiones completas
      para desvío estándar
- [ ] `x-message-ttl` en `mining.tasks` (ver hallazgo de RabbitMQ arriba)
- [ ] `lifecycle.ignore_changes` en `target_size` del MIG de mineros
      (mismo drift que ya se resolvió para `node_count` de `apps-pool`)
- [ ] **Informe final** — todavía sin redactar
- [ ] Ensayo completo de la demo en vivo
