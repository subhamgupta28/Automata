import {Avatar, Box, Chip, Grid, Typography} from "@mui/material";
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
import MusicNoteIcon from "@mui/icons-material/MusicNote";
import FiberManualRecordIcon from "@mui/icons-material/FiberManualRecord";
import DownloadIcon from "@mui/icons-material/Download";
import TvIcon from "@mui/icons-material/Tv";
import BedIcon from "@mui/icons-material/Bed";
import MonitorIcon from "@mui/icons-material/Monitor";
import KitchenIcon from "@mui/icons-material/Kitchen";
import BathroomIcon from "@mui/icons-material/Bathroom";
import MeetingRoomIcon from "@mui/icons-material/MeetingRoom";
import EditIcon from "@mui/icons-material/Edit";

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
    backgroundColor: C.card, color: C.text,

};

const chips = [
    {icon: <NotificationsIcon sx={{fontSize: 16, color: C.yellow}}/>, label: "6"},
    {icon: <GridViewIcon sx={{fontSize: 16, color: C.muted}}/>, label: "4"},
    {icon: <AlarmIcon sx={{fontSize: 16, color: C.muted}}/>, label: "08:00"},
    {icon: <HomeIcon sx={{fontSize: 16, color: C.muted}}/>, label: "Home"},
    {icon: <WindowIcon sx={{fontSize: 16, color: C.muted}}/>, label: "1"},
    {icon: <LockOpenIcon sx={{fontSize: 16, color: C.muted}}/>, label: ""},
    {icon: <PeopleIcon sx={{fontSize: 16, color: C.muted}}/>, label: ""},
];

export default function TopBar() {
    return (
        <Box sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            px: 2.5,
            py: 1.5,
        }}>
            <Box style={{display: 'flex', flexDirection: 'column'}}>
                <Typography variant="h1">Good Evening, Subham!</Typography>
                <Typography variant="title" style={{marginTop: '10px', width: '75%'}}>
                    It's 21:29, the weather is partly cloudy with 18.7°C. Right now there are 6 lights on and 1 windows
                    open. The security system is set to Armed home; the flat door is unlocked.
                </Typography>
                <Typography variant="body" sx={{color: C.muted}}>Your alarm is set to 08:00.</Typography>
            </Box>
            <Box sx={{display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 1}}>
                <Box sx={{display: "flex", gap: 0.8}}>
                    {chips.map((c, i) => (
                        <Chip key={i} icon={c.icon} label={c.label} sx={chipSx}/>
                    ))}
                </Box>
                <Box sx={{display: "flex", alignItems: "center", gap: 1}}>
                    <Avatar sx={{
                        width: 32,
                        height: 32,
                        backgroundColor: C.yellow,
                        color: "#111",
                        fontSize: 14,
                        fontWeight: 700
                    }}>J</Avatar>
                    <Box sx={{textAlign: "right"}}>
                        <Typography sx={{fontSize: 13, fontWeight: 600}}>Subham</Typography>
                        <Typography sx={{fontSize: 11, color: C.muted}}>Living room</Typography>
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}

const stats = [
    {icon: <WbCloudyIcon sx={{fontSize: 18}}/>, label: "Partly cloudy", value: "18.7°C"},
    {icon: <ThermostatIcon sx={{fontSize: 18}}/>, label: "Temperature", value: "24.0°C"},
    {icon: <WaterDropIcon sx={{fontSize: 18, color: C.red}}/>, label: "Humidity", value: "70.0%"},
    {icon: <BoltIcon sx={{fontSize: 18, color: C.yellow}}/>, label: "Energy", value: "235kwh"},
    {icon: <AlarmIcon sx={{fontSize: 18}}/>, label: "Alarm", value: "08:00"},
    {icon: <DockIcon sx={{fontSize: 18}}/>, label: "Dobby", value: "Docked"},
];

