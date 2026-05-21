import { useState } from 'react';

function TxBadge({ type }) {
    return <span className={`tx-badge ${type.toLowerCase()}`}>{type}</span>;
}

function BlockCard({ block, isNew }) {
    const [expanded, setExpanded] = useState(false);

    return (
        <div className={`block-card ${isNew ? 'block-new' : ''}`}>
            <div className="block-header" onClick={() => setExpanded(e => !e)}>
                <div className="block-index">#{block.index}</div>
                <div className="block-meta">
                    <span className="block-hash mono">{block.blockHash}</span>
                    <span className="block-info">
                        {block.transactions.length} tx · nonce {block.nonce} · {block.prefix}
                    </span>
                </div>
                <div className="block-toggle">{expanded ? '▲' : '▼'}</div>
            </div>

            {expanded && (
                <div className="block-body">
                    <div className="block-detail">
                        <span className="detail-label">PREV HASH</span>
                        <span className="mono">{block.previousHash}</span>
                    </div>
                    <div className="block-detail">
                        <span className="detail-label">TIMESTAMP</span>
                        <span>{new Date(block.timestamp).toLocaleString()}</span>
                    </div>
                    <div className="tx-list">
                        {block.transactions.map(tx => (
                            <div key={tx.id} className="tx-row">
                                <TxBadge type={tx.type} />
                                <span className="tx-sender">{tx.sender}</span>
                                <span className="tx-arrow">→</span>
                                <span className="tx-receiver">{tx.receiver}</span>
                                <span className="tx-amount">{tx.amount.toFixed(2)}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

export function BlockList({ blocks, newBlockIndex }) {
    if (!blocks.length) return (
        <div className="card">
            <h2 className="card-title">BLOCKCHAIN</h2>
            <div className="empty">Cargando bloques...</div>
        </div>
    );

    return (
        <div className="card blocks-card">
            <h2 className="card-title">BLOCKCHAIN <span className="block-count">{blocks.length} bloques</span></h2>
            <div className="block-list">
                {[...blocks].reverse().map(block => (
                    <BlockCard
                        key={block.index}
                        block={block}
                        isNew={block.index === newBlockIndex}
                    />
                ))}
            </div>
        </div>
    );
}