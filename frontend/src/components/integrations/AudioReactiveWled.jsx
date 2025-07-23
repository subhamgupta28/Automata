import {useEffect, useRef} from "react";


export default function AudioReactiveWled(){

    const ws = useRef(null);

    useEffect(() => {
        ws.current = new WebSocket("ws://raspberry.local:8010/audio");

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

                if (ws.current && ws.current.readyState === WebSocket.OPEN) {
                    ws.current.send(JSON.stringify(payload));
                }

                requestAnimationFrame(sendAudioData);
            };

            sendAudioData();
        });

        return () => {
            ws.current?.close();
        };
    }, []);

    return (
        <div>
            <h2>🎵 WLED Audio Sync (React + Spring Boot)</h2>
            <p>Make some noise and watch your WLED react!</p>
        </div>
    );
}