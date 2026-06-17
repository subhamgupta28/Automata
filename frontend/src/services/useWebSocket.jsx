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
    const token = JSON.parse(localStorage.getItem("user"))?.access_token;
    if (!token) {
        console.warn("No auth token found — WebSocket connection aborted.");
        return;
    }
    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(url, null, {withCredentials: false}),
            reconnectDelay: RECONNECT_DELAY,
            debug: () => {
            },
            connectHeaders: {
                Authorization: `Bearer ${token}`,
            },
            onConnect: () => {
                console.log("WebSocket connected");
                // setMessages({message: "Connected to server.", severity: "High"});
                client.subscribe(topic, (message) => {
                    setMessages(JSON.parse(message.body));
                });
            },
            onStompError: (frame) => {
                const errMsg = frame.headers?.message ?? "";
                console.warn("STOMP error:", errMsg);
                const isAuthError =
                    errMsg.toLowerCase().includes("unauthorized") ||
                    errMsg.toLowerCase().includes("forbidden") ||
                    frame.headers?.["receipt-id"] === "auth-error";

                if (isAuthError) {
                    console.error("WebSocket auth failed — deactivating client.");
                    client.deactivate();
                    return;
                }
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