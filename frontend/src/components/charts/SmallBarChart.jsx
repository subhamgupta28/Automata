// components/SmallBarChart.jsx
import React, {useEffect, useRef, useState} from 'react';
import {BarChart, BarPlot} from '@mui/x-charts/BarChart';
import {ChartContainer, LineChart, lineElementClasses, LinePlot, markElementClasses, MarkPlot} from "@mui/x-charts";
const gradientColors = [
    ['#42a5f5', '#ffffff'],
    ['#2ca02c', '#ffffff'],
    ['#ff7f0e', '#ffffff'],
    ['#d62728', '#ffffff'],
    ['#bcbd22', '#ffffff'],
    ['#17becf', '#ffffff'],
    ['#7f7f7f', '#ffffff'],
    ['#17becf', '#ffffff'],
];
export default function SmallBarChart({ messages, deviceId, attributes }) {
    const [data, setData] = useState([]);
    // const [attributes, setAttributes] = useState([]);
    const xLabels = data.map((item) => item.dateDay);
    const dataRef = useRef([]);
    console.log(messages)



    useEffect(() => {
            if (messages && messages.device_id === deviceId) {
                const now = new Date();
                const dd = String(now.getDate()).padStart(2, '0');
                const hh = String(now.getHours()).padStart(2, '0');
                const mm = String(now.getMinutes()).padStart(2, '0');
                const ss = String(now.getSeconds()).padStart(2, '0');

                const formattedTimestamp = `${mm}:${ss}`;

                const newEntry = {
                    dateDay: formattedTimestamp,
                    ...messages
                };


                const updatedData = [...dataRef.current, newEntry].slice(-10); // Keep last 50 points
                dataRef.current = updatedData;
                setData(updatedData);
            }
    }, [messages, deviceId]);

    const series = attributes.map((attr, index) => ({
        label: attr.charAt(0).toUpperCase() + attr.slice(1),
        data: data.map((item) => item[attr]),
        showMark: false,
        area: true,
        color: `url(#Gradient${index})`,
    }));
    return (
        <LineChart
            height={160}
            series={series}
            yAxis={[{position: 'none'}]}
            xAxis={[{scaleType: "point", data: xLabels, position: 'none' }]}
            sx={{
                [`& .${lineElementClasses.root}`]: {
                    strokeWidth: 2,
                },
                '& .MuiChartsArea-area': {
                    fillOpacity: 0.3,
                    fill: 'url(#gradientFill)',
                },
            }}
        >
            <defs>
                {attributes.map((_, index) => {
                    const [start, end] = gradientColors[index % gradientColors.length];
                    return (
                        <linearGradient
                            key={index}
                            id={`Gradient${index}`}
                            x1="0%"
                            y1="0%"
                            x2="0%"
                            y2="100%"
                        >
                            <stop offset="0%" stopColor={start} stopOpacity="0.8"/>
                            <stop offset="100%" stopColor={end} stopOpacity="0"/>
                        </linearGradient>
                    );
                })}
            </defs>
        </LineChart>
    );
}
