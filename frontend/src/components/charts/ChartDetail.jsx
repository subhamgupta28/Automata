import {
    LineChart,
    lineElementClasses,
    BarChart,
} from "@mui/x-charts";
import React, {useEffect, useState, useRef, useMemo} from "react";
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
const solidColors = [
    '#42a5f5',
    '#2ca02c',
    '#ff7f0e',
    '#d62728',
    '#bcbd22',
    '#935050',
    '#7f7f7f',
    '#9467bd',
];
export default function ChartDetail({deviceId, name, height = 450, width = 1000, deviceAttributes}) {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");
    const [chartType, setChartType] = useState("bar");
    const {messages} = useDeviceLiveData();
    const dataRef = useRef([]);
    const unitsMap
        = useMemo(() => new Map(deviceAttributes.map(obj => [obj.key, obj.units])), []);
    console.log("attr", unitsMap);

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
        if (range === "live") {
            if (messages.deviceId === deviceId && messages.data) {
                const now = new Date();
                const mm = String(now.getMinutes()).padStart(2, '0');
                const ss = String(now.getSeconds()).padStart(2, '0');
                const formattedTimestamp = `${mm}:${ss}`;

                const newEntry = {
                    dateDay: formattedTimestamp,
                    ...messages.data
                };

                const updatedData = [...dataRef.current, newEntry].slice(-44);
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
        area: chartType === "line",
        color: chartType === "line"
            ? `url(#Gradient${index})`
            : solidColors[index % solidColors.length],
        stack: chartType === "bar" ? "total" : undefined, // stack all bar series together
        valueFormatter: (value) => `${value} ${unitsMap.get(attr) || ''}`,
    }));

    return (
        <Card elevation={0} style={{
            // background: 'transparent',
            borderRadius: '12px',
            // backdropFilter: 'blur(8px)',
            // backgroundColor: 'rgb(255 255 255 / 8%)',
        }}>
            <CardContent>
                <Box
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    flexWrap="wrap"
                >
                    <Typography
                        variant="h6"
                        sx={{fontWeight: 600}}
                    >
                        {name}
                    </Typography>

                    {/* Range toggle */}
                    <ToggleButtonGroup
                        color="primary"
                        size="small"
                        value={range}
                        exclusive
                        onChange={(e) => setRange(e.target.value)}
                        aria-label="range"
                    >
                        <ToggleButton value="hour">Hour</ToggleButton>
                        <ToggleButton value="day">Day</ToggleButton>
                        <ToggleButton value="week">Week</ToggleButton>
                        <ToggleButton value="live">Live</ToggleButton>
                    </ToggleButtonGroup>

                    {/* Chart type toggle */}
                    <ToggleButtonGroup
                        color="primary"
                        size="small"
                        value={chartType}
                        exclusive
                        onChange={(e) => setChartType(e.target.value)}
                        aria-label="chart type"
                        sx={{ml: 2}}
                    >
                        <ToggleButton value="line">Line</ToggleButton>
                        <ToggleButton value="bar">Bar</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                {chartType === "line" ? (
                    <LineChart
                        height={height}
                        series={series}
                        yAxis={[{position: 'none'}]}
                        xAxis={[{scaleType: "band", data: xLabels}]}
                        sx={{
                            [`& .${lineElementClasses.root}`]: {strokeWidth: 2},
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
                ) : (
                    <BarChart
                        height={height}
                        series={series}
                        xAxis={[{scaleType: "band", data: xLabels}]}
                        // yAxis={[{ position: 'left' }]}
                        // sx={{
                        //     '& rect': {
                        //         rx: 2,   // horizontal radius
                        //         ry: 2,   // vertical radius
                        //     },
                        // }}
                        sx={{
                            '& .MuiChartsAxis-tickLabel': {
                                transform: 'rotate(-40deg)',
                                textAnchor: 'end',
                            },
                        }}
                        borderRadius={4}

                    />
                )}
            </CardContent>
        </Card>
    );
}
