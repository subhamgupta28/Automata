import React, {useEffect, useRef, useState} from "react";
import {Avatar, Box, Card, Chip, Typography} from "@mui/material";
import NotificationsIcon from "@mui/icons-material/Notifications";
import GridViewIcon from "@mui/icons-material/GridView";
import AlarmIcon from "@mui/icons-material/Alarm";
import HomeIcon from "@mui/icons-material/Home";
import WindowIcon from "@mui/icons-material/Window";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import LockIcon from "@mui/icons-material/Lock";
import PeopleIcon from "@mui/icons-material/People";
import WbSunnyIcon from "@mui/icons-material/WbSunny";
import OpacityIcon from "@mui/icons-material/Opacity";
import {Lightbulb} from "@mui/icons-material";
import AirIcon from '@mui/icons-material/Air';
import Co2Icon from '@mui/icons-material/Co2';
import ScienceIcon from '@mui/icons-material/Science';
import dayjs from "dayjs";
import '/src/App.css'
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {fetchOutdoorWeather, getRecentDeviceData} from "../../services/apis.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {CustomModal} from "../home/CustomModal.jsx";
import {useCardGlowEffect} from "../../utils/useCardGlowEffect.jsx";

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

// ─── Greeting — context-aware by hour ─────────────────────────────────────────
// Instead of a blunt 3-bucket split we use 7 natural time-of-day slots:
//   00–04  Late night   → "Still up, {name}?"
//   05–06  Early morning → "Rise and shine, {name}!"
//   07–11  Morning       → "Good morning, {name}!"
//   12–13  Midday        → "Good afternoon, {name}!"
//   14–17  Afternoon     → "Good afternoon, {name}!"
//   18–20  Evening       → "Good evening, {name}!"
//   21–23  Night         → "Good night, {name}!"
function buildGreeting(hour, name) {
    if (hour >= 0 && hour < 4) return `Still up, ${name}?`;
    if (hour >= 4 && hour < 6) return `Rise and shine, ${name}!`;
    if (hour >= 6 && hour < 12) return `Good morning, ${name}!`;
    if (hour >= 12 && hour < 14) return `Good afternoon, ${name}!`;
    if (hour >= 14 && hour < 18) return `Good afternoon, ${name}!`;
    if (hour >= 18 && hour < 21) return `Good evening, ${name}!`;
    return `Good night, ${name}!`;
}

