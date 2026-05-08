// Single unified WebSocket provider — one STOMP connection, two topic subscriptions.
import React, {createContext, useCallback, useContext, useEffect, useRef, useState} from 'react';
import SockJS from "sockjs-client";
import {Client} from "@stomp/stompjs";

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/ws'
    : `${window.location.protocol}//${window.location.host}/ws`;

const RECONNECT_DELAY = 5000;

const WebSocketContext = createContext(null);

export const useDeviceLiveData = () => useContext(WebSocketContext);

export const DeviceDataProvider = ({children}) => {
    const [messages, setMessages] = useState({device_id: "", deviceConfig: {}});
    const [alertMessages, setAlertMessages] = useState({device_id: ""});
    const clientRef = useRef(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(url),
            reconnectDelay: RECONNECT_DELAY,
            debug: () => {},
            onConnect: () => {
                console.log("WebSocket connected");
                setAlertMessages({message: "Connected to server.", severity: "High"});
                client.subscribe("/topic/data", (message) => {
                    setMessages(JSON.parse(message.body));
                });
                client.subscribe("/topic/alert", (message) => {
                    setAlertMessages(JSON.parse(message.body));
                });
            },
            onStompError: (frame) => {
                console.warn("STOMP error:", frame.headers?.message);
                setAlertMessages({message: "Cannot reach server, retrying connection", severity: "High"});
            },
            onWebSocketClose: () => console.warn("WebSocket closed"),
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, []);

    const sendMessage = useCallback((destination, message) => {
        if (clientRef.current?.connected) {
            clientRef.current.publish({destination, body: message});
        } else {
            console.warn("WebSocket not connected. Message not sent.");
        }
    }, []);

    return (
        <WebSocketContext.Provider value={{messages, alertMessages, sendMessage}}>
            {children}
        </WebSocketContext.Provider>
    );
};
