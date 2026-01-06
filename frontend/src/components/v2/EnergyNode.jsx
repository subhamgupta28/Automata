import {NodeResizer,} from "@xyflow/react";
import React, {useEffect, useMemo, useRef, useState} from "react";
import {Box, Button, Card, CardContent} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Chart} from "react-google-charts";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {combineAttributes} from "./VirtualDevice.jsx";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import Stack from "@mui/material/Stack";
import {getEnergyStats} from "../../services/apis.jsx";
import {useAnimatedNumber} from "../../utils/Helper.jsx";

export const cdata = [
    ["From", "To", ""],
    ["Battery 1", "System", 15],
    ["Battery 2", "System", 18],
    ["System", "Light", 10],
    ["System", "Sensor", 9],
    ["System", "Fan", 9],

];

const STORAGE_KEY = "energy_stats_history";

function useEnergyStats(deviceIds) {
    const [statsData, setStatsData] = useState(() => ({
        totalWh: 0,
        peakWh: 0,
        lowestWh: 0,
        chargeLowestWh: 0, chargePeakWh: 0, chargeTotalWh: 0,
        percent: 0
    }));

    const [history, setHistory] = useState(() => {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
        } catch {
            return [];
        }
    });

    useEffect(() => {
        if (!deviceIds?.length) return;

        const fetchStats = async () => {
            try {
                const results = await Promise.all(
                    deviceIds.map(id => getEnergyStats(id))
                );

                const combined = results.reduce(
                    (acc, res) => ({
                        totalWh: acc.totalWh + Number(res?.totalWh || 0),
                        peakWh: acc.peakWh + Number(res?.peakWh || 0),
                        lowestWh: acc.lowestWh + Number(res?.lowestWh || 0),
                        chargeTotalWh: acc.chargeTotalWh + Number(res?.chargeTotalWh || 0),
                        chargePeakWh: acc.chargePeakWh + Number(res?.chargePeakWh || 0),
                        chargeLowestWh: acc.chargeLowestWh + Number(res?.chargeLowestWh || 0),
                        percent: acc.percent + Number(res?.percent || 0),
                    }),
                    {
                        totalWh: 0,
                        peakWh: 0,
                        lowestWh: 0,
                        chargeLowestWh: 0,
                        chargePeakWh: 0,
                        chargeTotalWh: 0,
                        percent: 0
                    }
                );
                if (combined?.percent)
                    combined.percent = combined.percent / results.length
                // console.log("combined", combined)
                setHistory(prev => {
                    const next = [...prev, statsData].slice(-10);
                    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
                    return next;
                });
                setStatsData(combined);

            } catch (err) {
                console.error("Energy stats fetch failed", err);
            }
        };

        fetchStats();
        const id = setInterval(fetchStats, 60_000);

        return () => clearInterval(id);
    }, [deviceIds]);

    return {
        statsData,
        prevData: history,
    };
}


