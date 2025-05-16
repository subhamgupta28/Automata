import { useEffect, useState, useRef } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// const url = window.location.href + "ws";
// const url = "http://localhost:8080/ws";

const url = __API_MODE__ === 'serve'
    ? 'http://raspberry.local:8010/ws'
    : window.location.href + "ws";

// const url = 'http://localhost:8010/ws';

const useWebSocket = (topic) => {
    const [messages, setMessages] = useState({ device_id: "" });
    const stompClientRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);

    const connect = () => {
        const socket = new SockJS(url);
        const client = Stomp.over(socket);

        client.debug = () => {}; // Disable logging

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

    const attemptReconnect = (delay = 3000) => {
        if (reconnectTimeoutRef.current) return; // Prevent multiple timers

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
    }, [topic]);

    const sendMessage = (destination, message) => {
        if (stompClientRef.current?.connected) {
            stompClientRef.current.send(destination, {}, message);
        } else {
            console.warn("WebSocket not connected. Message not sent.");
        }
    };

    return { messages, sendMessage };
};

export default useWebSocket;
