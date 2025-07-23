import {useEffect, useRef} from "react";
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';


export default function AudioReactiveWled(){
    const stompClientRef = useRef(null);
    useEffect(() => {
        const connect = () => {
            const socket = new SockJS("http://localhost:8010/ws");
            const client = Stomp.over(socket);

            client.debug = () => {}; // Disable logging

            client.connect({}, (frame) => {
                console.log("WebSocket connected:", frame);
                stompClientRef.current = client;

                client.subscribe("app", (message) => {

                });

            }, (error) => {
                console.warn("WebSocket connection error:", error);
            });

            client.onclose = () => {
                console.warn("WebSocket closed");
            };
        };
        connect();
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
            const source = audioContext.createMediaStreamSource(stream);
            const analyser = audioContext.createAnalyser();
            analyser.fftSize = 64; // Gives 32 bins

            source.connect(analyser);

            const dataArray = new Uint8Array(analyser.frequencyBinCount);

            const sendAudioData = () => {
                analyser.getByteFrequencyData(dataArray);

                const fft = Array.from(dataArray.slice(0, 16));
                const peak = Math.max(...fft);
                const freqIndex = fft.indexOf(peak);
                const volume = fft.reduce((a, b) => a + b, 0) / fft.length;

                const payload = {
                    volume: Math.round(volume),
                    peak: peak > 220,
                    fft: fft,
                    magnitude: peak,
                    frequency: freqIndex * 100 // Estimate only
                };
                if (stompClientRef.current?.connected) {
                    stompClientRef.current.send("/app/audio", {}, JSON.stringify(payload));
                }

                requestAnimationFrame(sendAudioData);
            };

            sendAudioData();
        });

        return () => {
            if (stompClientRef.current?.connected) {
                stompClientRef.current.disconnect(() => {
                    console.log("WebSocket disconnected cleanly");
                });
            }
        };
    }, []);

    return (
        <div>
            <h2>🎵 WLED Audio Sync (React + Spring Boot)</h2>
            <p>Make some noise and watch your WLED react!</p>
        </div>
    );
}