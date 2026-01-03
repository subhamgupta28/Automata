import React, {useRef, useState, useEffect} from 'react';
import DeviceNodes from "../home/DeviceNodes.jsx";
import BubblesBackground from "../BubblesBackground.jsx";
import WeatherCard from "../v2/WeatherCard.jsx";


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
            <WeatherCard weather={{
                location: "Cortes, Madrid, Spain",
                time: "Tuesday, 3:00 PM",
                temp: 20,
                condition: "Cloud",
                humidity: 41,
                precipitation: 7,
                wind: 23,
                aqi: 358,
                gases: {
                    co2: 980,
                    tvoc: 420,
                    ch2o: 0.07,
                }
            }}/>


        </div>
    );
};

export default Exp;
