import {useEffect, useState} from 'react';
import {
    alpha,
    Box,
    Card,
    CardMedia,
    Chip,
    IconButton,
    ListItemIcon,
    ListItemText,
    Menu,
    MenuItem,
    Skeleton,
    Slider,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material';
import {
    DevicesOther,
    MusicNote,
    OpenInNew,
    Pause,
    PlayArrow,
    SkipNext,
    SkipPrevious,
    VolumeOff,
    VolumeUp,
} from '@mui/icons-material';
import {useSpotify} from "./useSpotify.jsx";


// ── Helpers ────────────────────────────────────────────────────────────────

function msToTime(ms) {
    if (!ms) return '0:00';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = String(totalSec % 60).padStart(2, '0');
    return `${m}:${s}`;
}

function deviceIcon(type) {
    const icons = {Computer: '💻', Smartphone: '📱', Speaker: '🔊', TV: '📺', GameConsole: '🎮'};
    return icons[type] ?? '🎵';
}

// ── Sub-components ─────────────────────────────────────────────────────────

function ConnectPrompt({onLogin}) {
    return (
        <Card
            sx={{
                width: 360,
                p: 4,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 2,
                background: 'linear-gradient(145deg, #0f0f0f 0%, #1a1a1a 100%)',
                border: '1px solid rgba(255,255,255,0.06)',
                borderRadius: 3,
            }}
        >
            <Box
                sx={{
                    width: 64,
                    height: 64,
                    borderRadius: '50%',
                    background: '#1DB954',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
            >
                <MusicNote sx={{color: '#000', fontSize: 32}}/>
            </Box>
            <Typography variant="h6" sx={{color: '#fff', fontWeight: 700}}>
                Connect Spotify
            </Typography>
            <Typography variant="body2" sx={{color: 'rgba(255,255,255,0.5)', textAlign: 'center'}}>
                Link your Spotify account to control playback from here.
            </Typography>
            <Box
                component="button"
                onClick={onLogin}
                sx={{
                    mt: 1,
                    px: 3,
                    py: 1.5,
                    borderRadius: 50,
                    border: 'none',
                    background: '#1DB954',
                    color: '#000',
                    fontWeight: 700,
                    fontSize: 14,
                    cursor: 'pointer',
                    '&:hover': {background: '#1ed760'},
                    transition: 'background 0.15s',
                }}
            >
                Connect with Spotify
            </Box>
        </Card>
    );
}

function LoadingSkeleton() {
    return (
        <Card sx={{width: 360, p: 3, background: '#111', borderRadius: 3}}>
            <Skeleton variant="rectangular" height={220} sx={{borderRadius: 2, mb: 2}}/>
            <Skeleton width="70%" height={28} sx={{mb: 0.5}}/>
            <Skeleton width="50%" height={20} sx={{mb: 2}}/>
            <Skeleton height={8} sx={{borderRadius: 4, mb: 2}}/>
            <Stack direction="row" justifyContent="center" spacing={1}>
                {[...Array(5)].map((_, i) => <Skeleton key={i} variant="circular" width={40} height={40}/>)}
            </Stack>
        </Card>
    );
}

function NothingPlaying() {
    return (
        <Box sx={{textAlign: 'center', py: 3}}>
            <MusicNote sx={{fontSize: 48, color: 'rgba(255,255,255,0.15)', mb: 1}}/>
            <Typography variant="body2" sx={{color: 'rgba(255,255,255,0.4)'}}>
                Nothing playing right now
            </Typography>
        </Box>
    );
}

// ── Main Component ─────────────────────────────────────────────────────────

export default function SpotifyPlayer() {
    const {
        authenticated, player, devices, loading,
        login, play, pause, next, previous, seek, setVolume, transferTo,
    } = useSpotify();

    // Local optimistic progress bar state
    const [progress, setProgress] = useState(0);
    const [volume, setVolumeState] = useState(50);
    const [muted, setMuted] = useState(false);
    const [deviceAnchor, setDeviceAnchor] = useState(null);

    const isPlaying = player?.is_playing ?? false;
    const track = player?.item;
    const duration = track?.duration_ms ?? 0;
    const device = player?.device;

    // Sync progress from server state
    useEffect(() => {
        if (player?.progress_ms != null) setProgress(player.progress_ms);
    }, [player?.progress_ms]);

    // Tick progress forward locally while playing
    useEffect(() => {
        if (!isPlaying) return;
        const id = setInterval(() => setProgress(p => Math.min(p + 1000, duration)), 1000);
        return () => clearInterval(id);
    }, [isPlaying, duration]);

    // Sync volume from device
    useEffect(() => {
        if (device?.volume_percent != null) setVolumeState(device.volume_percent);
    }, [device?.volume_percent]);

    const handleSeek = (_, value) => {
        setProgress(value);
        seek(value);
    };

    const handleVolume = (_, value) => {
        setVolumeState(value);
        setMuted(value === 0);
        setVolume(value);
    };

    const toggleMute = () => {
        const next = muted ? 50 : 0;
        setMuted(!muted);
        setVolumeState(next);
        setVolume(next);
    };

    const albumArt = track?.album?.images?.[0]?.url;
    const artists = track?.artists?.map(a => a.name).join(', ');
    const trackName = track?.name;
    const albumName = track?.album?.name;
    const spotifyUrl = track?.external_urls?.spotify;

    // ── Render ───────────────────────────────────────────────────────────────

    if (loading) return <LoadingSkeleton/>;
    if (!authenticated) return <ConnectPrompt onLogin={login}/>;

    return (
        <Card
            sx={{
                width: 360,
                background: 'linear-gradient(160deg, #141414 0%, #0d0d0d 100%)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: 3,
                overflow: 'hidden',
                boxShadow: '0 24px 60px rgba(0,0,0,0.6)',
            }}
        >
            {/* Album art */}
            <Box sx={{position: 'relative'}}>
                {albumArt ? (
                    <CardMedia
                        component="img"
                        image={albumArt}
                        alt={albumName}
                        sx={{height: 220, objectFit: 'cover'}}
                    />
                ) : (
                    <Box
                        sx={{
                            height: 220,
                            background: 'linear-gradient(135deg, #1a1a2e, #16213e)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                        }}
                    >
                        <MusicNote sx={{fontSize: 80, color: 'rgba(255,255,255,0.1)'}}/>
                    </Box>
                )}

                {/* Playing badge */}
                {isPlaying && (
                    <Chip
                        label="● PLAYING"
                        size="small"
                        sx={{
                            position: 'absolute',
                            top: 10,
                            left: 10,
                            background: alpha('#1DB954', 0.9),
                            color: '#000',
                            fontWeight: 800,
                            fontSize: 10,
                            letterSpacing: 1,
                            height: 22,
                        }}
                    />
                )}

                {/* Open in Spotify */}
                {spotifyUrl && (
                    <Tooltip title="Open in Spotify">
                        <IconButton
                            size="small"
                            component="a"
                            href={spotifyUrl}
                            target="_blank"
                            sx={{
                                position: 'absolute',
                                top: 8,
                                right: 8,
                                background: 'rgba(0,0,0,0.5)',
                                color: 'rgba(255,255,255,0.7)',
                                '&:hover': {background: 'rgba(0,0,0,0.8)', color: '#fff'},
                            }}
                        >
                            <OpenInNew sx={{fontSize: 16}}/>
                        </IconButton>
                    </Tooltip>
                )}
            </Box>

            <Box sx={{p: 2.5}}>
                {!track ? (
                    <NothingPlaying/>
                ) : (
                    <>
                        {/* Track info */}
                        <Stack direction="row" alignItems="flex-start" justifyContent="space-between" mb={0.5}>
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <Typography
                                    variant="subtitle1"
                                    sx={{
                                        color: '#fff',
                                        fontWeight: 700,
                                        fontSize: 16,
                                        whiteSpace: 'nowrap',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                    }}
                                >
                                    {trackName}
                                </Typography>
                                <Typography
                                    variant="body2"
                                    sx={{
                                        color: 'rgba(255,255,255,0.55)',
                                        whiteSpace: 'nowrap',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        fontSize: 13,
                                    }}
                                >
                                    {artists}
                                </Typography>
                            </Box>
                        </Stack>

                        {/* Progress bar */}
                        <Box sx={{mt: 1.5, mb: 0.5}}>
                            <Slider
                                size="small"
                                value={progress}
                                min={0}
                                max={duration || 1}
                                onChange={handleSeek}
                                sx={{
                                    color: '#1DB954',
                                    height: 4,
                                    padding: '10px 0',
                                    '& .MuiSlider-thumb': {
                                        width: 12,
                                        height: 12,
                                        opacity: 0,
                                        transition: 'opacity 0.15s',
                                        '&:hover, &.Mui-active': {opacity: 1},
                                    },
                                    '& .MuiSlider-rail': {background: 'rgba(255,255,255,0.15)'},
                                    '&:hover .MuiSlider-thumb': {opacity: 1},
                                }}
                            />
                            <Stack direction="row" justifyContent="space-between" mt={-0.5}>
                                <Typography variant="caption" sx={{color: 'rgba(255,255,255,0.4)', fontSize: 11}}>
                                    {msToTime(progress)}
                                </Typography>
                                <Typography variant="caption" sx={{color: 'rgba(255,255,255,0.4)', fontSize: 11}}>
                                    {msToTime(duration)}
                                </Typography>
                            </Stack>
                        </Box>

                        {/* Playback controls */}
                        <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5} mt={0.5}>
                            <Tooltip title="Previous">
                                <IconButton onClick={previous}
                                            sx={{color: 'rgba(255,255,255,0.7)', '&:hover': {color: '#fff'}}}>
                                    <SkipPrevious/>
                                </IconButton>
                            </Tooltip>

                            <IconButton
                                onClick={isPlaying ? pause : play}
                                sx={{
                                    width: 48,
                                    height: 48,
                                    background: '#1DB954',
                                    color: '#000',
                                    '&:hover': {background: '#1ed760', transform: 'scale(1.05)'},
                                    transition: 'all 0.15s',
                                }}
                            >
                                {isPlaying ? <Pause sx={{fontSize: 24}}/> : <PlayArrow sx={{fontSize: 24}}/>}
                            </IconButton>

                            <Tooltip title="Next">
                                <IconButton onClick={next}
                                            sx={{color: 'rgba(255,255,255,0.7)', '&:hover': {color: '#fff'}}}>
                                    <SkipNext/>
                                </IconButton>
                            </Tooltip>
                        </Stack>

                        {/* Volume + device switcher */}
                        <Stack direction="row" alignItems="center" spacing={1} mt={1.5}>
                            <Tooltip title={muted ? 'Unmute' : 'Mute'}>
                                <IconButton size="small" onClick={toggleMute} sx={{color: 'rgba(255,255,255,0.5)'}}>
                                    {muted || volume === 0 ? <VolumeOff fontSize="small"/> :
                                        <VolumeUp fontSize="small"/>}
                                </IconButton>
                            </Tooltip>

                            <Slider
                                size="small"
                                value={muted ? 0 : volume}
                                min={0}
                                max={100}
                                onChange={handleVolume}
                                sx={{
                                    flex: 1,
                                    color: 'rgba(255,255,255,0.6)',
                                    height: 3,
                                    '& .MuiSlider-thumb': {width: 10, height: 10},
                                    '& .MuiSlider-rail': {background: 'rgba(255,255,255,0.15)'},
                                }}
                            />

                            {/* Device switcher */}
                            <Tooltip title="Switch device">
                                <IconButton
                                    size="small"
                                    onClick={e => setDeviceAnchor(e.currentTarget)}
                                    sx={{
                                        color: device ? '#1DB954' : 'rgba(255,255,255,0.5)',
                                        '&:hover': {color: '#1DB954'},
                                    }}
                                >
                                    <DevicesOther fontSize="small"/>
                                </IconButton>
                            </Tooltip>
                        </Stack>

                        {/* Active device label */}
                        {device && (
                            <Typography
                                variant="caption"
                                sx={{
                                    display: 'block',
                                    textAlign: 'center',
                                    color: 'rgba(255,255,255,0.35)',
                                    mt: 1,
                                    fontSize: 11,
                                }}
                            >
                                {deviceIcon(device.type)} {device.name}
                            </Typography>
                        )}
                    </>
                )}
            </Box>

            {/* Device Menu */}
            <Menu
                anchorEl={deviceAnchor}
                open={Boolean(deviceAnchor)}
                onClose={() => setDeviceAnchor(null)}
                PaperProps={{
                    sx: {
                        background: '#282828',
                        border: '1px solid rgba(255,255,255,0.1)',
                        borderRadius: 2,
                        minWidth: 220,
                    },
                }}
            >
                <Typography
                    variant="caption"
                    sx={{
                        px: 2,
                        py: 0.75,
                        display: 'block',
                        color: 'rgba(255,255,255,0.4)',
                        fontWeight: 700,
                        letterSpacing: 1
                    }}
                >
                    SELECT A DEVICE
                </Typography>

                {devices.length === 0 && (
                    <MenuItem disabled>
                        <ListItemText primary="No devices found"
                                      sx={{'& span': {color: 'rgba(255,255,255,0.4)', fontSize: 13}}}/>
                    </MenuItem>
                )}

                {devices.map(d => (
                    <MenuItem
                        key={d.id}
                        selected={d.id === device?.id}
                        onClick={() => {
                            transferTo(d.id);
                            setDeviceAnchor(null);
                        }}
                        sx={{
                            '&.Mui-selected': {background: 'rgba(29,185,84,0.15)'},
                            '&:hover': {background: 'rgba(255,255,255,0.05)'},
                        }}
                    >
                        <ListItemIcon sx={{minWidth: 36, fontSize: 18}}>
                            {deviceIcon(d.type)}
                        </ListItemIcon>
                        <ListItemText
                            primary={d.name}
                            secondary={d.type}
                            sx={{
                                '& .MuiListItemText-primary': {
                                    color: d.id === device?.id ? '#1DB954' : '#fff',
                                    fontSize: 14
                                },
                                '& .MuiListItemText-secondary': {color: 'rgba(255,255,255,0.4)', fontSize: 12},
                            }}
                        />
                        {d.id === device?.id && (
                            <Typography variant="caption" sx={{color: '#1DB954', fontWeight: 700}}>●</Typography>
                        )}
                    </MenuItem>
                ))}
            </Menu>
        </Card>
    );
}