import React, {useEffect, useMemo, useRef, useState} from "react";
import {Box, Card, Chip, Typography} from "@mui/material";
import NotificationsIcon from "@mui/icons-material/Notifications";
import GridViewIcon from "@mui/icons-material/GridView";
import AlarmIcon from "@mui/icons-material/Alarm";
import HomeIcon from "@mui/icons-material/Home";
import WindowIcon from "@mui/icons-material/Window";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import LockIcon from "@mui/icons-material/Lock";
import PeopleIcon from "@mui/icons-material/People";
import OpacityIcon from "@mui/icons-material/Opacity";
import {
    CheckCircleOutline,
    DeviceThermostat,
    ErrorOutline,
    Lightbulb,
    SignalWifiStatusbarConnectedNoInternet4,
    WarningAmberOutlined
} from "@mui/icons-material";
import AirIcon from '@mui/icons-material/Air';
import Co2Icon from '@mui/icons-material/Co2';
import ScienceIcon from '@mui/icons-material/Science';
import dayjs from "dayjs";
import '/src/App.css'
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {
    fetchOutdoorWeather,
    fetchWeatherForecast,
    getAutomationAnalyticsSummary,
    getRecentDeviceData
} from "../../services/apis.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {CustomModal} from "../home/CustomModal.jsx";
import {useCardGlowEffect} from "../../utils/useCardGlowEffect.jsx";
import GasMeterIcon from '@mui/icons-material/GasMeter';
import ShinyText from "../charts/ShinyText.jsx";
import Tooltip from "@mui/material/Tooltip";
// ─── Design tokens (unchanged) ────────────────────────────────────────────────
export const C = {
    bg: "#111111",
    card: "#1c1c1e",
    cardAlt: "#242426",
    border: "#2c2c2e",
    text: "#ffffff",
    muted: "#8a8a8e",
    dim: "#5a5a5e",
    yellow: "#f5c842",
    green: "#4caf50",
    red: "#e53935",
};

const chipSx = {backgroundColor: C.card, color: C.text};

const GREETINGS = {
    lateNight: [
        name => `Still up, ${name}?`,
        name => `Burning the midnight oil, ${name}?`,
        name => `The night owl stirs… hey ${name}.`,
        name => `Can't sleep, ${name}?`,
    ],
    earlyMorn: [
        name => `Rise and shine, ${name}!`,
        name => `You're up early, ${name}.`,
        name => `The early bird, ${name}!`,
        name => `Dawn's barely here — morning, ${name}.`,
    ],
    morning: [
        name => `Good morning, ${name}!`,
        name => `Morning, ${name}! ☀️`,
        name => `Hope your coffee's ready, ${name}.`,
        name => `A fine morning to you, ${name}.`,
    ],
    midday: [
        name => `Good afternoon, ${name}!`,
        name => `Lunchtime thoughts, ${name}?`,
        name => `High noon, ${name}!`,
    ],
    afternoon: [
        name => `Good afternoon, ${name}!`,
        name => `Afternoon, ${name} — how's the day going?`,
        name => `Getting through the afternoon, ${name}?`,
    ],
    evening: [
        name => `Good evening, ${name}!`,
        name => `Winding down, ${name}?`,
        name => `Evening, ${name}. Long day?`,
        name => `The day's end approaches, ${name}.`,
    ],
    night: [
        name => `Good night, ${name}!`,
        name => `Getting late, ${name}.`,
        name => `Wrapping up for the night, ${name}?`,
    ],
};

function buildGreeting(hour, name) {
    const slot =
        hour < 4 ? 'lateNight' :
            hour < 6 ? 'earlyMorn' :
                hour < 12 ? 'morning' :
                    hour < 14 ? 'midday' :
                        hour < 18 ? 'afternoon' :
                            hour < 21 ? 'evening' : 'night';

    const pool = GREETINGS[slot];
    return pool[Math.floor(Math.random() * pool.length)](name);
}

