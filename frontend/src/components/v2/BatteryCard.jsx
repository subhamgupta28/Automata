import React, {useEffect, useRef, useState} from "react";
import {Card} from "@mui/material";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import SettingsIcon from "@mui/icons-material/Settings";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {getEnergyStats} from "../../services/apis.jsx";
import {useAnimatedNumber} from "../../utils/Helper.jsx";
import {useCardGlowEffect} from "../../utils/useCardGlowEffect.jsx";
import {CustomModal} from "../home/CustomModal.jsx";
import "../../App.css";

// ─── Status helper ─────────────────────────────────────────────────────────────

function getStatusMeta(status, percent) {
    if (status === "FULL") return {label: "Full", color: "#22D3A0"};
    if (status === "CHARGING") return {label: "Charging", color: "#3B82F6"};
    if (percent < 20) return {label: "Critical", color: "#EF4444"};
    if (percent < 40) return {label: "Low", color: "#F59E0B"};
    return {label: "Discharging", color: "#94A3B8"};
}

// ─── Sparkline ────────────────────────────────────────────────────────────────

function Sparkline({history, color}) {
    if (!history?.length) return null;
    const max = Math.max(...history);
    const min = Math.min(...history);
    const range = max - min || 1;
    const W = 100, H = 20;
    const pts = history.map((v, i) => {
        const x = (i / (history.length - 1)) * W;
        const y = H - ((v - min) / range) * (H - 4) - 2;
        return `${x},${y}`;
    });
    const gradId = `bsg-${color.replace("#", "")}`;
    return (
        <svg viewBox={`0 0 ${W} ${H}`} style={{width: "100%", height: 20}} preserveAspectRatio="none">
            <defs>
                <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={color} stopOpacity="0.3"/>
                    <stop offset="100%" stopColor={color} stopOpacity="0.02"/>
                </linearGradient>
            </defs>
            <path d={`M${pts.join("L")}L${W},${H}L0,${H}Z`} fill={`url(#${gradId})`}/>
            <path d={`M${pts.join("L")}`} fill="none" stroke={color} strokeWidth="1.5"
                  strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
    );
}

// ─── Mini battery icon ─────────────────────────────────────────────────────────
const TOTAL_SEGMENTS = 10;
const LOW_BATTERY_THRESHOLD = 20;

function BatteryBar({batteryPercent, lowBatteryThreshold = LOW_BATTERY_THRESHOLD}) {
    const percent = Math.max(0, Math.min(100, batteryPercent));
    const isLowBattery = percent <= lowBatteryThreshold;
    return (
        <Box
            sx={{
                width: 110,
                height: 25,
                border: "1px solid #2A2A2A",
                padding: "4px",
                borderRadius: "4px",
                display: "flex",
                gap: "6px",
            }}
        >
            {Array.from({length: TOTAL_SEGMENTS}).map((_, index) => {
                const segmentStart = index * (100 / TOTAL_SEGMENTS);

                const segmentProgress = Math.max(
                    0,
                    Math.min(
                        100,
                        ((percent - segmentStart) / (100 / TOTAL_SEGMENTS)) * 100
                    )
                );

                return (
                    <Box
                        key={index}
                        sx={{
                            flex: 1,
                            height: "100%",
                            borderRadius: "4px",
                            background: "#1A1A20",
                            overflow: "hidden",
                            position: "relative",
                        }}
                    >
                        <Box
                            sx={{
                                position: "absolute",
                                inset: 0,
                                width: `${segmentProgress}%`,
                                borderRadius: "2px",
                                transition: "all .5s ease",
                                background: isLowBattery
                                    ? "linear-gradient(180deg, #dd0000 0%, #400300 100%)"
                                    : "linear-gradient(180deg, #FFFFFF 0%, #383838 100%)",

                                // optional glow
                                boxShadow: isLowBattery
                                    ? "0 0 12px rgba(255,59,48,.5)"
                                    : "0 0 12px rgba(221,187,255,.4)",
                            }}
                        />
                    </Box>
                );
            })}
        </Box>
    );
}

function BatteryIcon({percent, color, status}) {
    const fillW = (percent / 100) * 34;
    return (
        <svg width="48" height="20" viewBox="0 0 48 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            {/* Terminal nub on the right */}
            <rect x="45" y="7" width="3" height="6" rx="1.5" fill={color} opacity="0.6"/>
            {/* Body outline */}
            <rect x="1" y="1" width="43" height="18" rx="3" stroke={color} strokeWidth="1.2" fill="none" opacity="0.3"/>
            {/* Fill bar — grows left to right */}
            <rect x="2.5" y="2.5" width={Math.max(fillW, 0)} height="15" rx="2" fill={color} opacity="0.85"/>
            {/* Charging bolt */}
            {status === "CHARGING" && (
                <text x="21" y="14" textAnchor="middle" fontSize="9" fill="white" fontWeight="bold"
                      opacity="0.9">⚡</text>
            )}
        </svg>
    );
}

// ─── Trend ────────────────────────────────────────────────────────────────────

