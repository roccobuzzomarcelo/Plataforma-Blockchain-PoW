import { useState } from 'react';
import { api } from '../main/api';

export function TransactionForm({ onSent }) {
    const [form, setForm] = useState({ sender: '', receiver: '', amount: '' });
    const [status, setStatus] = useState(null);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setStatus(null);
        try {
            const tx = {
                id: `tx-${Date.now()}`,
                sender: form.sender,
                receiver: form.receiver,
                amount: parseFloat(form.amount),
                timestamp: new Date().toISOString(),
                type: 'TRANSFER',
            };
            await api.sendTransaction(tx);
            setStatus({ ok: true, msg: `Transacción enviada: ${tx.id}` });
            setForm({ sender: '', receiver: '', amount: '' });
            onSent?.();
        } catch {
            setStatus({ ok: false, msg: 'Error al enviar la transacción' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="card form-card">
            <h2 className="card-title">NUEVA TRANSACCIÓN</h2>
            <form onSubmit={handleSubmit} className="tx-form">
                <div className="form-row">
                    <label>EMISOR</label>
                    <input
                        value={form.sender}
                        onChange={e => setForm(f => ({ ...f, sender: e.target.value }))}
                        placeholder="Alice"
                        required
                    />
                </div>
                <div className="form-row">
                    <label>RECEPTOR</label>
                    <input
                        value={form.receiver}
                        onChange={e => setForm(f => ({ ...f, receiver: e.target.value }))}
                        placeholder="Bob"
                        required
                    />
                </div>
                <div className="form-row">
                    <label>MONTO</label>
                    <input
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={form.amount}
                        onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                        placeholder="10.5"
                        required
                    />
                </div>
                <button type="submit" className="btn-primary" disabled={loading}>
                    {loading ? 'ENVIANDO...' : 'ENVIAR AL POOL'}
                </button>
                {status && (
                    <div className={`form-status ${status.ok ? 'ok' : 'err'}`}>
                        {status.msg}
                    </div>
                )}
            </form>
            <button className="btn-secondary" onClick={() => api.flushPool()}>
                FORZAR MINADO AHORA
            </button>
        </div>
    );
}