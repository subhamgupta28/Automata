import React, {useRef, useState, useEffect} from 'react';
import DeviceNodes from "../home/DeviceNodes.jsx";
import BubblesBackground from "../BubblesBackground.jsx";
import WeatherCard from "../v2/WeatherCard.jsx";
import WaterBatteryLevel from "../v2/WaterBatteryLevel.jsx";
import {RadarChart} from "@mui/x-charts/RadarChart";
import {RadarAxis} from "@mui/x-charts";
import {CompactWeeklyEnergyRadarWidget} from "../charts/CompactWeeklyEnergyRadarWidget.jsx";
// Dummy data following the EnergyStat Java model (one entry per day)
// Dummy data following the EnergyStat Java model (one entry per day)

const series = [
    {label: 'Battery 250Wh', data: [2000, 1700, 1400, 1159,1850, 1653]},
    {label: 'Battery 270Wh', data: [1250, 980, 860, 1199, 485, 965]},
    {label: 'Battery 500Wh', data: [1000, 700, 400, 159, 850, 653]},
];



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
            <CompactWeeklyEnergyRadarWidget series={series}/>


        </div>
    );
};

export default Exp;
