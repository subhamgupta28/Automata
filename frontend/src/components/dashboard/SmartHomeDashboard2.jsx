import React, {useState} from 'react';
import {
    Avatar,
    Badge,
    Box,
    Button,
    Card,
    createTheme,
    CssBaseline,
    Grid,
    IconButton,
    Stack,
    ThemeProvider,
    Typography,
} from '@mui/material';

// --- Icons Import ---
import HomeIcon from '@mui/icons-material/Home';
import GridViewIcon from '@mui/icons-material/GridView';
import MusicNoteIcon from '@mui/icons-material/MusicNote';
import SecurityIcon from '@mui/icons-material/Security';
import PersonIcon from '@mui/icons-material/Person';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import PlaylistPlayIcon from '@mui/icons-material/PlaylistPlay';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import SensorsIcon from '@mui/icons-material/Sensors';
import MeetingRoomIcon from '@mui/icons-material/MeetingRoom';
import LockIcon from '@mui/icons-material/Lock';
import PowerIcon from '@mui/icons-material/Power';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import WaterDropIcon from '@mui/icons-material/WaterDrop';
import ThunderboltIcon from '@mui/icons-material/ElectricBolt';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import AspectRatioIcon from '@mui/icons-material/AspectRatio';
import CloudQueueIcon from '@mui/icons-material/CloudQueue';
import BedIcon from '@mui/icons-material/Bed';
import MonitorIcon from '@mui/icons-material/Monitor';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import BathtubIcon from '@mui/icons-material/Bathtub';
import NightsStayIcon from '@mui/icons-material/NightsStay';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import BatteryChargingFullIcon from '@mui/icons-material/BatteryChargingFull';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';

// --- Theme Definition ---
const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        background: {
            default: '#08080a',
            paper: '#121318',
        },
        text: {
            primary: '#ffffff',
            secondary: '#94a3b8',
        },
        primary: {
            main: '#a78bfa', // Purple highlight
        },
    },
    typography: {
        fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
        button: {
            textTransform: 'none',
        },
    },
    shape: {
        borderRadius: 24, // Matches the high-rounded aesthetics
    },
    components: {
        MuiCard: {
            styleOverrides: {
                root: {
                    backgroundImage: 'none',
                    backgroundColor: '#111216',
                    borderRadius: 24,
                    border: '1px solid #1a1b20',
                },
            },
        },
    },
});

