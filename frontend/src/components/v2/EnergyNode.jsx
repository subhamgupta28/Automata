import {NodeResizer,} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {Box, Card, CircularProgress} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Chart} from "react-google-charts";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import Stack from "@mui/material/Stack";
import {useAnimatedNumber} from "../../utils/Helper.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {getEnergyStats} from "../../services/apis.jsx";
import Carousel from "./Carousel.jsx";
import {CompactWeeklyEnergyRadarWidget} from "../charts/CompactWeeklyEnergyRadarWidget.jsx";


export const EnergyNode = React.memo(({id, data, isConnectable, selected}) => {
    const {devices, loading, error} = useCachedDevices();
    const {messages} = useDeviceLiveData();
    const [chartData, setChartData] = useState([]);
    const [deviceList, setDeviceList] = useState([]);


    const {
        attributes,
        deviceIds,
        height,
        lastModified,
        name,
        tag,
        width,
        recentData,
        x,
        y,
    } = data.value;
    // console.log("energy", data.value)
    // const {statsData} = useEnergyStats(deviceIds);
    // console.log("deviceList", deviceList[0]?.lastData?.status)


    useEffect(() => {
        if (devices) {
            const focusIds = new Set(deviceIds);

            const focusDevices = devices?.filter(d => focusIds.has(d.id));
            const otherDevices = devices?.filter(
                d => !focusIds.has(d.id) && d.status === "ONLINE" && d.name !== "System"
            );
            const percentMap = {};

            let grid = [];

            // if (statsData.status !== "DISCHARGE") {
            //     grid = focusDevices.map(d => [
            //         d.name,
            //         "Grid",
            //         (d["lastData"] !== null) ? parseInt(d['lastData']['power']) : 20
            //     ])
            // }

            const items = [
                ["From", "To", ""],
                ...focusDevices.map(d => [
                    d.name,
                    "System",
                    (d["lastData"] !== null) ? parseInt(d['lastData']['power']) : 20
                ]),
                ...grid,
                ...otherDevices.map(d => [
                    "System",
                    d.name,
                    20
                ])
            ];

            setDeviceList(focusDevices);
            setChartData(items);

        }

    }, [devices])


    const energy = {
        solar: 17.1,
        home: 12.6,
        gridImport: 5.3,
        gridExport: 0.5,
        batteryCharge: 4.5,
        batteryDischarge: 4.2,
    };

    const slides = deviceIds.map(m => {
        return {
            component: ConsumptionCard,
            props:
                {
                    deviceId: m,
                    messages,
                    vid: id,
                    name: devices?.filter(d => m === d.id)[0]?.name
                }
        }
    })

    // console.log("slides", slides)

    const options = {
        sankey: {
            link: {
                // colorMode:'target',
                color: {
                    fillOpacity: 0.8,
                    fill: "#505050"
                }
            },
            // link: { color: { fill: "#d799ae" } },
            node: {
                labelPadding: 6,     // Horizontal distance between the label and the node.
                nodePadding: 5,
                colors: ["#ff0000", "#ffffff", "#ffd821"],
                label: {
                    color: "#ffffff",
                    fontSize: 12,
                    bold: true,
                },
            },
        },
    };

    return (
        <>
            {/*<NodeResizer*/}
            {/*    color="#ff0000"*/}
            {/*    isVisible={selected}*/}
            {/*    minWidth={width}*/}
            {/*    minHeight={height}*/}
            {/*/>*/}
            <Card variant="outlined" style={{
                background: 'transparent',
                backgroundColor: 'rgb(0 0 0 / 0%)',
                minHeight: height, height: '100%', minWidth: width, padding: '10px', borderRadius: '12px'
            }}>

                <div style={{display: 'flex'}}>
                    <Typography
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            marginLeft: '12px',
                            width: '100%',
                            fontWeight: 'bold',
                            fontSize: '18px',
                            marginRight: '10px'
                        }}
                    >
                        {name}
                    </Typography>

                </div>

                <Stack direction="row" style={{
                    justifyContent:'space-between'
                }}>
                    <Stack direction="column" spacing={4} width={700}>

                        <Carousel
                            slides={slides}
                            autoPlay
                            width={750}
                            interval={8000}
                            height={140}
                        />
                        {/*<ConsumptionCard deviceId={""} messages={messages}/>*/}
                        {chartData.length <= 1 ? (
                            <CircularProgress color="inherit"/>
                        ) : (
                            <Box style={{display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                                <Chart
                                    chartType="Sankey"
                                    width="600px"
                                    height="120px"
                                    data={chartData}
                                    options={options}
                                />
                            </Box>
                        )}
                        {/*<EnergyOverview/>*/}

                    </Stack>
                    <CompactWeeklyEnergyRadarWidget vid={id}/>
                </Stack>
            </Card>
        </>

    )
});

