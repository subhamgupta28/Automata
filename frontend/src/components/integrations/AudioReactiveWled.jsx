import {useEffect, useRef} from "react";
import SockJS from 'sockjs-client';
import {Client} from '@stomp/stompjs';

export default function AudioReactiveWled() {
    const stompClientRef = useRef(null);
    const animationFrameRef = useRef(null);
    const audioContextRef = useRef(null);

    useEffect(() => {
        let analyser = null;
        let dataArray = null;

        // --- STOMP Connection ---
        const client = new Client({
            webSocketFactory: () => new SockJS("http://localhost:8010/ws"),
            debug: () => {
            },
            reconnectDelay: 5000,
            onConnect: (frame) => {
                console.log("WebSocket connected:", frame);
                stompClientRef.current = client;
                client.subscribe("/topic/app", (message) => {
                    // handle incoming messages
                });
            },
            onStompError: (frame) => {
                console.warn("STOMP error:", frame);
            },
            onDisconnect: () => {
                console.warn("WebSocket disconnected");
            },
        });

        client.activate();

        // --- Audio Setup ---
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        audioContextRef.current = audioContext;

        navigator.mediaDevices.getUserMedia({audio: true})
            .then(stream => {
                const source = audioContext.createMediaStreamSource(stream);
                analyser = audioContext.createAnalyser();
                analyser.fftSize = 64; // 32 bins

                source.connect(analyser);
                dataArray = new Uint8Array(analyser.frequencyBinCount);

                const sendAudioData = () => {
                    analyser.getByteFrequencyData(dataArray);

                    const fft = Array.from(dataArray.slice(0, 16));
                    const peak = Math.max(...fft);
                    const freqIndex = fft.indexOf(peak);
                    const volume = fft.reduce((a, b) => a + b, 0) / fft.length;

                    const payload = {
                        volume: Math.round(volume),
                        peak: peak > 220,
                        fft,
                        magnitude: peak,
                        frequency: freqIndex * 100,
                    };

                    if (stompClientRef.current?.connected) {
                        stompClientRef.current.publish({
                            destination: "/app/audio",
                            body: JSON.stringify(payload),
                        });
                    }

                    animationFrameRef.current = requestAnimationFrame(sendAudioData);
                };

                sendAudioData();
            })
            .catch(err => console.error("Microphone access denied:", err));

        // --- Cleanup ---
        return () => {
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
            }
            if (stompClientRef.current?.connected) {
                stompClientRef.current.deactivate();
                console.log("WebSocket disconnected cleanly");
            }
            if (audioContextRef.current?.state !== 'closed') {
                audioContextRef.current.close();
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