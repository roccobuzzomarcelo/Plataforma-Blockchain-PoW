import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis', // necesario para sockjs-client en el navegador
  },
  // Proxy de desarrollo: replica las rutas del nginx.conf de producción para que
  // el frontend use rutas relativas también con `npm run dev`.
  // El orden importa: '/api/pool' tiene que ir antes que '/api'.
  server: {
    proxy: {
      '/api/pool': 'http://localhost:8082',
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
