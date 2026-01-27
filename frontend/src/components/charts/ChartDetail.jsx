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
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import {LocalizationProvider} from "@mui/x-date-pickers";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";

const gradientColors = [
    ['#42a5f5', '#ffffff'],
    ['#2ca02c', '#ffffff'],
    ['#ff7f0e', '#ffffff'],
    ['#d62728', '#ffffff'],
    ['#bcbd22', '#ffffff'],
    ['#b13b3b', '#ffffff'],
    ['#7f7f7f', '#ffffff'],
    ['#17becf', '#ffffff'],
    ['#1f77b4', '#ffffff'], // blue
    ['#9467bd', '#ffffff'], // purple
    ['#e377c2', '#ffffff'], // pink
    ['#8c564b', '#ffffff'], // brown
    ['#aec7e8', '#ffffff'], // light blue
    ['#98df8a', '#ffffff'], // light green
    ['#ffbb78', '#ffffff'], // light orange
    ['#c5b0d5', '#ffffff'], // lavender
    ['#f7b6d2', '#ffffff'], // light pink
    ['#c49c94', '#ffffff'], // muted brown
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
    '#17becf', // teal
    '#1f77b4', // blue
    '#e377c2', // pink
    '#8c564b', // brown
    '#aec7e8', // light blue
    '#98df8a', // light green
    '#ffbb78', // light orange
    '#c5b0d5', // lavender
    '#f7b6d2', // light pink
    '#c49c94', // muted brown
];
export default function ChartDetail({deviceId, name, height = 450, width = 1000, deviceAttributes, props}) {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");
    const [chartType, setChartType] = useState("bar");
    const {messages} = useDeviceLiveData();
    const dataRef = useRef([]);
    const [selectedMonth, setSelectedMonth] = useState(dayjs());
    const unitsMap
        = useMemo(() => new Map(deviceAttributes.map(obj => [obj.key, obj.units])), []);


    useEffect(() => {
        const fetchChartData = async () => {
            let response;
            if (range === "history" && selectedMonth) {
                const start = selectedMonth.startOf('month').toISOString();
                const end = selectedMonth.endOf('month').toISOString();

                getDetailChartData(deviceId, "history", { start, end })
                    .then((d) => {
                        setData(d.data);
                        setAttributes(d.attributes);
                        dataRef.current = d.data;
                    });
            }else if (range!=="live"){
                getDetailChartData(deviceId, range, {})
                    .then((d) => {
                        setData(d.data);
                        setAttributes(d.attributes);
                        dataRef.current = d.data;
                    });
            }

            if (response) {
                setData(response.data);
                dataRef.current = response.data;
                setAttributes(response.attributes);
            }
        };

        fetchChartData();
    }, [deviceId, range, selectedMonth]);


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
            background: 'transparent',
            borderRadius: '12px',
            // backdropFilter: 'blur(8px)',
            backgroundColor: 'rgb(0 0 0 / 20%)',
            ...props,
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
                        {/*<ToggleButton value="history">History</ToggleButton>*/}
                        <ToggleButton value="live">Live</ToggleButton>
                        {/*<LocalizationProvider dateAdapter={AdapterDayjs}>*/}
                        {/*    {range === "history" && (*/}
                        {/*        <DatePicker*/}
                        {/*            views={['year', 'month', 'day']}*/}
                        {/*            label="Select Month"*/}
                        {/*            value={selectedMonth}*/}
                        {/*            maxDate={dayjs()}*/}
                        {/*            onChange={(newValue) => setSelectedMonth(newValue)}*/}
                        {/*        />*/}
                        {/*    )}*/}
                        {/*</LocalizationProvider>*/}
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
                        sx={{
                            '& rect': {
                                rx: 2,   // horizontal radius
                                ry: 2,   // vertical radius
                            },
                        }}
                        // sx={{
                        //     '& .MuiChartsAxis-tickLabel': {
                        //         transform: 'rotate(-40deg)',
                        //         textAnchor: 'end',
                        //     },
                        // }}
                        borderRadius={4}

                    />
                )}
            </CardContent>
        </Card>
    );
}
