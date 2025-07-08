import React, { useEffect, useState } from 'react';

const CLIENT_ID = 'd1ee33b61aa44a61a3b81048a49940dc';
const REDIRECT_URI = 'https://automata.realsubhamgupta.in';
const SCOPES = ['user-read-playback-state'];
const SPOTIFY_AUTHORIZE_ENDPOINT = 'https://accounts.spotify.com/authorize';
const SPOTIFY_TOKEN_ENDPOINT = 'https://accounts.spotify.com/api/token';

function generateRandomString(length) {
    const array = new Uint8Array(length);
    window.crypto.getRandomValues(array);
    return btoa(String.fromCharCode(...array))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '')
        .substring(0, length);
}

async function generateCodeChallenge(verifier) {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    const base64 = btoa(String.fromCharCode(...new Uint8Array(digest)))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
    return base64;
}

export default function SpotifyPlayer() {
    const [accessToken, setAccessToken] = useState(null);
    const [devices, setDevices] = useState([]);
    const [error, setError] = useState(null);

    // Handle redirect with ?code
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        const storedVerifier = sessionStorage.getItem('pkce_code_verifier');

        if (code && storedVerifier) {
            const exchangeToken = async () => {
                try {
                    const body = new URLSearchParams({
                        client_id: CLIENT_ID,
                        grant_type: 'authorization_code',
                        code,
                        redirect_uri: REDIRECT_URI,
                        code_verifier: storedVerifier,
                    });

                    const res = await fetch(SPOTIFY_TOKEN_ENDPOINT, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                        },
                        body: body.toString(),
                    });

                    const data = await res.json();
                    if (data.access_token) {
                        setAccessToken(data.access_token);
                        window.history.replaceState({}, document.title, '/'); // clean URL
                    } else {
                        setError('Token exchange failed');
                    }
                } catch (e) {
                    setError('Token exchange error');
                }
            };

            exchangeToken();
        }
    }, []);

    // Fetch devices
    useEffect(() => {
        if (!accessToken) return;

        const fetchDevices = async () => {
            try {
                const res = await fetch('https://api.spotify.com/v1/me/player/devices', {
                    headers: {
                        Authorization: `Bearer ${accessToken}`,
                    },
                });

                const data = await res.json();
                setDevices(data.devices);
            } catch (e) {
                setError('Failed to fetch devices');
            }
        };

        fetchDevices();
    }, [accessToken]);

    // Start login
    const handleLogin = async () => {
        const verifier = generateRandomString(128);
        const challenge = await generateCodeChallenge(verifier);
        sessionStorage.setItem('pkce_code_verifier', verifier);

        const params = new URLSearchParams({
            response_type: 'code',
            client_id: CLIENT_ID,
            redirect_uri: REDIRECT_URI,
            scope: SCOPES.join(' '),
            code_challenge_method: 'S256',
            code_challenge: challenge,
        });

        window.location.href = `${SPOTIFY_AUTHORIZE_ENDPOINT}?${params.toString()}`;
    };

    return (
        <div className="p-4 font-sans">
            <h1 className="text-2xl font-bold mb-4">Spotify Devices</h1>
            {!accessToken ? (
                <button
                    onClick={handleLogin}
                    className="bg-green-600 text-white px-4 py-2 rounded"
                >
                    Login with Spotify
                </button>
            ) : error ? (
                <p className="text-red-500">{error}</p>
            ) : devices.length === 0 ? (
                <p>No devices found.</p>
            ) : (
                <ul className="space-y-2">
                    {devices.map((device) => (
                        <li key={device.id} className="border p-2 rounded shadow">
                            <strong>{device.name}</strong> — {device.type}
                            {device.is_active && <span className="ml-2 text-green-600">(Active)</span>}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
