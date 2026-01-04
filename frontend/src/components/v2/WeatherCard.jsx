import {
    Card,
    CardContent,
    Typography,
    Box,
    Stack,
    Divider, Slider, LinearProgress,
} from "@mui/material";
import WbSunnyIcon from "@mui/icons-material/WbSunny";
import CloudIcon from "@mui/icons-material/Cloud";
import GrainIcon from "@mui/icons-material/Grain";
import AirIcon from "@mui/icons-material/Air";
import WaterDropIcon from "@mui/icons-material/WaterDrop";
import OpacityIcon from "@mui/icons-material/Opacity";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useEffect, useState} from "react";
import dayjs from "dayjs";
import {SparkLineChart} from "@mui/x-charts";

const getWeatherIcon = (condition) => {
    const c = condition.toLowerCase();
    if (c.includes("sun") || c.includes("clear"))
        return <WbSunnyIcon sx={{fontSize: 48, color: "#f9a825"}}/>;
    if (c.includes("cloud"))
        return <CloudIcon sx={{fontSize: 48, color: "#90a4ae"}}/>;
    if (c.includes("rain"))
        return <GrainIcon sx={{fontSize: 48, color: "#4fc3f7"}}/>;

    return <WbSunnyIcon sx={{fontSize: 48}}/>;
};
const getAQILabel = (aqi) => {
    if (aqi <= 50) return {label: "Good", color: "#2ecc71"};
    if (aqi <= 100) return {label: "Moderate", color: "#f1c40f"};
    if (aqi <= 150) return {label: "Poor", color: "#f39c12"};
    if (aqi <= 200) return {label: "Unhealthy", color: "#e74c3c"};
    if (aqi <= 300) return {label: "Very Unhealthy", color: "#8e44ad"};
    return {label: "Hazardous", color: "#6c3483"};
};

const getAQIDescription = (aqi) => {
    if (aqi <= 100)
        return "Air quality is acceptable for most people.";
    if (aqi <= 150)
        return "The air has reached a high level of pollution and is unhealthy for sensitive groups.";
    if (aqi <= 200)
        return "Everyone may begin to experience health effects.";
    return "Health warnings of emergency conditions.";
};

const AQIBar = ({aqi}) => {
    const {label, color} = getAQILabel(aqi);

    return (
        <Box mt={3}>
            {/* Header */}
            <Box display="flex" alignItems="center" gap={3} mb={2}>
                <Box>
                    <Typography>
                        AQI
                    </Typography>
                    <Typography variant="h4" fontWeight={600}>
                        {aqi}
                    </Typography>
                    <Typography color="text.secondary">{label}</Typography>
                </Box>

                <Typography variant="body2" color="text.secondary" maxWidth={500}>
                    {getAQIDescription(aqi)}
                </Typography>
            </Box>

            {/* AQI Gradient Slider */}
            <Slider
                value={parseInt(aqi)}
                min={0}
                max={400}
                disabled
                sx={{
                    height: 8,
                    "& .MuiSlider-track": {
                        background: "transparent",
                    },
                    "& .MuiSlider-rail": {
                        background:
                            "linear-gradient(to right, #2ecc71 0%, #f1c40f 25%, #f39c12 40%, #e74c3c 55%, #8e44ad 75%, #6c3483 100%)",
                        opacity: 1,
                    },
                    "& .MuiSlider-thumb": {
                        width: 14,
                        height: 14,
                        backgroundColor: "#fff",
                        border: `3px solid ${color}`,
                    },
                }}
            />

            {/* Scale labels */}
            <Box display="flex" justifyContent="space-between" mt={1}>
                <Typography variant="caption">0</Typography>
                <Typography variant="caption">100</Typography>
                <Typography variant="caption">200</Typography>
                <Typography variant="caption">300</Typography>
                <Typography variant="caption">400+</Typography>
            </Box>
        </Box>
    );
};

const getLevelColor = (value, type) => {
    switch (type) {
        case "co2":
            if (value < 800) return "success";
            if (value < 1200) return "warning";
            return "error";

        case "tvoc":
            if (value < 300) return "success";
            if (value < 600) return "warning";
            return "error";

        case "ch2o":
            if (value < 0.08) return "success";
            if (value < 0.1) return "warning";
            return "error";

        default:
            return "success";
    }
};

const GasBar = ({label, value, unit, max, type}) => {
    const color = getLevelColor(value, type);
    const percent = Math.min((value / max) * 100, 100);

    return (
        <Box>
            <Box display="flex" justifyContent="space-between" mb={0.5}>
                <Typography variant="body2" fontWeight={600}>
                    {label}
                </Typography>
                <Typography variant="body2">
                    {value} {unit}
                </Typography>
            </Box>

            <LinearProgress
                variant="determinate"
                value={percent}
                color={color}
                sx={{
                    height: 8,
                    borderRadius: 5,
                }}
            />
        </Box>
    );
};

const GasQualitySection = ({gases}) => {
    return (
        <Box mt={4}>
            <Typography variant="subtitle1" fontWeight={600} mb={2}>
                Gas Levels
            </Typography>

            <Stack spacing={2}>
                <GasBar
                    label="CO₂"
                    value={gases.co2}
                    unit="ppm"
                    max={2000}
                    type="co2"
                />

                <GasBar
                    label="TVOC"
                    value={gases.tvoc}
                    unit="ppb"
                    max={1000}
                    type="tvoc"
                />

                <GasBar
                    label="CH₂O"
                    value={gases.ch2o}
                    unit="mg/m³"
                    max={0.3}
                    type="ch2o"
                />
            </Stack>
        </Box>
    );
};
const MAX_POINTS = 50;

