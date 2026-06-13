import React, {useEffect, useState} from "react";
import {MapView} from "../charts/MapView.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card} from "@mui/material";

const METRIC_DEFS = [
    {
        key: "SAT",
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
        key: "SPD",
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
        key: "COG",
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

function MetricChip({def, value}) {
    const status = def.colorFn(value);
    return (
        <div
            style={{
                display: "flex",
                flexDirection: "column",
                gap: 2,
                background: "var(--color-background-secondary)",
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

export const MapDevices = React.memo(() => {
    const {messages} = useDeviceLiveData();
    const {devices, loading, error} = useCachedDevices();

    const [liveDataMap, setLiveDataMap] = useState({});
    const [activeDeviceId, setActiveDeviceId] = useState(null);

    const gpsDevices = devices?.filter((d) => d.category === "SENSOR|GPS") ?? [];

    useEffect(() => {
        if (gpsDevices.length > 0 && activeDeviceId == null) {
            setActiveDeviceId(gpsDevices[0]._id ?? gpsDevices[0].id);
        }
    }, [gpsDevices.length]);

    useEffect(() => {
        if (!messages?.deviceId || !messages?.data) return;
        setLiveDataMap((prev) => ({
            ...prev,
            [messages.deviceId]: messages.data,
        }));
    }, [messages]);

    const activeDevice = gpsDevices.find(
        (d) => (d._id ?? d.id) === activeDeviceId
    );
    const liveData = liveDataMap[activeDeviceId] ?? {};

    const lat = liveData.LAT ?? liveData.lat;
    const lng = liveData.LONG ?? liveData.lng ?? liveData.LON;
    const hasPosition = lat != null && lng != null;

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
        <Card style={{
            display: "flex",
            flexDirection: "column",
            gap: 0,
            margin: "20px",
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
                }}
            >
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

                {hasPosition && (
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
            </div>

            {/* Device tabs */}
            {gpsDevices.length > 1 && (
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
                <MapView lat={lat} lng={lng} h="70dvh" w="100%"/>
            </div>

            {/* Coordinate badge */}
            <div style={{marginBottom: 12}}>
                {hasPosition ? (
                    <CoordBadge lat={lat} lng={lng}/>
                ) : (
                    <span style={{fontSize: 12, color: "var(--color-text-secondary)"}}>
                        Waiting for position…
                    </span>
                )}
            </div>

            {/* Metric chips grid */}
            <div
                style={{
                    display: "flex",
                    flexWrap: "wrap",
                    gap: 8,
                }}
            >
                {METRIC_DEFS.map((def) => (
                    <MetricChip
                        key={def.key}
                        def={def}
                        value={liveData[def.key]}
                    />
                ))}
            </div>

            {/* Device info footer */}
            {activeDevice && (
                <div
                    style={{
                        marginTop: 12,
                        paddingTop: 12,
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
            )}
        </Card>
    );
});