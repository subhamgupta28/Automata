const BASE = '/api/spotify';

async function req(method, path, params = {}) {
    const url = new URL(BASE + path, window.location.origin);
    if (method === 'GET' || method === 'PUT' || method === 'POST') {
        Object.entries(params).forEach(([k, v]) => v != null && url.searchParams.set(k, v));
    }

    const res = await fetch(url.toString(), {
        method,
        credentials: 'include',
    });

    if (res.status === 204 || res.status === 202) return null;
    if (!res.ok) throw new Error(`Spotify API error: ${res.status}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

export const spotify = {
    status: () => req('GET', '/status'),
    login: () => {
        window.location.href = BASE + '/login';
    },
    getPlayer: () => req('GET', '/player'),
    getDevices: () => req('GET', '/devices'),
    play: (deviceId) => req('PUT', '/play', {deviceId}),
    pause: (deviceId) => req('PUT', '/pause', {deviceId}),
    next: (deviceId) => req('POST', '/next', {deviceId}),
    previous: (deviceId) => req('POST', '/previous', {deviceId}),
    transfer: (deviceId) => req('PUT', '/transfer', {deviceId}),
    seek: (positionMs) => req('PUT', '/seek', {positionMs}),
    setVolume: (percent) => req('PUT', '/volume', {percent}),
};