export default function SmartHomeDashboard2() {
    const [activeTab, setActiveTab] = useState('Home');

    return (
        <ThemeProvider theme={darkTheme}>
            <CssBaseline/>
            <Box sx={{display: 'flex', minHeight: '100vh', bgcolor: '#08080a', p: 3, gap: 3}}>

                {/* ================= LEFT SIDEBAR ================= */}
                <Box
                    sx={{
                        width: 88,
                        bgcolor: '#121318',
                        borderRadius: '24px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        py: 4,
                        justifyContent: 'space-between',
                        border: '1px solid #1a1b20',
                    }}
                >
                    {/* Main Navigation Controls */}
                    <Stack spacing={4} alignItems="center" sx={{width: '100%'}}>
                        {/* Home Icon inside accent shell */}
                        <Box
                            onClick={() => setActiveTab('Home')}
                            sx={{
                                width: 56,
                                height: 56,
                                borderRadius: '20px',
                                bgcolor: activeTab === 'Home' ? '#2e224d' : 'transparent',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                cursor: 'pointer',
                                color: activeTab === 'Home' ? '#c084fc' : '#475569',
                                transition: '0.2s',
                            }}
                        >
                            <HomeIcon/>
                        </Box>

                        {/* Rooms Navigation */}
                        <Badge badgeContent={6} color="warning" overlap="circular">
                            <IconButton
                                onClick={() => setActiveTab('Rooms')}
                                sx={{
                                    color: activeTab === 'Rooms' ? '#c084fc' : '#475569',
                                    bgcolor: activeTab === 'Rooms' ? '#2e224d' : 'transparent',
                                    width: 56,
                                    height: 56,
                                    borderRadius: '20px',
                                }}
                            >
                                <GridViewIcon/>
                            </IconButton>
                        </Badge>

                        {/* Music */}
                        <IconButton
                            onClick={() => setActiveTab('Music')}
                            sx={{
                                color: activeTab === 'Music' ? '#c084fc' : '#475569',
                                bgcolor: activeTab === 'Music' ? '#2e224d' : 'transparent',
                                width: 56,
                                height: 56,
                                borderRadius: '20px',
                            }}
                        >
                            <MusicNoteIcon/>
                        </IconButton>

                        {/* Security */}
                        <Badge badgeContent={1} color="error" overlap="circular">
                            <IconButton
                                onClick={() => setActiveTab('Security')}
                                sx={{
                                    color: activeTab === 'Security' ? '#c084fc' : '#475569',
                                    bgcolor: activeTab === 'Security' ? '#2e224d' : 'transparent',
                                    width: 56,
                                    height: 56,
                                    borderRadius: '20px',
                                }}
                            >
                                <SecurityIcon/>
                            </IconButton>
                        </Badge>

                        {/* User Focus */}
                        <IconButton
                            onClick={() => setActiveTab('User')}
                            sx={{
                                color: activeTab === 'User' ? '#c084fc' : '#475569',
                                bgcolor: activeTab === 'User' ? '#2e224d' : 'transparent',
                                width: 56,
                                height: 56,
                                borderRadius: '20px',
                            }}
                        >
                            <PersonIcon/>
                        </IconButton>
                    </Stack>

                    {/* More Action */}
                    <IconButton sx={{color: '#475569'}}>
                        <MoreHorizIcon/>
                    </IconButton>
                </Box>

                {/* ================= MAIN CONTENT CONTAINER ================= */}
                <Box sx={{flexGrow: 1, display: 'flex', flexDirection: 'column', gap: 3}}>

                    {/* --- TOP STATUS HEADER SYSTEM --- */}
                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                        {/* Text Greeting and system status overview */}
                        <Box>
                            <Typography variant="h4" fontWeight="700" sx={{mb: 1}}>
                                Good Evening, Julian!
                            </Typography>
                            <Typography variant="body1" color="text.secondary" sx={{maxWidth: 650, lineHeight: 1.5}}>
                                It's 21:29, the weather is partly cloudy with 18.7°C. Right now there are 6 lights on
                                and
                                1 windows open. The security system is set to Armed home; the flat door is unlocked.
                                Your alarm is set to 08:00.
                            </Typography>
                        </Box>

                        {/* Status Pills and Quick User Widget */}
                        <Stack direction="row" spacing={1} alignItems="center">
                            {/* Pill elements */}
                            <Box sx={{
                                display: 'flex',
                                gap: 1,
                                bgcolor: '#121318',
                                p: 0.8,
                                borderRadius: '50px',
                                border: '1px solid #1a1b20'
                            }}>
                                <ChipWithIcon icon={<LightbulbIcon sx={{color: '#fcd34d', fontSize: 16}}/>} text="6"/>
                                <ChipWithIcon icon={<PlaylistPlayIcon sx={{color: '#60a5fa', fontSize: 16}}/>}
                                              text="4"/>
                                <ChipWithIcon icon={<AccessTimeIcon sx={{color: '#a78bfa', fontSize: 16}}/>}
                                              text="08:00"/>
                                <ChipWithIcon icon={<SensorsIcon sx={{color: '#34d399', fontSize: 16}}/>} text="Home"/>
                                <ChipWithIcon icon={<MeetingRoomIcon sx={{color: '#f87171', fontSize: 16}}/>} text="1"/>
                                <ChipWithIcon icon={<LockIcon sx={{color: '#f472b6', fontSize: 16}}/>}/>
                                <ChipWithIcon icon={<PowerIcon sx={{color: '#a78bfa', fontSize: 16}}/>}/>
                            </Box>

                            {/* Active User Avatar widget */}
                            <Box sx={{
                                display: 'flex',
                                alignItems: 'center',
                                bgcolor: '#121318',
                                py: 0.5,
                                pl: 0.5,
                                pr: 2,
                                borderRadius: '50px',
                                border: '1px solid #1a1b20',
                                gap: 1.5
                            }}>
                                <Avatar sx={{width: 36, height: 36, bgcolor: '#fdba74'}}>J</Avatar>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" display="block"
                                                sx={{lineHeight: 1}}>Julian</Typography>
                                    <Typography variant="body2" fontWeight="600">Living room</Typography>
                                </Box>
                            </Box>
                        </Stack>
                    </Box>

                    {/* --- HORIZONTAL QUICK SENSOR READINGS --- */}
                    <Stack direction="row" spacing={2} sx={{overflowX: 'auto', pb: 0.5}}>
                        <SensorBadge icon={<CloudQueueIcon/>} label="Partly cloudy" val="18.7°C"/>
                        <SensorBadge icon={<ThermostatIcon/>} label="Temperature" val="24.0°C"/>
                        <SensorBadge icon={<WaterDropIcon/>} label="Humidity" val="70.0%"/>
                        <SensorBadge icon={<ThunderboltIcon/>} label="Energy" val="235kWh"/>
                        <SensorBadge icon={<AccessTimeIcon/>} label="Alarm" val="08:00"/>
                        <SensorBadge icon={<SmartToyIcon/>} label="Dobby" val="Docked"/>
                    </Stack>

                    {/* --- MAIN GRID LAYOUT SECTION --- */}
                    <Grid container spacing={3}>

                        {/* COLUMN 1: MAPS & MEDIA CONTROLLER (LEFT SIDE) */}
                        <Grid item xs={12} lg={8} sx={{display: 'flex', flexDirection: 'column', gap: 3}}>

                            {/* Map Panel Mock */}
                            <Card sx={{position: 'relative', height: 320, overflow: 'hidden'}}>
                                <Box sx={{
                                    p: 2,
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    position: 'absolute',
                                    top: 0,
                                    width: '100%',
                                    zIndex: 2
                                }}>
                                    <Typography variant="subtitle1" fontWeight="600"
                                                sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                                        <MyLocationIcon fontSize="small"/> Map
                                    </Typography>
                                    {/* Small toggles */}
                                    <Stack direction="row" spacing={1}>
                                        <Box sx={{width: 8, height: 8, borderRadius: '50%', bgcolor: '#4ade80'}}/>
                                        <Box sx={{width: 8, height: 8, borderRadius: '50%', bgcolor: '#94a3b8'}}/>
                                    </Stack>
                                </Box>

                                {/* Simulated Map View (SVG Canvas Styling) */}
                                <Box
                                    sx={{
                                        width: '100%',
                                        height: '100%',
                                        position: 'relative',
                                        bgcolor: '#1b1c18',
                                        backgroundImage: 'radial-gradient(circle, #2d2e24 10%, transparent 11%)',
                                        backgroundSize: '16px 16px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                    }}
                                >
                                    {/* Decorative Map Vector lines */}
                                    <svg style={{position: 'absolute', width: '100%', height: '100%', opacity: 0.15}}>
                                        <path d="M0,150 Q150,120 300,180 T600,100 T900,160" fill="none" stroke="#fff"
                                              strokeWidth="2"/>
                                        <path d="M100,0 Q180,180 200,320" fill="none" stroke="#fff" strokeWidth="1.5"/>
                                        <path d="M400,0 Q350,150 500,320" fill="none" stroke="#fff" strokeWidth="1"/>
                                    </svg>

                                    {/* Geographical Point Markers with labels matching screenshot */}
                                    <MapLabel top={140} left={300} text="Eisenharz"/>
                                    <MapLabel top={120} left={500} text="Isny im Allgäu" active/>
                                    <MapLabel top={100} left={540} text="Kleinhaslach"/>
                                    <MapLabel top={210} left={380} text="Dorenwaid"/>
                                    <MapLabel top={260} left={250} text="Eglofs"/>
                                    <MapLabel top={240} left={610} text="Argen"/>

                                    {/* Left Controls (Zoom In, Zoom Out, Layer UI buttons) */}
                                    <Box sx={{
                                        position: 'absolute',
                                        left: 16,
                                        top: 60,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 1,
                                        zIndex: 1
                                    }}>
                                        <IconButton size="small"
                                                    sx={{bgcolor: '#27282e', '&:hover': {bgcolor: '#37383e'}}}>
                                            <AddIcon fontSize="small"/>
                                        </IconButton>
                                        <IconButton size="small"
                                                    sx={{bgcolor: '#27282e', '&:hover': {bgcolor: '#37383e'}}}>
                                            <RemoveIcon fontSize="small"/>
                                        </IconButton>
                                        <IconButton size="small"
                                                    sx={{bgcolor: '#27282e', '&:hover': {bgcolor: '#37383e'}}}>
                                            <MyLocationIcon fontSize="small"/>
                                        </IconButton>
                                        <IconButton size="small"
                                                    sx={{bgcolor: '#27282e', '&:hover': {bgcolor: '#37383e'}}}>
                                            <AspectRatioIcon fontSize="small"/>
                                        </IconButton>
                                    </Box>
                                </Box>
                            </Card>

                            {/* Media Interface Panel */}
                            <Box>
                                <Typography variant="subtitle1" fontWeight="600"
                                            sx={{mb: 2, display: 'flex', alignItems: 'center', gap: 1}}>
                                    <MusicNoteIcon/> Media <Typography variant="caption" color="#4ade80" sx={{ml: 1}}>●
                                    Idle</Typography>
                                </Typography>
                                <Grid container spacing={2}>
                                    {/* Select Target Room */}
                                    <Grid item xs={6}>
                                        <Card sx={{
                                            p: 2.5,
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center'
                                        }}>
                                            <Box>
                                                <Typography variant="h6" fontWeight="700">Choose</Typography>
                                                <Typography variant="body2" color="text.secondary">Room</Typography>
                                            </Box>
                                            <IconButton sx={{bgcolor: '#27282e'}}>
                                                <PlayArrowIcon sx={{transform: 'rotate(90deg)'}}/>
                                            </IconButton>
                                        </Card>
                                    </Grid>

                                    {/* Unified Audio Broadcast */}
                                    <Grid item xs={6}>
                                        <Card sx={{
                                            p: 2.5,
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center'
                                        }}>
                                            <Box>
                                                <Typography variant="h6" fontWeight="700">Play</Typography>
                                                <Typography variant="body2" color="text.secondary">in all
                                                    Rooms</Typography>
                                            </Box>
                                            <IconButton sx={{bgcolor: '#27282e'}}>
                                                <HomeIcon/>
                                            </IconButton>
                                        </Card>
                                    </Grid>
                                </Grid>

                                {/* Horizontal Quick-Selection Targets */}
                                <Stack direction="row" spacing={1.5} sx={{mt: 2}}>
                                    <MiniRoomButton color="#a3e635" icon={<HomeIcon fontSize="small"/>}/>
                                    <MiniRoomButton color="#60a5fa" icon={<BedIcon fontSize="small"/>}/>
                                    <MiniRoomButton color="#fda4af" icon={<MonitorIcon fontSize="small"/>}/>
                                    <MiniRoomButton color="#fcd34d" icon={<RestaurantIcon fontSize="small"/>}/>
                                    <MiniRoomButton color="#c084fc" icon={<BathtubIcon fontSize="small"/>}/>
                                    <MiniRoomButton color="#f97316" icon={<MeetingRoomIcon fontSize="small"/>}/>
                                </Stack>
                            </Box>

                            {/* Security Console Integration */}
                            <Box>
                                <Typography variant="subtitle1" fontWeight="600"
                                            sx={{mb: 2, display: 'flex', alignItems: 'center', gap: 1}}>
                                    <SecurityIcon/> Security &gt;
                                </Typography>
                                <Grid container spacing={2}>

                                    {/* Security Control Box */}
                                    <Grid item xs={12} md={6}>
                                        <Card sx={{
                                            p: 2,
                                            height: '100%',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            justifyContent: 'space-between'
                                        }}>
                                            <Box sx={{display: 'flex', alignItems: 'center', gap: 1.5, mb: 2}}>
                                                <SecurityIcon sx={{color: '#a78bfa'}}/>
                                                <Box>
                                                    <Typography variant="body2" color="text.secondary">Home</Typography>
                                                    <Typography variant="subtitle2" fontWeight="700">Security
                                                        System</Typography>
                                                </Box>
                                            </Box>
                                            {/* Security Action buttons */}
                                            <Stack direction="row" spacing={1} justifyContent="space-between"
                                                   sx={{bgcolor: '#1c1d24', p: 0.5, borderRadius: '16px'}}>
                                                <IconButton size="small" sx={{color: '#fff'}}><LockIcon
                                                    fontSize="small"/></IconButton>
                                                <IconButton size="small" sx={{color: '#fff'}}><NightsStayIcon
                                                    fontSize="small"/></IconButton>
                                                <IconButton size="small" sx={{
                                                    bgcolor: '#bef264',
                                                    color: '#111216',
                                                    '&:hover': {bgcolor: '#a3e635'}
                                                }}>
                                                    <HomeIcon fontSize="small"/>
                                                </IconButton>
                                                <IconButton size="small" sx={{color: '#fff'}}><VisibilityOffIcon
                                                    fontSize="small"/></IconButton>
                                            </Stack>
                                        </Card>
                                    </Grid>

                                    {/* Users Presence & Device Status */}
                                    <Grid item xs={12} md={6}>
                                        <Grid container spacing={1}>
                                            <Grid item xs={6}>
                                                <UserStatusCard name="Julian" status="Home" isHome avatar="J"
                                                                color="#fed7aa"/>
                                            </Grid>
                                            <Grid item xs={6}>
                                                <UserStatusCard name="Anna" status="Away" isHome={false} avatar="A"
                                                                color="#fcd34d"/>
                                            </Grid>
                                            <Grid item xs={6}>
                                                <UserStatusCard name="Valentin" status="Home" isHome avatar="🐶"
                                                                color="#bfdbfe"/>
                                            </Grid>
                                            <Grid item xs={6}>
                                                <UserStatusCard name="Simone" status="Away" isHome={false} avatar="🐈"
                                                                color="#fbcfe8"/>
                                            </Grid>
                                        </Grid>
                                    </Grid>

                                </Grid>
                            </Box>

                        </Grid>

                        {/* COLUMN 2: INDIVIDUAL ROOM SYSTEMS (RIGHT SIDE) */}
                        <Grid item xs={12} lg={4}>
                            <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2}}>
                                <Typography variant="h6" fontWeight="700">Rooms</Typography>
                                <Button variant="text" size="small" sx={{color: 'text.secondary'}}>Show all</Button>
                            </Box>

                            {/* Vertical Stack / Grid of Rooms */}
                            <Grid container spacing={2}>

                                {/* Living Room (Accent Card) */}
                                <Grid item xs={12}>
                                    <Card
                                        sx={{
                                            bgcolor: '#fed66c',
                                            color: '#1e1b15',
                                            p: 3,
                                            position: 'relative',
                                            overflow: 'hidden',
                                            height: 180,
                                            display: 'flex',
                                            flexDirection: 'column',
                                            justifyContent: 'space-between',
                                            border: 'none',
                                        }}
                                    >
                                        {/* Decorative split sidebar on card */}
                                        <Box sx={{
                                            position: 'absolute',
                                            right: 0,
                                            top: 0,
                                            bottom: 0,
                                            width: '12px',
                                            bgcolor: '#eab308'
                                        }}/>
                                        <Box sx={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'flex-start'
                                        }}>
                                            <Box>
                                                <Typography variant="h6" fontWeight="700" sx={{color: '#1e1b15'}}>Living
                                                    Room</Typography>
                                            </Box>
                                            <Avatar sx={{bgcolor: '#1e1b15', color: '#fed66c', width: 44, height: 44}}>
                                                <HomeIcon/>
                                            </Avatar>
                                        </Box>
                                        <Box>
                                            <Typography variant="h3" fontWeight="400"
                                                        sx={{display: 'inline-flex', alignItems: 'baseline'}}>
                                                25°
                                                <Typography variant="body2" sx={{
                                                    ml: 1,
                                                    opacity: 0.8,
                                                    color: '#1e1b15'
                                                }}>70%</Typography>
                                            </Typography>
                                        </Box>
                                    </Card>
                                </Grid>

                                {/* Bedroom */}
                                <Grid item xs={12}>
                                    <RoomCard title="Bedroom" temp="24°" humidity="66%" icon={<BedIcon/>}/>
                                </Grid>

                                {/* Office */}
                                <Grid item xs={12}>
                                    <RoomCard title="Office" temp="25°" humidity="68%" icon={<MonitorIcon/>}/>
                                </Grid>

                                {/* Kitchen */}
                                <Grid item xs={12}>
                                    <RoomCard title="Kitchen" temp="24°" humidity="71%" icon={<RestaurantIcon/>}/>
                                </Grid>

                                {/* Bathroom */}
                                <Grid item xs={12}>
                                    <RoomCard title="Bathroom" temp="24°" humidity="71%" icon={<BathtubIcon/>}/>
                                </Grid>

                                {/* Guest Room (Accent Styled Card) */}
                                <Grid item xs={12}>
                                    <RoomCard title="Guest room" temp="24°" humidity="71%" icon={<MeetingRoomIcon/>}
                                              iconBgColor="#fda4af" iconColor="#111216"/>
                                </Grid>

                            </Grid>
                        </Grid>

                    </Grid>
                </Box>

            </Box>
        </ThemeProvider>
    );
}