// ─── Summary sentence ─────────────────────────────────────────────────────────
function buildMessage({time, weather, homeStats, live = {}, outdoor = null}) {
    const parts = [];

    // ── Outdoor weather ────────────────────────────────────────────────────
    if (weather?.conditionLabel && weather?.temperature != null) {
        parts.push(`it's ${weather.conditionLabel.toLowerCase()} outside at ${weather.temperature.toFixed(1)}°C`);
    } else if (weather?.label && weather?.temp) {
        parts.push(`the weather is ${weather.label.toLowerCase()} with ${weather.temp}`);
    }

    // ── Indoor air quality ─────────────────────────────────────────────────
    if (live.temp != null && live.humid != null) {
        parts.push(`${live.temp}°C inside at ${live.humid}% humidity`);
    } else if (live.temp != null) {
        parts.push(`${live.temp}°C inside`);
    }

    if (live.co2 != null) {
        const co2Label =
            live.co2 < 800 ? "fresh" :
                live.co2 < 1200 ? "moderate" :
                    live.co2 < 2000 ? "stuffy" : "poor";
        parts.push(`air quality ${co2Label} (CO₂ ${live.co2} ppm)`);
    }

    if (live.aqi != null) {
        const aqiLabel =
            live.aqi <= 50 ? "good" :
                live.aqi <= 100 ? "moderate" :
                    live.aqi <= 150 ? "unhealthy for sensitive groups" : "unhealthy";
        parts.push(`AQI ${live.aqi} — ${aqiLabel}`);
    }

    // ── Home state ─────────────────────────────────────────────────────────
    // if (homeStats?.lightsOn != null) {
    //     parts.push(`${homeStats.lightsOn === 1 ? "1 light" : `${homeStats.lightsOn} lights`} on`);
    // }
    // if (homeStats?.windowsOpen != null && homeStats.windowsOpen > 0) {
    //     parts.push(`${homeStats.windowsOpen} window${homeStats.windowsOpen !== 1 ? "s" : ""} open`);
    // }
    // if (homeStats?.security) {
    //     parts.push(`security ${homeStats.security.toLowerCase()}`);
    // }
    // if (homeStats?.doorLocked != null) {
    //     parts.push(`door ${homeStats.doorLocked ? "locked" : "unlocked"}`);
    // }
    // if (homeStats?.occupancy != null && homeStats.occupancy > 0) {
    //     parts.push(`${homeStats.occupancy} ${homeStats.occupancy === 1 ? "person" : "people"} home`);
    // }

    const timeStr = time ?? new Date().toTimeString().slice(0, 5);
    return parts.length > 0
        ? `It's ${timeStr} — ${parts.join(", ")}.`
        : `It's ${timeStr}.`;
}

// ─── Automation Pill ──────────────────────────────────────────────────────────
function AutomationPill({icon, value, label, color, tooltip}) {
    return (
        <Tooltip title={tooltip ?? ""} placement="bottom" arrow>
            <Box
                sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 0.5,
                    px: 1,
                    py: 0.4,
                    borderRadius: "999px",
                    backgroundColor: C.card,
                    border: `1px solid ${C.border}`,
                    cursor: "default",
                    fontSize: 14,
                    lineHeight: 1,
                    color,
                    whiteSpace: "nowrap",
                    userSelect: "none",
                }}
            >
                <Box sx={{fontSize: 15, display: "flex", alignItems: "center"}}>{icon}</Box>
                <Typography sx={{fontSize: 14, fontWeight: 700, color}}>
                    {value}
                </Typography>
                <Typography sx={{fontSize: 14, color: C.muted}}>
                    {label}
                </Typography>
            </Box>
        </Tooltip>
    );
}