export const EnergyNode = React.memo(({id, data, isConnectable, selected}) => {
    const {devices, loading, error} = useCachedDevices();
    // const {messages} = useDeviceLiveData();
    const [chartData, setChartData] = useState([]);
    const [deviceList, setDeviceList] = useState([]);

    const [topCards, setTopCards] = useState({
        "totalWh": 0,
        "peakWh": 0,
        "lowestWh": 0,
    })
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
    // console.log("energy", data.value)
    const {statsData, prevData} = useEnergyStats(deviceIds);
    console.log("deviceList", deviceList[0]?.lastData?.status)
    // useEffect(()=>{
    //     console.log("att", combineAttributes(attributes))
    //     if (deviceIds && deviceIds.includes(messages.deviceId)) {
    //         if (messages.data) {
    //             const data = messages.data;
    //             console.log("bat", data)
    //
    //         }
    //     }
    // }, [messages])

    useEffect(() => {
        if (devices) {
            const focusIds = new Set(deviceIds);

            const focusDevices = devices?.filter(d => focusIds.has(d.id));
            const otherDevices = devices?.filter(
                d => !focusIds.has(d.id) && d.status === "ONLINE" && d.name !== "System"
            );
            const percentMap = {};


            const items = [
                ["From", "To", ""],
                ...focusDevices.map(d => [
                    d.name,
                    "System",
                    parseInt(d['lastData']['percent'])
                ]),
                ...focusDevices.map(d => [
                    d.name,
                    "Grid",
                    parseInt(d['lastData']['power'])
                ]),
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
            <Card style={{minHeight: '400px', height: '100%', minWidth: width, padding: '10px', borderRadius: '12px'}}>

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
                    <Typography variant="caption">
                        {deviceList.length !== 0 && deviceList[0]?.lastData?.status}
                    </Typography>
                </div>

                <Stack direction="column" spacing={4}>


                    <Stack direction="row" spacing={4} style={{padding: '14px'}}>
                        {(deviceList.length !== 0 && deviceList[0]?.lastData?.status === "CHARGING") ? (
                            <>
                                <StatItem
                                    label="Total charged today"
                                    value={statsData.chargeTotalWh}
                                    prevValue={avg(prevData, "chargeTotalWh")}
                                    unit="Wh"
                                />
                                <StatItem
                                    label="Peak hour consumption"
                                    value={statsData.chargePeakWh}
                                    prevValue={avg(prevData, "chargePeakWh")}
                                    unit="Wh"
                                />
                                <StatItem
                                    label="Lowest hourly usage"
                                    value={statsData.chargeLowestWh}
                                    prevValue={avg(prevData, "chargeLowestWh")}
                                    unit="Wh"
                                />
                            </>
                        ) : (
                            <>
                                <StatItem
                                    label="Total usage today"
                                    value={statsData.totalWh}
                                    prevValue={avg(prevData, "totalWh")}
                                    unit="Wh"
                                />
                                <StatItem
                                    label="Peak hour consumption"
                                    value={statsData.peakWh}
                                    prevValue={avg(prevData, "peakWh")}
                                    unit="Wh"
                                />
                                <StatItem
                                    label="Lowest hourly usage"
                                    value={statsData.lowestWh}
                                    prevValue={avg(prevData, "lowestWh")}
                                    unit="Wh"
                                />
                            </>
                        )}
                        <StatItem
                            label="Percent"
                            value={statsData.percent}
                            prevValue={statsData.percent - 1}
                            unit="%"
                        />
                    </Stack>
                    <Box>
                        <Chart
                            chartType="Sankey"
                            width="500px"
                            height="120px"
                            data={chartData}
                            options={options}
                        />
                    </Box>
                </Stack>
            </Card>
        </>

    )
});
const avg = (arr, key) => {
    if (!arr?.length) return null;
    const valid = arr.map(d => Number(d?.[key])).filter(v => !isNaN(v));
    return valid.length
        ? valid.reduce((s, v) => s + v, 0) / valid.length
        : null;
};

const StatItem = ({label, value, prevValue, unit}) => {
    const animated = useAnimatedNumber(value ?? 0);

    const hasPrev =
        prevValue !== null &&
        prevValue !== undefined &&
        prevValue !== 0;

    const diff = hasPrev ? value - prevValue : 0;
    const percentChange = hasPrev ? (diff / prevValue) * 100 : 0;

    const positive = diff > 0;

    return (
        <Box>
            <Typography variant="h4" fontWeight={600}>
                {animated.toFixed(2)}{" "}
                <Typography component="span" variant="body2" color="text.secondary">
                    {unit}
                </Typography>
            </Typography>

            {hasPrev && diff !== 0 && (
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
                        {Math.abs(diff).toFixed(2)} {unit} (
                        {Math.abs(percentChange).toFixed(1)}%)
                    </Typography>
                </Box>
            )}

            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
};
