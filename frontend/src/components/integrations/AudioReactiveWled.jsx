import {useCallback, useEffect, useRef, useState} from "react";

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const lerp = (a, b, t) => a + (b - a) * t;

function hsvToRgb(h, s, v) {
    h = ((h % 360) + 360) % 360;
    const c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
    let r = 0, g = 0, b = 0;
    if (h < 60) {
        r = c;
        g = x;
        b = 0;
    } else if (h < 120) {
        r = x;
        g = c;
        b = 0;
    } else if (h < 180) {
        r = 0;
        g = c;
        b = x;
    } else if (h < 240) {
        r = 0;
        g = x;
        b = c;
    } else if (h < 300) {
        r = x;
        g = 0;
        b = c;
    } else {
        r = c;
        g = 0;
        b = x;
    }
    return [Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)];
}

function ampToColor(amp, frac, palette) {
    const v = Math.pow(clamp(amp, 0, 1), 0.65);
    switch (palette) {
        case "fire":
            return hsvToRgb(lerp(0, 50, amp), 1, v);
        case "ocean":
            return hsvToRgb(lerp(220, 175, frac), 1, v);
        case "aurora":
            return hsvToRgb(lerp(130, 290, frac), 0.9, v);
        case "neon":
            return hsvToRgb(lerp(300, 160, frac), 1, v);
        case "rainbow":
            return hsvToRgb(frac * 300, 1, v);
        case "white": {
            const c = Math.round(v * 255);
            return [c, c, c];
        }
        default:
            return hsvToRgb(lerp(0, 270, frac), 1, v);
    }
}

// ── Strip Preview ─────────────────────────────────────────────────────────────
function StripPreview({leds, ledCount}) {
    const MAX = 240;
    const step = Math.max(1, Math.floor(leds.length / MAX));
    const shown = [];
    for (let i = 0; i < leds.length; i += step) shown.push(leds[i] || [0, 0, 0]);
    return (
        <div style={{
            background: "#010104", borderRadius: 10, padding: "14px 10px",
            border: "1px solid rgba(255,255,255,0.04)",
        }}>
            <div style={{display: "flex", gap: 1.5, alignItems: "center", justifyContent: "center"}}>
                {shown.map(([r, g, b], i) => {
                    const br = (r + g + b) / 3;
                    return (
                        <div key={i} style={{
                            width: Math.max(2, Math.min(9, 620 / shown.length)),
                            height: 22,
                            borderRadius: 3,
                            background: `rgb(${r},${g},${b})`,
                            boxShadow: br > 15 ? `0 0 ${3 + br / 35}px rgba(${r},${g},${b},0.9)` : "none",
                            transition: "background 0.04s",
                        }}/>
                    );
                })}
            </div>
            <div style={{
                textAlign: "center",
                fontSize: 10,
                color: "#1e3a52",
                marginTop: 7,
                fontFamily: "'Fira Code',monospace"
            }}>
                {ledCount} LEDs — centre = bass · edges = treble
            </div>
        </div>
    );
}

// ── Frequency bars ────────────────────────────────────────────────────────────
function SpectrumBars({bands, mirror}) {
    if (!bands || !bands.length) return null;
    const disp = mirror ? [...bands].reverse().concat([...bands]) : bands;
    return (
        <div style={{display: "flex", gap: 1.5, alignItems: "flex-end", height: 44, marginTop: 8}}>
            {disp.map((amp, i) => {
                const frac = i / disp.length;
                const hue = mirror ? Math.abs(frac - 0.5) * 2 * 240 : frac * 240;
                return (
                    <div key={i} style={{
                        flex: 1, borderRadius: "2px 2px 0 0",
                        height: `${Math.max(2, amp * 100)}%`,
                        background: `hsl(${hue},100%,55%)`,
                        boxShadow: amp > 0.25 ? `0 0 5px hsl(${hue},100%,55%)` : "none",
                        transition: "height 0.04s",
                    }}/>
                );
            })}
        </div>
    );
}

