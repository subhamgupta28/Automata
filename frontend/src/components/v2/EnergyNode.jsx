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

function useEnergyStats(deviceIds) {
    const [statsData, setData] = useState({
        "totalWh": 0,
        "peakWh": 0,
        "lowestWh": 0,
    });
    const prevData = useRef(null);

    useEffect(() => {
        if (!deviceIds) return;
        const fetchStats = async () => {
            const result = {"totalWh": 0, "lowestWh": 0, "peakWh": 0};
            for (const deviceId of deviceIds) {
                const res = await getEnergyStats(deviceId);
                result["totalWh"] += parseFloat(res["totalWh"]);
                result["peakWh"] += parseFloat(res["peakWh"]);
                result["lowestWh"] += parseFloat(res["lowestWh"]);
            }
            prevData.current = statsData;
            setData(result);
        };


        fetchStats();
        const interval = setInterval(fetchStats, 60_000);
        return () => clearInterval(interval);
    }, [deviceIds]);

    return { statsData, prevData: prevData.current };
}

export const EnergyNode = React.memo(({id, data, isConnectable, selected}) => {
    const {devices, loading, error} = useCachedDevices();
    const {messages} = useDeviceLiveData();
    const [chartData, setChartData] = useState([]);

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
    const { statsData, prevData } = useEnergyStats(deviceIds);
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
                ...otherDevices.map(d => [
                    "System",
                    d.name,
                    20
                ])
            ];

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

                <Typography
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        marginLeft: '18px',
                        width: '100%',
                        fontWeight: 'bold',
                        fontSize: '18px',
                        marginRight: '10px'
                    }}
                >
                    {name}
                </Typography>
                <Stack direction="column" spacing={4}>


                    <Stack direction="row" spacing={4} style={{padding: '14px'}}>
                        <StatItem
                            label="Total usage today"
                            value={statsData.totalWh}
                            prevValue={prevData?.totalWh}
                            unit="Wh"
                        />
                        <StatItem
                            label="Peak hour consumption"
                            value={statsData.peakWh}
                            prevValue={prevData?.peakWh}
                            unit="Wh"
                        />
                        <StatItem
                            label="Lowest hourly usage"
                            value={statsData.lowestWh}
                            prevValue={prevData?.lowestWh}
                            unit="Wh"
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

const StatItem = ({label, value, prevValue, unit}) => {
    const animated = useAnimatedNumber(value);

    const diff =
        prevValue !== null && prevValue !== undefined
            ? value - prevValue
            : 0;

    const positive = diff > 0;
    return (

        <Box>
            <Typography variant="h4" fontWeight={600}>
                {animated.toFixed(2)}{" "}
                <Typography
                    component="span"
                    variant="body2"
                    color="text.secondary"
                >
                    {unit}
                </Typography>
            </Typography>


            <Box display="flex" alignItems="center" gap={0.5}>
                {diff !== 0 && (
                    <>
                        {positive ? (
                            <ArrowUpwardIcon
                                sx={{ fontSize: 16, color: "success.main" }}
                            />
                        ) : (
                            <ArrowDownwardIcon
                                sx={{ fontSize: 16, color: "error.main" }}
                            />
                        )}
                        <Typography
                            variant="caption"
                            sx={{
                                color: positive
                                    ? "success.main"
                                    : "error.main",
                            }}
                        >
                            {Math.abs(diff).toFixed(2)} {unit}
                        </Typography>
                    </>
                )}
            </Box>
            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
}