// ─── Automation Summary Chips (inline, no fetch — data is passed in) ──────────
function AutomationSummaryChips({summary}) {
    if (!summary) return null;
    const {total, healthy, warnings, errors, totalUndelivered, totalSlowEvals} = summary;

    return (
        <>
            <AutomationPill
                icon={<CheckCircleOutline sx={{fontSize: 13}}/>}
                value={`${healthy}/${total}`}
                label="healthy"
                color="#22c55e"
                tooltip="Automations with no errors or delivery failures"
            />
            {warnings > 0 && (
                <AutomationPill
                    icon={<WarningAmberOutlined sx={{fontSize: 13}}/>}
                    value={warnings}
                    label="warn"
                    color="#f59e0b"
                    tooltip="Automations with slow evaluations or minor issues"
                />
            )}
            {errors > 0 && (
                <AutomationPill
                    icon={<ErrorOutline sx={{fontSize: 13}}/>}
                    value={errors}
                    label="err"
                    color="#ef4444"
                    tooltip="Automations with dispatch errors or evaluation exceptions"
                />
            )}
            {totalUndelivered > 0 && (
                <AutomationPill
                    icon={<SignalWifiStatusbarConnectedNoInternet4 sx={{fontSize: 13}}/>}
                    value={totalUndelivered}
                    label="undelivered"
                    color="#f59e0b"
                    tooltip="Total actions with no device ACK"
                />
            )}
            {totalSlowEvals > 0 && (
                <AutomationPill
                    icon={<WarningAmberOutlined sx={{fontSize: 13}}/>}
                    value={totalSlowEvals}
                    label="slow"
                    color="#60a5fa"
                    tooltip="Evaluations exceeding the 200ms threshold"
                />
            )}
        </>
    );
}

// ─── Chips ────────────────────────────────────────────────────────────────────
function buildChips({notifications, scenes, alarm, location, windowsOpen, doorLocked, occupancy}) {
    return [
        notifications != null && {
            icon: <NotificationsIcon sx={{fontSize: 16, color: notifications > 0 ? C.yellow : C.muted}}/>,
            label: String(notifications),
        },
        scenes != null && {
            icon: <GridViewIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: String(scenes),
        },
        alarm && {
            icon: <AlarmIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: alarm,
        },
        location && {
            icon: <HomeIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: location,
        },
        windowsOpen != null && {
            icon: <WindowIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: String(windowsOpen),
        },
        doorLocked != null && {
            icon: doorLocked
                ? <LockIcon sx={{fontSize: 16, color: C.muted}}/>
                : <LockOpenIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: "",
        },
        occupancy != null && {
            icon: <PeopleIcon sx={{fontSize: 16, color: C.muted}}/>,
            label: occupancy > 0 ? String(occupancy) : "",
        },
    ].filter(Boolean);
}

// ─── Device helpers ───────────────────────────────────────────────────────────
function getDevicesWithCO2(attributes) {
    if (!attributes || typeof attributes !== "object") return [];
    return Object.entries(attributes)
        .filter(([_, attrs]) => Array.isArray(attrs) && attrs.some(a => a?.key === "co2"))
        .map(([deviceId]) => deviceId);
}

// ─── Weather icon mapper ───────────────────────────────────────────────────────
function getWeatherIconFile(conditionLabel = "", isNight = false) {
    const c = conditionLabel.toLowerCase();

    if (c.includes("thunder") || c.includes("storm")) return "thunder.svg";
    if (c.includes("snow") || c.includes("blizzard")) return "snowy-4.svg";
    if (c.includes("sleet") || c.includes("freezing rain")) return "snowy-6.svg";
    if (c.includes("heavy rain") || c.includes("shower")) return isNight ? "rainy-6.svg" : "rainy-5.svg";
    if (c.includes("rain") || c.includes("drizzle")) return isNight ? "rainy-4.svg" : "rainy-3.svg";
    if (c.includes("overcast") || c.includes("fog") || c.includes("mist")) return "cloudy.svg";
    if (c.includes("mostly cloudy") || c.includes("broken"))
        return isNight ? "cloudy-night-3.svg" : "cloudy-day-3.svg";
    if (c.includes("partly cloudy") || c.includes("few clouds"))
        return isNight ? "cloudy-night-1.svg" : "cloudy-day-1.svg";
    if (c.includes("cloudy"))
        return isNight ? "cloudy-night-2.svg" : "cloudy-day-2.svg";
    if (c.includes("clear") || c.includes("sunny"))
        return isNight ? "night.svg" : "day.svg";

    // fallback
    return isNight ? "night.svg" : "day.svg";
}

