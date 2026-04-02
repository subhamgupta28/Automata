// WebSocketContext.js
import React, {createContext, useContext, useEffect, useRef, useState} from 'react';
import SockJS from "sockjs-client";
import {Client} from "@stomp/stompjs";

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/ws'
    : `${window.location.protocol}//${window.location.host}/ws`;

const TOPIC = "/topic/data";
const RECONNECT_DELAY = 5000;

const WebSocketContext = createContext(null);

export const useDeviceLiveData = () => useContext(WebSocketContext);

export const DeviceDataProvider = ({children}) => {
    const [messages, setMessages] = useState({device_id: "", deviceConfig: {}});
    const clientRef = useRef(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(url),
            reconnectDelay: RECONNECT_DELAY,
            debug: () => {
            },
            onConnect: () => {
                console.log("WebSocket connected");
                client.subscribe(TOPIC, (message) => {
                    setMessages(JSON.parse(message.body));
                });
            },
            onStompError: (frame) => console.warn("STOMP error:", frame.headers?.message),
            onDisconnect: () => console.warn("WebSocket disconnected"),
            onWebSocketClose: () => console.warn("WebSocket closed"),
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, []);

    return (
        <WebSocketContext.Provider value={{messages}}>
            {children}
        </WebSocketContext.Provider>
    );
};