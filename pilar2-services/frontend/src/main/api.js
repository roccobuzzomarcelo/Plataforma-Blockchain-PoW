import axios from 'axios';

// Rutas relativas: el navegador siempre habla con el mismo origen que sirvió la app.
//  - Producción (Docker / Kubernetes): las resuelve el proxy de nginx (nginx.conf).
//  - Desarrollo (npm run dev): las resuelve el proxy de Vite (vite.config.js).
const http = axios.create({ timeout: 10000 });

export const api = {
    // Blockchain (blockchain-api)
    getBlocks: () => http.get('/api/chain/blocks'),
    getStats: () => http.get('/api/chain/stats'),
    getPending: () => http.get('/api/chain/transactions/pending'),

    // Transacciones: pasan por blockchain-api, que valida y reenvía al transaction-pool
    sendTransaction: (tx) => http.post('/api/transactions', tx),

    // Pool (transaction-pool)
    flushPool: () => http.post('/api/pool/flush'),
    getPoolStatus: () => http.get('/api/pool/status'),
};