// ─── Inline weather widget ────────────────────────────────────────────────────
function WeatherInline({weather, live, hour, iconBasePath = "/icons/weather/"}) {
    const isNight = hour < 6 || hour >= 21;

    const conditionLabel =
        weather?.conditionLabel ??
        weather?.label ??
        (live?.temp != null
            ? live.temp >= 30 ? "Sunny" : live.temp >= 20 ? "Partly cloudy" : "Cloudy"
            : null);

    const temperature =
        weather?.temperature ??
        (weather?.temp ? parseFloat(weather.temp) : null) ??
        live?.outTemp ??
        live?.temp;

    if (!conditionLabel && temperature == null) return null;

    const iconFile = getWeatherIconFile(conditionLabel ?? "", isNight);
    const iconSrc = `${iconBasePath}${iconFile}`;

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                gap: 0.75,
                mt: 0.5,
            }}
        >
            <Box
                component="img"
                src={iconSrc}
                alt={conditionLabel ?? "weather"}
                sx={{width: 100, flexShrink: 0, scale: 1.5}}
            />
            <Box>
                {temperature != null && (
                    <Typography sx={{fontSize: 36, fontWeight: 700, lineHeight: 1, color: C.text}}>
                        {typeof temperature === "number" ? temperature.toFixed(1) : temperature}°C
                    </Typography>
                )}
                {conditionLabel && (
                    <Typography sx={{fontSize: 18, color: C.muted, lineHeight: 1.2}}>
                        {conditionLabel}
                    </Typography>
                )}
            </Box>

        </Box>
    );
}

const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function getForecastIcon(conditionLabel = "") {
    const c = conditionLabel.toLowerCase();
    if (c.includes("thunder") || c.includes("storm")) return "⛈";
    if (c.includes("snow")) return "❄️";
    if (c.includes("heavy rain") || c.includes("shower")) return "🌧";
    if (c.includes("rain") || c.includes("drizzle")) return "🌦";
    if (c.includes("overcast") || c.includes("fog")) return "☁️";
    if (c.includes("partly cloudy") || c.includes("cloudy")) return "🌤";
    if (c.includes("clear") || c.includes("sunny")) return "☀️";
    return "🌡";
}

function ForecastStrip({days = []}) {
    if (!days.length) return null;
    return (
        <Box sx={{display: "flex", gap: 0.8}}>
            {days.map((d, i) => {
                const dayName = DAY_NAMES[new Date(d.date).getDay()];
                return (
                    <Box
                        key={i}
                        sx={{
                            display: "flex",
                            flexDirection: "row",
                            alignItems: "center",
                            gap: 1.5,
                            px: 1.8,
                            py: 0.8,
                            borderRadius: "10px",
                            backgroundColor: C.card,
                            border: `1px solid ${C.border}`,
                            minWidth: 52,
                        }}
                    >
                        <Typography sx={{fontSize: 28, lineHeight: 1}}>
                            {getForecastIcon(d.conditionLabel)}
                        </Typography>
                        <Box>
                            <Typography sx={{
                                fontSize: 14,
                                color: C.muted,
                                fontWeight: 600,
                                letterSpacing: "0.05em",
                                textTransform: "uppercase"
                            }}>
                                {dayName}
                            </Typography>
                            <Box style={{display: 'flex', flexDirection: 'row', gap: "10px"}}>
                                <Typography sx={{fontSize: 18, fontWeight: 200, color: C.text}}>
                                    {Math.round(d.tempMax)}°
                                </Typography>
                                <Typography sx={{fontSize: 14, color: C.dim}}>
                                    {Math.round(d.tempMin)}°
                                </Typography>
                            </Box>
                        </Box>

                    </Box>
                );
            })}
        </Box>
    );
}

