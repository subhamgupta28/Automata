import {BarChart, LineChart, lineElementClasses,} from "@mui/x-charts";
import React, {memo, useEffect, useMemo, useRef, useState} from "react";
import {getDetailChartData} from "../../services/apis.jsx";
import {Box, Card, CardContent, Chip, ToggleButton, ToggleButtonGroup, Typography,} from "@mui/material";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
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
    ['#1f77b4', '#ffffff'],
    ['#9467bd', '#ffffff'],
    ['#e377c2', '#ffffff'],
    ['#8c564b', '#ffffff'],
    ['#aec7e8', '#ffffff'],
    ['#98df8a', '#ffffff'],
    ['#ffbb78', '#ffffff'],
    ['#c5b0d5', '#ffffff'],
    ['#f7b6d2', '#ffffff'],
    ['#c49c94', '#ffffff'],
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
    '#17becf',
    '#1f77b4',
    '#e377c2',
    '#8c564b',
    '#aec7e8',
    '#98df8a',
    '#ffbb78',
    '#c5b0d5',
    '#f7b6d2',
    '#c49c94',
];

const ChartDetailComponent = ({deviceId, name, height = 450, width = 1000, deviceAttributes, props}) => {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");
    const [chartType, setChartType] = useState("bar");
    const [selectedAttrs, setSelectedAttrs] = useState(null); // null = awaiting first load
    const {messages} = useDeviceLiveData();
    const dataRef = useRef([]);
    const [selectedMonth, setSelectedMonth] = useState(dayjs());
    const unitsMap = useMemo(() => new Map(deviceAttributes.map(obj => [obj.key, obj.units])), []);

    useEffect(() => {
        const fetchChartData = async () => {
            let response;
            if (range === "history" && selectedMonth) {
                const start = selectedMonth.startOf('month').toISOString();
                const end = selectedMonth.endOf('month').toISOString();

                getDetailChartData(deviceId, "history", {start, end})
                    .then((d) => {
                        setData(d.data);
                        setAttributes(d.attributes);
                        dataRef.current = d.data;
                    });
            } else if (range !== "live") {
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

    // Initialize selectedAttrs to only the first attribute on first load
    useEffect(() => {
        if (attributes.length > 0 && selectedAttrs === null) {
            setSelectedAttrs([attributes[0]]);
        }
    }, [attributes]);

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

    const toggleAttr = (attr) => {
        setSelectedAttrs(prev => {
            if (!prev) return [attr];
            if (prev.includes(attr)) {
                // Prevent deselecting the last remaining attribute
                return prev.length === 1 ? prev : prev.filter(a => a !== attr);
            }
            return [...prev, attr];
        });
    };

    const xLabels = useMemo(() => data.map((item) => item.dateDay), [data]);

    const series = useMemo(() => attributes.map((attr, index) => ({
        label: attr.charAt(0).toUpperCase() + attr.slice(1),
        data: data.map((item) => item[attr]),
        showMark: false,
        area: chartType === "line",
        color: chartType === "line"
            ? `url(#Gradient${index})`
            : solidColors[index % solidColors.length],
        stack: chartType === "bar" ? "total" : undefined,
        valueFormatter: (value) => `${value} ${unitsMap.get(attr) || ''}`,
        _attr: attr, // internal key for filtering
        _colorIndex: index,
    })), [attributes, data, chartType, unitsMap]);

    // Filter series to only selected attributes
    const visibleSeries = useMemo(() => {
        const active = selectedAttrs ?? (attributes.length > 0 ? [attributes[0]] : []);
        return series
            .filter(s => active.includes(s._attr))
            .map(({_attr, _colorIndex, ...rest}) => rest); // strip internal keys
    }, [series, selectedAttrs, attributes]);

    const gradients = useMemo(() => attributes.map((_, index) => {
        const [start, end] = gradientColors[index % gradientColors.length];
        return {index, start, end};
    }), [attributes]);

    return (
        <Card elevation={0} style={{
            background: 'transparent',
            borderRadius: '12px',
            backgroundColor: 'rgb(0 0 0 / 20%)',
            ...props,
        }}>
            <CardContent>
                {/* Header row */}
                <Box
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    flexWrap="wrap"
                    gap={1}
                    mb={1.5}
                >
                    <Typography variant="h6" sx={{fontWeight: 600}}>
                        {name}
                    </Typography>

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

                    <ToggleButtonGroup
                        color="primary"
                        size="small"
                        value={chartType}
                        exclusive
                        onChange={(e) => setChartType(e.target.value)}
                        aria-label="chart type"
                    >
                        <ToggleButton value="line">Line</ToggleButton>
                        <ToggleButton value="bar">Bar</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                {/* Attribute selector chips */}
                {attributes.length > 0 && (
                    <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
                        {attributes.map((attr, index) => {
                            const isSelected = (selectedAttrs ?? [attributes[0]]).includes(attr);
                            const color = solidColors[index % solidColors.length];
                            return (
                                <Chip
                                    key={attr}
                                    label={attr.charAt(0).toUpperCase() + attr.slice(1)}
                                    size="small"
                                    onClick={() => toggleAttr(attr)}
                                    sx={{
                                        cursor: 'pointer',
                                        fontWeight: isSelected ? 600 : 400,
                                        backgroundColor: isSelected ? color : 'transparent',
                                        color: isSelected ? '#fff' : 'text.secondary',
                                        border: `1.5px solid ${color}`,
                                        transition: 'all 0.18s ease',
                                        '&:hover': {
                                            backgroundColor: isSelected
                                                ? color
                                                : `${color}22`,
                                            opacity: 0.9,
                                        },
                                    }}
                                />
                            );
                        })}
                    </Box>
                )}

                {/* Chart */}
                {chartType === "line" ? (
                    <LineChart
                        height={height}
                        series={visibleSeries}
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
                            {gradients.map(({index, start, end}) => (
                                <linearGradient
                                    key={index}
                                    id={`Gradient${index}`}
                                    x1="0%" y1="0%" x2="0%" y2="100%"
                                >
                                    <stop offset="0%" stopColor={start} stopOpacity="0.8"/>
                                    <stop offset="100%" stopColor={end} stopOpacity="0"/>
                                </linearGradient>
                            ))}
                        </defs>
                    </LineChart>
                ) : (
                    <BarChart
                        height={height}
                        series={visibleSeries}
                        xAxis={[{scaleType: "band", data: xLabels}]}
                        sx={{
                            '& rect': {rx: 2, ry: 2},
                        }}
                        borderRadius={4}
                    />
                )}
            </CardContent>
        </Card>
    );
};

const ChartDetail = memo(ChartDetailComponent, (prevProps, nextProps) => {
    if (prevProps.deviceId !== nextProps.deviceId) return false;
    if (prevProps.name !== nextProps.name) return false;
    if (prevProps.height !== nextProps.height) return false;
    if (prevProps.width !== nextProps.width) return false;
    const pa = prevProps.deviceAttributes;
    const na = nextProps.deviceAttributes;
    if (pa === na) return true;
    if (!pa || !na || pa.length !== na.length) return false;
    return pa.every((attr, i) => attr.key === na[i].key);
});

export default ChartDetail;