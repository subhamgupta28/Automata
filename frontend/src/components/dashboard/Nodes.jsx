import React, {useCallback, useEffect, useMemo, useState} from "react";
import {Handle, Position} from "@xyflow/react";
import {getChartData, getDevices, refreshDeviceById, sendAction, updateAttrCharts} from "../../services/apis.jsx";
import {
    Alert,
    Button,
    Card, CardActions,
    CardContent,
    CardHeader, Chip,
    Dialog, DialogActions,
    DialogContent,
    DialogTitle,
    Modal, Paper, Slider,
    SvgIcon, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow
} from "@mui/material";
import Typography from "@mui/material/Typography";
import SettingsIcon from '@mui/icons-material/Settings';
import MoodIcon from '@mui/icons-material/Mood';
import MoodBadIcon from '@mui/icons-material/MoodBad';
import SentimentDissatisfiedIcon from '@mui/icons-material/SentimentDissatisfied';
import IconButton from "@mui/material/IconButton";
import {GaugeChart} from "../charts/GaugeChart.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import ChartNode from "../charts/ChartNode.jsx";
import {axisClasses} from "@mui/x-charts/ChartsAxis";
import {BarChart, ChartContainer, lineElementClasses, LinePlot, markElementClasses, MarkPlot} from "@mui/x-charts";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import MapView from "../charts/MapView.jsx";
import {LineChart} from "@mui/x-charts/LineChart";
import Stack from "@mui/material/Stack";
import {styled} from "@mui/material/styles";
import Checkbox from "@mui/material/Checkbox";
import Divider from "@mui/material/Divider";
import SwitchButton from "../charts/SwitchButton.jsx";