const ConsumptionCard = ({deviceId, messages, vid, name}) => {
    const [statsData, setStatsData] = useState(() => ({
        totalWh: 0,
        peakWh: 0,
        lowestWh: 0,
        chargeTotalWh: 0,
        chargePeakWh: 0,
        chargeLowestWh: 0,
        percent: 0,
        status: "",
        // NEW trend fields
        totalWhTrend: 0,
        peakWhTrend: 0,
        lowestWhTrend: 0,
        percentTrend: 0,
    }));
    const [live, setLive] = useState({});

    useEffect(() => {
        const get = async () => {
            const res = await getEnergyStats(deviceId);
            // console.log("sts", res)
            setStatsData(res);
        }

        get();
    }, [])

    useEffect(() => {
        if (messages && messages.data) {
            if (messages.deviceId === vid) {
                const data = messages.data;
                // if (data.filter())
                // console.log("bat",deviceId, data.filter(d => d.deviceId === deviceId)[0])
                setStatsData(data.filter(d => d.deviceId === deviceId)[0]);
            }
            if (messages.deviceId === deviceId) {
                setLive(messages.data);
            }
        }
        // }
    }, [messages])
    return (
        <>

            <div style={{
                paddingTop: '6px',

                marginLeft: '12px'
            }}>
                <Typography variant="caption" color="primary">
                    {name}
                </Typography>
                <Typography
                    variant="caption"
                    sx={{marginLeft: "20px", color: statsData.status === "CHARGING" ? "success.main" : "error.main"}}
                >
                    {"Status: "} {statsData.status.toLowerCase()}
                    {", Realtime Power Utilization: "}{live["power"]}{" W"}
                </Typography>
            </div>
            <Stack direction="row" spacing={4} style={{padding: '14px'}}>
                {(statsData.status === "CHARGING") ? (
                    <>
                        <StatItem
                            label="Total charged today"
                            value={statsData.chargeTotalWh}
                            prevValue={statsData.totalWhTrend}
                            unit="Wh"
                        />
                        <StatItem
                            label="Peak hour consumption"
                            value={statsData.chargePeakWh}
                            prevValue={statsData.peakWhTrend}
                            unit="Wh"
                        />
                        <StatItem
                            label="Lowest hourly usage"
                            value={statsData.chargeLowestWh}
                            prevValue={statsData.lowestWhTrend}
                            unit="Wh"
                        />
                    </>
                ) : (
                    <>
                        <StatItem
                            label="Total usage today"
                            value={statsData.totalWh}
                            prevValue={statsData.totalWhTrend}
                            unit="Wh"
                        />
                        <StatItem
                            label="Peak hour consumption"
                            value={statsData.peakWh}
                            prevValue={statsData.peakWhTrend}
                            unit="Wh"
                        />
                        <StatItem
                            label="Lowest hourly usage"
                            value={statsData.lowestWh}
                            prevValue={statsData.lowestWhTrend}
                            unit="Wh"
                        />
                    </>
                )}
                <StatItem
                    label="Percent"
                    value={statsData.percent}
                    prevValue={statsData.percentTrend}
                    unit="%"
                />
            </Stack>
        </>
    )
}


const StatItem = ({label, value = 0, prevValue = 0, unit}) => {
    const animated = useAnimatedNumber(value);

    const hasTrend =
        prevValue !== null &&
        prevValue !== undefined &&
        prevValue !== 0;

    const positive = prevValue > 0;
    const absDiff = Math.abs(prevValue);

    // Percent change is relative to current value (safe + meaningful)
    const percentChange =
        value !== 0 ? (absDiff / Math.abs(value)) * 100 : 0;

    return (
        <Box>
            <Typography variant="h4" fontWeight={600}>
                {animated.toFixed(2)}{" "}
                <Typography component="span" variant="body2" color="text.secondary">
                    {unit}
                </Typography>
            </Typography>

            {hasTrend && (
                <Box display="flex" alignItems="center" gap={0.5}>
                    {positive ? (
                        <ArrowUpwardIcon sx={{fontSize: 16, color: "success.main"}}/>
                    ) : (
                        <ArrowDownwardIcon sx={{fontSize: 16, color: "error.main"}}/>
                    )}

                    <Typography
                        variant="caption"
                        sx={{color: positive ? "success.main" : "error.main"}}
                    >
                        {absDiff.toFixed(2)} {unit} (
                        {percentChange.toFixed(1)}%)
                    </Typography>
                </Box>
            )}

            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
};

