import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {MapView} from "../charts/MapView.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select} from "@mui/material";
import {getLastData, getSessionBuckets, getSessions} from "../../services/apis.jsx";
import {findGpsDeviceId} from "../../utils/Helper.jsx";
import {Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from "recharts";

const METRIC_DEFS = [
    {
        key: "SATS",
        label: "Satellites",
        icon: "satellite",
        unit: "",
        format: (v) => (v != null ? v : "—"),
        colorFn: (v) => (v >= 6 ? "success" : v >= 3 ? "warning" : "danger"),
    },
    {
        key: "FIX",
        label: "Fix",
        icon: "gps",
        unit: "",
        format: (v) => {
            if (v == null) return "—";
            const map = {
                0: "No fix",
                1: "GPS",
                2: "DGPS",
                3: "PPS",
                4: "RTK",
                5: "Float",
                6: "Est.",
                7: "Manual",
                8: "Sim"
            };
            return map[v] ?? `${v}`;
        },
        colorFn: (v) => (v >= 2 ? "success" : v === 1 ? "warning" : "danger"),
    },
    {
        key: "HDOP",
        label: "HDOP",
        icon: "crosshair",
        unit: "",
        format: (v) => (v != null ? parseFloat(v).toFixed(1) : "—"),
        colorFn: (v) => (v <= 1.5 ? "success" : v <= 3 ? "warning" : "danger"),
    },
    {
        key: "SPEED",
        label: "Speed",
        icon: "speedometer",
        unit: "km/h",
        format: (v) => (v != null ? parseFloat(v).toFixed(1) : "—"),
        colorFn: () => "info",
    },
    {
        key: "ALT",
        label: "Altitude",
        icon: "mountain",
        unit: "m",
        format: (v) => (v != null ? parseFloat(v).toFixed(0) : "—"),
        colorFn: () => "info",
    },
    {
        key: "COURSE",
        label: "Heading",
        icon: "compass",
        unit: "°",
        format: (v) => (v != null ? parseFloat(v).toFixed(1) : "—"),
        colorFn: () => "info",
    },
];

const STATUS_COLOR = {
    success: "green",
    warning: "yellow",
    danger: "red",
    info: "lightblue",
};

const STATUS_BG = {
    success: "green",
    warning: "yellow",
    danger: "red",
    info: "blue",
};

// Cap how many readings we keep in memory per device so a long-running
// live session doesn't grow the route/chart buffers unbounded.
const MAX_BUFFER_POINTS = 500;
// Down-sample factor for map markers — showing a dot per reading would
// clutter the map, so we only render every Nth point.
const MARKER_SAMPLE_EVERY = 5;
// Poll interval for an ACTIVE recording session being viewed in Recording mode.
const SESSION_POLL_MS = 10_000;

function MetricChip({def, value}) {
    const status = def.colorFn(value);
    return (
        <div
            style={{
                display: "flex",
                flexDirection: "column",
                gap: 2,
                border: "0.5px solid var(--color-border-tertiary)",
                borderRadius: 10,
                padding: "10px 14px",
                minWidth: 90,
                flex: "1 1 90px",
            }}
        >
            <span
                style={{
                    fontSize: 11,
                    color: "var(--color-text-secondary)",
                    textTransform: "uppercase",
                    letterSpacing: "0.04em",
                    fontWeight: 500,
                }}
            >
                {def.label}
            </span>
            <span
                style={{
                    fontSize: 20,
                    fontWeight: 500,
                    color: STATUS_COLOR[status],
                    lineHeight: 1.2,
                }}
            >
                {def.format(value)}
                {value != null && def.unit ? (
                    <span style={{fontSize: 12, marginLeft: 2, color: "var(--color-text-secondary)"}}>
                        {def.unit}
                    </span>
                ) : null}
            </span>
            <span
                style={{
                    display: "inline-block",
                    fontSize: 10,
                    borderRadius: 4,
                    padding: "1px 6px",
                    // background: STATUS_BG[status],
                    color: STATUS_COLOR[status],
                    width: "fit-content",
                }}
            >
                {status}
            </span>
        </div>
    );
}

function CoordBadge({lat, lng}) {
    if (lat == null || lng == null) return null;
    return (
        <div
            style={{
                display: "flex",
                gap: 8,
                fontSize: 12,
                color: "var(--color-text-secondary)",
                fontFamily: "var(--font-mono)",
                alignItems: "center",
            }}
        >
            <span>LAT {parseFloat(lat).toFixed(6)}</span>
            <span style={{opacity: 0.4}}>·</span>
            <span>LNG {parseFloat(lng).toFixed(6)}</span>
        </div>
    );
}

// Mirrors the haversine helper used in GpsRoutePanel, for route distance.
const haversineKm = ([lat1, lon1], [lat2, lon2]) => {
    const R = 6371;
    const dL = ((lat2 - lat1) * Math.PI) / 180;
    const dl = ((lon2 - lon1) * Math.PI) / 180;
    const a =
        Math.sin(dL / 2) ** 2 +
        Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dl / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

// Same fix/stationary/no-data filtering rules as Helper.jsx's extractGpsRoute,
// applied to a single incoming live reading against the last accepted point.
const isAcceptableReading = (reading, lastLat, lastLng) => {
    const lat = parseFloat(reading.LAT ?? reading.lat ?? reading.latitude);
    const lng = parseFloat(reading.LONG ?? reading.lng ?? reading.longitude ?? reading.lon);
    const fix = reading.FIX ?? reading.fix;

    if (!lat || !lng) return null;               // no fix
    if (fix === 0 || fix === '0') return null;    // explicit invalid fix
    if (lat === lastLat && lng === lastLng) return null; // stationary duplicate

    return {lat, lng};
};

/** Builds the same {lat,lng,speed,satellites,fix,time} shape from a raw reading,
 *  used by both the live buffer and the recording-session buckets so downstream
 *  stats/markers/chart code can be shared between modes. */
const toBufferPoint = (lat, lng, reading) => ({
    lat,
    lng,
    speed: (reading.SPEED ?? reading.speed) != null ? parseFloat(reading.SPEED ?? reading.speed) : null,
    satellites: reading.SATS ?? reading.satellites ?? null,
    fix: reading.FIX ?? reading.fix ?? null,
    time: reading.ts ? new Date(reading.ts).getTime() : Date.now(),
});

/** Extracts a buffer (lat/lng/speed/satellites/fix/time) from a recording
 *  session's buckets, scoped to the GPS device, reusing Helper.jsx's
 *  filtering rules via extractGpsRoute for the lat/lng/fix/stationary logic,
 *  then re-walking the readings to attach speed/satellites/time per point. */
const bufferFromSessionBuckets = (buckets, gpsDeviceId) => {
    const gpsBuckets = gpsDeviceId
        ? buckets.filter((b) => b.deviceId === gpsDeviceId)
        : buckets;

    const buffer = [];
    let lastLat = null;
    let lastLng = null;

    for (const bucket of gpsBuckets) {
        for (const r of bucket.readings ?? []) {
            const lat = parseFloat(r.LAT ?? r.lat ?? r.latitude);
            const lng = parseFloat(r.LONG ?? r.lng ?? r.longitude ?? r.lon);
            const fix = r.FIX ?? r.fix;

            if (!lat || !lng) continue;
            if (fix === 0 || fix === '0') continue;
            if (lat === lastLat && lng === lastLng) continue;

            buffer.push(toBufferPoint(lat, lng, r));
            lastLat = lat;
            lastLng = lng;
        }
    }
    return buffer;
};

/** Route + chart stats derived from an accumulated reading buffer. Shared
 *  between Live and Recording modes since both produce the same buffer shape. */
function useRouteStats(buffer) {
    return useMemo(() => {
        if (!buffer.length) return null;

        const route = buffer.map((r) => [r.lat, r.lng]);

        let distKm = 0;
        for (let i = 1; i < route.length; i++) {
            distKm += haversineKm(route[i - 1], route[i]);
        }

        const speeds = buffer.map((r) => r.speed).filter((s) => s != null);
        const avgSpeed = speeds.length
            ? speeds.reduce((a, b) => a + b, 0) / speeds.length
            : null;
        const maxSpeed = speeds.length ? Math.max(...speeds) : null;

        const last = buffer.at(-1);

        return {
            points: route.length,
            distKm: distKm.toFixed(2),
            avgSpeed,
            maxSpeed,
            satellites: last.satellites,
            fix: last.fix,
        };
    }, [buffer]);
}

function StatPill({label, value}) {
    return (
        <div
            style={{
                display: "flex",
                flexDirection: "row",
                gap: 2,
                alignItems: 'center',
                // border: "0.5px solid var(--color-border-tertiary)",
                // borderRadius: 10,
                padding: "8px 12px",
                minWidth: 84,
            }}
        >
            <span style={{
                fontSize: 12,
                color: "var(--color-text-secondary)",
                textTransform: "uppercase",
                letterSpacing: "0.04em"
            }}>
                {label}
            </span>
            <span style={{fontSize: 12, fontWeight: 500, marginLeft: '12px', color: "var(--color-text-primary)"}}>
                {value}
            </span>
        </div>
    );
}

const speedColor = (speed) => {
    if (speed == null) return '#94a3b8'; // slate - unknown
    if (speed < 5) return '#60a5fa';     // blue - idle / very slow
    if (speed < 30) return '#4ade80';    // green - normal
    if (speed < 60) return '#facc15';    // yellow - brisk
    return '#f87171';                    // red - fast
};

function SpeedTooltip({active, payload, label}) {
    if (!active || !payload?.length) return null;
    const speed = payload[0].value;
    return (
        <div
            style={{
                background: "#1e1e1e",
                border: "1px solid #3a3a3a",
                borderRadius: 8,
                padding: "8px 12px",
                fontSize: 12,
                color: "#ffffff",
            }}
        >
            <div style={{color: "#a1a1aa", marginBottom: 2}}>
                {new Date(label).toLocaleTimeString()}
            </div>
            <div style={{display: "flex", alignItems: "center", gap: 6}}>
                <span
                    style={{
                        width: 8,
                        height: 8,
                        borderRadius: "50%",
                        background: speedColor(speed),
                        display: "inline-block",
                    }}
                />
                <span style={{fontWeight: 600}}>{speed} km/h</span>
            </div>
        </div>
    );
}

function SpeedChart({data, routeStats}) {
    if (!data.length) return null;

    // Build gradient stops positioned by each point's fractional x-position,
    // colored by that point's speed — so the line's color at any pixel
    // matches the speed value at that point, not just a fixed start/end fade.
    const lastIdx = data.length - 1;
    const gradientId = "speedLineGradient";
    const gradientStops = data.map((d, i) => ({
        offset: lastIdx === 0 ? "0%" : `${(i / lastIdx) * 100}%`,
        color: speedColor(d.speed),
    }));

    return (
        <div
            style={{
                // border: "0.5px solid #3a3a3a",
                borderRadius: 12,
                background: "#141414",
                padding: "8px",
                // margin: "10px 10px 0",
            }}
        >
            <div>
                <span
                    style={{
                        fontSize: 11,
                        color: "#a1a1aa",
                        textTransform: "uppercase",
                        letterSpacing: "0.04em",
                        fontWeight: 500,
                        marginLeft: 8,
                    }}
                >
                Speed over time
            </span>
                {/* Route stat pills — only once we have an accumulated route */}
                {routeStats && (
                    <div style={{display: "flex", flexWrap: "wrap", gap: 8, marginLeft: 10, marginBottom: 4}}>
                        <StatPill label="Route points" value={routeStats.points}/>
                        <StatPill label="Distance" value={`${routeStats.distKm} km`}/>
                        <StatPill label="Avg speed"
                                  value={routeStats.avgSpeed != null ? `${routeStats.avgSpeed.toFixed(1)} km/h` : "—"}/>
                        <StatPill label="Max speed"
                                  value={routeStats.maxSpeed != null ? `${routeStats.maxSpeed.toFixed(1)} km/h` : "—"}/>
                    </div>
                )}
            </div>

            <ResponsiveContainer width="100%" height={160}>
                <LineChart data={data} margin={{top: 10, right: 16, left: -10, bottom: 0}}>
                    <defs>
                        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="0">
                            {gradientStops.map((stop, i) => (
                                <stop key={i} offset={stop.offset} stopColor={stop.color}/>
                            ))}
                        </linearGradient>
                    </defs>
                    <XAxis
                        dataKey="time"
                        tick={{fontSize: 10, fill: "#ffffff"}}
                        axisLine={{stroke: "#3a3a3a"}}
                        tickLine={{stroke: "#3a3a3a"}}
                        tickFormatter={(t) => new Date(t).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}
                        minTickGap={30}
                    />
                    <YAxis
                        tick={{fontSize: 10, fill: "#ffffff"}}
                        axisLine={{stroke: "#3a3a3a"}}
                        tickLine={{stroke: "#3a3a3a"}}
                        width={36}
                        label={{value: "km/h", angle: -90, position: "insideLeft", fontSize: 10, fill: "#ffffff"}}
                    />
                    <Tooltip content={<SpeedTooltip/>}/>
                    <Line
                        type="monotone"
                        dataKey="speed"
                        stroke={`url(#${gradientId})`}
                        strokeWidth={2.5}
                        dot={false}
                        isAnimationActive={false}
                    />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
}

// ── ModeToggle ────────────────────────────────────────────────────────────────
function ModeToggle({mode, onChange}) {
    return (
        <div
            style={{
                display: "inline-flex",
                border: "0.5px solid var(--color-border-tertiary)",
                borderRadius: 8,
                padding: 2,
                gap: 2,
            }}
        >
            {[["live", "Live"], ["recording", "Recording"]].map(([key, label]) => {
                const isActive = mode === key;
                return (
                    <button
                        key={key}
                        onClick={() => onChange(key)}
                        style={{
                            padding: "5px 12px",
                            borderRadius: 6,
                            fontSize: 12,
                            fontWeight: isActive ? 500 : 400,
                            cursor: "pointer",
                            border: "none",
                            background: isActive
                                ? "var(--color-background-info)"
                                : "transparent",
                            color: isActive
                                ? "var(--color-text-info)"
                                : "var(--color-text-secondary)",
                            transition: "all 0.15s ease",
                        }}
                    >
                        {label}
                    </button>
                );
            })}
        </div>
    );
}

// ── SessionPicker ─────────────────────────────────────────────────────────────
function SessionPicker({sessions, loading, selectedId, onSelect}) {
    if (loading) {
        return (
            <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                Loading recordings…
            </span>
        );
    }

    if (!sessions.length) {
        return (
            <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                No GPS recordings found
            </span>
        );
    }

    return (
        <FormControl size="small" fullWidth>
            <InputLabel>Recording session</InputLabel>
            <Select
                label="Recording session"
                variant="outlined"
                size="small"
                value={selectedId ?? ""}
                onChange={(e) => onSelect(e.target.value)}
            >
                {sessions.map((s) => (
                    <MenuItem key={s.id} value={s.id}>
                        {s.name} — {s.status}
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
}

export const MapDevices = React.memo(() => {
    const {messages} = useDeviceLiveData();
    const {devices, loading, error} = useCachedDevices();

    const [mode, setMode] = useState("live"); // 'live' | 'recording'

    // ── Live mode state ───────────────────────────────────────────────────
    const [liveDataMap, setLiveDataMap] = useState({});
    const [activeDeviceId, setActiveDeviceId] = useState(null);

    // Per-device accumulated reading buffer for live mode, used to build the
    // route + speed chart. Kept in a ref to avoid re-render churn on every
    // push; mirrored into state (liveRouteBuffers) only when it changes shape.
    const buffersRef = useRef({}); // { [deviceId]: [{lat, lng, speed, satellites, fix, time}] }
    const [liveRouteBuffers, setLiveRouteBuffers] = useState({});

    const gpsDevices = devices?.filter((d) => d.category === "SENSOR|GPS") ?? [];

    useEffect(() => {
        if (gpsDevices.length > 0 && activeDeviceId == null) {
            const id = gpsDevices[0]._id ?? gpsDevices[0].id
            setActiveDeviceId(id);
            getLastData(id).then((data) => {
                setLiveDataMap({[id]: data})
            })

        }
    }, [gpsDevices.length]);

    useEffect(() => {
        if (mode === "recording" || !messages?.deviceId || !messages?.data) return;
        setLiveDataMap((prev) => ({
            ...prev,
            [messages.deviceId]: messages.data,
        }));

        const id = messages.deviceId;
        const reading = messages.data;
        const buf = buffersRef.current[id] ?? [];
        const lastPoint = buf.at(-1);
        const accepted = isAcceptableReading(reading, lastPoint?.lat ?? null, lastPoint?.lng ?? null);

        if (accepted) {
            const next = [...buf, toBufferPoint(accepted.lat, accepted.lng, reading)].slice(-MAX_BUFFER_POINTS);
            buffersRef.current[id] = next;
            setLiveRouteBuffers((prev) => ({...prev, [id]: next}));
        }
    }, [messages]);

    // ── Recording mode state ──────────────────────────────────────────────
    const [sessions, setSessions] = useState([]);
    const [sessionsLoading, setSessionsLoading] = useState(false);
    const [selectedSessionId, setSelectedSessionId] = useState(null);
    const [selectedSession, setSelectedSession] = useState(null); // full session w/ buckets
    const sessionPollRef = useRef(null);

    // Fetch the session list once we enter Recording mode, filtered to
    // sessions that actually involve a GPS device, and to ACTIVE/STOPPED only.
    useEffect(() => {
        if (mode !== "recording" || !devices?.length) return;

        let cancelled = false;
        setSessionsLoading(true);
        getSessions()
            .then((data) => {
                if (cancelled) return;
                const gpsSessions = (data ?? []).filter((s) => {
                    if (s.status !== "ACTIVE" && s.status !== "STOPPED") return false;
                    return !!findGpsDeviceId(s.deviceIds, devices);
                });
                setSessions(gpsSessions);
                // Auto-select the first session if none chosen yet
                if (!selectedSessionId && gpsSessions.length > 0) {
                    setSelectedSessionId(gpsSessions[0].id);
                }
            })
            .finally(() => {
                if (!cancelled) setSessionsLoading(false);
            });

        return () => {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mode, devices?.length]);

    // Load buckets for the selected session, and poll while it's ACTIVE.
    useEffect(() => {
        if (mode !== "recording" || !selectedSessionId) {
            setSelectedSession(null);
            return;
        }

        let cancelled = false;

        const load = () => {
            getSessionBuckets(selectedSessionId).then((withBuckets) => {
                if (!cancelled) setSelectedSession(withBuckets);
            });
        };

        load();

        clearInterval(sessionPollRef.current);
        const meta = sessions.find((s) => s.id === selectedSessionId);
        if (meta?.status === "ACTIVE") {
            sessionPollRef.current = setInterval(load, SESSION_POLL_MS);
        }

        return () => {
            cancelled = true;
            clearInterval(sessionPollRef.current);
        };
    }, [mode, selectedSessionId, sessions]);

    const sessionGpsDeviceId = useMemo(
        () => findGpsDeviceId(selectedSession?.deviceIds, devices),
        [selectedSession?.deviceIds, devices]
    );

    const recordingBuffer = useMemo(() => {
        if (!selectedSession?.buckets?.length) return [];
        return bufferFromSessionBuckets(selectedSession.buckets, sessionGpsDeviceId);
    }, [selectedSession?.buckets, sessionGpsDeviceId]);

    // ── Derived data, shared shape between modes ──────────────────────────
    const activeDevice = gpsDevices.find(
        (d) => (d._id ?? d.id) === activeDeviceId
    );
    const liveData = liveDataMap[activeDeviceId] ?? {};
    const liveBuffer = liveRouteBuffers[activeDeviceId] ?? [];

    const activeBuffer = mode === "live" ? liveBuffer : recordingBuffer;

    const lastPoint = activeBuffer.at(-1);
    const lat = mode === "live" ? (liveData.LAT ?? liveData.lat) : lastPoint?.lat;
    const lng = mode === "live" ? (liveData.LONG ?? liveData.lng ?? liveData.LON) : lastPoint?.lng;
    const hasPosition = lat != null && lng != null;

    const routeStats = useRouteStats(activeBuffer);

    const route = activeBuffer.length > 1 ? activeBuffer.map((r) => [r.lat, r.lng]) : null;

    // Full-length speed array, same order as `route` (one entry per
    // coordinate, NOT down-sampled). Passed to MapView so it can color each
    // route segment by speed — the map-equivalent of the chart's gradient line.
    const routeSpeeds = useMemo(
        () => (activeBuffer.length > 1 ? activeBuffer.map((r) => r.speed) : null),
        [activeBuffer]
    );

    const markerPoints = useMemo(() => {
        if (activeBuffer.length < 2) return [];
        const sampled = activeBuffer.filter((_, i) => i % MARKER_SAMPLE_EVERY === 0);
        const last = activeBuffer.at(-1);
        if (sampled.at(-1) !== last) sampled.push(last);
        return sampled.map((r) => ({
            lat: r.lat,
            lng: r.lng,
            speed: r.speed,
            satellites: r.satellites,
            fix: r.fix,
            timestamp: r.time,
        }));
    }, [activeBuffer]);

    const speedSeries = useMemo(
        () => activeBuffer
            .filter((r) => r.speed != null)
            .map((r) => ({time: r.time, speed: r.speed})),
        [activeBuffer]
    );

    const handleModeChange = useCallback((next) => {
        setMode(next);
    }, []);

    if (loading) {
        return (
            <div style={{padding: "2rem", color: "var(--color-text-secondary)", textAlign: "center"}}>
                Loading GPS devices…
            </div>
        );
    }

    if (error || gpsDevices.length === 0) {
        return (
            <div
                style={{
                    padding: "2rem",
                    background: "var(--color-background-secondary)",
                    borderRadius: 12,
                    textAlign: "center",
                    color: "var(--color-text-secondary)",
                    border: "0.5px solid var(--color-border-tertiary)",
                }}
            >
                <span style={{fontSize: 13}}>No GPS devices found</span>
            </div>
        );
    }

    return (
        <Card variant="outlined" style={{
            display: "flex",
            flexDirection: "column",
            gap: 0,
            // maxHeight: '97dvh',
            margin: "10px",
            background: 'transparent',
            backdropFilter: 'blur(4)',
            padding: "20px",
            borderRadius: "12px"
        }}>
            {/* Header row */}
            <div
                style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    marginBottom: 12,
                    flexWrap: "wrap",
                    gap: 8,
                }}
            >
                <div style={{display: "flex", alignItems: "center", gap: 10}}>
                    <span
                        style={{
                            fontSize: 13,
                            fontWeight: 500,
                            color: "var(--color-text-primary)",
                            letterSpacing: "0.01em",
                        }}
                    >
                        GPS Tracker
                    </span>
                    <ModeToggle mode={mode} onChange={handleModeChange}/>
                </div>

                {mode === "live" && hasPosition && (
                    <span
                        style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: 5,
                            fontSize: 11,
                            fontWeight: 500,
                            color: "var(--color-text-success)",
                            background: "var(--color-background-success)",
                            border: "0.5px solid var(--color-border-success)",
                            borderRadius: 6,
                            padding: "3px 8px",
                        }}
                    >
                        <span
                            style={{
                                width: 6,
                                height: 6,
                                borderRadius: "50%",
                                background: "var(--color-text-success)",
                                display: "inline-block",
                            }}
                        />
                        Live
                    </span>
                )}

                {mode === "recording" && selectedSession?.status === "ACTIVE" && (
                    <span
                        style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: 5,
                            fontSize: 11,
                            fontWeight: 500,
                            color: "var(--color-text-warning)",
                            background: "var(--color-background-warning)",
                            border: "0.5px solid var(--color-border-warning)",
                            borderRadius: 6,
                            padding: "3px 8px",
                        }}
                    >
                        Recording in progress
                    </span>
                )}
                {/* Recording mode: session dropdown */}
                {mode === "recording" && (
                    <div style={{marginBottom: 12}}>
                        <SessionPicker
                            sessions={sessions}
                            loading={sessionsLoading}
                            selectedId={selectedSessionId}
                            onSelect={setSelectedSessionId}
                        />
                    </div>
                )}
            </div>

            {/* Live mode: device tabs */}
            {mode === "live" && gpsDevices.length > 1 && (
                <div
                    style={{
                        display: "flex",
                        gap: 6,
                        flexWrap: "wrap",
                        marginBottom: 12,
                    }}
                >
                    {gpsDevices.map((device) => {
                        const id = device._id ?? device.id;
                        const isActive = id === activeDeviceId;
                        const hasData = !!liveDataMap[id];
                        return (
                            <button
                                key={id}
                                onClick={() => setActiveDeviceId(id)}
                                style={{
                                    padding: "6px 14px",
                                    borderRadius: 8,
                                    fontSize: 12,
                                    fontWeight: isActive ? 500 : 400,
                                    cursor: "pointer",
                                    border: isActive
                                        ? "0.5px solid var(--color-border-info)"
                                        : "0.5px solid var(--color-border-tertiary)",
                                    background: isActive
                                        ? "var(--color-background-info)"
                                        : "var(--color-background-primary)",
                                    color: isActive
                                        ? "var(--color-text-info)"
                                        : "var(--color-text-secondary)",
                                    transition: "all 0.15s ease",
                                    display: "flex",
                                    alignItems: "center",
                                    gap: 6,
                                }}
                            >
                                {hasData && (
                                    <span
                                        style={{
                                            width: 5,
                                            height: 5,
                                            borderRadius: "50%",
                                            background: isActive
                                                ? "var(--color-text-info)"
                                                : "var(--color-text-success)",
                                            display: "inline-block",
                                        }}
                                    />
                                )}
                                {device.name ?? device.label ?? id}
                            </button>
                        );
                    })}
                </div>
            )}


            {/* Map */}
            <div
                style={{
                    borderRadius: 12,
                    overflow: "hidden",
                    border: "0.5px solid var(--color-border-tertiary)",
                    margin: 10,
                }}
            >
                <MapView lat={lat} lng={lng} h="70dvh" w="100%" route={route} points={markerPoints}
                         centerMap={mode === "live"}
                         routeSpeeds={routeSpeeds}/>
            </div>

            {/* Coordinate badge */}
            <div style={{display: 'flex', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'}}>
                <div style={{marginBottom: 6, marginLeft: 10}}>
                    {hasPosition ? (
                        <CoordBadge lat={lat} lng={lng}/>
                    ) : (
                        <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                        {mode === "live" ? "Waiting for position…" : "No GPS data in this recording"}
                    </span>
                    )}
                </div>
            </div>
            {/* Speed-over-time chart */}
            <SpeedChart data={speedSeries} routeStats={routeStats}/>

            {/* Metric chips grid — live mode shows the latest live reading;
                recording mode shows the most recent reading in the selected session */}


            {/* Device / session info footer */}
            {mode === "live" && activeDevice && (
                <>

                    <div
                        style={{
                            display: "flex",
                            flexWrap: "wrap",
                            gap: 8,
                            // marginTop: 12,
                        }}
                    >
                        {METRIC_DEFS.map((def) => {
                            const value = mode === "live"
                                ? liveData[def.key]
                                : (def.key === "SPEED" ? lastPoint?.speed
                                    : def.key === "SATS" ? lastPoint?.satellites
                                        : def.key === "FIX" ? lastPoint?.fix
                                            : undefined);
                            return <MetricChip key={def.key} def={def} value={value}/>;
                        })}
                    </div>
                    <div
                        style={{
                            // marginTop: 12,
                            // paddingTop: 12,
                            borderTop: "0.5px solid var(--color-border-tertiary)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between",
                            flexWrap: "wrap",
                            gap: 6,
                        }}
                    >
                    <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                        {activeDevice.name ?? activeDevice.label ?? activeDeviceId}
                    </span>
                        <span
                            style={{
                                fontSize: 11,
                                color: "var(--color-text-tertiary)",
                                fontFamily: "var(--font-mono)",
                            }}
                        >
                        {activeDeviceId}
                    </span>
                    </div>
                </>
            )}

            {mode === "recording" && selectedSession && (
                <div
                    style={{
                        // marginTop: 12,
                        // paddingTop: 12,
                        borderTop: "0.5px solid var(--color-border-tertiary)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        flexWrap: "wrap",
                        gap: 6,
                    }}
                >
                    <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                        {selectedSession.name}
                    </span>
                    <span
                        style={{
                            fontSize: 11,
                            color: "var(--color-text-tertiary)",
                            fontFamily: "var(--font-mono)",
                        }}
                    >
                        {selectedSession.startTime ? new Date(selectedSession.startTime).toLocaleString() : ""}
                    </span>
                </div>
            )}
        </Card>
    );
});