// ─── TopBar — UI unchanged ────────────────────────────────────────────────────
export function TopBar({
                           alertMessage,
                           userName = "Subham",
                           avatarLetter,
                           avatarColor = C.yellow,
                           userRoom = "Living room",
                           time,
                           weather,
                           homeStats,
                           alarm,
                           chips: chipsProp,
                           notifications,
                           scenes,
                           occupancy,
                           location = "Home",
                           live, outdoor,
                           automationSummary,
                           weatherIconBasePath = "/icons/weather/",   // ← new prop; adjust to your asset path
                           forecast = [],
                       }) {
    const hour = time ? parseInt(time.split(":")[0], 10) : new Date().getHours();
    const greeting = useMemo(() => buildGreeting(hour, userName), [hour, userName]);
    const message = buildMessage({time, weather, homeStats, alarm, live, outdoor});
    const chips = chipsProp ?? buildChips({
        notifications, scenes, alarm, location,
        windowsOpen: homeStats?.windowsOpen,
        doorLocked: homeStats?.doorLocked,
        occupancy,
    });
    const alert = weather?.todayAlert ?? null;
    const [alertMsg, setAlertMsg] = useState("");
    useEffect(() => {
        if (alertMessage.severity) {
            setAlertMsg(alertMessage.message);
        }
    }, [alertMessage])
    const ALERT_COLORS = {
        thunderstorm: "#f59e0b",
        "rain showers": "#60a5fa",
        snow: "#a5b4fc",
        rain: "#60a5fa",
        drizzle: "#93c5fd",
        "possible rain": "#8a8a8e",
    };
    const alertColor = alert
        ? Object.entries(ALERT_COLORS).find(([k]) => alert.toLowerCase().includes(k))?.[1] ?? "#8a8a8e"
        : null;
    return (
        <Box sx={{display: "flex", justifyContent: "space-between", alignItems: "flex-start", px: 2.5, py: 1}}>

            {/* ── Left column ─────────────────────────────────────────── */}
            <Box sx={{display: "flex", flexDirection: "column", flex: 1}}>

                {/* Row 1 — Greeting */}
                <Typography variant="h2">
                    <ShinyText
                        text={greeting}
                        speed={10}
                        delay={1}
                        color="#ffffff"
                        shineColor="#EAB308"
                        spread={120}
                        direction="left"
                        yoyo={false}
                        pauseOnHover={false}
                        disabled={false}
                    />
                </Typography>

                {/* Row 2 — Weather icon + message side by side */}
                <Box sx={{display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap"}}>
                    <WeatherInline
                        weather={weather}
                        live={live}
                        hour={hour}
                        iconBasePath={weatherIconBasePath}
                    />
                    {forecast?.length > 0 && (
                        <>
                            <Box sx={{width: "1px", height: 46, backgroundColor: C.muted, flexShrink: 0}}/>
                            <ForecastStrip days={forecast}/>
                        </>
                    )}
                    {/* Thin divider */}
                    <Box sx={{width: "1px", height: 46, backgroundColor: C.muted, flexShrink: 0}}/>

                    <Typography
                        variant="title"
                        sx={{color: C.muted, fontSize: 12, lineHeight: 1.4, maxWidth: 420}}
                    >
                        {message}
                    </Typography>
                </Box>

                {/*/!* Alarm line (unchanged) *!/*/}
                {/*{alarm && (*/}
                {/*    <Typography variant="body" sx={{color: C.muted, mt: 0.5}}>*/}
                {/*        Your alarm is set to {alarm}.*/}
                {/*    </Typography>*/}
                {/*)}*/}
            </Box>

            {/* ── Right column (avatar + automation pills — unchanged) ── */}
            <Box sx={{display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 1, width: "30%"}}>
                {(chips.length > 0 || automationSummary) && (
                    <Box sx={{display: "flex", gap: 0.8, flexWrap: "wrap", justifyContent: "flex-end"}}>
                        <AutomationSummaryChips summary={automationSummary}/>
                    </Box>
                )}
                {alert && (
                    <Chip variant="outlined" label={"⚠ " + alert} sx={{
                        fontSize: 14,
                        color: alertColor,
                        ml: 3.4,
                        fontWeight: 500,
                        letterSpacing: "0.01em",
                    }}>

                    </Chip>
                )}
                <Box sx={{display: "flex", alignItems: "center", gap: 1}}>
                    <Chip label={alertMsg}/>
                    {/*<Avatar sx={{*/}
                    {/*    width: 32,*/}
                    {/*    height: 32,*/}
                    {/*    backgroundColor: avatarColor,*/}
                    {/*    color: "#111",*/}
                    {/*    fontSize: 14,*/}
                    {/*    fontWeight: 700*/}
                    {/*}}>*/}
                    {/*    {avatarLetter ?? userName?.[0] ?? "?"}*/}
                    {/*</Avatar>*/}
                    {/*<Box sx={{textAlign: "right"}}>*/}
                    {/*    <Typography sx={{fontSize: 13, fontWeight: 600}}>{userName}</Typography>*/}
                    {/*    <Typography sx={{fontSize: 11, color: C.muted}}>{userRoom}</Typography>*/}
                    {/*</Box>*/}
                </Box>
            </Box>
        </Box>
    );
}

// ─── StatsRow — UI unchanged ──────────────────────────────────────────────────
export function StatsRow({items}) {

    return (
        <Box sx={{display: "flex", alignItems: "center", gap: 1, px: 2.5, py: 1, overflowX: "auto"}}>
            {items.map((s, i) => (
                <Box key={i} sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    padding: "10px 15px 5px 15px",
                    borderRadius: "10px",
                    // backgroundColor: C.card,
                    border: `1px solid ${C.border}`,
                }}>
                    <Box sx={{display: "flex", alignItems: "center", gap: 1, whiteSpace: "nowrap"}}>
                        <Box sx={{color: C.muted}}>{s.icon}</Box>
                        <Box>
                            <Typography sx={{color: C.muted, lineHeight: 1}}>{s.label}</Typography>
                            <Typography variant="h6" sx={{fontWeight: 600}}>{s.value}</Typography>
                        </Box>
                    </Box>
                </Box>
            ))}
        </Box>
    );
}

