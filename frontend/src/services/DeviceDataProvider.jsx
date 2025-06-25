// WebSocketContext.js
import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import SockJS from "sockjs-client";
import Stomp from "stompjs";

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/ws'
    : window.location.href + "ws";

const topic = "/topic/data";
// const url = 'http://localhost:8010/ws';
// Create context for WebSocket
const WebSocketContext = createContext();

export const useDeviceLiveData = () => {
    return useContext(WebSocketContext);
};

// WebSocket provider component
export const DeviceDataProvider = ({ children }) => {
    const [messages, setMessages] = useState({ device_id: "", deviceConfig: {} });
    const stompClientRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);

    const connect = () => {
        const socket = new SockJS(url);
        const client = Stomp.over(socket);

        client.debug = () => {}; // silence logs

        client.connect({}, (frame) => {
            console.log("WebSocket connected:", frame);
            stompClientRef.current = client;

            client.subscribe(topic, (message) => {
                setMessages(JSON.parse(message.body));
            });
        }, (error) => {
            console.warn("WebSocket connection error:", error);
            attemptReconnect();
        });

        client.onclose = () => {
            console.warn("WebSocket closed");
            attemptReconnect();
        };
    };

    const attemptReconnect = (delay = 6000) => {
        if (reconnectTimeoutRef.current) return;

        reconnectTimeoutRef.current = setTimeout(() => {
            console.log("Attempting WebSocket reconnect...");
            connect();
            reconnectTimeoutRef.current = null;
        }, delay);
    };

    useEffect(() => {
        connect();

        return () => {
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }

            if (stompClientRef.current?.connected) {
                stompClientRef.current.disconnect(() => {
                    console.log("WebSocket disconnected cleanly");
                });
            }
        };
    }, []); // No need to depend on url or topic unless they're dynamic

    return (
        <WebSocketContext.Provider value={{ messages }}>
            {children}
        </WebSocketContext.Provider>
    );
};
