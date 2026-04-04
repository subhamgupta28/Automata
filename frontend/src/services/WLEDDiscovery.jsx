import {useState} from "react";
import {saveWLEDDevices} from "./apis.jsx";

export const WLEDDiscovery = () => {
    const [devices, setDevices] = useState([]);
    const [loading, setLoading] = useState(false);

    const scanNetwork = async () => {
        setLoading(true);
        const baseIP = "192.168.1."; // adjust
        const foundDevices = [];

        const requests = Array.from({length: 254}, async (_, i) => {
            const ip = baseIP + (i + 1);

            try {
                const infoRes = await fetch(`http://${ip}/json/info`);
                const info = await infoRes.json();

                if (info.name) {
                    // ✅ Extract MAC safely
                    const mac =
                        info.mac ||
                        info.wifi?.mac ||
                        "UNKNOWN";

                    // ✅ Fetch presets
                    const presetsRes = await fetch(`http://${ip}/presets.json`);
                    const presetsJson = await presetsRes.json();

                    const presets = Object.entries(presetsJson).map(([id, p]) => ({
                        id,
                        name: p.n || `Preset ${id}`
                    }));

                    foundDevices.push({
                        ip,
                        name: info.name,
                        mac,
                        presets
                    });
                }
            } catch (err) {
                // ignore unreachable IPs
            }
        });

        await Promise.all(requests);

        setDevices(foundDevices);

        // Send to backend
        await sendToServer(foundDevices);

        setLoading(false);
    };

    const sendToServer = async (data) => {
        try {
            await saveWLEDDevices(data);
            console.log("Sent to Spring Boot");
        } catch (err) {
            console.error("Error sending data", err);
        }
    };

    return (
        <div>
            <button onClick={scanNetwork} disabled={loading}>
                {loading ? "Scanning..." : "Scan WLED Devices"}
            </button>

            <ul>
                {devices.map(d => (
                    <li key={d.ip}>
                        <strong>{d.name}</strong> ({d.ip})
                        <pre>{JSON.stringify(d.presets, null, 2)}</pre>
                    </li>
                ))}
            </ul>
        </div>
    );
}