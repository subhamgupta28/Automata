import { useEffect, useState, useRef } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// const url = window.location.href + "ws";
// const url = "http://localhost:8080/ws";

const url = __API_MODE__ === 'serve'
    ? 'http://raspberry.local:8010/ws'
    : window.location.href + "ws";

// const url = 'http://raspberry.local:8010/ws';

const useWebSocket = (topic) => {
    const [messages, setMessages] = useState({ device_id: "" });
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

    const sendMessage = (destination, message) => {
        if (stompClientRef.current) {
            stompClientRef.current.send(destination, {}, message);
        }
    };

    return { messages, sendMessage };
};

export default useWebSocket;