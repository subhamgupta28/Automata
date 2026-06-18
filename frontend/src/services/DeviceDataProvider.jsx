import React, {createContext, useCallback, useContext, useEffect, useMemo, useRef, useState} from 'react';
import SockJS from "sockjs-client";
import {Client} from "@stomp/stompjs";
import {useHome} from '../components/home/HomeContext'; // Import useHome

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
    const {selectedHomeId} = useHome(); // Get selectedHomeId from HomeContext
    const subscriptionsRef = useRef({}); // To hold current subscriptions

    const subscribeToHome = useCallback((client, homeId) => {
        if (!client || !client.connected || !homeId) return;

        // Unsubscribe from previous home's topics
        Object.values(subscriptionsRef.current).forEach(sub => sub.unsubscribe());
        subscriptionsRef.current = {};

        // console.log(`Subscribing to topics for home: ${homeId}`);

        const topics = ["data", "live", "action", "ack", "events", "status"];
        topics.forEach(topic => {
            const destination = `/topic/home/${homeId}/${topic}`;
            subscriptionsRef.current[topic] = client.subscribe(destination, (message) => {
                const parsed = JSON.parse(message.body);
                // You might want to handle different topics differently here
                setMessages(parsed);
            });
        });

        // Legacy alert topic
        subscriptionsRef.current.alert = client.subscribe("/topic/alert", (message) => {
            setAlertMessages(JSON.parse(message.body));
        });

    }, []);

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
                // console.log("WebSocket connected. Waiting for home selection to subscribe...");
                // Note: We intentionally do NOT subscribe here.
                subscribeToHome(clientRef.current, selectedHomeId);
                // The 'selectedHomeId' captured in this closure is stale (likely null).
                // Subscriptions are handled entirely by the effect below.
            },
            onStompError: (frame) => {
                const errMsg = frame.headers?.message ?? "";
                console.warn("STOMP error:", errMsg);
                if (errMsg.toLowerCase().includes("unauthorized") || errMsg.toLowerCase().includes("forbidden")) {
                    console.error("WebSocket auth failed — deactivating client.");
                    client.deactivate();
                }
            },
            onWebSocketClose: () => console.warn("WebSocket closed"),
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, []); // This effect runs only once on mount to establish the connection

    // Effect to handle subscriptions based on home changes OR initial connection
    useEffect(() => {
        // If connected AND we have a home selected, subscribe.
        // This will fire when selectedHomeId goes from null -> ID, and when it changes ID -> ID.
        if (clientRef.current?.connected && selectedHomeId) {
            subscribeToHome(clientRef.current, selectedHomeId);
        }
    }, [selectedHomeId, subscribeToHome]);


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