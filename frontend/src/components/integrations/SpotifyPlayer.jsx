import {useEffect, useRef, useState} from 'react';
import {alpha, Box, IconButton, Slider, Stack, Tooltip, Typography,} from '@mui/material';
import {
    DevicesOther,
    MusicNote,
    Pause,
    PlayArrow,
    SkipNext,
    SkipPrevious,
    VolumeOff,
    VolumeUp,
} from '@mui/icons-material';
import {useSpotify} from './useSpotify.jsx';


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
        <Box
            sx={{
                width: 300,
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
            <Box sx={{
                width: 56, height: 56, borderRadius: '50%', background: '#1DB954',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
                <MusicNote sx={{color: '#000', fontSize: 28}}/>
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
                    mt: 1, px: 3, py: 1.5, borderRadius: 50, border: 'none',
                    background: '#1DB954', color: '#000', fontWeight: 700,
                    fontSize: 14, cursor: 'pointer',
                    '&:hover': {background: '#1ed760'},
                    transition: 'background 0.15s',
                }}
            >
                Connect with Spotify
            </Box>
        </Box>
    );
}

function LoadingSkeleton() {
    return (
        <Box sx={{
            width: 300, height: 200, borderRadius: 3,
            background: 'linear-gradient(135deg, #1a1a1a, #111)',
            border: '1px solid rgba(255,255,255,0.06)',
        }}/>
    );
}

