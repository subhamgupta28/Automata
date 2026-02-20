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
import {Card, CardContent, Typography, Box, LinearProgress, Stack, TextField, Popover} from "@mui/material";
import {styled} from "@mui/material/styles";
import IconButton from "@mui/material/IconButton";
import PaletteIcon from "@mui/icons-material/Palette";
import ColorPicker from "../charts/ColorPicker.jsx";
import LiquidFillGauge from "react-ts-liquid-gauge";
import BatteryCard from "../v2/BatteryCard.jsx";

const BatteryBar = styled(LinearProgress)(({theme, value}) => {
    let color = theme.palette.success.main;
    if (value < 20) color = theme.palette.error.main;
    else if (value < 50) color = theme.palette.warning.main;

    return {
        height: 50,
        borderRadius: 8,
        backgroundColor: theme.palette.grey[300],
        "& .MuiLinearProgress-bar": {
            borderRadius: 8,
            backgroundColor: color,
        },
    };
});

function BatteryGaugeCard({level = 75}) {
    return (
        <Card
            sx={{
                width: "100%",
                maxWidth: 420,
                borderRadius: 3,
            }}
            elevation={3}
        >
            <CardContent>
                <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="subtitle1" fontWeight={600}>
                        Battery Level
                    </Typography>
                    <Typography variant="subtitle1" fontWeight={600}>
                        {level}%
                    </Typography>
                </Box>

                <BatteryBar variant="determinate" value={level}/>
            </CardContent>
        </Card>
    );
}

const series = [
    {label: 'Battery 250Wh', data: [2000, 1700, 1400, 1159, 1850, 1653]},
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
        <div style={{marginTop: '100px'}}>

            <BatteryCard name={"battery"}/>


        </div>
    );
};

export default Exp;
