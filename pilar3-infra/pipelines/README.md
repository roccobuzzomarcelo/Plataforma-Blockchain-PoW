# Pipelines de CI/CD (seccion 3.2)

Los 4 archivos de esta carpeta son **copias identicas, solo para documentacion**,
de los workflows que GitHub Actions ejecuta de verdad, ubicados en
`.github/workflows/` (GitHub solo corre workflows desde esa ruta exacta).

| Pipeline | Que hace | Dispara con |
| --- | --- | --- |
| `pipeline1-infra.yml` | OpenTofu: VPC, subred, cluster GKE, node pools, plantilla de las VMs mineras | push/PR a `pilar3-infra/opentofu/**` -> solo `plan`. Boton manual -> `plan` + `apply` |
| `pipeline2-service.yml` | Namespaces, Secrets, StatefulSets de Redis y RabbitMQ | push a `main` en `pilar3-infra/k8s/{namespaces,secrets}.yaml` o `infra/**` |
| `pipeline3-apps.yml` | Build + push de las 5 imagenes, `kubectl apply` y rollout de los 5 Deployments | push a `main` en `pilar2-services/**` o `pilar3-infra/k8s/apps/**` |
| `pipeline4-workers.yml` | Ajusta la cantidad de VMs mineras externas (o las recrea) | solo boton manual, con un input `size` |

## Por que Pipeline 1 no aplica solo

Los otros tres hacen `kubectl apply`, declarativo e idempotente: aplicar de
nuevo el mismo YAML no hace nada si no cambio nada, y revertir es tan simple
como aplicar la version anterior. Un `tofu apply` sobre VPC/cluster no tiene
esa propiedad -un error ahi puede ser mucho mas caro de deshacer-, asi que
esta pipeline solo hace `plan` (chequeo, sin efecto) en cada push, y el
`apply` real queda atras de un boton manual con confirmacion explicita
(`workflow_dispatch` + `apply: true`). Es la misma disciplina -revisar el
plan antes de aplicar- que se uso a mano durante todo el desarrollo.

## Por que Pipeline 4 es manual y no un autoscaler

Es una decision de diseño documentada, no una limitacion. Con solo 0-1 VM
de base, un autoscaler por CPU no tiene forma de "despertar" un grupo que
esta en 0 (no hay ninguna instancia de la que medir uso de CPU). Iniciar o
destruir instancias "cuando sea necesario" (P5 de la guia) queda como una
decision operativa explicita en lugar de un lazo de control automatico -este
pipeline es exactamente ese mecanismo: el mismo `gcloud resize` que se uso a
mano durante las pruebas, ahora como un boton versionado y con historial.

## Configuracion necesaria (una sola vez)

Ver `docs/ci-cd-setup.md` en la raiz del repo para los comandos exactos:
crear la service account de CI, darle los roles minimos que necesita, y
cargar las dos claves como Secrets del repositorio en GitHub.
