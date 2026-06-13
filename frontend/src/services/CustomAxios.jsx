import axios from "axios";

const BASE_URL = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/api/'
    : window.location.protocol + "//" + window.location.host + "/api/";

const api = axios.create({
    baseURL: BASE_URL + "v1/",
    headers: {
        'Content-Type': 'application/json',
    },
});

const getStoredUser = () => {
    try {
        return JSON.parse(localStorage.getItem("user") || "{}");
    } catch {
        return {};
    }
};

const getToken = () => getStoredUser()?.access_token || "";
const getRefreshToken = () => getStoredUser()?.refresh_token || "";

// ── Logout callback (set by AuthContext on mount) ─────────────────────────────
let _logoutCallback = null;
export const registerLogoutCallback = (fn) => {
    _logoutCallback = fn;
};

const forceLogout = () => {
    localStorage.removeItem("user");
    if (_logoutCallback) {
        _logoutCallback();
    } else {
        window.location.href = "/signin";
    }
};

// ── JWT decode (no library needed — just read the payload) ────────────────────
const isTokenExpired = (token) => {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        // exp is in seconds, Date.now() is in ms
        return payload.exp * 1000 < Date.now();
    } catch {
        return true; // treat unreadable token as expired
    }
};

// ── Shared refresh state ──────────────────────────────────────────────────────
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
    failedQueue.forEach(prom => {
        if (error) prom.reject(error);
        else prom.resolve(token);
    });
    failedQueue = [];
};

const refreshAccessToken = async () => {
    const {data} = await axios.post(
        BASE_URL + "v1/auth/refresh-token",
        {},
        {headers: {Authorization: `Bearer ${getRefreshToken()}`}}
    );
    const updatedUser = {...getStoredUser(), ...data};
    localStorage.setItem("user", JSON.stringify(updatedUser));
    api.defaults.headers.Authorization = `Bearer ${data.access_token}`;
    return data.access_token;
};

// ── Request interceptor: proactively refresh BEFORE sending if token is expired
api.interceptors.request.use(async config => {
    const token = getToken();

    if (!token) return config;

    if (isTokenExpired(token)) {
        // Token already expired client-side — refresh before the request goes out.
        // This prevents the server from ever seeing the stale token.
        if (isRefreshing) {
            const newToken = await new Promise((resolve, reject) => {
                failedQueue.push({resolve, reject});
            });
            config.headers.Authorization = `Bearer ${newToken}`;
            return config;
        }

        isRefreshing = true;
        try {
            const newToken = await refreshAccessToken();
            processQueue(null, newToken);
            config.headers.Authorization = `Bearer ${newToken}`;
        } catch (err) {
            processQueue(err, null);
            forceLogout();
            return Promise.reject(err);
        } finally {
            isRefreshing = false;
        }
    } else {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

// ── Response interceptor: handle 401 AND 500 (unpatched Spring backends) ──────
api.interceptors.response.use(
    response => response,
    async error => {
        const originalRequest = error.config;
        const status = error.response?.status;

        // 401 = correct status after backend fix
        // 500 = what Spring throws before the fix (contains 'expired' in the trace)
        const isExpiredTokenError =
            status === 401 ||
            (status === 500 && JSON.stringify(error.response?.data ?? '').toLowerCase().includes('expired'));

        if (isExpiredTokenError && !originalRequest._retry) {

            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({resolve, reject});
                })
                    .then(token => {
                        originalRequest.headers.Authorization = `Bearer ${token}`;
                        return api(originalRequest);
                    })
                    .catch(err => Promise.reject(err));
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const newToken = await refreshAccessToken();
                processQueue(null, newToken);
                originalRequest.headers.Authorization = `Bearer ${newToken}`;
                return api(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                forceLogout();
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        return Promise.reject(error);
    }
);

export default api;