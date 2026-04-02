import {useCallback, useEffect, useRef, useState} from 'react';
import SockJS from 'sockjs-client';
import {Client} from '@stomp/stompjs';

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/ws'
    : `${window.location.protocol}//${window.location.host}/ws`;

const RECONNECT_DELAY = 5000;

const useWebSocket = (topic) => {
    const [messages, setMessages] = useState({device_id: ""});
    const clientRef = useRef(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(url),
            reconnectDelay: RECONNECT_DELAY,
            debug: () => {
            },
            onConnect: () => {
                console.log("WebSocket connected");
                setMessages({message: "Connected to server.", severity: "High"});
                client.subscribe(topic, (message) => {
                    setMessages(JSON.parse(message.body));
                });
            },
            onStompError: (frame) => {
                console.warn("STOMP error:", frame.headers?.message);
                setMessages({message: "Cannot reach server, retrying connection", severity: "High"});
            },
            onWebSocketClose: () => console.warn("WebSocket closed"),
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, [topic]);

    const sendMessage = useCallback((destination, message) => {
        if (clientRef.current?.connected) {
            clientRef.current.publish({destination, body: message});
        } else {
            console.warn("WebSocket not connected. Message not sent.");
        }
    }, []);

    return {messages, sendMessage};
};

export default useWebSocket;