function Trend({prevValue, value, unit}) {
    const hasTrend = prevValue !== null && prevValue !== undefined && prevValue !== 0;
    if (!hasTrend) return null;
    const positive = (value - prevValue) > prevValue; // 90, 100, is less than 90
    const absDiff = Math.abs(prevValue);
    const pct = value !== 0 ? ((absDiff / Math.abs(value)) * 100).toFixed(1) : "0.0";
    return (
        <Box display="flex" alignItems="center" gap={0.3}>
            {positive
                ? <ArrowUpwardIcon sx={{fontSize: 12, color: "success.main"}}/>
                : <ArrowDownwardIcon sx={{fontSize: 12, color: "error.main"}}/>
            }
            <Typography sx={{fontSize: 10, color: positive ? "success.main" : "error.main"}}>
                {absDiff.toFixed(1)}{unit} ({pct}%)
            </Typography>
        </Box>
    );
}

// ─── MiniStat ─────────────────────────────────────────────────────────────────

function MiniStat({label, value = 0, trend, unit}) {
    const animated = useAnimatedNumber(value);
    return (
        <>
            <Typography sx={{fontSize: 11, color: "text.secondary"}}>{label}</Typography>
            <Box style={{display: "flex", flexDirection: "row", alignItems: "center"}}>
                <Typography sx={{fontSize: 11, fontWeight: 600, lineHeight: 1}}>
                    {animated.toFixed(1)}
                    <Typography component="span"
                                sx={{fontSize: 9, color: "text.secondary", marginRight: "4px"}}> {unit}</Typography>
                </Typography>
                <Trend prevValue={trend} value={value} unit={unit}/>

            </Box>
        </>

    );
}

// ─── TelemetryChip ────────────────────────────────────────────────────────────

function TelemetryChip({label, value}) {
    return (
        <Box sx={{textAlign: "center"}}>
            <Typography sx={{fontSize: 10, fontWeight: 600, lineHeight: 1}}>{value}</Typography>
            <Typography sx={{fontSize: 8, color: "text.disabled"}}>{label}</Typography>
        </Box>
    );
}

// ─── SingleBatteryCard ────────────────────────────────────────────────────────
// One card per deviceId. Owns its own stats + live state,
// wired identically to ConsumptionCard in the carousel.

const SingleBatteryCard = React.memo(({deviceId, vid, name, messages}) => {
    const [stats, setStats] = useState({
        percent: 0, status: "DISCHARGING",
        totalWh: 0, totalWhTrend: 0,
        chargeTotalWh: 0,
        peakWh: 0, peakWhTrend: 0,
        chargePeakWh: 0,
        lowestWh: 0, lowestWhTrend: 0,
        percentTrend: 0,
        history: [],
    });
    const [live, setLive] = useState({power: 0, voltage: null, temperature: null});

    // REST poll — same interval as ConsumptionCard
    useEffect(() => {
        const load = async () => {
            const res = await getEnergyStats(deviceId);
            if (res) setStats(res);
        };
        load();
        const id = setInterval(load, 30_000);
        return () => clearInterval(id);
    }, [deviceId]);

    // WebSocket — same two-channel pattern as ConsumptionCard
    useEffect(() => {
        if (!messages?.data) return;
        // Batch push from parent node (vid = parent node id)
        if (messages.deviceId === vid) {
            const match = messages.data.find(d => d.deviceId === deviceId);
            if (match) setStats(match);
        }
        // Per-device live telemetry
        if (messages.deviceId === deviceId) {
            setLive(messages.data);
        }
    }, [messages]);

    const meta = getStatusMeta(stats.status, stats.percent);
    const animPercent = useAnimatedNumber(stats.percent);
    const isCharging = stats.status === "CHARGING";

    const primaryVal = isCharging ? (stats.chargeTotalWh ?? stats.totalWh) : stats.totalWh;
    const primaryLabel = isCharging ? "Charged today:" : "Used today:";
    const peakVal = isCharging ? (stats.chargePeakWh ?? stats.peakWh) : stats.peakWh;

    return (
        <Box sx={{
            flex: 1,
            minWidth: 0,
            height: "100%",
            display: "flex",
            flexDirection: "column",
            gap: "5px",
            p: "14px",
            // Divider between cards — left border on 2nd and 3rd
            overflow: "hidden",
        }}>

            {/* Row 1 — device name + status badge */}
            <Box sx={{display: "flex", alignItems: "center", justifyContent: "space-between", flexShrink: 0}}>
                <Typography sx={{fontSize: 11, fontWeight: 700, lineHeight: 1, color: "primary.main"}}>
                    {name}
                </Typography>
                <Box sx={{
                    display: "flex", alignItems: "center", gap: "3px",
                    px: "6px", py: "1px",
                }}>
                    <Typography sx={{fontSize: 9, fontWeight: 600, color: meta.color, letterSpacing: 0.3}}>
                        {meta.label}
                    </Typography>
                </Box>
            </Box>

            {/* Row 2 — live power subtitle (mirrors ConsumptionCard's status line) */}
            <Box style={{display: "flex", flexDirection: "row"}}>
                <Typography
                    variant="caption"
                    sx={{
                        fontSize: 10,
                        flexShrink: 0,
                    }}
                >
                    Power:
                </Typography>
                <Typography variant="caption" sx={{
                    fontSize: 10,
                    flexShrink: 0,
                    marginLeft: "6px",
                    color: stats.status === "CHARGING" ? "success.main" : "error.main"
                }}>
                    {live.power}
                </Typography>
            </Box>


            {/* Row 3 — battery icon + percent + bar + mini-stats */}
            <Box sx={{
                display: "flex",
                gap: "8px",
                alignItems: "center",
                flexShrink: 0,
                // paddingLeft: "6px",
                // paddingRight: "6px"
            }}>
                <BatteryBar batteryPercent={Math.round(animPercent)} color={meta.color} status={stats.status}/>

                <Box sx={{flex: 1, display: "flex", flexDirection: "column", gap: "4px"}}>
                    <Box sx={{display: "flex", alignItems: "baseline", gap: "2px"}}>
                        <Typography sx={{fontSize: 20, fontWeight: 700, color: meta.color, lineHeight: 1}}>
                            {Math.round(animPercent)}
                        </Typography>
                        <Typography sx={{fontSize: 10, color: "text.secondary"}}>%</Typography>
                        {/*<Trend prevValue={stats.percentTrend} value={stats.percent} unit="%"/>*/}
                    </Box>
                </Box>

            </Box>
            <MiniStat label={primaryLabel} value={primaryVal} trend={stats.totalWhTrend} unit="Wh"/>
            {/* Row 4 — sparkline (flex grows to fill) */}
            {stats.history?.length > 1 && (
                <Box sx={{flex: "1 1 auto", minHeight: 0}}>
                    <Sparkline history={stats.history} color={meta.color}/>
                    <Typography sx={{fontSize: 8, color: "text.disabled", textAlign: "right"}}>
                        last 8h
                    </Typography>
                </Box>
            )}
        </Box>
    );
});

