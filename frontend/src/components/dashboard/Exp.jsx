import React, {useRef, useState, useEffect} from 'react';
import DeviceNodes from "../home/DeviceNodes.jsx";
import BubblesBackground from "../BubblesBackground.jsx";
import WeatherCard from "../v2/WeatherCard.jsx";
import WaterBatteryLevel from "../v2/WaterBatteryLevel.jsx";


const Exp = () => {
    const weather = {
        location: "Cortes, Madrid, Spain",
        time: "Tuesday, 3:00 PM",
        temp: 20,
        condition: "Cloud",
        humidity: 41,
        precipitation: 7,
        wind: 23,
        aqi: 358,
    };


    return (
        <div>
            <WaterBatteryLevel/>


        </div>
    );
};

export default Exp;
