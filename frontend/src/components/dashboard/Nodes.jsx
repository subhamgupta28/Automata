import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Handle, Position} from "@xyflow/react";
import {
    getChartData,
    getPieChartData,
    refreshDeviceById,
    sendAction,
    updateAttrCharts
} from "../../services/apis.jsx";
import {
    Button,
    Card,
    CardContent,
    Chip,
    Dialog, DialogActions,
    DialogContent,
    DialogTitle,
} from "@mui/material";
import Typography from "@mui/material/Typography";
import SettingsIcon from '@mui/icons-material/Settings';
import IconButton from "@mui/material/IconButton";
import {GaugeChart} from "../charts/GaugeChart.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {MapView} from "../charts/MapView.jsx";
import Stack from "@mui/material/Stack";
import Checkbox from "@mui/material/Checkbox";
import Divider from "@mui/material/Divider";
import {SwitchButton} from "../charts/SwitchButton.jsx";
import {Presets} from "../charts/Presets.jsx";
import CustomPieChart from "../charts/CustomPieChart.jsx";
import CustomBarChart from "../charts/CustomBarChart.jsx";
import {useDeviceLiveData} from "../../services/WebSocketProvider.jsx";


const CustomModal = ({isOpen, onClose, device}) => {
    const [attrs, setAttrs] = useState(device.attributes);
    const fetchData = async () => {
        try {
            await refreshDeviceById(device.id);
        } catch (err) {
            // Handle error gracefully
        }
    };
    const switchBtn = device.attributes.filter((t) => t.type.startsWith("ACTION|MENU|BTN"));

    const handleAction = async (action) => {
        try {
            await sendAction(device.id, {key: action, [action]: true, device_id: device.id, direct: true}, device.type);
        } catch (err) {
            // Handle action error
        }
    };

    const handleReboot = () => handleAction("reboot");

    const handleUpdate = () => fetchData();

    const handleAttrUpdate = async (attribute) => {
        try {
            const updatedAttribute = {...attribute, visible: !attribute.visible}; // Toggle the 'visible' property
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
            <DialogContent style={{overflow: 'auto'}}>
                <Stack direction="row" divider={<Divider orientation="vertical" flexItem/>} spacing={2}>
                    <div style={{width: '65%'}}>
                        {/* make the table strike border*/}
                        <table>
                            <thead>
                            <tr>
                                <td>Params</td>
                                <td>Value</td>
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                <td>Access URL</td>
                                <td>{device.accessUrl}</td>
                            </tr>
                            <tr>
                                <td>Mac Address</td>
                                <td>{device.macAddr}</td>
                            </tr>
                            <tr>
                                <td>Update Interval</td>
                                <td>{device.updateInterval}</td>
                            </tr>
                            </tbody>
                        </table>

                        {switchBtn.map((btn) => (
                            <Button key={btn["key"]} aria-label="delete" variant="contained"
                                    style={{marginLeft: "12px", marginTop: "12px"}}
                                    onClick={() => handleAction(btn["key"])}>
                                {btn["displayName"]}
                            </Button>
                        ))}


                    </div>
                    <div style={{width: '30%'}}>
                        <table style={{marginTop: '12px'}}>
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
                <Button style={{marginLeft: '12px'}} onClick={handleUpdate}>Update</Button>
                <div style={{flexGrow: 1}}/>
                <Button variant="secondary" onClick={onClose}>Close</Button>
                <Button variant="primary" onClick={onClose}>Save Changes</Button>
            </DialogActions>
        </Dialog>
    );
};

const DeviceItem = React.memo(({device}) => {
    return <li>{device.name}: {device.data}</li>;
});

export const Device = React.memo(({id, data, isConnectable}) => {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [liveData, setLiveData] = useState(data.live);
    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    const {messages} = useDeviceLiveData();
    let color;
    const gaugeData = data.value.attributes.filter((t) => t.type === "DATA|GAUGE");
    const sliderData = data.value.attributes.filter((t) => t.type === "ACTION|SLIDER");
    const map = data.value.attributes.filter((t) => t.type.startsWith("DATA|MAP"));
    const switchBtn = data.value.attributes.filter((t) => t.type.startsWith("ACTION|SWITCH"));
    const presets = data.value.attributes.filter((t) => t.type.startsWith("ACTION|PRESET"));
    // console.log("presets", presets)

    useEffect(() => {
        if (id === messages.deviceId) {
            setLiveData(messages.data && messages.data);
        }
    }, [messages]);

    const handleAction = (attribute) => {
        const send = async () => {
            try {
                let act = attribute.key;
                await sendAction(data.value.id, {
                    "key": attribute.key,
                    [act]: 200,
                    "device_id": data.value.id
                }, data.value.type);
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
        <div className="text-updater-node" key={data.value.id}>

            <div style={{borderRadius: '12px'}}>
                <Card elevation={0} style={{
                    display: 'flex',
                    borderRadius: '12px',
                    marginLeft: '2px',
                    marginRight: '2px',
                    padding: '4px',
                    boxShadow: 'rgb(255 255 255 / 6%) 0px 0px 50px 15px'
                }}>

                    <CardContent
                        style={{
                            width: '200px',
                            alignItems: 'center',
                            paddingTop: '6px',
                            paddingBottom: '6px',
                            justifyContent: 'center'
                        }}>
                        <Typography style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                            {data.value.name}
                            <IconButton onClick={handleOpenModal} variant='text' style={{marginLeft: '8px'}}>
                                <SettingsIcon/>
                            </IconButton>
                        </Typography>


                        {map.length > 0 && liveData && (
                            <MapView lat={liveData.LAT} lng={liveData.LONG}/>
                        )}

                        {gaugeData && liveData && gaugeData.map((gauge) => (
                            <GaugeChart key={gauge.key} value={liveData[gauge.key]} maxValue={gauge.extras.max}
                                        displayName={gauge.displayName}/>
                        ))}

                        {sliderData && liveData && sliderData.map((slide) => (
                            <CustomSlider key={slide.key} value={liveData[slide.key]} deviceId={data.value.id}
                                          type={data.value.type} data={slide}
                                          displayName={slide.displayName}/>
                        ))}
                        {presets && liveData && presets.map((slide) => (
                            <Presets key={slide.key} value={liveData} deviceId={data.value.id} type={data.value.type}
                                     data={slide}
                                     displayName={slide.displayName}/>
                        ))}


                        <div style={{
                            gridTemplateColumns: 'repeat(2, 1fr)',
                            display: 'grid',
                            gap: '10px',
                            marginTop: '10px'
                        }}>
                            {data.value.attributes.map(attribute => (
                                (attribute.type === "DATA|MAIN") && (
                                    <Card key={attribute.id} style={{
                                        borderRadius: '12px',
                                        padding: '6px',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}>
                                        <Typography
                                            variant='subtitle2'>{liveData && liveData[attribute["key"]]} {attribute["units"]}</Typography>
                                        <Typography variant="subtitle2">{attribute["displayName"]}</Typography>
                                    </Card>
                                )
                            ))}
                            {switchBtn && (
                                <div style={{display: 'flex', justifyContent: 'space-around'}}>
                                    {liveData && switchBtn.map((slide) => (
                                        <SwitchButton key={slide.key} value={liveData[slide.key]}
                                                      deviceId={data.value.id} data={slide} type={data.value.type}
                                                      displayName={slide.displayName}/>
                                    ))}
                                </div>
                            )}

                        </div>


                        {liveData && (
                            <div style={{
                                width: '100%',
                                marginTop: '12px',
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center'
                            }}>
                                {
                                    data.value.attributes.map(attribute => (
                                        (attribute.type === "ACTION|OUT") && (
                                            <Button key={attribute.id} aria-label="delete"
                                                    onClick={() => handleAction(attribute)}>
                                                {attribute["displayName"]}
                                            </Button>
                                        )
                                    ))
                                }
                            </div>
                        )}
                    </CardContent>
                </Card>

                <CustomModal isOpen={isModalOpen} onClose={handleCloseModal} device={data.value}/>
            </div>
            <Handle
                type="source"
                position={Position.Right}
                id="b"
                style={{top: 30, width: '10px', height: '10px', background: color}}
                isConnectable={isConnectable}
            />
        </div>
    );
});


export function MainNode({data, isConnectable}) {
    const {devices, numOfDevices, chartNodes} = data.value;

    // Filter charts from the devices
    const charts = useMemo(() =>
        devices.filter(device =>
            device.showCharts === true
        ), [devices]);


    // Memoize the node and chart IDs since they don't change during render
    const nodeIds = useMemo(() =>
        Array.from({length: numOfDevices}, (_, i) => `main-node-${i}`), [numOfDevices]);

    const chartIds = useMemo(() =>
        Array.from({length: chartNodes}, (_, i) => `chart-node-${i}`), [chartNodes]);

    // Fetch chart data when device or attribute is selected
    const cardRef = useRef(null);
    const [cardHeight, setCardHeight] = useState(550);

    // Calculate top position for handles
    const handleSpacing = cardHeight / (numOfDevices + 1); // +1 to avoid handles being on the very top or bottom


    return (
        <div className="text-updater-node">
            <div style={{borderRadius: '16px'}}>
                <Card ref={cardRef} elevation={0} style={{
                    padding: '0px',
                    minHeight: '600px',
                    // maxWidth: '95%',
                    borderRadius: '18px',
                    // backgroundColor:'transparent',
                    // backdropFilter: 'blur(1px)',
                    boxShadow: 'rgb(255 255 255 / 8%) 0px 0px 50px 15px'
                }}>

                    <CardContent style={{
                        marginLeft: '15px', display: 'grid', marginTop: '10px',
                        gridTemplateColumns: 'repeat(1, 1fr)', /* 4 columns */
                        gap: '10px', /* Space between items */
                    }}>
                        {charts.map((device, index) => (
                            <BarChartComp key={device.id} chartDevice={device}/>
                        ))}

                    </CardContent>

                    {/*<Stack style={{ display: 'flex', alignItems: 'center', marginLeft: '20px', marginRight: '20px' }}>*/}
                    {/*   */}
                    {/*</Stack>*/}

                    {/* Render Handles for Main Nodes */}
                    {nodeIds.map((id, index) => (
                        <Handle
                            key={id}
                            type="target"
                            position={Position.Left}
                            id={id}
                            style={{top: handleSpacing * (index + 1), width: '10px', height: '10px', background: '#fce02b'}}
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
        data: [{p: 0}],
        label: "p",
        attributes: [],
        timestamps: [""],
        unit: ""
    });

    const visibleAttr = chartDevice?.attributes.filter(attr => attr.visible === true);

    // console.log("visibleAttr", visibleAttr)
    // const [deviceId, setDeviceId] = useState(0);
    const [selectedAttribute, setAttribute] = useState(visibleAttr[0]?.key || "");
    // const [chartDevice, setChartDevice] = useState(chartDevice?.id || "");
    // const [deviceName, setDeviceName] = useState(chartDevice?.name || "");

    useEffect(() => {
        const fetchChartData = async () => {
            try {
                // console.log(visibleAttr.length)
                if (visibleAttr.length <= 1) {
                    const data = await getPieChartData(chartDevice.id);
                    setChartData(data);
                } else {
                    const data = await getChartData(chartDevice.id, selectedAttribute);
                    // console.log("data", data)
                    setChartData(data);
                }


            } catch (err) {
                console.error("Failed to fetch chart data", err);
            }
        };

        fetchChartData();
    }, [selectedAttribute]);

    // Handle selecting an attribute
    const handleAttribute = useCallback((key) => {
        setAttribute(key);
        // console.log("Selected attribute:", key);
    }, []);


    return (
        <div>
            {chartDevice.name}
            {
                visibleAttr.length <= 1 ? (
                    <CustomPieChart className="nodrag" chartData={chartData}/>
                ) : (
                    <CustomBarChart chartData={chartData}/>
                )
            }
            {chartData.attributes.length > 1 && (
                <div style={{display: 'flex', alignItems: 'center', flexDirection: 'column', width: '100%'}}>
                    <Typography>Attributes</Typography>
                    <div style={{margin: '14px'}}>
                        {chartData.attributes.map((name) => (
                            <Chip
                                key={name}
                                className="nodrag"
                                clickable
                                onClick={() => handleAttribute(name)}
                                style={{margin: '4px'}}
                                label={name}
                                // color="info"
                            />
                        ))}
                    </div>
                </div>
            )}

        </div>
    );
}
