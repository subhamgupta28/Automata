import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/v1/main/';


export const getDevices = async () => {
    const response = await axios.get(BASE_URL + "devices", {
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
    const response = await axios.get(BASE_URL + 'data/' + deviceId, {
        headers: {
            'Content-Type': 'application/json', // Specify the content type if necessary
            // Add any other headers if needed, e.g., Authorization
        },
    });
    return response.data;
}

