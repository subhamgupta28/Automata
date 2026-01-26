import {Box, Card, CardContent, Divider, Slider, Stack, Typography,} from "@mui/material";
import WbSunnyIcon from "@mui/icons-material/WbSunny";
import CloudIcon from "@mui/icons-material/Cloud";
import GrainIcon from "@mui/icons-material/Grain";
import AirIcon from "@mui/icons-material/Air";
import OpacityIcon from "@mui/icons-material/Opacity";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useEffect, useState} from "react";
import dayjs from "dayjs";
import {Lightbulb, Thermostat} from "@mui/icons-material";
import {GasBubble, GasLegend} from "./GasBubble.jsx";
import {getRecentDeviceData} from "../../services/apis.jsx";

const getWeatherIcon = (condition, humid) => {
    const c = condition;
    if (c >= 30)
        return <WbSunnyIcon sx={{fontSize: 48, color: "#f9a825"}}/>;
    if (c < 30)
        return <CloudIcon sx={{fontSize: 48, color: "#90a4ae"}}/>;
    if (humid > 70)
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


const GasQualitySection = ({gases}) => {
    return (
        <Box mt={2}>
            <Typography variant="subtitle1" fontWeight={600} mb={2}>
                Gas Levels
            </Typography>

            <Stack spacing={2} sx={{
                position: "relative",
                width: "100%",
                height: 200,
                margin: "auto",
            }}>
                {/* CO₂ – largest */}
                <GasBubble
                    label="CO₂"
                    value={gases.co2}
                    unit="ppm"
                    max={2000}
                    color={'#22537c'}
                    type="co2"
                    size={120}
                    top="30px"
                    left="10px"
                />

                {/* CH₂O */}
                <GasBubble
                    label="CH₂O"
                    value={gases.ch2o}
                    unit="ppb"
                    max={1000}
                    color={'#1c641c'}
                    type="ch2o"
                    size={100}
                    top="0px"
                    left="180px"
                />

                {/* TVOC */}
                <GasBubble
                    label="TVOC"
                    value={gases.tvoc}
                    unit="mg/m³"
                    max={1}
                    color={'#ad6322'}
                    type="tvoc"
                    size={80}
                    top="100px"
                    left="130px"
                />
                <GasBubble
                    label="PM2.5"
                    value={gases.pm25}
                    unit="ug/m³"
                    max={350}
                    color={'#a2292a'}
                    type="pm25"
                    size={60}
                    top="110px"
                    left="280px"
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
            pm25: 0
        }
    });
    // console.log("data", data.value)
    const {
        attributes,
        deviceIds,
        height,
        lastModified,
        recentData,
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
            const deviceId = co2DeviceIds[0];
            setMainDevice(deviceId)
            const other = deviceIds.filter(d => d !== co2DeviceIds[0]);
            if (other.length)
                setOtherDevice(other[0])

            const get = async () => {
                return await getRecentDeviceData(deviceIds);
            }
            get().then(res => {
                setDatapoint(res);
                setLiveDataHandle(deviceId, res[deviceId]);
            });

        }

        console.log("CO2 devices:", co2DeviceIds);

    }, [attributes, deviceIds]);

    useEffect(() => {
        if (messages?.data && messages.deviceId && deviceIds?.includes(messages.deviceId)) {
            const deviceId = messages.deviceId;
            // ignore devices we don't care about
            if (!deviceIds?.includes(deviceId)) return;
            const attrs = attributes?.[deviceId];
            if (!Array.isArray(attrs)) return;
            const allData = attrs.reduce((acc, m) => {
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
                    ...allData,
                },
            }));

            setLiveDataHandle(deviceId, allData)
        }


    }, [messages, attributes, deviceIds, mainDevice, recentData]);

    const setLiveDataHandle = (deviceId, allData) => {
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
            pm25
        } = allData;

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
                pm25,
            },
            time: dayjs().format("dddd, h:mm A"),
        }));
    }

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
            variant="outlined"
            sx={{
                background: 'transparent',
                backgroundColor: 'rgb(0 0 0 / 0%)',
                borderRadius: '10px',
                width,
                height: {height},
                p: 1,
            }}
        >
            <CardContent>
                {/* Header */}
                <Box style={{
                    display: "flex", justifyContent: "space-between"
                }}>
                    <Box>
                        <Typography fontWeight={600}>
                            {weather.location}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {weather.time}
                        </Typography>
                    </Box>
                </Box>
                <Box mb={2} style={{
                    display: "flex", alignItems: "center", gap: 2
                }}>
                    <Typography>
                        Outdoor
                    </Typography>
                    <Thermostat style={{height: "18px"}}/>
                    <Typography variant="body2" color="text.secondary" maxWidth={500}>
                        {dataPoint?.[otherDevice]?.["temp"]} °C
                    </Typography>
                    <OpacityIcon style={{height: "18px"}}/>
                    <Typography variant="body2" color="text.secondary" maxWidth={500}>
                        {dataPoint?.[otherDevice]?.["humid"]}%
                    </Typography>
                    <Lightbulb style={{height: "18px"}}/>
                    <Typography variant="body2" color="text.secondary" maxWidth={500}>
                        {dataPoint?.[otherDevice]?.["lux"]}
                    </Typography>
                </Box>
                <Divider sx={{mb: 2}}/>

                {/* Main Content */}
                <Box style={{
                    display: "flex", justifyContent: "space-between", alignItems: "center"
                }}>
                    {/* Left */}
                    <Box style={{
                        display: "flex", alignItems: "center", gap: 8
                    }}>
                        {getWeatherIcon(weather.temp, weather.humidity)}

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
                        <Box style={{
                            display: "flex", alignItems: "center", gap: 1
                        }}>
                            <OpacityIcon fontSize="small"/>
                            <Typography variant="body2">
                                Humidity: {weather.humidity}%
                            </Typography>
                        </Box>

                        <Box style={{
                            display: "flex", alignItems: "center", gap: 1
                        }}>
                            <Lightbulb fontSize="small"/>
                            <Typography variant="body2">
                                Indoor Light: {weather.lux}
                            </Typography>
                        </Box>

                        <Box style={{
                            display: "flex", alignItems: "center", gap: 1
                        }}>
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
                    <GasLegend
                        items={[
                            {label: "CO₂", value: weather.gases.co2, type: "co2", color: '#42a5f5', units: "ppm"},
                            {label: "CH₂O", value: weather.gases.ch2o, type: "ch20", color: '#2ca02c', units: "ppb"},
                            {label: "TVOC", value: weather.gases.tvoc, type: "tvoc", color: '#ff7f0e', units: "mg/m³"},
                            {label: "PM2.5", value: weather.gases.pm25, type: "pm25", color: '#d62728', units: "ug/m³"},
                        ]}
                    />
                </Box>
            </CardContent>
        </Card>
    );
};

