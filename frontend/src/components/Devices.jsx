import React, { useEffect, useState } from 'react';
import {getDeviceById, getDevices} from "../services/apis.jsx";
import { Modal, Button } from 'bootstrap';


const SensorDataCards = () => {
    const [sensors, setSensors] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [selectedSensor, setSelectedSensor] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
            try {
                await getDevices().then((data)=> {
                    console.log(data);
                    setSensors(data);
                }); // Replace with your actual API endpoint

            } catch (err) {
                setError(err);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, []);

    const handleShow = async (sensorId) => {
        try {
            await getDeviceById(sensorId).then((data)=> {
                console.log(data);
                setSelectedSensor(data);
                setShowModal(true);
            });  // Replace with your detail API endpoint

        } catch (err) {
            console.error('Error fetching device details:', err);
        }
    };

    const handleClose = () => setShowModal(false);

    if (loading) return <p>Loading...</p>;
    if (error) return <p>Error fetching data: {error.message}</p>;

    return (
        <div className="container mt-4">
            <div className="row">
                {sensors.map(sensor => (
                    <div key={sensor.id} className="col-md-4 mb-4">
                        <div className="card h-100">
                            <div className="card-body">
                                <h5 className="card-title">{sensor.name}</h5>
                                <p className="card-text">Status: {sensor.status}</p>
                                {sensor.attributes.map(attribute => (
                                    <div key={attribute.id} className="mb-2">
                                        <strong>{attribute.displayName}:</strong> {/* Display name */}
                                        {attribute.value || "N/A"} {attribute.units} {/* Display value and units */}
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

        </div>
    );
};

export default SensorDataCards;
