import {useEffect, useState} from "react";
import api from "./CustomAxios.jsx";


// const BROWSER_URL = window.location.href;
// const BASE_URL = apiUrl+'api/v1/';
// const BASE_URL = 'http://localhost:8080/api/v1/';


export const snoozeAutomation = async (id, min) => {
    const response = await api.post(`action/automation/${id}/snooze?minutes=${min}`, {}, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary

            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const timedDisableAuto = async (id, min) => {
    const response = await api.post(`action/automation/${id}/disable-timed?minutes=${min}`, {}, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary

            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const resumeAutomation = async (id) => {
    const response = await api.post(`action/automation/${id}/resume`, {}, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary

            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const getSnoozeStatus = async (id) => {
    const response = await api.get(`action/automation/${id}/snooze-status`, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getActions = async () => {
    const response = await api.get("action/getAction", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}

export const updateAttribute = async (deviceId, attribute, isShow) => {
    const response = await api.get("main/updateAttribute/" + deviceId + "/" + attribute + "/" + isShow, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const sendBrowserState = async (state) => {
    const response = await api.post("action/browserState/" + state, {}, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary

            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const getWiFiDetails = async () => {
    const response = await api.post("main/wifiList", {}, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary

            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const updateVirtualDevicePosition = async (vid, x, y, width, height) => {
    const response = await api.get("virtual/updatePosition/" + vid + "/" + x + "/" + y + "/" + width + "/" + height, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getEnergyAnalytics = async (vid, param) => {
    const response = await api.get("virtual/energyChart/" + vid + "/" + param, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getEnergyStats = async (deviceId) => {
    const response = await api.get("virtual/energyStats/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const updatePosition = async (deviceId, x, y) => {
    const response = await api.get("main/updatePosition/" + deviceId + "/" + x + "/" + y, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getDashboardDevices = async () => {
    const response = await api.get("main/dashboard", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getDevices = async () => {
    const response = await api.get("main/devices", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getDetailChartData = async (
    deviceId,
    range = "day",
    params = {}
) => {
    let url = `main/chartDetail/${deviceId}/${range}`;

    // Handle historical range
    if (range === "history") {
        const query = new URLSearchParams({
            start: params.start,
            end: params.end,
        }).toString();

        url += `?${query}`;
    }

    const response = await api.get(url, {
        headers: {
            "Content-Type": "application/json",
        },
    });

    return response.data;
};
export const getPieChartData = async (deviceId) => {
    const response = await api.get("main/pieChart/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const getChartData = async (deviceId, attribute) => {
    const response = await api.get("main/chart/" + deviceId + "/" + attribute, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const updateAttrCharts = async (deviceId, attribute, isVisible) => {
    const response = await api.get("main/attrCharts/" + deviceId + "/" + attribute + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const updateShowVirtualDevice = async (vid, isVisible) => {
    const response = await api.get("virtual/showVirtualDevice/" + vid + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const updateShowCharts = async (deviceId, isVisible) => {
    const response = await api.get("main/showCharts/" + deviceId + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const updateShowInDashboard = async (deviceId, isVisible) => {
    const response = await api.get("main/showInDashboard/" + deviceId + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const disableAutomation = async (id, isEnabled) => {
    const response = await api.get("action/disable/" + id + "/" + isEnabled, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const refreshDeviceById = async (deviceId) => {
    const response = await api.get("main/update/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}

export const getRecentDeviceData = async (deviceIds) => {
    const params = new URLSearchParams();
    deviceIds.forEach(id => params.append("deviceIds", id));
    const response = await api.get(`virtual/recentData?${params.toString()}`, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getVirtualDeviceList = async () => {
    const response = await api.get("virtual/deviceList", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const saveVirtualDevice = async (payload) => {
    const response = await api.post("virtual/create", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const sendAction = async (deviceId, payload, deviceType) => {
    const response = await api.post("action/sendAction/" + deviceId + "/" + deviceType, payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const saveWLEDDevices = async (payload) => {
    const response = await api.post("/utils/wledDevices", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const saveWiFiList = async (payload) => {
    const response = await api.post("main/saveWiFiList", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const rebootAllDevices = async () => {
    const response = await api.get("action/rebootAllDevices", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const notificationAction = async (action, payload) => {
    const response = await api.post("utils/action/" + action, payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}

export const getAutomationDetail = async (id) => {
    const response = await api.get("action/getAutomationDetail/" + id, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const validateAutomation = async (payload) => {
    const response = await api.post("action/validate", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const saveAutomationDetail = async (payload) => {
    const response = await api.post("action/saveAutomationDetail", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getLastData = async (deviceId) => {
    const response = await api.get("main/lastData/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}
export const getMainNodePos = async () => {
    const response = await api.get("main/mainNodePos", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}

export const getNotifications = () => {
    return useGetFetch("utils/notifications");
}

export const getDataByDeviceId = async (deviceId) => {
    return useGetFetch('main/data/' + deviceId);
}

export const getServerTime = async () => {
    const response = await api.get('main/time', {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization

        },
    });
    return response.data;
}


export const getLastDataByDeviceId = async (deviceId) => {
    const response = await api.get('main/lastData/' + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const getAutomationAnalytics = async (automationId, daysBack = 7) => {
    const response = await api.get(`action/${automationId}/analytics?daysBack=${daysBack}`, {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: 120000, // 2 minute timeout
    });
    return response.data;
}

export const getAutomationAnalyticsOverview = async (daysBack = 7) => {
    const response = await api.get(`action/analytics/overview?daysBack=${daysBack}`, {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: 600000, // 10 minute timeout for large overview requests (increased from 5)
    });
    return response.data;
}

export const getTopPerformingAutomations = async (limit = 5, daysBack = 7) => {
    const response = await api.get(`action/analytics/top-performing?limit=${limit}&daysBack=${daysBack}`, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
}

export const getProblematicAutomations = async (successThreshold = 70, daysBack = 7) => {
    const response = await api.get(`action/analytics/problematic?successThreshold=${successThreshold}&daysBack=${daysBack}`, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
}

function useGetFetch(url) {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        setData(null);
        const fetch = async () => {
            const response = await api.get(url, {
                headers: {
                    'Content-Type': 'application/json', // Specify the content type if necessary
                    // Add any other headers if needed, e.g., Authorization
                },
            })
            setData(response.data)
        }
        fetch();
    }, [url])

    return {data, error}
}


export const signInReq = async (payload) => {
    const response = await api.post("auth/authenticate", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

/**
 * Guest login - authenticates using the guest user account
 * Guest user has read-only access to specific routes
 * @returns {Promise} User object with role: 'guest'
 */
export const guestLoginReq = async () => {
    const response = await api.post("auth/authenticate", {
        email: 'user@automata.local',
        password: '12345678'
    }, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
}

export const registerDevice = async (payload) => {
    const response = await api.post("main/register", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const signUpReq = async (payload) => {
    const response = await api.post("auth/register", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

// Scenes
export const getScenes = async () => {
    const response = await api.get('automations/scenes');
    return response.data;
};

export const saveScene = async (s) => {
    const response = await api.post('automations/scenes', s, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};

export const deleteScene = async (id) => {
    const response = await api.delete(`automations/scenes/${id}`);
    return response.data;
};

export const triggerScene = async (id, by) => {
    const response = await api.post(`automations/scenes/${id}/trigger`, {triggeredBy: by}, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};

export const toggleScene = async (id, en) => {
    const response = await api.patch(`automations/scenes/${id}/toggle?enabled=${en}`);
    return response.data;
};


// A/B Tests
export const getAbTests = async () => {
    const response = await api.get('automations/ab-tests');
    return response.data;
};

export const createAbTest = async (t) => {
    const response = await api.post('automations/ab-tests', t, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};

export const pauseAbTest = async (id) => {
    const response = await api.post(`automations/ab-tests/${id}/pause`);
    return response.data;
};

export const resumeAbTest = async (id) => {
    const response = await api.post(`automations/ab-tests/${id}/resume`);
    return response.data;
};

export const endAbTest = async (id, winner, conclusion) => {
    const response = await api.post(`automations/ab-tests/${id}/end`, {winner, conclusion}, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};

export const getAbDivergences = async (id) => {
    const response = await api.get(`automations/ab-tests/${id}/divergences`);
    return response.data;
};

export const getAbLogs = async (id) => {
    const response = await api.get(`automations/ab-tests/${id}/logs?limit=30`);
    return response.data;
};

export const getAutomationAnalyticsSummary = async () => {
    const response = await api.get(`automations/analytics/summary`);
    return response.data;
};

export const getAutomationAnalyticsV2 = async () => {
    const response = await api.get(`automations/analytics`);
    return response.data;
};
// Versions
export const getVersions = async (aid) => {
    const response = await api.get(`automations/${aid}/versions`);
    return response.data;
};

export const rollback = async (aid, v, user) => {
    const response = await api.post(`automations/${aid}/versions/${v}/rollback`, {user}, {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};


// Management
export const copyAutomation = async (id) => {
    const response = await api.post(`action/${id}/copy`);
    return response.data;
};

export const deleteAutomation = async (id) => {
    const response = await api.delete(`action/${id}`);
    return response.data;
};

export const getAutomations = async () => {
    const response = await api.get("action/getAction");
    return response.data;
};

// ─── Inspector: state + plan ──────────────────────────────────────────────────

export const getAutomationState = async (id) => {
    const response = await api.get(`automations/${id}/state`);
    return response.data;
};

export const getAutomationPlan = async (id) => {
    const response = await api.get(`automations/${id}/plan`);
    return response.data;
};

/**
 * Fetches state and plan in parallel.
 * Returns { state, plan } on success; throws on any non-OK response.
 */
export const getAutomationStateAndPlan = async (id) => {
    const [state, plan] = await Promise.all([
        getAutomationState(id),
        getAutomationPlan(id),
    ]);
    return {state, plan};
};

/**
 * @param {string} id          - automation ID
 * @param {string} action      - e.g. "FORCE_ACTIVE" | "FORCE_IDLE" | "RESET" | "RESET_MEMORY" | "RESET_COALITION"
 */
export const postAutomationOverride = async (id, action) => {
    const response = await api.post(`automations/${id}/override`, {action});
    return response.data; // { success: boolean, message: string }
};

// ─── Snooze ───────────────────────────────────────────────────────────────────

/**
 * @param {string} id      - automation ID
 * @param {number} minutes - snooze duration (1–1440)
 */
export const postAutomationSnooze = async (id, minutes) => {
    const response = await api.post(`automations/${id}/snooze`, {minutes});
    return response.data; // { success: boolean, message: string }
};

export const deleteAutomationSnooze = async (id) => {
    const response = await api.delete(`automations/${id}/snooze`);
    return response.data; // { success: boolean, message: string }
};

export const fetchOutdoorWeather = async () => {
    const response = await api.get(`utils/currentWeather`);
    return response.data;
};
export const fetchWeatherForecast = async () => {
    const res = await api.get(`utils/forecast`);
    return res.data; // ForecastDay[]
};
// ─── Admin: Login Analytics ───────────────────────────────────────────────────

/**
 * Fetch login analytics for admin dashboard
 * @param {string} endpoint - 'stats', 'summary', 'recent', or 'user/{email}'
 */
export const getLoginAnalytics = async (endpoint) => {
    const response = await api.get(`admin/login-analytics/${endpoint}`);
    return response;
};
/**
 * The backend stores bucketStart as a Date (ISO string from MongoDB).
 * BucketRow expects a short "HH:mm" label in bucket.bucketStart.
 * We also normalise the deviceId field which may come back as a raw string.
 */
const formatBucket = (bucket) => ({
    ...bucket,
    bucketStart: bucket.bucketStart
        ? new Date(bucket.bucketStart).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})
        : '—',
    bucketEnd: bucket.bucketEnd
        ? new Date(bucket.bucketEnd).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})
        : '—',
});

// ── Session list ──────────────────────────────────────────────────────────────

export const getSessions = async () => {
    const {data} = await api.get('recordings/sessions');
    return data;
};

// ── Create ────────────────────────────────────────────────────────────────────

export const createSession = async (payload) => {
    const {data} = await api.post('recordings/sessions', payload);
    return data;
};

// ── Start / stop ──────────────────────────────────────────────────────────────

export const startSession = async (id) => {
    const {data} = await api.post(`recordings/sessions/${id}/start`);
    return data;
};

export const stopSession = async (id) => {
    const {data} = await api.post(`recordings/sessions/${id}/stop`);
    return data;
};

// ── Delete ────────────────────────────────────────────────────────────────────

export const deleteSession = async (id) => {
    const {data} = await api.delete(`recordings/sessions/${id}`);
    return data;
};

// ── Session detail + buckets (used by Replay tab) ─────────────────────────────

/**
 * Fetches session metadata AND all its data buckets in parallel.
 * Returns a single object shaped like a RecordingSession with an extra
 * `buckets` array, matching what SessionDetail / BucketRow expect.
 *
 * Controller endpoints:
 *   GET /api/v1/recordings/{sessionId}          → session metadata
 *   GET /api/v1/recordings/{sessionId}/data     → RecordingBucket[]
 */
export const getSessionBuckets = async (sessionId) => {
    const [sessionRes, bucketsRes] = await Promise.all([
        api.get(`recordings/${sessionId}`),
        api.get(`recordings/${sessionId}/data`),
    ]);

    return {
        ...sessionRes.data,
        buckets: (bucketsRes.data ?? []).map(formatBucket),
    };
};

// ── Per-device buckets (paginated) ────────────────────────────────────────────

export const getDeviceBuckets = async (sessionId, deviceId, page = 0, size = 100) => {
    const {data} = await api.get(`recordings/${sessionId}/data/${deviceId}`, {
        params: {page, size},
    });
    return (data ?? []).map(formatBucket);
};

// ── Summary ───────────────────────────────────────────────────────────────────

export const getSessionSummary = async (sessionId) => {
    const {data} = await api.get(`recordings/${sessionId}/summary`);
    return data;
};

// ── CSV export ────────────────────────────────────────────────────────────────

/**
 * The backend doesn't have a dedicated CSV endpoint yet — we build the CSV
 * client-side from the bucket data so the Export button works immediately.
 *
 * Each row in the CSV is one individual reading (unwrapped from its bucket).
 * Columns: sessionId, deviceId, bucketStart, ts, + every field in the reading.
 */
export const exportSessionCsv = async (sessionId, sessionName = 'recording') => {
    const buckets = await api
        .get(`recordings/${sessionId}/data`)
        .then((r) => r.data ?? []);

    if (!buckets.length) return null;

    // Collect all field keys across every reading (order: ts first, then rest)
    const extraKeys = new Set();
    buckets.forEach((b) =>
        (b.readings ?? []).forEach((r) =>
            Object.keys(r).forEach((k) => k !== 'ts' && extraKeys.add(k))
        )
    );
    const columns = ['sessionId', 'deviceId', 'bucketStart', 'ts', ...extraKeys];

    const rows = [columns.join(',')];
    buckets.forEach((b) => {
        const bucketStart = b.bucketStart
            ? new Date(b.bucketStart).toISOString()
            : '';
        (b.readings ?? []).forEach((reading) => {
            const cells = columns.map((col) => {
                if (col === 'sessionId') return sessionId;
                if (col === 'deviceId') return b.deviceId ?? '';
                if (col === 'bucketStart') return bucketStart;
                const v = reading[col] ?? '';
                // Wrap in quotes if value contains comma or quote
                return String(v).includes(',') || String(v).includes('"')
                    ? `"${String(v).replace(/"/g, '""')}"`
                    : v;
            });
            rows.push(cells.join(','));
        });
    });

    const blob = new Blob([rows.join('\n')], {type: 'text/csv;charset=utf-8;'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${sessionName.replace(/\s+/g, '_')}_${sessionId.slice(0, 8)}.csv`;
    a.click();
    URL.revokeObjectURL(url);

    return blob;
};


export const getSpotifyStatus = async () => {
    const response = await api.get(`spotify/status`);
    return response.data;
};

export const getSpotifyPlayer = async () => {
    const response = await api.get(`spotify/player`);
    return response.data;
};

export const getSpotifyDevices = async () => {
    const response = await api.get(`spotify/devices`);
    return response.data;
};

export const spotifyPlay = async (deviceId) => {
    const response = await api.put(`spotify/play`, {}, {
        params: {deviceId},
    });
    return response.data;
};

export const spotifyPause = async (deviceId) => {
    const response = await api.put(`spotify/pause`, {}, {
        params: {deviceId},
    });
    return response.data;
};

export const spotifyNext = async (deviceId) => {
    const response = await api.post(`spotify/next`, {}, {
        params: {deviceId},
    });
    return response.data;
};

export const spotifyPrevious = async (deviceId) => {
    const response = await api.post(`spotify/previous`, {}, {
        params: {deviceId},
    });
    return response.data;
};

export const spotifyTransfer = async (deviceId) => {
    const response = await api.put(`spotify/transfer`, {}, {
        params: {deviceId},
    });
    return response.data;
};

export const spotifySeek = async (positionMs) => {
    const response = await api.put(`spotify/seek`, {}, {
        params: {positionMs},
    });
    return response.data;
};

export const spotifySetVolume = async (percent) => {
    const response = await api.put(`spotify/volume`, {}, {
        params: {percent},
    });
    return response.data;
};

export const getHomes = async () => {
    const response = await api.get("homes/mine", {
        headers: {
            'Content-Type': 'application/json',
        },
    });
    return response.data;
};

export const getOrphanDevices = async () => {
    const {data} = await api.get('devices/orphan');
    return data;
};

export const claimDevice = async (homeId, deviceId) => {
    const {data} = await api.get(`main/${homeId}/devices/${deviceId}/claim`);
    return data;
};

export const getMyHomes = async () => {
    const {data} = await api.get(`homes/mine`);
    return data;
};

export const createHome = async (homeData) => {
    const {data} = await api.post('homes/create', homeData);
    return data;
};

export const joinHome = async (token) => {
    const {data} = await api.post('invites/accept', null, {params: {token}});
    return data;
};

export const getHomeMembers = async (homeId) => {
    const {data} = await api.get(`homes/${homeId}/members`);
    return data;
};

export const changeMemberRole = async (homeId, userId, role) => {
    const {data} = await api.patch(`homes/${homeId}/members/${userId}`, {role});
    return data;
};

export const revokeAccess = async (homeId, userId) => {
    const {data} = await api.delete(`homes/${homeId}/members/${userId}`);
    return data;
};

export const createInvite = async (inviteData) => {
    const {data} = await api.post('invites', inviteData);
    return data;
};

export const deleteHome = async (homeId) => {
    const {data} = await api.delete(`homes/${homeId}`);
    return data;
};

// services/apis.js
export const getUnclaimedDevices = (homeId) =>
    api.get(`/main/${homeId}/devices/unclaimed`).then(r => r.data);

export const getHomeInvites = (homeId) =>
    api.get(`/invites/${homeId}/invites`).then(r => r.data);