// ─── Summary sentence ─────────────────────────────────────────────────────────
function buildMessage({time, weather, homeStats}) {
    const parts = [];
    if (weather?.conditionLabel && weather?.temperature != null) {
        parts.push(`it's ${weather.conditionLabel.toLowerCase()} outside at ${weather.temperature.toFixed(1)}°C`);
    } else if (weather?.label && weather?.temp) {
        parts.push(`the weather is ${weather.label.toLowerCase()} with ${weather.temp}`);
    }
    if (homeStats?.lightsOn != null) {
        parts.push(
            `${homeStats.lightsOn === 1 ? "1 light" : `${homeStats.lightsOn} lights`} on`
        );
    }
    if (homeStats?.windowsOpen != null) {
        parts.push(`${homeStats.windowsOpen} window${homeStats.windowsOpen !== 1 ? "s" : ""} open`);
    }
    if (homeStats?.security) {
        parts.push(`security ${homeStats.security.toLowerCase()}`);
    }
    if (homeStats?.doorLocked != null) {
        parts.push(`door ${homeStats.doorLocked ? "locked" : "unlocked"}`);
    }
    const timeStr = time ?? new Date().toTimeString().slice(0, 5);
    return parts.length > 0
        ? `It's ${timeStr} — ${parts.join(", ")}.`
        : `It's ${timeStr}.`;
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


// ─── TopBar — UI unchanged ────────────────────────────────────────────────────
export function TopBar({
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
                       }) {
    const hour = time ? parseInt(time.split(":")[0], 10) : new Date().getHours();
    // ↓ new: context-aware greeting includes the name
    const greeting = buildGreeting(hour, userName);
    const message = buildMessage({time, weather, homeStats, alarm});
    const chips = chipsProp ?? buildChips({
        notifications, scenes, alarm, location,
        windowsOpen: homeStats?.windowsOpen,
        doorLocked: homeStats?.doorLocked,
        occupancy,
    });

    return (
        <Box sx={{display: "flex", justifyContent: "space-between", alignItems: "flex-start", px: 2.5, py: 1.5}}>
            <Box style={{display: "flex", flexDirection: "column"}}>
                {/* greeting no longer needs ", {userName}!" appended — it's inside buildGreeting */}
                <Typography variant="h1">{greeting}</Typography>
                <Typography variant="title" style={{marginTop: "10px", width: "75%"}}>
                    {message}
                </Typography>
                {alarm && (
                    <Typography variant="body" sx={{color: C.muted}}>
                        Your alarm is set to {alarm}.
                    </Typography>
                )}
            </Box>

            <Box sx={{display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 1}}>
                {chips.length > 0 && (
                    <Box sx={{display: "flex", gap: 0.8}}>
                        {chips.map((c, i) => (
                            <Chip key={i} icon={c.icon} label={c.label} sx={chipSx}/>
                        ))}
                    </Box>
                )}
                <Box sx={{display: "flex", alignItems: "center", gap: 1}}>
                    <Avatar sx={{
                        width: 32,
                        height: 32,
                        backgroundColor: avatarColor,
                        color: "#111",
                        fontSize: 14,
                        fontWeight: 700
                    }}>
                        {avatarLetter ?? userName?.[0] ?? "?"}
                    </Avatar>
                    <Box sx={{textAlign: "right"}}>
                        <Typography sx={{fontSize: 13, fontWeight: 600}}>{userName}</Typography>
                        <Typography sx={{fontSize: 11, color: C.muted}}>{userRoom}</Typography>
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}

// ─── StatsRow — UI unchanged ──────────────────────────────────────────────────
export function StatsRow({items}) {

    return (
        <Box sx={{display: "flex", alignItems: "center", gap: 3, px: 2.5, py: 1, overflowX: "auto"}}>
            {items.map((s, i) => (
                <Box key={i} sx={{display: "flex", alignItems: "center", gap: 3}}>
                    <Box sx={{display: "flex", alignItems: "center", gap: 4, whiteSpace: "nowrap"}}>
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
    const {messages} = useDeviceLiveData();
    const {devices} = useCachedDevices();

    const [dataPoint, setDatapoint] = useState({});
    const [mainDevice, setMainDevice] = useState("");
    const [otherDevice, setOtherDevice] = useState("");
    const [deviceList, setDeviceList] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // Indoor sensor live data
    const [live, setLive] = useState({
        time: dayjs().format("HH:mm"),
        temp: null, humid: null, aqi: null,
        lux: null, co2: null, tvoc: null,
        ch2o: null, pm25: null,
        // secondary device (outdoor sensor)
        outTemp: null, outHumid: null, outLux: null,
    });

    // Outdoor weather from Open-Meteo via Spring Boot
    const [outdoor, setOutdoor] = useState(null); // WeatherResponse shape

    const {
        attributes,
        deviceIds,
        height,
        width,
        apiBase = "",
        weatherRefreshMs = 30 * 60 * 1000, // 10 min
        topBarProps = {
            userName: "Subham",
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
        setLive(prev => ({
            ...prev,
            time: dayjs().format("HH:mm"),
            temp: d.temp ?? prev.temp,
            humid: d.humid ?? prev.humid,
            aqi: d.aqi ?? prev.aqi,
            lux: d.lux ?? prev.lux,
            co2: d.s_co2 ?? prev.co2,
            tvoc: d.tvoc ?? prev.tvoc,
            ch2o: d.ch2o ?? prev.ch2o,
            pm25: d.pm25 ?? prev.pm25,
        }));
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

    // ── Build weather prop for TopBar ──────────────────────────────────────
    // Prefer the Open-Meteo outdoor data; fall back to live indoor sensor
    const weatherForTopBar = outdoor
        ? {
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
                icon: <WbSunnyIcon sx={{fontSize: 36, color: C.yellow}}/>,
                label: "Temperature",
                value: fmt(live.outTemp, " °C"),
            },
            {
                icon: <OpacityIcon sx={{fontSize: 36, color: C.muted}}/>,
                label: "Humidity",
                value: fmt(live.outHumid, "%"),
            },
            {icon: <AirIcon sx={{fontSize: 36, color: C.yellow}}/>, label: "AQI", value: fmt(live.aqi),},
            {icon: <Co2Icon sx={{fontSize: 36, color: C.muted}}/>, label: "CO₂", value: fmt(live.co2),},
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
            />

            <StatsRow items={liveStatsItems}/>
        </Card>
    );
});