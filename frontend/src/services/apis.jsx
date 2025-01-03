import axios from 'axios';


const BASE_URL = __API_MODE__ === 'serve'
    ? 'http://localhost:8080/api/v1/' // Local API server for development
    : window.location.href + "api/v1/";

// const BROWSER_URL = window.location.href;
// const BASE_URL = apiUrl+'api/v1/';
// const BASE_URL = 'http://localhost:8080/api/v1/';

export const getActions = async () => {
    const response = await axios.get(BASE_URL + "action/getAction", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
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
        },
    });
    return response.data;
}

export const getDevices = async () => {
    const response = await axios.get(BASE_URL + "main/devices", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const getPieChartData = async (deviceId) => {
    const response = await axios.get(BASE_URL + "main/pieChart/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const getChartData = async (deviceId, attribute) => {
    const response = await axios.get(BASE_URL + "main/chart/" + deviceId + "/" + attribute, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const updateAttrCharts = async (deviceId, attribute, isVisible) => {
    const response = await axios.get(BASE_URL + "main/attrCharts/" + deviceId + "/" + attribute + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const updateShowInDashboard = async (deviceId, isVisible) => {
    const response = await axios.get(BASE_URL + "main/showInDashboard/" + deviceId  + "/" + isVisible, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const refreshDeviceById = async (deviceId) => {
    const response = await axios.get(BASE_URL + "main/update/" + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}
export const sendAction = async (deviceId, payload, deviceType) => {
    const response = await axios.post(BASE_URL + "action/sendAction/" + deviceId + "/" + deviceType, payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const saveAutomationDetail = async (payload) => {
    const response = await axios.post(BASE_URL + "action/saveAutomationDetail", payload, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const getNotifications = async () => {
    const response = await axios.get(BASE_URL + "utils/notifications", {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const getDataByDeviceId = async (deviceId) => {
    const response = await axios.get(BASE_URL + 'main/data/' + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

export const getServerTime = async () => {
    const response = await axios.get(BASE_URL + 'main/time', {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
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