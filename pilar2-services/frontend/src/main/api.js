import axios from 'axios';

const API_BASE = 'http://localhost:8080';
const POOL_BASE = 'http://localhost:8082';

export const api = {
    // Blockchain
    getBlocks: () => axios.get(`${API_BASE}/api/chain/blocks`),
    getStats: () => axios.get(`${API_BASE}/api/chain/stats`),
    getPending: () => axios.get(`${API_BASE}/api/chain/transactions/pending`),

    // Transacciones
    sendTransaction: (tx) => axios.post(`${POOL_BASE}/api/pool/transactions`, tx),

    // Pool
    flushPool: () => axios.post(`${POOL_BASE}/api/pool/flush`),
    getPoolStatus: () => axios.get(`${POOL_BASE}/api/pool/status`),
};