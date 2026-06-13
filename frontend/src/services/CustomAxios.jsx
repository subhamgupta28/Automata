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

// ─── FIX 3: logout callback ───────────────────────────────────────────────────
// AuthContext calls this once on mount so the interceptor can drive React state.
let _logoutCallback = null;
export const registerLogoutCallback = (fn) => {
    _logoutCallback = fn;
};

const forceLogout = () => {
    localStorage.removeItem("user");
    if (_logoutCallback) {
        _logoutCallback();          // updates React state → UI reflects logout
    } else {
        window.location.href = "/signin"; // fallback if context not yet wired
    }
};
// ─────────────────────────────────────────────────────────────────────────────

api.interceptors.request.use(config => {
    const token = getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
    failedQueue.forEach(prom => {
        if (error) prom.reject(error);
        else prom.resolve(token);
    });
    failedQueue = [];
};

api.interceptors.response.use(
    response => response,
    async error => {
        const originalRequest = error.config;

        // ─── FIX 1: 401 is the correct "token expired" status, not 403 ────────
        if (error.response?.status === 401 && !originalRequest._retry) {

            if (isRefreshing) {
                // ─── FIX 5: .catch(Promise.reject) → .catch(err => Promise.reject(err))
                return new Promise((resolve, reject) => {
                    failedQueue.push({resolve, reject});
                })
                    .then(token => {
                        originalRequest.headers.Authorization = `Bearer ${token}`;
                        // ─── FIX 4: use `api` instance, not bare `axios` ────
                        return api(originalRequest);
                    })
                    .catch(err => Promise.reject(err));
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const {data} = await axios.post(
                    BASE_URL + "v1/auth/refresh-token",
                    {},
                    {
                        headers: {
                            Authorization: `Bearer ${getRefreshToken()}`,
                        },
                    }
                );

                const updatedUser = {...getStoredUser(), ...data};
                localStorage.setItem("user", JSON.stringify(updatedUser));

                api.defaults.headers.Authorization = `Bearer ${data.access_token}`;
                processQueue(null, data.access_token);

                originalRequest.headers.Authorization = `Bearer ${data.access_token}`;
                // ─── FIX 4 (continued): use `api`, not `axios` ─────────────
                return api(originalRequest);

            } catch (refreshError) {
                processQueue(refreshError, null);
                // ─── FIX 2: drives React state via callback, not just localStorage ──
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