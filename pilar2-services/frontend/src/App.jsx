import { useState, useEffect, useCallback } from 'react';
import { api } from './main/api';
import { useWebSocket } from './hooks/useWebSocket';
import { StatsBar } from './components/StatsBar';
import { TransactionForm } from './components/TransactionForm';
import { BlockList } from './components/BlockList';
import { EventLog } from './components/EventLog';
import './App.css';

export default function App() {
  const [blocks, setBlocks] = useState([]);
  const [stats, setStats] = useState(null);
  const [poolStatus, setPoolStatus] = useState(null);
  const [events, setEvents] = useState([]);
  const [newBlockIdx, setNewBlockIdx] = useState(null);

  const fetchData = useCallback(async () => {
    try {
      const [blocksRes, statsRes, poolRes] = await Promise.all([
        api.getBlocks(),
        api.getStats(),
        api.getPoolStatus(),
      ]);
      setBlocks(blocksRes.data);
      setStats(statsRes.data);
      setPoolStatus(poolRes.data);
    } catch (e) {
      console.error('Error fetching data:', e);
    }
  }, []);

  // Polling cada 10 segundos como fallback
  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const onBlockMined = useCallback((event) => {
    const ev = { ...event, receivedAt: Date.now() };
    setEvents(prev => [ev, ...prev].slice(0, 20));
    setNewBlockIdx(event.blockIndex);
    setTimeout(() => setNewBlockIdx(null), 3000);
    fetchData(); // refrescar la chain
  }, [fetchData]);

  const { connected } = useWebSocket(onBlockMined);

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-inner">
          <div className="logo">
            <span className="logo-icon">⬡</span>
            <span className="logo-text">UNLu <strong>Blockchain</strong></span>
          </div>
          <span className="logo-sub">Proof of Work · 2025</span>
        </div>
      </header>

      <StatsBar stats={stats} poolStatus={poolStatus} wsConnected={connected} />

      <main className="app-main">
        <div className="col-left">
          <TransactionForm onSent={fetchData} />
          <EventLog events={events} />
        </div>
        <div className="col-right">
          <BlockList blocks={blocks} newBlockIndex={newBlockIdx} />
        </div>
      </main>
    </div>
  );
}