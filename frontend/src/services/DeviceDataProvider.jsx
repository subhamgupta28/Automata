// Single unified WebSocket provider — one STOMP connection, two topic subscriptions.
import React, {createContext, useCallback, useContext, useEffect, useMemo, useRef, useState} from 'react';
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
        const token = JSON.parse(localStorage.getItem("user"))?.access_token;
        if (!token) {
            console.warn("No auth token found — WebSocket connection aborted.");
            return;
        }

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
                // setAlertMessages({message: "Connected to server.", severity: "High"});
                client.subscribe("/topic/data", (message) => {
                    setMessages(JSON.parse(message.body));
                });
                client.subscribe("/topic/alert", (message) => {
                    setAlertMessages(JSON.parse(message.body));
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

    const contextValue = useMemo(
        () => ({messages, alertMessages, sendMessage}),
        [messages, alertMessages, sendMessage]
    );

    return (
        <WebSocketContext.Provider value={contextValue}>
            {children}
        </WebSocketContext.Provider>
    );
};