export function StatsRow() {
    return (
        <Box sx={{
            display: "flex",
            alignItems: "center",
            gap: 3,
            px: 2.5,
            py: 1,
            // borderBottom: `1px solid ${C.border}`,
            overflowX: "auto"
        }}>
            {stats.map((s, i) => (
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


const roomIcons = [
    {icon: TvIcon, color: C.yellow},
    {icon: BedIcon, color: "#4a9eff"},
    {icon: MonitorIcon, color: "#ff6b6b"},
    {icon: KitchenIcon, color: C.text},
    {icon: BathroomIcon, color: "#e060f0"},
    {icon: MeetingRoomIcon, color: "#f0a060"},
];

export function MediaPanel() {
    return (
        <Box sx={{bgcolor: C.card, borderRadius: 3, border: `1px solid ${C.border}`, height: "100%"}}>
            <Box sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                px: 2,
                py: 1,
                borderBottom: `1px solid ${C.border}`
            }}>
                <Box sx={{display: "flex", alignItems: "center", gap: 0.7}}>
                    <MusicNoteIcon sx={{fontSize: 16, color: C.muted}}/>
                    <Typography sx={{fontSize: 13, fontWeight: 600}}>Media</Typography>
                </Box>
                <Box sx={{display: "flex", alignItems: "center", gap: 1.5}}>
                    <Box sx={{display: "flex", alignItems: "center", gap: 0.5}}>
                        <FiberManualRecordIcon sx={{fontSize: 8, color: C.green}}/>
                        <Typography sx={{fontSize: 11, color: C.muted}}>Idle</Typography>
                    </Box>
                    <Box sx={{display: "flex", alignItems: "center", gap: 0.5}}>
                        <Box sx={{width: 10, height: 10, bgcolor: C.cardAlt, borderRadius: 0.5}}/>
                        <Typography sx={{fontSize: 11, color: C.muted}}>Off</Typography>
                    </Box>
                </Box>
            </Box>

            <Box sx={{p: 1.5, display: "flex", flexDirection: "column", gap: 1.5}}>
                <Box sx={{display: "flex", gap: 1}}>
                    {[
                        {title: "Choose", sub: "Room", icon: <DownloadIcon sx={{fontSize: 16, color: C.muted}}/>},
                        {title: "Play", sub: "in all Rooms", icon: <HomeIcon sx={{fontSize: 16, color: C.muted}}/>},
                    ].map((item, i) => (
                        <Box key={i} sx={{
                            flex: 1,
                            bgcolor: C.cardAlt,
                            borderRadius: 2,
                            p: 1.2,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between"
                        }}>
                            <Box>
                                <Typography sx={{fontSize: 15, fontWeight: 600}}>{item.title}</Typography>
                                <Typography sx={{fontSize: 11, color: C.muted}}>{item.sub}</Typography>
                            </Box>
                            {item.icon}
                        </Box>
                    ))}
                </Box>

                <Box sx={{display: "flex", gap: 1}}>
                    {roomIcons.map(({icon: Icon, color}, i) => (
                        <Box key={i} sx={{
                            width: 36,
                            height: 36,
                            bgcolor: C.cardAlt,
                            borderRadius: "50%",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            cursor: "pointer",
                            color
                        }}>
                            <Icon sx={{fontSize: 16}}/>
                        </Box>
                    ))}
                </Box>
            </Box>
        </Box>
    );
}

const rooms = [
    {name: "Living\nRoom", icon: TvIcon, temp: 25, humidity: 70, active: true},
    {name: "Bedroom", icon: BedIcon, temp: 24, humidity: 66},
    {name: "Office", icon: MonitorIcon, temp: 25, humidity: 68},
    {name: "Kitchen", icon: KitchenIcon, temp: 24, humidity: 71},
    {name: "Bathroom", icon: BathroomIcon, temp: 24, humidity: 71},
    {name: "Guest\nroom", icon: GridViewIcon, temp: 24, humidity: 71, accent: "#ff6b4a"},
];

function RoomCard({name, icon: Icon, temp, humidity, active, accent}) {
    const bg = active ? C.yellow : C.card;
    const fg = active ? "#111" : C.text;
    const sub = active ? "#333" : C.dim;
    const iconBg = active ? "#111" : C.cardAlt;
    const iconColor = active ? C.yellow : accent || C.muted;

    return (
        <Box sx={{
            bgcolor: bg,
            borderRadius: 3,
            border: active ? "none" : `1px solid ${C.border}`,
            p: 1.5,
            minHeight: 120,
            cursor: "pointer"
        }}>
            <Box sx={{display: "flex", justifyContent: "space-between", alignItems: "flex-start"}}>
                <Typography sx={{
                    fontSize: 13,
                    fontWeight: 600,
                    color: fg,
                    whiteSpace: "pre-line",
                    lineHeight: 1.3
                }}>{name}</Typography>
                <Box sx={{
                    width: 30,
                    height: 30,
                    bgcolor: iconBg,
                    borderRadius: "50%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center"
                }}>
                    <Icon sx={{fontSize: 16, color: iconColor}}/>
                </Box>
            </Box>
            <Typography sx={{fontSize: 36, fontWeight: 200, color: fg, mt: 1, lineHeight: 1}}>{temp}°</Typography>
            <Typography sx={{fontSize: 12, color: sub}}>{humidity}%</Typography>
        </Box>
    );
}

export function RoomsGrid() {
    return (
        <Box sx={{width: 340, flexShrink: 0}}>
            <Box sx={{display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1}}>
                <Box sx={{display: "flex", alignItems: "center", gap: 0.7}}>
                    <GridViewIcon sx={{fontSize: 16, color: C.muted}}/>
                    <Typography sx={{fontSize: 13, fontWeight: 600}}>Rooms</Typography>
                </Box>
                <Box sx={{display: "flex", alignItems: "center", gap: 0.5, cursor: "pointer"}}>
                    <EditIcon sx={{fontSize: 13, color: C.muted}}/>
                    <Typography sx={{fontSize: 12, color: C.muted}}>Show all</Typography>
                </Box>
            </Box>
            <Grid container spacing={1.2}>
                {rooms.map((r, i) => (
                    <Grid item xs={6} key={i}>
                        <RoomCard {...r} />
                    </Grid>
                ))}
            </Grid>
        </Box>
    );
}