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

    // Reactive — when user switches home, effect re-runs and resubscribes
    const [homeId, setHomeId] = useState(() => localStorage.getItem('selectedHomeId'));

    const clientRef = useRef(null);

    const switchHome = useCallback((newHomeId) => {
        localStorage.setItem('selectedHomeId', newHomeId);
        setHomeId(newHomeId);
    }, []);

    useEffect(() => {
        const token = JSON.parse(localStorage.getItem("user"))?.access_token;
        if (!token) {
            console.warn("No auth token — WebSocket connection aborted.");
            return;
        }
        if (!homeId) {
            console.warn("No homeId — WebSocket connection aborted.");
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
                console.log(`WebSocket connected — subscribed to home: ${homeId}`);

                client.subscribe(`/topic/home/${homeId}/data`, (message) => {
                    setMessages(JSON.parse(message.body));
                });

                // Alert topic stays global — not home-scoped (system-wide alerts)
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
    }, [homeId]); // ← re-runs on home switch; token changes don't happen mid-session

    const sendMessage = useCallback((destination, message) => {
        if (clientRef.current?.connected) {
            clientRef.current.publish({destination, body: message});
        } else {
            console.warn("WebSocket not connected — message dropped.");
        }
    }, []);

    const contextValue = useMemo(
        () => ({messages, alertMessages, sendMessage, homeId, switchHome}),
        [messages, alertMessages, sendMessage, homeId, switchHome]
    );

    return (
        <WebSocketContext.Provider value={contextValue}>
            {children}
        </WebSocketContext.Provider>
    );
};