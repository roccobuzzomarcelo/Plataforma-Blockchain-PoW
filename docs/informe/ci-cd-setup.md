# Configuracion de las pipelines de CI/CD (una sola vez)

Las 4 pipelines usan dos identidades distintas en GCP, con el mismo criterio
de minimo privilegio que ya se uso para la VM minera externa:

- **`opentofu-sa`** (ya existe, se creo para trabajar con OpenTofu a mano) ->
  la usa **solo Pipeline 1**, porque es la unica que administra el cluster
  en si (VPC, node pools).
- **`github-actions-ci`** (nueva, mas chica) -> la usan Pipelines 2, 3 y 4:
  `kubectl apply`, `docker push` y ajustar el tamano de las VMs mineras. No
  tiene permiso para tocar el cluster ni crear otros recursos de IAM.

## 1. Crear la service account de CI

```powershell
gcloud iam service-accounts create github-actions-ci `
  --display-name="GitHub Actions - CI/CD" `
  --project=sdypp-rocco

$SA = "github-actions-ci@sdypp-rocco.iam.gserviceaccount.com"

# kubectl apply / rollout (Pipelines 2 y 3)
gcloud projects add-iam-policy-binding sdypp-rocco --member="serviceAccount:$SA" --role="roles/container.developer"
# docker push a Artifact Registry (Pipeline 3)
gcloud projects add-iam-policy-binding sdypp-rocco --member="serviceAccount:$SA" --role="roles/artifactregistry.writer"
# resize/recreate del MIG de mineros (Pipeline 4)
gcloud projects add-iam-policy-binding sdypp-rocco --member="serviceAccount:$SA" --role="roles/compute.instanceAdmin.v1"
```

> `roles/compute.instanceAdmin.v1` es a nivel de todo el proyecto -mas
> permiso del que Pipeline 4 usa en la practica-. Para produccion
> convendria un rol a medida, limitado a `compute.instanceGroupManagers.*`
> sobre `external-miner-mig` nada mas. Se documenta aca como una
> simplificacion consciente dado el tiempo disponible, no como un
> descuido.

## 2. Generar las dos claves

```powershell
New-Item -ItemType Directory -Force ci-keys | Out-Null
gcloud iam service-accounts keys create ci-keys\opentofu-sa.json --iam-account=opentofu-sa@sdypp-rocco.iam.gserviceaccount.com
gcloud iam service-accounts keys create ci-keys\github-actions-ci.json --iam-account=$SA
```

## 3. Cargar los Secrets en GitHub

En el repo, **Settings -> Secrets and variables -> Actions -> Secrets ->
New repository secret**:

| Nombre | Contenido |
| --- | --- |
| `GCP_OPENTOFU_SA_KEY` | contenido completo de `ci-keys\opentofu-sa.json` |
| `GCP_CI_SA_KEY` | contenido completo de `ci-keys\github-actions-ci.json` |

Y en la pestana **Variables** (misma pantalla, al lado de Secrets) -esto no
es sensible, por eso va en Variables y no en Secrets-:

| Nombre | Valor |
|--------|-------|
| `GCP_PROJECT_ID` | `sdypp-rocco` |

Guardar `GCP_PROJECT_ID` como variable, no hardcodeado en cada workflow, es
la leccion de esta semana: el proyecto ya cambio de nombre dos veces
(`blockchain-unlu-2026` -> `sdypp-rocco-v2` -> `sdypp-rocco`). Si vuelve a
pasar, se cambia en un solo lugar de la interfaz de GitHub, sin tocar
ningun archivo.

## 4. Borrar las claves locales

```powershell
Remove-Item -Recurse -Force ci-keys
```

Una vez cargadas en GitHub, no hace falta conservarlas en el disco -son
credenciales permanentes, lo mismo que `~/.gcp/opentofu-key.json` ya
protegido en `.gitignore`.

## 5. Probar

**Pipeline 4** es la mas facil para el primer chequeo (no toca nada
critico, solo ajusta un numero): pestana **Actions** del repo ->
"Pipeline 4 - VMs mineras externas" -> **Run workflow** -> `size: 1`.
Si termina en verde, las credenciales estan bien cargadas.

**Pipeline 1** conviene probarla primero SIN aplicar (`apply: false`, el
default) para ver el `plan` en los logs antes de animarse a un `apply: true`
real desde CI.
