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