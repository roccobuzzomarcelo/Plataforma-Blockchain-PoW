export function EventLog({ events }) {
    if (!events.length) return null;

    return (
        <div className="card event-card">
            <h2 className="card-title">EVENTOS EN TIEMPO REAL</h2>
            <div className="event-list">
                {events.map((ev, i) => (
                    <div key={i} className="event-row">
                        <span className="event-time">
                            {new Date(ev.receivedAt).toLocaleTimeString()}
                        </span>
                        <span className="event-text">
                            Bloque <strong>#{ev.blockIndex}</strong> minado por{' '}
                            <strong>{ev.winnerWorkerId}</strong> · nonce {ev.nonce} ·
                            recompensa {ev.reward} coins
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}