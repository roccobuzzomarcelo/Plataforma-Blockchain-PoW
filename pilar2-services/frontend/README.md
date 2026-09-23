# Frontend - Interfaz React

## Descripción

Interfaz web desarrollada en React + Vite que permite visualizar el estado
de la blockchain en tiempo real y enviar nuevas transacciones.
Se comunica con `blockchain-api` via REST para operaciones puntuales
y via WebSocket (STOMP + SockJS) para actualizaciones en tiempo real.

## Puerto: 5173 (desarrollo) / 80 (producción con Nginx)

## Funcionalidades

### Visualización

- Lista de bloques confirmados ordenados del más reciente al más antiguo
- Detalle expandible de cada bloque: hash, previousHash, nonce, timestamp y transacciones
- Estadísticas generales en tiempo real: total de bloques, último bloque, hash, tx pendientes
- Indicador de estado WebSocket (LIVE / OFF)

### Interacción

- Formulario para enviar transacciones (sender, receiver, amount) al Transaction Pool
- Botón para forzar el minado inmediato sin esperar el scheduler de 60 segundos
- Log de eventos en tiempo real: cada bloque minado aparece con worker ganador, nonce y recompensa

### Tiempo real

Cuando el Coordinator confirma un bloque nuevo:

1. Notifica a `blockchain-api` via REST
2. `blockchain-api` hace broadcast via WebSocket
3. El frontend recibe el evento y actualiza la UI sin recargar la página
4. El nuevo bloque se resalta visualmente durante 3 segundos

## Estructura

```bash
src/
├── App.jsx                     # Componente raíz, maneja estado global y polling
├── App.css                     # Estilos globales (tema claro verde)
├── main.jsx                    # Entry point de Vite
├── main/
│   └── api.js                  # Llamadas REST a blockchain-api y transaction-pool
├── hooks/
│   └── useWebSocket.js         # Conexión STOMP/SockJS a blockchain-api
└── components/
├── StatsBar.jsx             # Barra de estadísticas superior
├── TransactionForm.jsx      # Formulario de nueva transacción + flush
├── BlockList.jsx            # Lista de bloques con detalle expandible
└── EventLog.jsx             # Log de eventos WebSocket en tiempo real
```

## Conexión a servicios

El frontend usa **rutas relativas**: el navegador siempre habla con el mismo origen que sirvió la app, y un proxy reverso reenvía cada ruta al servicio que corresponde. Así no hay URLs de `localhost` fijas en el código, no se depende de CORS y el mismo build funciona en Docker Compose, minikube y GKE.

| Ruta                     | Servicio destino        | Uso                                                        |
| ------------------------ | ----------------------- | ---------------------------------------------------------- |
| `/api/chain/*`           | `blockchain-api:8080`   | Bloques, estadísticas, transacciones pendientes            |
| `/api/transactions`      | `blockchain-api:8080`   | Alta de transacciones (valida y reenvía al pool)           |
| `/ws`                    | `blockchain-api:8080`   | WebSocket STOMP/SockJS (`/topic/blocks`)                   |
| `/api/pool/*`            | `transaction-pool:8082` | Estado del pool y flush manual                             |
| `/api/events/*`          | — (404)                 | Interno: coordinator → blockchain-api. No se expone        |
| `/api/pool/miners/*`     | — (404)                 | Interno: keep-alive de mineros GPU. No se expone           |

- **Producción** (imagen Docker): el proxy es nginx, configurado en `nginx.conf`. Los destinos son los nombres de servicio de Docker Compose, que coinciden con los nombres de los Services de Kubernetes.
- **Desarrollo** (`npm run dev`): el proxy es el de Vite, configurado en `vite.config.js` con las mismas rutas, apuntando a `localhost:8080` y `localhost:8082`.

Las transacciones se envían a `blockchain-api` (`POST /api/transactions` con `{ sender, receiver, amount }`) y no directamente al pool: `blockchain-api` valida los datos, genera el `id` y el `timestamp`, y reenvía al `transaction-pool`. Si la validación falla, el formulario muestra el motivo que devuelve el servicio.

## Estrategia de actualización

- WebSocket: actualización instantánea al recibir `BlockMinedEvent` en `/topic/blocks`
- Polling: fallback cada 10 segundos para mantener sincronía si el WebSocket falla

## Dependencias principales

```json
{
  "axios": "REST calls",
  "@stomp/stompjs": "WebSocket STOMP",
  "sockjs-client": "fallback WebSocket"
}
```

## Configuración Vite

```javascript
// vite.config.js
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis', // necesario para que sockjs-client funcione en el browser
  },
  server: {
    proxy: {                                   // replica las rutas de nginx.conf
      '/api/pool': 'http://localhost:8082',    // debe ir antes que '/api'
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
```

## Levantar

```bash
npm install
npm run dev
```

## Build para producción

```bash
npm run build
# Los archivos estáticos quedan en dist/
# En la imagen Docker los sirve nginx, que además hace de proxy reverso (nginx.conf)
```

## Verificar

Abrí `http://localhost:5173` (desarrollo) o `http://localhost` (Docker Compose) y verificá:

- La stats bar muestra bloques y hash
- El indicador WebSocket muestra `● LIVE`
- Los bloques aparecen en la lista
- Al enviar una transacción y hacer flush, el nuevo bloque aparece sin recargar
