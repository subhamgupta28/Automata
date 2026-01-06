import {Box, Card, CardContent, Divider, LinearProgress, Slider, Stack, Typography,} from "@mui/material";
import WbSunnyIcon from "@mui/icons-material/WbSunny";
import CloudIcon from "@mui/icons-material/Cloud";
import GrainIcon from "@mui/icons-material/Grain";
import AirIcon from "@mui/icons-material/Air";
import OpacityIcon from "@mui/icons-material/Opacity";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useEffect, useState} from "react";
import dayjs from "dayjs";
import {Lightbulb} from "@mui/icons-material";
import ThermostatIcon from "@mui/icons-material/Thermostat";
import WaterDropIcon from "@mui/icons-material/WaterDrop";
import DirectionsCarIcon from "@mui/icons-material/DirectionsCar";
import SunriseSunsetCard from "./SunriseSunsetCard.jsx";

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

function getDevicesWithCO2(attributes) {
    if (!attributes || typeof attributes !== "object") return [];

    return Object.entries(attributes)
        .filter(([_, attrs]) =>
            Array.isArray(attrs) &&
            attrs.some(a => a?.key === "co2")
        )
        .map(([deviceId]) => deviceId);
}

function StatusPill({
                        icon,
                        value,
                        unit,
                        color = "#fff",
                        bg = "#505050",
                    }) {
    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                gap: 1,
                px: 1.6,
                py: 0.8,
                borderRadius: "999px",
                backgroundColor: bg,
                border: "1px solid #E0E0E0",
                boxShadow: "0px 1px 3px rgba(0,0,0,0.08)",
                fontWeight: 600,
                minHeight: 36,
            }}
        >
            <Box
                sx={{
                    display: "flex",
                    alignItems: "center",
                    color,
                }}
            >
                {icon}
            </Box>

            <Typography fontWeight={600} fontSize={14}>
                {value}
                {unit && (
                    <Typography
                        component="span"
                        fontSize={12}
                        color="text.secondary"
                        ml={0.5}
                    >
                        {unit}
                    </Typography>
                )}
            </Typography>
        </Box>
    );
}

export default function WeatherCard({id, data, isConnectable, selected}) {
    const {messages} = useDeviceLiveData();
    const [dataPoint, setDatapoint] = useState({});
    const [mainDevice, setMainDevice] = useState("");
    const [otherDevice, setOtherDevice] = useState("");
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
        if (!attributes || !deviceIds?.length) return;

        // get all devices that expose CO2
        const co2DeviceIds = getDevicesWithCO2(attributes);
        if (co2DeviceIds.length !== 0) {

            setMainDevice(co2DeviceIds[0])
            const other = deviceIds.filter(d => d !== co2DeviceIds[0]);
            if (other.length)
                setOtherDevice(other[0])
        }

        console.log("CO2 devices:", co2DeviceIds);

    }, [attributes, deviceIds]);

    useEffect(() => {
        if (!messages?.data || !messages?.deviceId) return;

        const deviceId = messages.deviceId;

        // ignore devices we don't care about
        if (!deviceIds?.includes(deviceId)) return;

        const attrs = attributes?.[deviceId];
        if (!Array.isArray(attrs)) return;

        // build datapoint object
        const dt = attrs.reduce((acc, m) => {
            if (messages.data[m.key] !== undefined) {
                acc[m.key] = messages.data[m.key];
            }
            return acc;
        }, {});

        // update per-device datapoint
        setDatapoint(prev => ({
            ...prev,
            [deviceId]: {
                ...(prev[deviceId] || {}),
                ...dt,
            },
        }));

        // only update live weather if this is the main device
        if (deviceId !== mainDevice) return;

        const {
            temp,
            humid,
            aqi,
            s_co2,
            tvoc,
            ch2o,
            lux,
        } = dt;

        setLiveData(prev => ({
            ...prev,
            temp,
            humidity: humid,
            aqi,
            lux,
            gases: {
                co2: s_co2,
                tvoc,
                ch2o,
            },
            time: dayjs().format("dddd, h:mm A"),
        }));


    }, [messages, attributes, deviceIds, mainDevice]);


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
                borderRadius: '10px',
                width: 400,
                p: 1,
            }}
        >
            <CardContent>
                {/* Header */}
                <Box display="flex" justifyContent="space-between">
                    <Box>
                        <Typography fontWeight={600}>
                            {weather.location}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {weather.time}
                        </Typography>
                    </Box>
                </Box>
                <Box  mb={2}>
                    <Typography variant="body2" color="text.secondary" maxWidth={500}>
                        Outdoor: {dataPoint?.[otherDevice]?.["temp"]} °C
                        {" H: "}{dataPoint?.[otherDevice]?.["humid"]}%
                    </Typography>
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
                            <Lightbulb fontSize="small"/>
                            <Typography variant="body2">
                                Indoor Light: {weather.lux}
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
                <Box mb={2}>
                    <GasQualitySection
                        gases={weather.gases}
                    />
                </Box>
                <Typography variant="subtitle1" fontWeight={600} mb={2} mt={2}>
                    Sunrise & sunset
                </Typography>
                {/*<Box display="flex" gap={1.5} mt={0} justifyContent="space-between">*/}
                {/*    <StatusPill*/}
                {/*        icon={<ThermostatIcon fontSize="small"/>}*/}
                {/*        value={dataPoint?.[otherDevice]?.["temp"]}*/}
                {/*        unit="°C"*/}
                {/*        color="#E53935"*/}
                {/*    />*/}

                {/*    <StatusPill*/}
                {/*        icon={<WaterDropIcon fontSize="small"/>}*/}
                {/*        value={dataPoint?.[otherDevice]?.["humid"]}*/}
                {/*        unit="%"*/}
                {/*        color="#1E88E5"*/}
                {/*    />*/}

                {/*    <StatusPill*/}
                {/*        icon={<Lightbulb fontSize="small"/>}*/}
                {/*        value={dataPoint?.[otherDevice]?.["lux"]}*/}
                {/*        color="#fff"*/}
                {/*    />*/}
                {/*</Box>*/}
                <Box>
                    <SunriseSunsetCard/>
                </Box>
            </CardContent>
        </Card>
    );
};

