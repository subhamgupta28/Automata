import {useEffect, useState} from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const url = "http://localhost:8010/ws";
const topic = '/topic/update';

const useWebSocket = () => {
    const [stompClient, setStompClient] = useState(null);
    const [messages, setMessages] = useState([]);

    useEffect(() => {
        const socket = new SockJS(url);
        const client = Stomp.over(socket);

        client.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            client.subscribe(topic, (message) => {
                setMessages((prev) => [...prev, message.body]);
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