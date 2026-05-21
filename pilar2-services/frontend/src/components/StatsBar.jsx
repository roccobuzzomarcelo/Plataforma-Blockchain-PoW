export function StatsBar({ stats, poolStatus, wsConnected }) {
    return (
        <div className="stats-bar">
            <div className="stat">
                <span className="stat-label">BLOQUES</span>
                <span className="stat-value">{stats?.totalBlocks ?? '—'}</span>
            </div>
            <div className="stat">
                <span className="stat-label">ÚLTIMO BLOQUE</span>
                <span className="stat-value">#{stats?.latestBlockIndex ?? '—'}</span>
            </div>
            <div className="stat">
                <span className="stat-label">HASH</span>
                <span className="stat-value mono">{stats?.latestBlockHash?.slice(0, 12) ?? '—'}...</span>
            </div>
            <div className="stat">
                <span className="stat-label">TX PENDIENTES</span>
                <span className="stat-value">{poolStatus?.pendingTransactions ?? '—'}</span>
            </div>
            <div className={`stat ws-indicator ${wsConnected ? 'connected' : 'disconnected'}`}>
                <span className="stat-label">WEBSOCKET</span>
                <span className="stat-value">{wsConnected ? '● LIVE' : '○ OFF'}</span>
            </div>
        </div>
    );
}