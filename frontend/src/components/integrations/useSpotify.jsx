import {useCallback, useEffect, useRef, useState} from 'react';
import {
    getSpotifyDevices,
    getSpotifyPlayer,
    getSpotifyStatus,
    spotifyNext,
    spotifyPause,
    spotifyPlay,
    spotifyPrevious,
    spotifySeek,
    spotifySetVolume,
    spotifyTransfer,
} from '../../services/apis.jsx';

const POLL_MS = 3000; // refresh player state every 3 seconds

export function useSpotify() {
    const [authenticated, setAuthenticated] = useState(false);
    const [player, setPlayer] = useState(null);
    const [devices, setDevices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const pollRef = useRef(null);

    // ── Bootstrap ──────────────────────────────────────────────────────────────
    useEffect(() => {
        // Handle ?spotify_connected=true after OAuth redirect
        const params = new URLSearchParams(window.location.search);
        if (params.get('spotify_connected') === 'true') {
            window.history.replaceState({}, '', window.location.pathname);
        }

        getSpotifyStatus()
            .then(({authenticated}) => {
                setAuthenticated(authenticated);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);

    // ── Poll player state ──────────────────────────────────────────────────────
    const fetchPlayer = useCallback(async () => {
        try {
            const [playerData, devicesData] = await Promise.all([
                getSpotifyPlayer(),
                getSpotifyDevices(),
            ]);
            setPlayer(playerData);
            setDevices(devicesData?.devices ?? []);
            setError(null);
        } catch (e) {
            setError(e.message);
        }
    }, []);

    useEffect(() => {
        if (!authenticated) return;
        fetchPlayer();
        pollRef.current = setInterval(fetchPlayer, POLL_MS);
        return () => clearInterval(pollRef.current);
    }, [authenticated, fetchPlayer]);

    // ── Controls ───────────────────────────────────────────────────────────────
    const activeDeviceId = player?.device?.id ?? null;

    const withRefresh = (fn) => async (...args) => {
        try {
            await fn(...args);
            // Give Spotify a beat before polling
            await new Promise(r => setTimeout(r, 500));
            await fetchPlayer();
        } catch (e) {
            setError(e.message);
        }
    };

    const handleLogin = () => {
        window.location.href = 'https://automata.realsubhamgupta.in/api/v1/spotify/login';
    };

    return {
        authenticated,
        player,
        devices,
        loading,
        error,
        login: handleLogin,
        play: withRefresh(() => spotifyPlay(activeDeviceId)),
        pause: withRefresh(() => spotifyPause(activeDeviceId)),
        next: withRefresh(() => spotifyNext(activeDeviceId)),
        previous: withRefresh(() => spotifyPrevious(activeDeviceId)),
        seek: withRefresh((ms) => spotifySeek(ms)),
        setVolume: withRefresh((pct) => spotifySetVolume(pct)),
        transferTo: withRefresh((id) => spotifyTransfer(id)),
        refresh: fetchPlayer,
    };
}