// ─── BatteryCardNode (React Flow node) ────────────────────────────────────────
//
//  data.value shape — identical to EnergyConsumptionCarouselNode:
//    {
//      deviceIds : string[]  — array of device ids (3 expected)
//      name      : string    — node title
//      width     : number    — initial node width  (default 760)
//      height    : number    — initial node height (default 200)
//    }

export const BatteryCardNode = React.memo(({id, data, isConnectable, selected}) => {
    const {devices} = useCachedDevices();
    const {messages} = useDeviceLiveData();

    const {deviceIds, name, width = 760, height = 200} = data.value;

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [deviceList, setDeviceList] = useState([]);
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef, true);

    // Resolve full device objects for the settings modal — same as carousel node
    useEffect(() => {
        if (devices) {
            const idSet = new Set(deviceIds);
            setDeviceList(devices.filter(d => idSet.has(d.id)));
        }
    }, [devices, deviceIds]);

    return (
        <>
            {/*<NodeResizer*/}
            {/*    isVisible={selected}*/}
            {/*    minWidth={420}*/}
            {/*    minHeight={160}*/}
            {/*/>*/}

            <Card
                ref={cardRef}
                className="card-glow-container"
                variant="elevated"
                style={{
                    background: "transparent",
                    backgroundColor: "rgb(0 0 0 / 0%)",
                    minHeight: height,
                    height: "100%",
                    minWidth: width,
                    borderRadius: "12px",
                    boxShadow: "rgb(30 30 30) 0px 0px 36px 6px inset",
                    backdropFilter: "blur(4px)",
                    position: "relative",
                    overflow: "hidden",
                    display: "flex",
                    flexDirection: "column",
                    paddingBottom: "12px"
                }}
            >
                <div className="card-glow"/>

                {/* Node header — name + settings gear, same layout as carousel node */}
                <Box sx={{
                    display: "flex", alignItems: "center", justifyContent: "space-between",
                    px: "10px", pt: "8px", pb: "4px",
                    flexShrink: 0,
                }}>
                    <Typography sx={{fontWeight: "bold", fontSize: "12px"}}>
                        {name}
                    </Typography>
                    <IconButton onClick={() => setIsModalOpen(true)} size="small">
                        <SettingsIcon style={{fontSize: 16}}/>
                    </IconButton>
                </Box>

                {/* Three battery cards side-by-side */}
                <Box sx={{display: "flex", flex: 1, minHeight: 0, overflow: "hidden"}}>
                    {deviceIds.map((deviceId) => {
                        const deviceName = devices?.find(d => d.id === deviceId)?.name ?? deviceId;
                        return (
                            <SingleBatteryCard
                                key={deviceId}
                                deviceId={deviceId}
                                vid={id}
                                name={deviceName}
                                messages={messages}
                            />
                        );
                    })}
                </Box>
            </Card>

            {isModalOpen && (
                <CustomModal
                    map={null}
                    isOpen={isModalOpen}
                    messages={messages}
                    onClose={() => setIsModalOpen(false)}
                    devices={deviceList}
                    version="v2"
                />
            )}
        </>
    );
});