const CustomModal = ({ isOpen, onClose, device }) => {
    const [attrs, setAttrs] = useState(device.attributes);
    const fetchData = async () => {
        try {
            await refreshDeviceById(device.id);
        } catch (err) {
            // Handle error gracefully
        }
    };

    const handleAction = async (action) => {
        try {
            await sendAction(device.id, { key: action, [action]: true, device_id: device.id, direct: true });
        } catch (err) {
            // Handle action error
        }
    };

    const handleReboot = () => handleAction("reboot");

    const handleUpdate = () => fetchData();

    const handleAttrUpdate = async (attribute) => {
        try {
            const updatedAttribute = { ...attribute, visible: !attribute.visible }; // Toggle the 'visible' property
            await updateAttrCharts(device.id, attribute.key, updatedAttribute.visible);
            device.attributes = device.attributes.map(attr =>
               attr.id === updatedAttribute.id ? updatedAttribute : attr
            );
            setAttrs(device.attributes);
        } catch (err) {
            // Handle error gracefully
        }
    };

    return (
        <Dialog fullWidth maxWidth="md" onClose={onClose} open={isOpen}>
            <DialogTitle>Device Settings</DialogTitle>
            <DialogContent style={{ overflow: 'auto' }}>
                <Stack direction="row" divider={<Divider orientation="vertical" flexItem />} spacing={2}>
                    <div style={{ width: '70%' }}>
                        Item 1
                    </div>
                    <div>
                        <table style={{ marginTop: '12px' }}>
                            <thead>
                            <tr>
                                <td>Attribute</td>
                                <td>Show in charts</td>
                            </tr>
                            </thead>
                            <tbody>
                            {attrs.map((attribute) => (
                                <tr key={attribute.id}>
                                    <td>{attribute.displayName}</td>
                                    <td>
                                        <Checkbox
                                            checked={attribute.visible}
                                            onChange={() => handleAttrUpdate(attribute)}
                                        />
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleReboot}>Reboot</Button>
                <Button style={{ marginLeft: '12px' }} onClick={handleUpdate}>Update</Button>
                <div style={{ flexGrow: 1 }} />
                <Button variant="secondary" onClick={onClose}>Close</Button>
                <Button variant="primary" onClick={onClose}>Save Changes</Button>
            </DialogActions>
        </Dialog>
    );
};


export function Device({data, isConnectable}) {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    let color;


    const gaugeData = data.value.attributes.filter((t) => t.type === "DATA|GAUGE");
    const sliderData = data.value.attributes.filter((t) => t.type === "DATA|SLIDER");
    const map = data.value.attributes.filter((t) => t.type.startsWith("DATA|MAIN,MAP"));
    const switchBtn = data.value.attributes.filter((t) => t.type.startsWith("DATA|SWITCH"));


    const handleAction = (attribute) => {
        const send = async () => {
            try {
                let act = attribute.key;
                await sendAction(data.value.id, {"key": attribute.key, [act]: 200, "device_id": data.value.id});
            } catch (err) {
                // console.error("Action send failed", err);
            }
        };
        send();
    }
    if (data.value["status"])
        if (data.value.status === 'ONLINE') {
            color = "#84fd49"; // Icon for connected

        } else
            color = "#ff0000"; // Default icon



    return (
        <div className="text-updater-node" key={data.value.id} >
            <div style={{borderRadius: '12px', padding: '1px'}}>
                <Card elevation={0} style={{display: 'flex', borderRadius: '12px', marginLeft: '2px', marginRight: '2px', padding: '4px', boxShadow: '0 0 50px 15px #1c1c1c'}}>
                    <CardContent
                        style={{minWidth: '200px', alignItems: 'center', paddingTop: '6px', paddingBottom: '6px', justifyContent: 'center'}}>
                        <Typography style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                            {data.value.name}
                            {/*<SvgIcon component={icon} inheritViewBox style={{marginLeft: '8px',}}/>*/}
                            <span style={{fontSize:'x-small', color: color}} >{data.value["status"]}</span>
                            <IconButton onClick={handleOpenModal} variant='text' style={{marginLeft: '8px'}}>
                                <SettingsIcon/>
                            </IconButton>
                        </Typography>

                        {/*<ChartNode/>*/}

                        {map.length > 0 && data.live && (
                            <MapView lat={data.live.LAT} lng={data.live.LONG}/>
                        )}

                        {gaugeData && data.live && gaugeData.map((gauge) => (
                            <GaugeChart value={data.live[gauge.key]} maxValue={gauge.extras.max}
                                        displayName={gauge.displayName}/>
                        ))}

                        {sliderData && data.live && sliderData.map((slide) => (
                            <CustomSlider value={data.live[slide.key]} deviceId={data.value.id} data={slide}
                                          displayName={slide.displayName}/>
                        ))}

                        <div style={{display:'flex', justifyContent: 'space-around'}}>
                            {switchBtn && data.live && switchBtn.map((slide) => (
                                <SwitchButton value={data.live[slide.key]} deviceId={data.value.id} data={slide}
                                              displayName={slide.displayName}/>
                            ))}
                        </div>



                        {data.live && (
                            <table style={{width: '100%', marginTop: '12px'}}>
                                <tbody style={{padding: '0'}}>
                                {
                                    data.value.attributes.map(attribute => (
                                        (attribute.type === "DATA|MAIN") && (
                                            <tr key={attribute.id} >
                                                <td>{attribute["displayName"]}</td>
                                                <td>{data.live[attribute["key"]]}</td>
                                                <td>{attribute["units"]}</td>
                                            </tr>
                                        )
                                    ))
                                }
                                {
                                    data.value.attributes.map(attribute => (
                                        (attribute.type === "ACTION|OUT") && (
                                            <tr style={{width: '100%'}} key={attribute.id}>
                                                <th colSpan="3">
                                                    <Button aria-label="delete" style={{width: '100%'}}
                                                            onClick={() => handleAction(attribute)}>
                                                        {attribute["displayName"]}
                                                    </Button>
                                                </th>
                                            </tr>
                                        )
                                    ))
                                }
                                </tbody>
                            </table>
                        )}
                    </CardContent>
                </Card>

                <CustomModal isOpen={isModalOpen} onClose={handleCloseModal} device={data.value}/>
            </div>
            <Handle
                type="source"
                position={Position.Right}
                id="b"
                style={{top: 30}}
                isConnectable={isConnectable}
            />
        </div>
    );
}


export function MainNode({data, isConnectable}) {
    const { devices, numOfDevices, chartNodes } = data.value;

    // Filter charts from the devices
    const charts = useMemo(() =>
        devices.filter(device =>
            device.showCharts === true
        ), [devices]);





    // Memoize the node and chart IDs since they don't change during render
    const nodeIds = useMemo(() =>
        Array.from({ length: numOfDevices }, (_, i) => `main-node-${i}`), [numOfDevices]);

    const chartIds = useMemo(() =>
        Array.from({ length: chartNodes }, (_, i) => `chart-node-${i}`), [chartNodes]);

    // Fetch chart data when device or attribute is selected


    return (
        <div className="text-updater-node">
            <div style={{ borderRadius: '16px' }}>
                <Card elevation={0} style={{
                    padding: '0px',
                    minHeight: '500px',
                    minWidth: '400px',
                    borderRadius: '18px',
                    boxShadow: '0 0 50px 15px #1c1c1c'
                }}>
                    {/* Render Handles for Chart Nodes */}
                    {chartIds.map((id, index) => (
                        <Handle
                            key={id}
                            type="target"
                            position={Position.Right}
                            id={id}
                            style={{ top: 10 + index * 50 }}
                            isConnectable={isConnectable}
                        />
                    ))}
                    {/*<Typography style={{color:'white', margin: '20px'}} >*/}
                    {/*    Device*/}
                    {/*</Typography>*/}


                    <CardContent style={{ padding: '12px', marginLeft: '15px', display: 'grid',
                        gridTemplateColumns: 'repeat(2, 1fr)', /* 4 columns */
                        gap: '10px', /* Space between items */
                         }}>
                        {charts.map((device, index) => (
                            <BarChartComp chartDevice={device} />
                        ))}
                    </CardContent>

                    {/*<Stack style={{ display: 'flex', alignItems: 'center', marginLeft: '20px', marginRight: '20px' }}>*/}
                    {/*   */}
                    {/*</Stack>*/}

                    {/* Render Handles for Main Nodes */}
                    {nodeIds && nodeIds.map((id, index) => (
                        <Handle
                            key={id}
                            type="target"
                            position={Position.Left}
                            id={id}
                            style={{ top: 140 + index * 50 }}
                            isConnectable={isConnectable}
                        />
                    ))}
                </Card>
            </div>
        </div>
    );
}

function BarChartComp({chartDevice}) {
    // Initialize state for the chart data and the selected device/attribute
    const [chartData, setChartData] = useState({
        dataKey: "p",
        data: [0],
        label: "p",
        attributes: [],
        timestamps: [""],
        unit: ""
    });

    const visibleAttr = chartDevice?.attributes.filter(attr => attr.visible === true);

    console.log("visibleAttr", visibleAttr)
    // const [deviceId, setDeviceId] = useState(0);
    const [selectedAttribute, setAttribute] = useState(visibleAttr[0]?.key || "");
    // const [chartDevice, setChartDevice] = useState(chartDevice?.id || "");
    // const [deviceName, setDeviceName] = useState(chartDevice?.name || "");

    useEffect(() => {
        const fetchChartData = async () => {
            try {
                const data = await getChartData(chartDevice.id, selectedAttribute);
                console.log("chart data for device:", chartDevice, "attribute:", selectedAttribute, data);
                setChartData(data);

            } catch (err) {
                console.error("Failed to fetch chart data", err);
            }
        };

        fetchChartData();
    }, [selectedAttribute]);

    // Handle selecting a device
    // const handleChartDevice = useCallback((deviceId) => {
    //     const selectedDevice = charts[deviceId];
    //     setDeviceId(deviceId);
    //     setChartDevice(selectedDevice.id);
    //     setDeviceName(selectedDevice.name);
    //     setAttribute(selectedDevice.attributes[0]?.key || "");
    // }, [chartDevice]);

    // Handle selecting an attribute
    const handleAttribute = useCallback((key) => {
        setAttribute(key);
        console.log("Selected attribute:", key);
    }, []);

    const valueFormatter = (value) => {
        return `${value} ${chartData.unit}`;
    };

    const chartSetting = useMemo(() => ({
        yAxis: [
            {
                label: chartData.label,
            },
        ],
        series: [{ dataKey: chartData.dataKey,label: 'Showing last 12 Hours data', valueFormatter }],
        height: 250,
        width: 600,
        sx: {
            [`& .${axisClasses.directionY} .${axisClasses.label}`]: {
                transform: 'translateX(-10px)',
            },
        },
    }), [chartData]);

    return (
        <div>
            <BarChart className="nodrag"
                      dataset={chartData.data}
                      // barLabel={(item, context) => {
                      //     return item.value?.toString();
                      // }}
                      colors={['#757575']}
                      xAxis={[{ scaleType: 'band', dataKey: chartData.dataKey, data: chartData.timestamps }]}
                      borderRadius={10}
                      {...chartSetting}
            />
            <div style={{ display: 'flex', alignItems: 'center' }}>
                <Typography>Attributes</Typography>
                <div style={{ margin: '14px' }}>
                    {chartData.attributes.map((name) => (
                        <Chip
                            key={name}
                            className="nodrag"
                            clickable
                            onClick={() => handleAttribute(name)}
                            style={{ margin: '4px' }}
                            label={name}
                            color="info"
                        />
                    ))}
                </div>
            </div>
        </div>
    );
}
