import axios from 'axios';
import {useEffect, useState} from "react";


const BASE_URL = __API_MODE__ === 'serve'
    ? 'http://localhost:8010/api/v1/' // Local API server for development
    : window.location.href + "api/v1/";

// const BROWSER_URL = window.location.href;
// const BASE_URL = apiUrl+'api/v1/';
// const BASE_URL = 'http://localhost:8080/api/v1/';

const getToken = ()=>{
    const storedUser = JSON.parse(localStorage.getItem('user'));
    return storedUser.access_token;
}

export const getActions = async () => {
    const response = await axios.get(BASE_URL + "action/getAction", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const getWiFiDetails = async () => {
    const response = await axios.post(BASE_URL + "main/wifiList", {},{
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            'Authorization':'Bearer '+getToken()
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const updatePosition = async (deviceId, x, y) => {
    const response = await axios.get(BASE_URL + "main/updatePosition/" + deviceId + "/" + x + "/" + y, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getDashboardDevices = async () => {
    const response = await axios.get(BASE_URL + "main/dashboard", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getDevices = async () => {
    const response = await axios.get(BASE_URL + "main/devices", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getDetailChartData = async (deviceId, range = 'day') => {
    const response = await axios.get(BASE_URL + "main/chartDetail/" + deviceId+ "/" + range, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getPieChartData = async (deviceId) => {
    const response = await axios.get(BASE_URL + "main/pieChart/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getChartData = async (deviceId, attribute) => {
    const response = await axios.get(BASE_URL + "main/chart/" + deviceId + "/" + attribute, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const updateAttrCharts = async (deviceId, attribute, isVisible) => {
    const response = await axios.get(BASE_URL + "main/attrCharts/" + deviceId + "/" + attribute + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const updateShowCharts = async (deviceId, isVisible) => {
    const response = await axios.get(BASE_URL + "main/showCharts/" + deviceId + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const updateShowInDashboard = async (deviceId, isVisible) => {
    const response = await axios.get(BASE_URL + "main/showInDashboard/" + deviceId + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const disableAutomation = async (id, isEnabled) => {
    const response = await axios.get(BASE_URL + "action/disable/" + id + "/" + isEnabled, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const refreshDeviceById = async (deviceId) => {
    const response = await axios.get(BASE_URL + "main/update/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const sendAction = async (deviceId, payload, deviceType) => {
    const response = await axios.post(BASE_URL + "action/sendAction/" + deviceId + "/" + deviceType, payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const saveWiFiList = async (payload) => {
    const response = await axios.post(BASE_URL + "main/saveWiFiList" , payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const rebootAllDevices = async () => {
    const response = await axios.get(BASE_URL + "action/rebootAllDevices", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const notificationAction = async (action, payload) => {
    const response = await axios.post(BASE_URL + "utils/action/" + action, payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const getAutomationDetail = async (id) => {
    const response = await axios.get(BASE_URL + "action/getAutomationDetail/" + id, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const saveAutomationDetail = async (payload) => {
    const response = await axios.post(BASE_URL + "action/saveAutomationDetail", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}
export const getMainNodePos = async () => {
    const response = await axios.get(BASE_URL + "main/mainNodePos", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}

export const getNotifications = () => {
    return useGetFetch(BASE_URL + "utils/notifications");
}

export const getDataByDeviceId = async (deviceId) => {
    return useGetFetch(BASE_URL + 'main/data/' + deviceId);
}

export const getServerTime = async () => {
    const response = await axios.get(BASE_URL + 'main/time', {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
            'Authorization':'Bearer '+getToken()
        },
    });
    return response.data;
}


export const getLastDataByDeviceId = async (deviceId) => {
    const response = await axios.get(BASE_URL + 'main/lastData/' + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
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
            const response = await axios.get(url, {
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
    const response = await axios.post(BASE_URL + "auth/authenticate", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const signUpReq = async (payload) => {
    const response = await axios.post(BASE_URL + "auth/register", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}