// ─── WeatherCardV2 ────────────────────────────────────────────────────────────
/**
 * Drop-in replacement for <WeatherCard />.
 *
 * New additions to data.value:
 *   latitude       {number}   device / home lat  (for outdoor weather API)
 *   longitude      {number}   device / home lon
 *   apiBase        {string}   optional base URL for your Spring Boot server
 *                             e.g. "https://api.yourdomain.com" (default: same origin)
 *   weatherRefreshMs {number} how often to re-fetch outdoor weather (default: 10 min)
 *   topBarProps    {object}   static props for TopBar
 *   statsItemsFn   {fn}       optional (live, outdoor) => StatsRow items[]
 */
export const WeatherCardV2 = React.memo(({id, data, isConnectable, selected}) => {
    const {messages, alertMessages} = useDeviceLiveData();
    const {devices} = useCachedDevices();

    const [dataPoint, setDatapoint] = useState({});
    const [mainDevice, setMainDevice] = useState("");
    const [otherDevice, setOtherDevice] = useState("");
    const [deviceList, setDeviceList] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [automationSummary, setAutomationSummary] = useState(null);
    // Indoor sensor live data
    const [live, setLive] = useState({
        time: dayjs().format("HH:mm"),
        temp: null, humid: null, aqi: null,
        lux: null, co2: null, tvoc: null,
        ch2o: null, pm25: null,
        co2Status: null, eCo2: null,
        // secondary device (outdoor sensor)
        outTemp: null, outHumid: null, outLux: null,
    });

    // Outdoor weather from Open-Meteo via Spring Boot
    const [outdoor, setOutdoor] = useState(null); // WeatherResponse shape
    const [forecast, setForecast] = useState([]);
    const user = useMemo(() => JSON.parse(localStorage.getItem('user')), []);
    const {
        attributes,
        deviceIds,
        height,
        width,
        apiBase = "",
        weatherRefreshMs = 12 * 60 * 1000, // 12 min
        topBarProps = {
            userName: user?.firstName,
            userRoom: "Living room",
            alarm: "08:00",
            notifications: 6,
            scenes: 4,
            occupancy: 2,
            location: "Home",
            homeStats: {
                lightsOn: 3,
                windowsOpen: 1,
                security: "Armed home",
                doorLocked: false,
            },
        },
        statsItemsFn,
    } = data.value;

    // ── Fetch outdoor weather on mount + on interval ───────────────────────
    useEffect(() => {
        fetchWeatherForecast()
            .then(setForecast)
            .catch(() => setForecast([]));
        const fetch_ = () => {
            fetchOutdoorWeather()
                .then(setOutdoor)
                .catch(err => console.warn("Outdoor weather fetch failed:", err));
        };

        fetch_();
        const id = setInterval(fetch_, weatherRefreshMs);
        return () => clearInterval(id);
    }, [apiBase, weatherRefreshMs]);

    // ── Bootstrap indoor devices ───────────────────────────────────────────
    useEffect(() => {
        if (!attributes || !deviceIds?.length) return;
        const co2Devices = getDevicesWithCO2(attributes);
        if (!co2Devices.length) return;

        const main = co2Devices[0];
        const other = deviceIds.filter(d => d !== main);
        setMainDevice(main);
        if (other.length) setOtherDevice(other[0]);

        getRecentDeviceData(deviceIds).then(res => {
            setDatapoint(res);
            applyMainData(main, res[main] ?? {});
            if (other.length) applyOutdoorSensor(other[0], res[other[0]] ?? {});
        });
    }, [attributes, deviceIds]);

    // ── Live WebSocket ─────────────────────────────────────────────────────
    useEffect(() => {
        if (!messages?.data || !messages.deviceId) return;
        const {deviceId} = messages;
        if (!deviceIds?.includes(deviceId)) return;

        const attrs = attributes?.[deviceId];
        if (!Array.isArray(attrs)) return;

        const allData = attrs.reduce((acc, m) => {
            if (messages.data[m.key] !== undefined) acc[m.key] = messages.data[m.key];
            return acc;
        }, {});

        setDatapoint(prev => ({...prev, [deviceId]: {...(prev[deviceId] ?? {}), ...allData}}));

        if (deviceId === mainDevice) applyMainData(deviceId, allData);
        else applyOutdoorSensor(deviceId, allData);
    }, [messages, attributes, deviceIds, mainDevice]);

    function applyMainData(_, d) {
        setLive({
            time: dayjs().format("HH:mm"),
            temp: d.temp ?? 0,
            humid: d.humid ?? 0,
            aqi: d.aqi ?? 0,
            lux: d.lux ?? 0,
            co2: d.s_co2 ?? 0,
            tvoc: d.tvoc ?? 0,
            ch2o: d.ch2o ?? 0,
            pm25: d.pm25 ?? 0,
            eCo2: d.co2 ?? 0,
            ch2oStatus: d.ch2oStatus ?? "",
        });
    }

    function applyOutdoorSensor(_, d) {
        setLive(prev => ({
            ...prev,
            outTemp: d.temp ?? prev.outTemp,
            outHumid: d.humid ?? prev.outHumid,
            outLux: d.lux ?? prev.outLux,
        }));
    }

    useEffect(() => {
        if (deviceIds && devices) {
            setDeviceList(devices.filter(d => deviceIds.includes(d.id)));
        }
    }, [devices, deviceIds]);
    useEffect(() => {
        getAutomationAnalyticsSummary()
            .then(setAutomationSummary)
            .catch(() => setAutomationSummary(null));
    }, []);
    // ── Build weather prop for TopBar ──────────────────────────────────────
    // Prefer the Open-Meteo outdoor data; fall back to live indoor sensor
    const weatherForTopBar = outdoor
        ? {
            ...outdoor,
            label: outdoor.conditionLabel,
            temp: `${outdoor.temperature.toFixed(1)}°C`,
            // keep the richer shape too so buildMessage can use it
            conditionLabel: outdoor.conditionLabel,
            temperature: outdoor.temperature,
        }
        : live.temp != null
            ? {label: live.temp >= 30 ? "Sunny" : live.temp >= 20 ? "Partly cloudy" : "Cloudy", temp: `${live.temp}°C`}
            : topBarProps.weather;

    const mergedHomeStats = {...topBarProps.homeStats};

    // ── StatsRow items ─────────────────────────────────────────────────────
    const fmt = (v, suffix = "") => v != null ? `${v}${suffix}` : "—";

    const liveStatsItems = statsItemsFn
        ? statsItemsFn(live, outdoor)
        : [
            {
                icon: <DeviceThermostat sx={{fontSize: 36, color: C.yellow}}/>,
                label: "Temperature",
                value: fmt(live.temp, " °C"),
            },
            {
                icon: <OpacityIcon sx={{fontSize: 36, color: C.muted}}/>,
                label: "Humidity",
                value: fmt(live.humid, "%"),
            },
            {icon: <AirIcon sx={{fontSize: 36, color: C.yellow}}/>, label: "AQI", value: fmt(live.aqi),},
            {icon: <Co2Icon sx={{fontSize: 36, color: C.muted}}/>, label: "CO₂", value: fmt(live.co2),},
            {icon: <Co2Icon sx={{fontSize: 36, color: C.muted}}/>, label: "eCO₂", value: fmt(live.eCo2),},
            {icon: <GasMeterIcon sx={{fontSize: 36, color: C.muted}}/>, label: "Ch2o", value: fmt(live.ch2oStatus),},
            {icon: <ScienceIcon sx={{fontSize: 36, color: C.muted}}/>, label: "TVOC", value: fmt(live.tvoc),},
            {icon: <Lightbulb sx={{fontSize: 36, color: C.muted}}/>, label: "Lux", value: fmt(live.lux),},
        ];
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef, true);
    return (
        <Card
            ref={cardRef}
            className="card-glow-container"
            variant="elevated"
            sx={{
                background: 'transparent',
                backgroundColor: 'rgb(0 0 0 / 0%)',
                borderRadius: '10px',
                width,
                // backdropFilter: 'blur(4px)',
                height,
                p: 0,
            }}
        >
            <div className="card-glow"/>
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

            <TopBar
                alertMessage={alertMessages}
                userName={topBarProps.userName}
                avatarLetter={topBarProps.avatarLetter}
                avatarColor={topBarProps.avatarColor}
                userRoom={topBarProps.userRoom}
                time={live.time}
                weather={weatherForTopBar}
                homeStats={mergedHomeStats}
                alarm={topBarProps.alarm}
                notifications={topBarProps.notifications}
                scenes={topBarProps.scenes}
                occupancy={topBarProps.occupancy}
                location={topBarProps.location}
                live={live}
                outdoor={outdoor}
                automationSummary={automationSummary}
                forecast={forecast}
            />

            <StatsRow items={liveStatsItems}/>
        </Card>
    );
});