function Slider({label, min, max, step = 1, value, onChange, fmt}) {
    return (
        <div style={{
            display: "grid",
            gridTemplateColumns: "130px 1fr 52px",
            gap: 10,
            alignItems: "center",
            marginBottom: 10
        }}>
            <span style={{fontSize: 12, color: "#94a3b8"}}>{label}</span>
            <input type="range" min={min} max={max} step={step} value={value}
                   onChange={e => onChange(+e.target.value)}
                   style={{accentColor: "#22d3ee", width: "100%", cursor: "pointer"}}/>
            <span style={{fontSize: 12, color: "#22d3ee", fontFamily: "'Fira Code',monospace", textAlign: "right"}}>
        {fmt ? fmt(value) : value}
      </span>
        </div>
    );
}

function Toggle({label, value, onChange}) {
    return (
        <div onClick={() => onChange(!value)} style={{
            display: "flex", alignItems: "center", gap: 10, cursor: "pointer",
            fontSize: 13, color: value ? "#22d3ee" : "#4b5563", userSelect: "none",
        }}>
            <div style={{
                width: 36, height: 20, borderRadius: 10, position: "relative",
                background: value ? "#06b6d4" : "rgba(255,255,255,0.08)",
                transition: "background 0.2s", flexShrink: 0,
            }}>
                <div style={{
                    position: "absolute", top: 3, left: value ? 19 : 3,
                    width: 14, height: 14, borderRadius: "50%", background: "#fff",
                    transition: "left 0.2s",
                }}/>
            </div>
            {label}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
export default function AudioReactiveWled() {
    const [wledIP, setWledIP] = useState("192.168.1.48");
    const [ledCount, setLedCount] = useState(60);
    const [connected, setConnected] = useState(false);
    const [connecting, setConnecting] = useState(false);
    const [status, setStatus] = useState("Enter WLED IP and click Connect");
    const [listening, setListening] = useState(false);
    const [apiMode, setApiMode] = useState("—"); // feedback to user

    // Params
    const [sensitivity, setSensitivity] = useState(2.2);
    const [decay, setDecay] = useState(0.80);
    const [brightness, setBrightness] = useState(220);
    const [palette, setPalette] = useState("fire");
    const [mirror, setMirror] = useState(true);
    const [minGlow, setMinGlow] = useState(0);

    // State
    const [previewLeds, setPreviewLeds] = useState([]);
    const [bands, setBands] = useState([]);
    const [vuLevel, setVuLevel] = useState(0);

    const analyserRef = useRef(null);
    const audioCtxRef = useRef(null);
    const streamRef = useRef(null);
    const rafRef = useRef(null);
    const smoothRef = useRef([]);
    const sendingRef = useRef(false);
    const lastSend = useRef(0);

    // ── Connect ───────────────────────────────────────────────────────────────
    const testConnection = useCallback(async () => {
        setConnecting(true);
        setStatus("Connecting…");
        try {
            const r = await fetch(`http://${wledIP}/json/info`, {signal: AbortSignal.timeout(4000)});
            if (!r.ok) throw new Error();
            const info = await r.json();
            const lc = info.leds?.count;
            if (lc) setLedCount(lc);
            setConnected(true);
            setStatus(`✓ ${info.name ?? "WLED"} · ${lc ?? ledCount} LEDs · fw ${info.ver ?? "?"}`);

            // Put WLED into solid/controllable state
            await fetch(`http://${wledIP}/json/state`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({on: true, bri: brightness, seg: [{fx: 0, col: [[0, 0, 0]]}]}),
                signal: AbortSignal.timeout(3000),
            });
        } catch {
            setConnected(false);
            setStatus("✗ Cannot reach WLED — check IP and CORS settings (see below)");
        } finally {
            setConnecting(false);
        }
    }, [wledIP, ledCount, brightness]);

    // ── Send frame ────────────────────────────────────────────────────────────
    // Tries per-LED "i" format first; on failure falls back to segment colour
    const sendFrame = useCallback(async (leds) => {
        if (sendingRef.current) return;
        sendingRef.current = true;
        const n = leds.length;

        // --- Method 1: per-LED via "i" flat array --------------------------------
        // WLED docs: seg.i = [r0,g0,b0,r1,g1,b1,...] as flat number array
        // fx MUST be 0 (Solid) for "i" to work
        const flat = new Array(n * 3);
        for (let k = 0; k < n; k++) {
            flat[k * 3] = leds[k][0];
            flat[k * 3 + 1] = leds[k][1];
            flat[k * 3 + 2] = leds[k][2];
        }

        try {
            const res = await fetch(`http://${wledIP}/json/state`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    on: true,
                    bri: brightness,
                    transition: 0,
                    seg: [{
                        id: 0,
                        start: 0,
                        stop: n,
                        fx: 0,        // Solid — mandatory for "i" to work
                        sx: 0,
                        ix: 255,
                        col: [[255, 255, 255], [0, 0, 0], [0, 0, 0]],
                        i: flat,      // flat RGB array
                    }],
                }),
                signal: AbortSignal.timeout(180),
            });
            if (res.ok) {
                setApiMode("per-LED ✓");
                sendingRef.current = false;
                return;
            }
        } catch { /* fall through */
        }

        // --- Method 2: Segment palette colours (always works, less precise) ------
        // Pick representative colours from bass (centre) and treble (edge)
        const centre = leds[Math.floor(n / 2)] ?? [255, 0, 0];
        const edge = leds[0] ?? [0, 0, 255];
        const qtr = leds[Math.floor(n / 4)] ?? [0, 255, 0];
        try {
            await fetch(`http://${wledIP}/json/state`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    on: true, bri: brightness, transition: 0,
                    seg: [{fx: 38, sx: 200, ix: 200, col: [centre, edge, qtr]}],
                }),
                signal: AbortSignal.timeout(180),
            });
            setApiMode("segment fallback");
        } catch {
            setApiMode("send error");
        }

        sendingRef.current = false;
    }, [wledIP, brightness]);

    // ── Build LED array ───────────────────────────────────────────────────────
    const buildLEDs = useCallback((amps) => {
        const n = ledCount;
        const nb = amps.length;
        const leds = [];
        for (let led = 0; led < n; led++) {
            let bandIdx, frac;
            if (mirror) {
                const half = n / 2;
                const dist = Math.abs(led - (half - 0.5)) / half; // 0=centre 1=edge
                bandIdx = Math.min(nb - 1, Math.floor(dist * nb));
                frac = dist;
            } else {
                bandIdx = Math.min(nb - 1, Math.floor((led / n) * nb));
                frac = led / n;
            }
            const amp = amps[bandIdx];
            if (amp < 0.012 && minGlow === 0) {
                leds.push([0, 0, 0]);
            } else {
                const [r, g, b] = ampToColor(Math.max(amp, minGlow / 255), frac, palette);
                leds.push([r, g, b]);
            }
        }
        return leds;
    }, [ledCount, mirror, palette, minGlow]);

    // ── Audio loop ────────────────────────────────────────────────────────────
    useEffect(() => {
        if (!listening) return;
        const NUM_BANDS = Math.max(8, Math.floor(ledCount / 2));
        smoothRef.current = new Array(NUM_BANDS).fill(0);

        const loop = () => {
            rafRef.current = requestAnimationFrame(loop);
            const analyser = analyserRef.current;
            if (!analyser) return;

            const bins = analyser.frequencyBinCount;
            const data = new Uint8Array(bins);
            analyser.getByteFrequencyData(data);

            const sr = audioCtxRef.current.sampleRate;
            const nyquist = sr / 2;

            for (let b = 0; b < NUM_BANDS; b++) {
                // Log-spaced bands from 40 Hz to nyquist
                const fLo = 40 * Math.pow(nyquist / 40, b / NUM_BANDS);
                const fHi = 40 * Math.pow(nyquist / 40, (b + 1) / NUM_BANDS);
                const bLo = Math.max(0, Math.floor(fLo / nyquist * bins));
                const bHi = Math.min(bins - 1, Math.ceil(fHi / nyquist * bins));

                let sum = 0, cnt = 0;
                for (let i = bLo; i <= bHi; i++) {
                    sum += data[i];
                    cnt++;
                }
                const raw = cnt > 0 ? sum / cnt / 255 : 0;
                const boosted = clamp(raw * sensitivity, 0, 1);
                const prev = smoothRef.current[b];
                smoothRef.current[b] = boosted > prev
                    ? lerp(prev, boosted, 0.75)
                    : lerp(prev, boosted, 1 - decay);
            }

            const avg = smoothRef.current.reduce((a, v) => a + v, 0) / NUM_BANDS;
            setVuLevel(clamp(avg * 2.5, 0, 1));
            setBands([...smoothRef.current]);

            const leds = buildLEDs(smoothRef.current);
            setPreviewLeds(leds);

            const now = Date.now();
            if (connected && now - lastSend.current > 40) {
                lastSend.current = now;
                sendFrame(leds);
            }
        };
        rafRef.current = requestAnimationFrame(loop);
        return () => cancelAnimationFrame(rafRef.current);
    }, [listening, ledCount, connected, sensitivity, decay, buildLEDs, sendFrame]);

    // ── Mic ───────────────────────────────────────────────────────────────────
    const startListening = useCallback(async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({audio: true, video: false});
            streamRef.current = stream;
            const ctx = new AudioContext();
            audioCtxRef.current = ctx;
            const src = ctx.createMediaStreamSource(stream);
            const analyser = ctx.createAnalyser();
            analyser.fftSize = 512;
            analyser.smoothingTimeConstant = 0.4;
            src.connect(analyser);
            analyserRef.current = analyser;
            setListening(true);
            setStatus("🎙  Live — spectrum reactive");
        } catch {
            setStatus("✗ Microphone access denied");
        }
    }, []);

    const stopListening = useCallback(() => {
        cancelAnimationFrame(rafRef.current);
        streamRef.current?.getTracks().forEach(t => t.stop());
        audioCtxRef.current?.close();
        analyserRef.current = null;
        setListening(false);
        setVuLevel(0);
        setBands([]);
        setPreviewLeds([]);
        if (connected) {
            fetch(`http://${wledIP}/json/state`, {
                method: "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({on: true, bri: 10, seg: [{fx: 0, col: [[0, 0, 0]]}]}),
            }).catch(() => {
            });
        }
        setStatus(connected ? "✓ Connected — press Start" : "Enter IP and click Connect");
    }, [connected, wledIP]);

    const PALETTES = [
        {id: "fire", label: "🔥 Fire", g: "linear-gradient(90deg,#ff1800,#ff6200,#ffc200)"},
        {id: "ocean", label: "🌊 Ocean", g: "linear-gradient(90deg,#0030ff,#00c6ff)"},
        {id: "aurora", label: "🌌 Aurora", g: "linear-gradient(90deg,#00ff87,#7b00ff)"},
        {id: "neon", label: "⚡ Neon", g: "linear-gradient(90deg,#ff00cc,#00ffff)"},
        {id: "rainbow", label: "🌈 Rainbow", g: "linear-gradient(90deg,red,orange,yellow,green,blue,violet)"},
        {id: "white", label: "❄️ White", g: "linear-gradient(90deg,#555,#fff)"},
    ];

    const vuHue = 120 - vuLevel * 120;
    const card = {
        background: "rgba(255,255,255,0.025)",
        border: "1px solid rgba(255,255,255,0.06)",
        borderRadius: 16,
        padding: "18px 20px"
    };
    const cardTitle = {
        fontSize: 10,
        fontWeight: 700,
        letterSpacing: 2,
        color: "#334155",
        textTransform: "uppercase",
        fontFamily: "'Fira Code',monospace",
        marginBottom: 12
    };
    const inp = {
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.09)",
        borderRadius: 9,
        padding: "10px 13px",
        color: "#e2e8f0",
        fontSize: 14,
        fontFamily: "'Fira Code',monospace",
        outline: "none"
    };

    return (
        <>
            <link
                href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;600;700&family=Fira+Code:wght@400;500&display=swap"
                rel="stylesheet"/>
            <div style={{
                minHeight: "100vh",
                background: "radial-gradient(ellipse 80% 50% at 50% -5%, #061020 0%, #020509 55%)",
                color: "#e2e8f0",
                fontFamily: "'DM Sans',sans-serif",
                paddingBottom: 60
            }}>

                {/* Header */}
                <div style={{
                    background: "linear-gradient(180deg,rgba(6,182,212,0.08) 0%,transparent 100%)",
                    borderBottom: "1px solid rgba(6,182,212,0.1)",
                    padding: "20px 28px 16px",
                    display: "flex",
                    alignItems: "center",
                    gap: 14
                }}>
                    <div style={{
                        width: 40,
                        height: 40,
                        borderRadius: 11,
                        background: "linear-gradient(135deg,#0ea5e9,#06b6d4)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontSize: 20,
                        boxShadow: "0 0 20px rgba(6,182,212,0.5)",
                        flexShrink: 0
                    }}>〰️
                    </div>
                    <div>
                        <div style={{fontSize: 19, fontWeight: 700, letterSpacing: -0.5}}>WLED Spectrum Strip</div>
                        <div style={{fontSize: 11, color: "#0ea5e9", fontFamily: "'Fira Code',monospace"}}>center-out ·
                            bass→middle · treble→edges
                        </div>
                    </div>
                    <div style={{marginLeft: "auto", textAlign: "right"}}>
                        <div style={{fontSize: 10, color: "#334155", fontFamily: "'Fira Code',monospace"}}>api</div>
                        <div style={{
                            fontSize: 11,
                            color: apiMode.includes("✓") ? "#22d3ee" : "#94a3b8",
                            fontFamily: "'Fira Code',monospace"
                        }}>{apiMode}</div>
                    </div>
                </div>

                <div style={{
                    maxWidth: 720,
                    margin: "0 auto",
                    padding: "20px 16px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 14
                }}>

                    {/* Connection */}
                    <div style={card}>
                        <div style={cardTitle}>WLED Connection</div>
                        <div style={{display: "flex", gap: 10, alignItems: "center"}}>
                            <input value={wledIP} onChange={e => setWledIP(e.target.value)} placeholder="192.168.x.x"
                                   style={{...inp, flex: 1}}/>
                            <input value={ledCount} onChange={e => setLedCount(Math.max(1, +e.target.value))}
                                   type="number" min={1} max={1500} style={{...inp, width: 80, textAlign: "center"}}/>
                            <button onClick={testConnection} disabled={connecting} style={{
                                padding: "10px 18px", borderRadius: 9, border: "none", flexShrink: 0,
                                background: "linear-gradient(135deg,#06b6d4,#0284c7)", color: "#fff",
                                fontWeight: 600, fontSize: 13, cursor: "pointer",
                                boxShadow: "0 0 14px rgba(6,182,212,0.3)", opacity: connecting ? 0.6 : 1,
                            }}>{connecting ? "…" : "Connect"}</button>
                        </div>
                        <div style={{
                            marginTop: 10,
                            fontSize: 11,
                            padding: "8px 12px",
                            background: connected ? "rgba(6,182,212,0.07)" : "rgba(255,255,255,0.03)",
                            border: `1px solid ${connected ? "rgba(6,182,212,0.2)" : "rgba(255,255,255,0.06)"}`,
                            borderRadius: 7,
                            color: connected ? "#67e8f9" : "#475569",
                            fontFamily: "'Fira Code',monospace",
                        }}>{status}</div>
                        <div style={{
                            marginTop: 8,
                            fontSize: 11,
                            padding: "8px 12px",
                            background: "rgba(251,146,60,0.05)",
                            border: "1px solid rgba(251,146,60,0.12)",
                            borderRadius: 7,
                            color: "#c2773a",
                            lineHeight: 1.8
                        }}>
                            ⚠️ Required: <b>WLED → Config → Security → CORS</b> → type <code>*</code> → Save →
                            Reboot<br/>
                            Also: <b>Config → LED Prefs → "Disable realtime override"</b> → must be <b>OFF</b>
                        </div>
                    </div>

                    {/* Strip Preview */}
                    <div style={card}>
                        <div style={cardTitle}>Live Strip Preview</div>
                        {previewLeds.length > 0
                            ? <StripPreview leds={previewLeds} ledCount={ledCount}/>
                            : <div style={{
                                height: 52,
                                borderRadius: 10,
                                background: "#010104",
                                border: "1px solid rgba(255,255,255,0.03)",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontSize: 12,
                                color: "#1e293b"
                            }}>waiting for audio…</div>
                        }
                        {bands.length > 0 && <SpectrumBars bands={bands} mirror={mirror}/>}
                        <div style={{marginTop: 10}}>
                            <div style={{display: "flex", justifyContent: "space-between", marginBottom: 4}}>
                                <span style={{fontSize: 10, color: "#1e3a52", fontFamily: "'Fira Code',monospace"}}>master level</span>
                                <span style={{
                                    fontSize: 10,
                                    color: `hsl(${vuHue},100%,60%)`,
                                    fontFamily: "'Fira Code',monospace"
                                }}>{Math.round(vuLevel * 100)}%</span>
                            </div>
                            <div style={{
                                height: 5,
                                borderRadius: 3,
                                background: "rgba(255,255,255,0.05)",
                                overflow: "hidden"
                            }}>
                                <div style={{
                                    height: "100%",
                                    borderRadius: 3,
                                    width: `${clamp(vuLevel * 100, 0, 100)}%`,
                                    background: `linear-gradient(90deg,hsl(${vuHue},100%,45%),hsl(${vuHue},100%,65%))`,
                                    boxShadow: `0 0 8px hsl(${vuHue},100%,55%)`,
                                    transition: "width 0.04s"
                                }}/>
                            </div>
                        </div>
                    </div>

                    {/* Palette */}
                    <div style={card}>
                        <div style={cardTitle}>Colour Palette</div>
                        <div style={{display: "flex", gap: 8, flexWrap: "wrap"}}>
                            {PALETTES.map(p => (
                                <button key={p.id} onClick={() => setPalette(p.id)} style={{
                                    padding: "7px 15px",
                                    borderRadius: 20,
                                    border: "none",
                                    cursor: "pointer",
                                    fontSize: 12,
                                    background: palette === p.id ? p.g : "rgba(255,255,255,0.05)",
                                    color: palette === p.id ? "#fff" : "#64748b",
                                    fontWeight: palette === p.id ? 700 : 400,
                                    boxShadow: palette === p.id ? "0 0 12px rgba(255,255,255,0.12)" : "none",
                                    transition: "all 0.15s",
                                }}>{p.label}</button>
                            ))}
                        </div>
                    </div>

                    {/* Settings */}
                    <div style={card}>
                        <div style={cardTitle}>Settings</div>
                        <div style={{marginBottom: 14}}>
                            <Toggle label="Center-out (bass = centre, treble = edges)" value={mirror}
                                    onChange={setMirror}/>
                        </div>
                        <Slider label="Brightness" min={10} max={255} value={brightness} onChange={setBrightness}/>
                        <Slider label="Sensitivity" min={0.5} max={6} step={0.1} value={sensitivity}
                                onChange={setSensitivity} fmt={v => v.toFixed(1) + "×"}/>
                        <Slider label="Decay" min={0.4} max={0.97} step={0.01} value={decay} onChange={setDecay}
                                fmt={v => v.toFixed(2)}/>
                        <Slider label="Min glow" min={0} max={40} value={minGlow} onChange={setMinGlow}/>
                    </div>

                    {/* Start/Stop */}
                    <button onClick={listening ? stopListening : startListening} style={{
                        width: "100%", padding: "15px", borderRadius: 12, border: "none",
                        background: listening ? "linear-gradient(135deg,#dc2626,#b91c1c)" : "linear-gradient(135deg,#06b6d4,#6366f1)",
                        color: "#fff", fontWeight: 700, fontSize: 16, cursor: "pointer", letterSpacing: 0.3,
                        boxShadow: listening ? "0 0 28px rgba(220,38,38,0.4)" : "0 0 28px rgba(6,182,212,0.4)",
                        transition: "all 0.2s",
                    }}>
                        {listening ? "⏹  Stop" : "🎙  Start Spectrum Reactive"}
                    </button>

                    {/* Troubleshooting */}
                    <div style={{
                        background: "rgba(255,255,255,0.015)",
                        border: "1px solid rgba(255,255,255,0.05)",
                        borderRadius: 12,
                        padding: "14px 16px",
                        fontSize: 11,
                        color: "#334155",
                        lineHeight: 2,
                        fontFamily: "'Fira Code',monospace"
                    }}>
                        <div style={{color: "#475569", fontWeight: 600, marginBottom: 4}}>Step-by-step checklist</div>
                        1. Open <code style={{color: "#4b5563"}}>http://{wledIP}</code> in browser — should show WLED UI<br/>
                        2. WLED → ⚙️ Config → Security → CORS allow: <code style={{color: "#4b5563"}}>*</code> →
                        Save &amp; Reboot<br/>
                        3. WLED → ⚙️ Config → LED Preferences → "Disable realtime" = OFF<br/>
                        4. WLED → ⚙️ Config → Sync → "Receive realtime UDP" = ON<br/>
                        5. Enter IP above → Connect → Start — strip should light up immediately
                    </div>

                </div>
            </div>
        </>
    );
}