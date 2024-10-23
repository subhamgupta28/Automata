import axios from 'axios';


const BROWSER_URL = window.location.href;
const BASE_URL = BROWSER_URL+'api/v1/';
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
export const getDevices = async () => {
    const response = await axios.get(BASE_URL + "main/devices", {
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
export const getDeviceById = (deviceId) => {
    return fetch(BASE_URL + "device/" + deviceId, {method: 'GET'}).then(res => res.json());
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

