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
            // blockchain-api valida los datos, genera id/timestamp y reenvía al pool
            const { data } = await api.sendTransaction({
                sender: form.sender.trim(),
                receiver: form.receiver.trim(),
                amount: parseFloat(form.amount),
            });
            setStatus({ ok: true, msg: data });
            setForm({ sender: '', receiver: '', amount: '' });
            onSent?.();
        } catch (err) {
            // En un 400, blockchain-api devuelve el motivo como texto plano
            const detail = err.response?.data;
            setStatus({
                ok: false,
                msg: typeof detail === 'string' && detail ? detail : 'Error al enviar la transacción',
            });
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