export default function WeatherCard({id, data, isConnectable, selected}) {
    const {messages} = useDeviceLiveData();
    const [history, setHistory] = useState({
        co2: [],
        tvoc: [],
        ch2o: [],
    });
    const [weather, setLiveData] = useState({
        location: "Khariar Road, Odisha",
        time: "Tuesday, 3:00 PM",
        temp: 0,
        condition: "Cloud",
        humidity: 0,
        precipitation: 0,
        wind: 23,
        aqi: 0,
        gases: {
            co2: 0,
            tvoc: 0,
            ch2o: 0,
        }
    });
    // console.log("data", data.value)
    const {
        attributes,
        deviceIds,
        height,
        lastModified,
        name,
        tag,
        width,
        x,
        y,
    } = data.value;

    useEffect(() => {
        // console.log("de id", deviceIds);
        // console.log("ms id", messages.deviceId);
        if (deviceIds && deviceIds.includes(messages.deviceId)) {
            // console.log("live", messages)
            if (messages.data) {
                const data = messages.data;
                setLiveData({
                    ...weather,
                    temp: data['temp'],
                    humidity: data['humid'],
                    aqi: data['aqi'],
                    gases: {
                        co2: data['s_co2'],
                        tvoc: data['tvoc'],
                        ch2o: data['ch2o']
                    },
                    time: dayjs().format("dddd, h:mm A")
                })
                setHistory((prev) => ({
                    co2: [...prev.co2, parseInt(weather.gases.co2)].slice(-MAX_POINTS),
                    tvoc: [...prev.tvoc, parseInt(weather.gases.tvoc)].slice(-MAX_POINTS),
                    ch2o: [...prev.ch2o, parseInt(weather.gases.ch2o)].slice(-MAX_POINTS),
                }));
            }
            // setActionAck(messages.ack);
        }
    }, [messages, data.live]);


    // const weather = {
    //     location: "Cortes, Madrid, Spain",
    //     time: "Tuesday, 3:00 PM",
    //     temp: 20,
    //     condition: "Cloud",
    //     humidity: 41,
    //     precipitation: 7,
    //     wind: 23,
    //     aqi: 358,
    //     gases: {
    //         co2: 980,
    //         tvoc: 420,
    //         ch2o: 0.07,
    //     }
    // }
    return (
        <Card
            sx={{
                borderRadius: 4,
                width: 400,
                p: 1,
            }}
        >
            <CardContent>
                {/* Header */}
                <Box display="flex" justifyContent="space-between" mb={2}>
                    <Box>
                        <Typography fontWeight={600}>
                            {weather.location}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {weather.time}
                        </Typography>
                    </Box>


                </Box>

                <Divider sx={{mb: 2}}/>

                {/* Main Content */}
                <Box display="flex" justifyContent="space-between" alignItems="center">
                    {/* Left */}
                    <Box display="flex" alignItems="center" gap={2}>
                        {getWeatherIcon(weather.condition)}

                        <Typography variant="h2" fontWeight={600}>
                            {weather.temp}
                        </Typography>

                        <Box>
                            <Typography variant="body" fontWeight={600}>
                                °C
                            </Typography>
                        </Box>
                    </Box>

                    {/* Right Stats */}
                    <Stack spacing={1}>
                        <Box display="flex" alignItems="center" gap={1}>
                            <OpacityIcon fontSize="small"/>
                            <Typography variant="body2">
                                Humidity: {weather.humidity}%
                            </Typography>
                        </Box>

                        <Box display="flex" alignItems="center" gap={1}>
                            <WaterDropIcon fontSize="small"/>
                            <Typography variant="body2">
                                Precipitation: {weather.precipitation}%
                            </Typography>
                        </Box>

                        <Box display="flex" alignItems="center" gap={1}>
                            <AirIcon fontSize="small"/>
                            <Typography variant="body2">
                                Wind: {weather.wind} mph
                            </Typography>
                        </Box>
                    </Stack>
                </Box>
                <Box sx={{mt: 2}}>
                    <AQIBar aqi={weather.aqi}/>
                </Box>
                <Box>
                    <GasQualitySection
                        gases={weather.gases}
                    />
                </Box>
                <Box
                    sx={{
                        position: "relative",
                        height: 120,
                        marginTop: '20px',
                        width: "100%",
                    }}
                >
                    {/* CO₂ */}
                    <SparkLineChart
                        data={history.co2}
                        height={100}
                        curve="natural"
                        color="orange"
                        sx={{
                            position: "absolute",
                            inset: 0,
                            opacity: 0.6,
                        }}
                    />

                    {/* TVOC */}
                    <SparkLineChart
                        data={history.tvoc}
                        height={100}
                        curve="natural"
                        color="red"
                        sx={{
                            position: "absolute",
                            inset: 0,
                            opacity: 0.45,
                        }}
                    />

                    {/* CH₂O */}
                    <SparkLineChart
                        data={history.ch2o}
                        height={100}
                        color="green"
                        curve="natural"
                        sx={{
                            position: "absolute",
                            inset: 0,
                            opacity: 0.35,
                        }}
                    />
                </Box>
            </CardContent>
        </Card>
    );
};

