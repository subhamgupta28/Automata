import {LineChart, lineElementClasses} from "@mui/x-charts";
import React, {useEffect, useState, useRef} from "react";
import {getDetailChartData} from "../../services/apis.jsx";
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
    ['#2ca02c', '#ffffff'],
    ['#ff7f0e', '#ffffff'],
    ['#d62728', '#ffffff'],
    ['#bcbd22', '#ffffff'],
    ['#17becf', '#ffffff'],
    ['#7f7f7f', '#ffffff'],
    ['#17becf', '#ffffff'],
];

export default function ChartDetail({deviceId, name}) {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");
    const {messages} = useDeviceLiveData();
    const dataRef = useRef([]);

    useEffect(() => {
        const fetchChartData = async () => {
            const d = await getDetailChartData(deviceId, range);
            setData(d.data);
            dataRef.current = d.data;
            setAttributes(d.attributes);
        };
        if (range !== "live")
            fetchChartData();
    }, [deviceId, range]);

    // Listen for live updates
    useEffect(() => {
        if (range === "live"){
            if (messages.deviceId === deviceId && messages.data) {
                const now = new Date();
                const dd = String(now.getDate()).padStart(2, '0');
                const hh = String(now.getHours()).padStart(2, '0');
                const mm = String(now.getMinutes()).padStart(2, '0');
                const ss = String(now.getSeconds()).padStart(2, '0');

                const formattedTimestamp = `${mm}:${ss}`;

                const newEntry = {
                    dateDay: formattedTimestamp,
                    ...messages.data
                };


                const updatedData = [...dataRef.current, newEntry].slice(-10); // Keep last 50 points
                dataRef.current = updatedData;
                setData(updatedData);
            }
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
        <Card elevation={1} style={{
            borderRadius:'10px',
            backgroundColor: 'transparent',
            borderColor: 'grey',
            borderWidth:'1px',
            borderStyle:'dashed',
        }}>
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
                            // color: "#90caf9",
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
                        <ToggleButton value="live">Live</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                <LineChart
                    height={400}
                    series={series}
                    yAxis={[{position: 'none'}]}
                    xAxis={[{scaleType: "band", data: xLabels}]}
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
            </CardContent>
        </Card>
    );
}
