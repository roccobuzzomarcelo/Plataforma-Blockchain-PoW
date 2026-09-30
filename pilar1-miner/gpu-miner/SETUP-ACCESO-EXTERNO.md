# Preparar el acceso externo temporal (una sola vez)

## 1. Usuario restringido en RabbitMQ (correr una sola vez, ahora)

No uses el usuario `admin` para el worker externo. Este usuario nuevo solo
puede tocar recursos que empiecen con `mining.` -las dos colas y los dos
exchanges que necesita el worker GPU, nada más, ni siquiera puede ver el
resto de lo que hay en el vhost.

```powershell
$PASS = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 24 | % {[char]$_})
Write-Host "Guardá esta password, no se vuelve a mostrar: $PASS"

kubectl exec -n blockchain-infra rabbitmq-0 -- rabbitmqctl add_user gpu-external "$PASS"
kubectl exec -n blockchain-infra rabbitmq-0 -- rabbitmqctl set_permissions -p / gpu-external "^mining\." "^mining\." "^mining\."
```

Guardá el `$PASS` en algún lado seguro (un gestor de contraseñas, no en el
repo) — es lo que le vas a pasar al script en el clúster del profesor
como `RABBITMQ_PASS`.

## 2. El día de la prueba, recién cuando coordines con el profesor

```powershell
cd pilar1-miner\gpu-miner
kubectl apply -f expose-rabbitmq-temporal.yaml

# Esperar la IP publica (puede tardar 1-2 minutos)
kubectl get svc rabbitmq-external-temp -n blockchain-infra -w
```

Esa `EXTERNAL-IP` es el `RABBITMQ_HOST` que va a usar el worker en el
clúster del profesor, con el usuario `gpu-external` del paso 1.

## 3. Apenas termines la prueba

```powershell
kubectl delete -f expose-rabbitmq-temporal.yaml
```

El usuario `gpu-external` lo podés dejar creado (no hace daño estar ahí
sin usarse), pero si querés borrarlo también:

```powershell
kubectl exec -n blockchain-infra rabbitmq-0 -- rabbitmqctl delete_user gpu-external
```

## Si el profesor te da un rango de IPs de salida de su clúster

Mejor todavía: en vez de dejar el `LoadBalancer` abierto a cualquier IP,
lo restringís con un firewall de GCP a ese rango puntual, además del
usuario ya restringido -defensa en profundidad-:

```powershell
gcloud compute firewall-rules create allow-rabbitmq-gpu-cluster `
  --network=blockchain-vpc --direction=INGRESS --action=ALLOW `
  --rules=tcp:5672 --source-ranges=<RANGO_QUE_TE_DE_EL_PROFESOR> `
  --project=sdypp-rocco
```

Y borrarlo también después de la prueba:

```powershell
gcloud compute firewall-rules delete allow-rabbitmq-gpu-cluster --project=sdypp-rocco
```
