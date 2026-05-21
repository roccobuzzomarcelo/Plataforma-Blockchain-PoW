import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function useWebSocket(onBlockMined) {
    const clientRef = useRef(null);
    const [connected, setConnected] = useState(false);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 3000,
            onConnect: () => {
                setConnected(true);
                client.subscribe('/topic/blocks', (msg) => {
                    const event = JSON.parse(msg.body);
                    onBlockMined(event);
                });
            },
            onDisconnect: () => setConnected(false),
        });

        client.activate();
        clientRef.current = client;

        return () => client.deactivate();
    }, []);

    return { connected };
}