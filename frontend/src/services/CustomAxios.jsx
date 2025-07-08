import axios from "axios";

const BASE_URL = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/api/v1/' // Local API server for development
    : window.location.href + "api/v1/";


const api = axios.create({
    baseURL: BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

const getStoredUser = () =>
    JSON.parse(localStorage.getItem("user") || "{}");

const getToken = () => getStoredUser()?.access_token || "";
const getRefreshToken = () => getStoredUser()?.refresh_token || "";

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
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
};

api.interceptors.response.use(
    response => response,
    async error => {
        const originalRequest = error.config;
        // If unauthorized and not a refresh request
        if (error.response?.status === 403 && !originalRequest._retry) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                })
                    .then(token => {
                        originalRequest.headers.Authorization = `Bearer ${token}`;
                        return axios(originalRequest);
                    })
                    .catch(Promise.reject);
            }

            originalRequest._retry = true;
            isRefreshing = true;
            try {
                const { data } = await axios.post(BASE_URL + "auth/refresh-token", {}, {
                    headers: {
                        'Authorization': 'Bearer '+getRefreshToken(), // Specify the content type if necessary
                        // Add any other headers if needed, e.g., Authorization

                    },
                });

                const updatedUser = { ...getStoredUser(), ...data };
                localStorage.setItem("user", JSON.stringify(updatedUser));

                api.defaults.headers.Authorization = `Bearer ${data.access_token}`;
                processQueue(null, data.access_token);
                return api(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                // Optional: logout or redirect
                localStorage.removeItem("user");
                window.location.href = "/signin"; // adjust as needed
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        return Promise.reject(error);
    }
);

export default api;