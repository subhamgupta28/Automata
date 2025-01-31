// WebSocketContext.js
import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import SockJS from "sockjs-client";
import Stomp from "stompjs";

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8080/ws'
    : window.location.href + "ws";

const topic = "/topic/data";
// Create context for WebSocket
const WebSocketContext = createContext();

export const useDeviceLiveData = () => {
    return useContext(WebSocketContext);
};

// WebSocket provider component
export const WebSocketProvider = ({ children }) => {
    const [messages, setMessages] = useState({ device_id: "", deviceConfig: {} });
    const stompClientRef = useRef(null);

    useEffect(() => {
        const socket = new SockJS(url);
        const client = Stomp.over(socket);

        client.debug = () => {};
        // console.log(client)

        client.connect({}, (frame) => {
            client.subscribe(topic, (message) => {
                setMessages(JSON.parse(message.body));
            });
        });

        stompClientRef.current = client;

        return () => {
            if (stompClientRef.current.connected){
                console.log("websocket disconnected")
                stompClientRef.current.disconnect();
            }
        };
    }, [url, topic]);


    return (
        <WebSocketContext.Provider value={{ messages }}>
            {children}
        </WebSocketContext.Provider>
    );
};
