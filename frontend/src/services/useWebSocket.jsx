import {useEffect, useState} from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// const url = window.location.href + "ws";
// const url = "http://localhost:8080/ws";

const url = __API_MODE__ === 'serve'
    ? 'http://localhost:8080/ws' // Local API server for development
    : window.location.href + "ws"; // Production API server



const useWebSocket = (topic) => {
    const [stompClient, setStompClient] = useState(null);
    const [messages, setMessages] = useState({device_id: ""});

    useEffect(() => {
        const socket = new SockJS(url);
        const client = Stomp.over(socket);


        client.connect({}, (frame) => {
            // console.log('Connected: ' + frame);
            client.subscribe(topic, (message) => {
                // setMessages((prev) => [...prev, message.body]);
                setMessages(JSON.parse(message.body));
            });
        });

        setStompClient(client);
    }, [url, topic]);

    const sendMessage = (destination, message) => {
        if (stompClient) {
            stompClient.send(destination, {}, message);
        }
    };

    return {messages, sendMessage};
};

export default useWebSocket;