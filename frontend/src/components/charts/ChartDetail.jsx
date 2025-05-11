import { LineChart, lineElementClasses } from "@mui/x-charts";
import React, { useEffect, useState, useRef } from "react";
import { getDetailChartData } from "../../services/apis.jsx";
import {
    Card,
    CardContent,
    Typography,
    Box,
    ToggleButton,
    ToggleButtonGroup,
} from "@mui/material";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
// Adjust path as needed

const gradientColors = [
    ['#42a5f5', '#ffffff'],
    ['#66bb6a', '#ffffff'],
    ['#ffa726', '#ffffff'],
    ['#ab47bc', '#ffffff'],
    ['#ff6a00', '#ffffff'],
    ['#006fff', '#ffffff'],
    ['#84fd49', '#ffffff'],
    ['#935050', '#ffffff'],
];

export default function ChartDetail({ deviceId, name }) {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");
    const { messages } = useDeviceLiveData();
    const dataRef = useRef([]);

    useEffect(() => {
        const fetchChartData = async () => {
            const d = await getDetailChartData(deviceId, range);
            setData(d.data);
            dataRef.current = d.data;
            setAttributes(d.attributes);
        };
        fetchChartData();
    }, [deviceId, range]);

    // Listen for live updates
    useEffect(() => {
        if (messages.deviceId === deviceId && messages.data) {
            const timestamp = new Date().toISOString(); // Or whatever time format you want

            const newEntry = {
                dateDay: timestamp,
                ...messages.data
            };

            const updatedData = [...dataRef.current, newEntry].slice(-50); // Keep last 50 points
            dataRef.current = updatedData;
            setData(updatedData);
        }
    }, [messages, deviceId]);

    const xLabels = data.map((item) => item.dateDay);

    const series = attributes.map((attr, index) => ({
        label: attr.charAt(0).toUpperCase() + attr.slice(1),
        data: data.map((item) => item[attr]),
        showMark: false,
        area: true,
        color: `url(#Gradient${index})`,
    }));

    return (
        <Card elevation={1} sx={{ borderRadius: 3 }}>
            <CardContent>
                <Box
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    mb={2}
                    flexWrap="wrap"
                >
                    <Typography
                        variant="h6"
                        sx={{
                            fontWeight: 600,
                            color: "#90caf9",
                            mb: 2,
                        }}
                    >
                        {name}
                    </Typography>
                    <ToggleButtonGroup
                        color="primary"
                        size="small"
                        value={range}
                        exclusive
                        onChange={(e) => setRange(e.target.value)}
                        aria-label="Platform"
                    >
                        <ToggleButton value="hour">Hour</ToggleButton>
                        <ToggleButton value="day">Day</ToggleButton>
                        <ToggleButton value="week">Week</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                <LineChart
                    height={300}
                    series={series}
                    yAxis={[{ position: 'none' }]}
                    xAxis={[{ scaleType: "band", data: xLabels }]}
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
                                    <stop offset="0%" stopColor={start} stopOpacity="0.6" />
                                    <stop offset="100%" stopColor={end} stopOpacity="0" />
                                </linearGradient>
                            );
                        })}
                    </defs>
                </LineChart>
            </CardContent>
        </Card>
    );
}
