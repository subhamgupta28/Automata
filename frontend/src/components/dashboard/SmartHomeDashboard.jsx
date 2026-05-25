import {Avatar, Box, Chip, Typography} from "@mui/material";
import NotificationsIcon from "@mui/icons-material/Notifications";
import GridViewIcon from "@mui/icons-material/GridView";
import AlarmIcon from "@mui/icons-material/Alarm";
import HomeIcon from "@mui/icons-material/Home";
import WindowIcon from "@mui/icons-material/Window";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import PeopleIcon from "@mui/icons-material/People";
import WbCloudyIcon from "@mui/icons-material/WbCloudy";
import ThermostatIcon from "@mui/icons-material/Thermostat";
import WaterDropIcon from "@mui/icons-material/WaterDrop";
import BoltIcon from "@mui/icons-material/Bolt";
import DockIcon from "@mui/icons-material/Dock";
import LockIcon from "@mui/icons-material/Lock";


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

const chipSx = {
    backgroundColor: C.card,
    color: C.text,
};

// ─── Dynamic message builder ─────────────────────────────────────────────────
function buildGreeting(hour) {
    if (hour < 12) return "Good Morning";
    if (hour < 17) return "Good Afternoon";
    return "Good Evening";
}

function buildMessage({time, weather, homeStats, alarm}) {
    const parts = [];

    if (weather) {
        parts.push(
            `the weather is ${weather.label.toLowerCase()} with ${weather.temp}`
        );
    }

    if (homeStats?.lightsOn != null) {
        parts.push(
            `there ${homeStats.lightsOn === 1 ? "is" : "are"} ${homeStats.lightsOn} light${homeStats.lightsOn !== 1 ? "s" : ""} on`
        );
    }

    if (homeStats?.windowsOpen != null) {
        parts.push(
            `${homeStats.windowsOpen} window${homeStats.windowsOpen !== 1 ? "s" : ""} open`
        );
    }

    if (homeStats?.security) {
        parts.push(`the security system is set to ${homeStats.security}`);
    }

    if (homeStats?.doorLocked != null) {
        parts.push(`the flat door is ${homeStats.doorLocked ? "locked" : "unlocked"}`);
    }

    const hour = time ? parseInt(time.split(":")[0], 10) : new Date().getHours();
    const timeStr = time ?? new Date().toTimeString().slice(0, 5);

    const sentence =
        parts.length > 0
            ? `It's ${timeStr}, ${parts.join(". ")}.`
            : `It's ${timeStr}.`;

    return sentence;
}

// ─── Default chip builder from props ─────────────────────────────────────────
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

// ─── TopBar ──────────────────────────────────────────────────────────────────
/**
 * Props:
 *   userName       {string}               Display name, e.g. "Subham"
 *   avatarLetter   {string}               Single char for avatar, defaults to userName[0]
 *   avatarColor    {string}               Avatar background color
 *   userRoom       {string}               e.g. "Living room"
 *   time           {string}               "HH:MM" – used in greeting + message
 *   weather        {{ label, temp }}      e.g. { label: "Partly cloudy", temp: "18.7°C" }
 *   homeStats      {{
 *                    lightsOn, windowsOpen,
 *                    security, doorLocked
 *                  }}
 *   alarm          {string}               "HH:MM"
 *   chips          {Array}                Override auto-generated chips entirely
 *   notifications  {number}               Chip: notification count
 *   scenes         {number}               Chip: active scenes count
 *   occupancy      {number}               Chip: people home
 *   location       {string}               Chip: location label
 */
export default function TopBar({
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
    const hour = time
        ? parseInt(time.split(":")[0], 10)
        : new Date().getHours();

    const greeting = buildGreeting(hour);
    const message = buildMessage({time, weather, homeStats, alarm});

    const chips = chipsProp ?? buildChips({
        notifications,
        scenes,
        alarm,
        location,
        windowsOpen: homeStats?.windowsOpen,
        doorLocked: homeStats?.doorLocked,
        occupancy,
    });

    return (
        <Box
            sx={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "flex-start",
                px: 2.5,
                py: 1.5,
            }}
        >
            <Box style={{display: "flex", flexDirection: "column"}}>
                <Typography variant="h1">
                    {greeting}, {userName}!
                </Typography>
                <Typography
                    variant="title"
                    style={{marginTop: "10px", width: "75%"}}
                >
                    {message}
                </Typography>
                {alarm && (
                    <Typography variant="body" sx={{color: C.muted}}>
                        Your alarm is set to {alarm}.
                    </Typography>
                )}
            </Box>

            <Box
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-end",
                    gap: 1,
                }}
            >
                {chips.length > 0 && (
                    <Box sx={{display: "flex", gap: 0.8}}>
                        {chips.map((c, i) => (
                            <Chip key={i} icon={c.icon} label={c.label} sx={chipSx}/>
                        ))}
                    </Box>
                )}
                <Box sx={{display: "flex", alignItems: "center", gap: 1}}>
                    <Avatar
                        sx={{
                            width: 32,
                            height: 32,
                            backgroundColor: avatarColor,
                            color: "#111",
                            fontSize: 14,
                            fontWeight: 700,
                        }}
                    >
                        {avatarLetter ?? userName?.[0] ?? "?"}
                    </Avatar>
                    <Box sx={{textAlign: "right"}}>
                        <Typography sx={{fontSize: 13, fontWeight: 600}}>
                            {userName}
                        </Typography>
                        <Typography sx={{fontSize: 11, color: C.muted}}>
                            {userRoom}
                        </Typography>
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}

// ─── StatsRow ─────────────────────────────────────────────────────────────────
/**
 * Props:
 *   items  {Array<{ icon, label, value }>}   Full control over stat items
 */
export function StatsRow({items}) {
    const defaultItems = [
        {
            icon: <WbCloudyIcon sx={{fontSize: 18}}/>,
            label: "Partly cloudy",
            value: "18.7°C",
        },
        {
            icon: <ThermostatIcon sx={{fontSize: 18}}/>,
            label: "Temperature",
            value: "24.0°C",
        },
        {
            icon: <WaterDropIcon sx={{fontSize: 18, color: C.red}}/>,
            label: "Humidity",
            value: "70.0%",
        },
        {
            icon: <BoltIcon sx={{fontSize: 18, color: C.yellow}}/>,
            label: "Energy",
            value: "235kwh",
        },
        {
            icon: <AlarmIcon sx={{fontSize: 18}}/>,
            label: "Alarm",
            value: "08:00",
        },
        {
            icon: <DockIcon sx={{fontSize: 18}}/>,
            label: "Dobby",
            value: "Docked",
        },
    ];

    const resolved = items ?? defaultItems;

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                gap: 3,
                px: 2.5,
                py: 1,
                overflowX: "auto",
            }}
        >
            {resolved.map((s, i) => (
                <Box key={i} sx={{display: "flex", alignItems: "center", gap: 3}}>
                    <Box
                        sx={{
                            display: "flex",
                            alignItems: "center",
                            gap: 4,
                            whiteSpace: "nowrap",
                        }}
                    >
                        <Box sx={{color: C.muted}}>{s.icon}</Box>
                        <Box>
                            <Typography sx={{color: C.muted, lineHeight: 1}}>
                                {s.label}
                            </Typography>
                            <Typography variant="h6" sx={{fontWeight: 600}}>
                                {s.value}
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            ))}
        </Box>
    );
}