function NothingPlaying() {
    return (
        <Box sx={{textAlign: 'center', py: 2}}>
            <MusicNote sx={{fontSize: 36, color: 'rgba(255,255,255,0.15)', mb: 0.5}}/>
            <Typography variant="caption" sx={{color: 'rgba(255,255,255,0.35)', display: 'block'}}>
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

    const [progress, setProgress] = useState(0);
    const [volume, setVolumeState] = useState(50);
    const [muted, setMuted] = useState(false);
    const [deviceMenuOpen, setDeviceMenuOpen] = useState(false);

    // Persist the last known track so we can show it when playback stops
    const lastTrackRef = useRef(null);
    const lastProgressRef = useRef(0);

    const isPlaying = player?.is_playing ?? false;
    const track = player?.item;
    const duration = track?.duration_ms ?? lastTrackRef.current?.duration_ms ?? 0;
    const device = player?.device;
    const albumArt = track?.album?.images?.[0]?.url ?? lastTrackRef.current?.album?.images?.[0]?.url;
    const artists = (track ?? lastTrackRef.current)?.artists?.map(a => a.name).join(', ');

    // Keep lastTrack up to date whenever we have a real track
    useEffect(() => {
        if (track) {
            lastTrackRef.current = track;
        }
    }, [track]);

    // Sync progress from server
    useEffect(() => {
        if (player?.progress_ms != null) {
            setProgress(player.progress_ms);
            lastProgressRef.current = player.progress_ms;
        }
    }, [player?.progress_ms]);

    // Tick progress locally while playing
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

    // The track to render — live or last known
    const displayTrack = track ?? lastTrackRef.current;
    const isPaused = !isPlaying && !!displayTrack;

    // ── Render ───────────────────────────────────────────────────────────────

    if (loading) return <LoadingSkeleton/>;
    if (!authenticated) return <ConnectPrompt onLogin={login}/>;

    return (
        <Box
            sx={{
                width: "100%",
                height: 220,
                borderRadius: "12px",
                overflow: 'hidden',
                position: 'relative',
                flexShrink: 0,
            }}
        >
            {/* ── Full-bleed blurred album art background ── */}
            <Box
                sx={{
                    position: 'absolute',
                    inset: -10,
                    backgroundImage: albumArt ? `url(${albumArt})` : undefined,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                    backgroundColor: albumArt ? undefined : '#1a1a1a',
                    filter: 'blur(18px)',
                    transform: 'scale(1.15)',
                    transition: 'background-image 0.4s ease',
                    // Dim the background when paused
                    opacity: isPaused ? 0.45 : 1,
                }}
            />

            {/* ── Gradient overlay ── */}
            <Box sx={{
                position: 'absolute',
                inset: 0,
                background: 'linear-gradient(to bottom, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.65) 50%, rgba(0,0,0,0.88) 100%)',
            }}/>

            {/* ── Foreground content ── */}
            <Stack
                sx={{
                    position: 'relative',
                    zIndex: 2,
                    height: '100%',
                    p: '12px 14px 10px',
                    // Slightly fade content too when paused
                    opacity: isPaused ? 0.75 : 1,
                    transition: 'opacity 0.3s ease',
                }}
            >
                {!displayTrack ? (
                    <NothingPlaying/>
                ) : (
                    <>
                        {/* Top row: art thumbnail + track info + device icon */}
                        <Stack direction="row" alignItems="center" gap="10px" sx={{flex: 1, minHeight: 0}}>

                            {/* Album art thumbnail */}
                            <Box
                                sx={{
                                    width: 52, height: 52, flexShrink: 0,
                                    borderRadius: 1.5, overflow: 'hidden',
                                    boxShadow: '0 4px 16px rgba(0,0,0,0.6)',
                                    background: '#111',
                                    // Greyscale when paused
                                    filter: isPaused ? 'grayscale(60%)' : 'none',
                                    transition: 'filter 0.3s ease',
                                }}
                            >
                                {albumArt
                                    ? <Box component="img" src={albumArt} alt={displayTrack.album?.name}
                                           sx={{width: '100%', height: '100%', objectFit: 'cover'}}/>
                                    : <Box sx={{
                                        width: '100%', height: '100%',
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    }}>
                                        <MusicNote sx={{fontSize: 20, color: 'rgba(255,255,255,0.2)'}}/>
                                    </Box>
                                }
                            </Box>

                            {/* Track name + artist */}
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <Stack direction="row" alignItems="center" gap="5px" mb="1px">
                                    {isPlaying ? (
                                        /* Green pulsing dot — playing */
                                        <Box sx={{
                                            width: 5, height: 5, borderRadius: '50%',
                                            background: '#1DB954', flexShrink: 0,
                                            '@keyframes pulse': {
                                                '0%, 100%': {opacity: 1},
                                                '50%': {opacity: 0.3},
                                            },
                                            animation: 'pulse 1.5s ease-in-out infinite',
                                        }}/>
                                    ) : (
                                        /* Grey static dot + "Last played" label — paused */
                                        <Stack direction="row" alignItems="center" gap="4px" flexShrink={0}>
                                            <Box sx={{
                                                width: 5, height: 5, borderRadius: '50%',
                                                background: 'rgba(255,255,255,0.25)', flexShrink: 0,
                                            }}/>
                                            <Typography sx={{
                                                fontSize: 9, fontWeight: 600, letterSpacing: 0.6,
                                                color: 'rgba(255,255,255,0.3)',
                                                textTransform: 'uppercase',
                                            }}>
                                                Last played
                                            </Typography>
                                        </Stack>
                                    )}
                                </Stack>
                                <Typography
                                    sx={{
                                        fontSize: 13, fontWeight: 500, color: '#fff',
                                        whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                    }}
                                >
                                    {displayTrack.name}
                                </Typography>
                                <Typography
                                    sx={{
                                        fontSize: 11, color: 'rgba(255,255,255,0.5)',
                                        whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                    }}
                                >
                                    {artists}
                                </Typography>
                            </Box>

                            {/* Device switcher icon */}
                            <Tooltip title={deviceMenuOpen ? '' : 'Switch device'}>
                                <IconButton
                                    size="small"
                                    onClick={() => setDeviceMenuOpen(v => !v)}
                                    sx={{
                                        color: device ? '#1DB954' : 'rgba(255,255,255,0.4)',
                                        p: 0, flexShrink: 0,
                                        '&:hover': {color: '#fff'},
                                    }}
                                >
                                    <DevicesOther sx={{fontSize: 16}}/>
                                </IconButton>
                            </Tooltip>
                        </Stack>

                        {/* Device list (inline, no Menu/Portal) */}
                        {deviceMenuOpen && (
                            <Box
                                sx={{
                                    position: 'absolute',
                                    top: 8, right: 8,
                                    zIndex: 10,
                                    background: '#1e1e1e',
                                    border: '1px solid rgba(255,255,255,0.1)',
                                    borderRadius: 2,
                                    minWidth: 180,
                                    py: 0.5,
                                    boxShadow: '0 4px 24px rgba(0,0,0,0.6)',
                                }}
                            >
                                <Typography sx={{
                                    px: 1.5, py: 0.5, fontSize: 9, fontWeight: 700,
                                    letterSpacing: 1, color: 'rgba(255,255,255,0.35)',
                                }}>
                                    SELECT A DEVICE
                                </Typography>
                                {devices.length === 0 && (
                                    <Typography sx={{px: 1.5, py: 0.5, fontSize: 12, color: 'rgba(255,255,255,0.35)'}}>
                                        No devices found
                                    </Typography>
                                )}
                                {devices.map(d => (
                                    <Stack
                                        key={d.id}
                                        direction="row"
                                        alignItems="center"
                                        gap={1}
                                        onClick={() => {
                                            transferTo(d.id);
                                            setDeviceMenuOpen(false);
                                        }}
                                        sx={{
                                            px: 1.5, py: 0.75, cursor: 'pointer',
                                            background: d.id === device?.id ? alpha('#1DB954', 0.12) : 'transparent',
                                            '&:hover': {background: 'rgba(255,255,255,0.06)'},
                                        }}
                                    >
                                        <Box sx={{fontSize: 14}}>{deviceIcon(d.type)}</Box>
                                        <Box sx={{flex: 1, minWidth: 0}}>
                                            <Typography sx={{
                                                fontSize: 12,
                                                color: d.id === device?.id ? '#1DB954' : '#fff',
                                                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                            }}>
                                                {d.name}
                                            </Typography>
                                        </Box>
                                        {d.id === device?.id && (
                                            <Box sx={{
                                                width: 6, height: 6, borderRadius: '50%',
                                                background: '#1DB954', flexShrink: 0,
                                            }}/>
                                        )}
                                    </Stack>
                                ))}
                            </Box>
                        )}

                        {/* Seekbar */}
                        <Box sx={{my: '6px'}}>
                            <Slider
                                size="small"
                                value={progress}
                                min={0}
                                max={duration || 1}
                                onChange={handleSeek}
                                disabled={isPaused}
                                sx={{
                                    color: isPaused ? 'rgba(255,255,255,0.25)' : '#1DB954',
                                    height: 3,
                                    p: '8px 0',
                                    transition: 'color 0.3s ease',
                                    '& .MuiSlider-thumb': {
                                        width: 9, height: 9,
                                        opacity: 0,
                                        transition: 'opacity 0.15s',
                                        '&:hover, &.Mui-active': {opacity: isPaused ? 0 : 1},
                                    },
                                    '&:hover .MuiSlider-thumb': {opacity: isPaused ? 0 : 1},
                                    '& .MuiSlider-rail': {background: 'rgba(255,255,255,0.15)'},
                                }}
                            />
                            <Stack direction="row" justifyContent="space-between" mt="-4px">
                                <Typography sx={{fontSize: 9, color: 'rgba(255,255,255,0.35)'}}>
                                    {msToTime(progress)}
                                </Typography>
                                <Typography sx={{fontSize: 9, color: 'rgba(255,255,255,0.35)'}}>
                                    {msToTime(duration)}
                                </Typography>
                            </Stack>
                        </Box>

                        {/* Controls row: mute + vol · prev/play/next · device label */}
                        <Stack direction="row" alignItems="center" justifyContent="center" gap="6px">

                            {/* Mute */}
                            <Tooltip title={muted ? 'Unmute' : 'Mute'}>
                                <IconButton
                                    size="small"
                                    onClick={toggleMute}
                                    sx={{color: 'rgba(255,255,255,0.4)', p: 0, '&:hover': {color: '#fff'}}}
                                >
                                    {muted || volume === 0
                                        ? <VolumeOff sx={{fontSize: 14}}/>
                                        : <VolumeUp sx={{fontSize: 14}}/>
                                    }
                                </IconButton>
                            </Tooltip>

                            {/* Volume slider */}
                            <Slider
                                size="small"
                                value={muted ? 0 : volume}
                                min={0}
                                max={100}
                                onChange={handleVolume}
                                sx={{
                                    width: 54,
                                    color: 'rgba(255,255,255,0.4)',
                                    height: 3,
                                    p: '8px 0',
                                    '& .MuiSlider-thumb': {width: 8, height: 8},
                                    '& .MuiSlider-rail': {background: 'rgba(255,255,255,0.15)'},
                                }}
                            />

                            {/* Prev / Play / Next */}
                            <Stack direction="row" alignItems="center" gap="2px" mx="8px">
                                <Tooltip title="Previous">
                                    <IconButton
                                        onClick={previous}
                                        sx={{
                                            width: 28, height: 28,
                                            background: 'rgba(255,255,255,0.08)',
                                            color: 'rgba(255,255,255,0.75)',
                                            '&:hover': {background: 'rgba(255,255,255,0.18)', color: '#fff'},
                                        }}
                                    >
                                        <SkipPrevious sx={{fontSize: 14}}/>
                                    </IconButton>
                                </Tooltip>

                                <IconButton
                                    onClick={isPlaying ? pause : play}
                                    sx={{
                                        width: 34, height: 34, mx: '2px',
                                        background: isPaused ? 'rgba(255,255,255,0.15)' : '#1DB954',
                                        color: isPaused ? 'rgba(255,255,255,0.7)' : '#000',
                                        '&:hover': {
                                            background: isPaused ? 'rgba(255,255,255,0.22)' : '#1ed760',
                                            transform: 'scale(1.07)',
                                        },
                                        transition: 'all 0.2s ease',
                                    }}
                                >
                                    {isPlaying
                                        ? <Pause sx={{fontSize: 16}}/>
                                        : <PlayArrow sx={{fontSize: 16}}/>
                                    }
                                </IconButton>

                                <Tooltip title="Next">
                                    <IconButton
                                        onClick={next}
                                        sx={{
                                            width: 28, height: 28,
                                            background: 'rgba(255,255,255,0.08)',
                                            color: 'rgba(255,255,255,0.75)',
                                            '&:hover': {background: 'rgba(255,255,255,0.18)', color: '#fff'},
                                        }}
                                    >
                                        <SkipNext sx={{fontSize: 14}}/>
                                    </IconButton>
                                </Tooltip>
                            </Stack>

                            {/* Active device label */}
                            <Typography
                                sx={{
                                    fontSize: 9,
                                    color: 'rgba(255,255,255,0.3)',
                                    maxWidth: 54,
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    textAlign: 'right',
                                }}
                            >
                                {device ? `${deviceIcon(device.type)} ${device.name}` : ''}
                            </Typography>

                        </Stack>
                    </>
                )}
            </Stack>
        </Box>
    );
}