// ================= PRIVATE SUB-COMPONENTS =================

// Top Pill Chip Component
function ChipWithIcon({icon, text}) {
    return (
        <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            bgcolor: '#1d1e24',
            px: 1.2,
            py: 0.6,
            borderRadius: '30px'
        }}>
            {icon}
            {text && <Typography variant="caption" fontWeight="600">{text}</Typography>}
        </Box>
    );
}

// Environmental Sensor Badge Helper
function SensorBadge({icon, label, val}) {
    return (
        <Box
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                bgcolor: '#111216',
                px: 2.5,
                py: 1.5,
                borderRadius: '50px',
                border: '1px solid #1a1b20',
                minWidth: 'max-content',
            }}
        >
            <Box sx={{color: 'text.secondary', display: 'flex'}}>
                {icon}
            </Box>
            <Box>
                <Typography variant="caption" color="text.secondary" display="block" sx={{lineHeight: 1}}>
                    {label}
                </Typography>
                <Typography variant="subtitle2" fontWeight="700">
                    {val}
                </Typography>
            </Box>
        </Box>
    );
}

// Map Labels
function MapLabel({top, left, text, active = false}) {
    return (
        <Box sx={{position: 'absolute', top, left, display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
            <Box
                sx={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    bgcolor: active ? '#4ade80' : '#475569',
                    boxShadow: active ? '0 0 10px #4ade80' : 'none',
                    mb: 0.5,
                }}
            />
            <Typography variant="caption"
                        sx={{color: '#fff', fontSize: 10, fontWeight: '500', textShadow: '0 2px 4px rgba(0,0,0,0.8)'}}>
                {text}
            </Typography>
        </Box>
    );
}

// Room Selector Circular Icons
function MiniRoomButton({color, icon}) {
    return (
        <IconButton
            sx={{
                bgcolor: color,
                color: '#111216',
                width: 44,
                height: 44,
                '&:hover': {
                    bgcolor: color,
                    opacity: 0.9,
                },
            }}
        >
            {icon}
        </IconButton>
    );
}

// Simple Presence Cards
function UserStatusCard({name, status, isHome, avatar, color}) {
    return (
        <Box
            sx={{
                bgcolor: '#1c1d24',
                p: 1.5,
                borderRadius: '20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                border: '1px solid #272832',
            }}
        >
            <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                <Badge
                    overlap="circular"
                    anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}
                    badgeContent={
                        <Box
                            sx={{
                                width: 10,
                                height: 10,
                                borderRadius: '50%',
                                bgcolor: isHome ? '#4ade80' : '#ef4444',
                                border: '2px solid #1c1d24',
                            }}
                        />
                    }
                >
                    <Avatar
                        sx={{bgcolor: color, color: '#111216', width: 32, height: 32, fontSize: 14}}>{avatar}</Avatar>
                </Badge>
                <Box>
                    <Typography variant="body2" fontWeight="600" sx={{lineHeight: 1.1}}>{name}</Typography>
                    <Typography variant="caption" color="text.secondary">{status}</Typography>
                </Box>
            </Box>
            <BatteryChargingFullIcon sx={{fontSize: 16, color: 'text.secondary'}}/>
        </Box>
    );
}

// Standard Right-Column Room Card Components
function RoomCard({title, temp, humidity, icon, iconBgColor = '#1e1f26', iconColor = '#ffffff'}) {
    return (
        <Card sx={{p: 2.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
            <Box>
                <Typography variant="subtitle1" fontWeight="700" color="text.primary">{title}</Typography>
                <Typography variant="h4" fontWeight="400" sx={{display: 'inline-flex', alignItems: 'baseline', mt: 1}}>
                    {temp}
                    <Typography variant="body2" color="text.secondary" sx={{ml: 1}}>{humidity}</Typography>
                </Typography>
            </Box>
            <Avatar sx={{bgcolor: iconBgColor, color: iconColor, width: 44, height: 44}}>
                {icon}
            </Avatar>
        </Card>
    );
}