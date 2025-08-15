import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Handle, Position} from "@xyflow/react";
import '/src/App.css'
import {
    getChartData,
    getPieChartData,
    refreshDeviceById,
    sendAction,
    updateAttrCharts, updateShowCharts
} from "../../services/apis.jsx";
import {
    Alert,
    Button,
    Card,
    CardContent,
    Chip,
    Dialog, DialogActions,
    DialogContent,
    DialogTitle, LinearProgress,
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
import CustomBarChart from "../charts/CustomBarChart.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import CustomLineChart from "../charts/CustomLineChart.jsx";
import CustomRadarChart from "../charts/CustomRadarChart.jsx";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import SmallLineChart from "../charts/SmallLineChart.jsx";
import SmallBarChart from "../charts/SmallBarChart.jsx";
import BoltIcon from '@mui/icons-material/Bolt';
import FlashOffIcon from '@mui/icons-material/FlashOff';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import CloudIcon from '@mui/icons-material/Cloud';
import BrightnessLowIcon from '@mui/icons-material/BrightnessLow';
import BrightnessHighIcon from '@mui/icons-material/BrightnessHigh';
import TemperatureGauge from "../charts/TemperatureGauge.jsx";
import PersonTracker from "../charts/PersonTracker.jsx";

dayjs.extend(relativeTime);


const CustomModal = ({isOpen, onClose, device, liveData, map}) => {
    const [attrs, setAttrs] = useState(device.attributes);
    const [showCharts, setShowCharts] = useState(device.showCharts);
    const fetchData = async () => {
        try {
            await refreshDeviceById(device.id);
        } catch (err) {
            // Handle error gracefully
        }
    };
    const switchBtn = device.attributes.filter((t) => t.type.startsWith("ACTION|MENU|BTN"));
    const sliderData = device.attributes.filter((t) => t.type === "ACTION|MENU|SLIDER");
    const radarData = device.attributes.filter((t) => t.type === "DATA|RADAR");

    const handleAction = async (action) => {
        try {
            await sendAction(device.id, {key: action, [action]: true, device_id: device.id, direct: true}, device.type);
        } catch (err) {
            // Handle action error
        }
    };

    const handleReboot = () => handleAction("reboot");

    const handleUpdate = () => fetchData();

    const handleShowCharts = async (e) => {
        // console.log("Show Charts", e.target.checked);
        await updateShowCharts(device.id, e.target.checked);
        setShowCharts(!e.target.checked);
    }

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
        <Dialog fullWidth maxWidth="md" onClose={onClose} open={isOpen}
                PaperProps={{
                    sx: {
                        backgroundColor: 'rgba(255, 255, 255, 0.0)',
                        backdropFilter: 'blur(8px)',
                    }
                }}
        >
            <DialogTitle>Device Settings</DialogTitle>
            <DialogContent style={{overflow: 'auto',}}>
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
                                <td><a href={"http://" + device.host + ".local"} target="_blank">{device.host}.local</a>
                                </td>
                            </tr>
                            <tr>
                                <td>Mac Address</td>
                                <td>{device.macAddr}</td>
                            </tr>
                            <tr>
                                <td>Update Interval</td>
                                <td>{device.updateInterval}</td>
                            </tr>
                            <tr>
                                <td>Last Online</td>
                                <td>{dayjs(device.lastOnline).format("MMMM D, YYYY h:mm A")}</td>
                                <td>({dayjs(device.lastOnline).fromNow()})</td>
                            </tr>
                            <tr>
                                <td>Registered At</td>
                                <td>{dayjs(device.lastRegistered).format("MMMM D, YYYY h:mm A")}</td>
                                <td>({dayjs(device.lastRegistered).fromNow()})</td>
                            </tr>
                            <tr>
                                <td>Show Charts</td>
                                <td>
                                    <Checkbox
                                        checked={showCharts}
                                        onChange={handleShowCharts}
                                    />
                                </td>
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
                        <div style={{width: '50%'}}>
                            {sliderData && liveData && sliderData.map((slide) => (
                                <CustomSlider key={slide.key} value={liveData[slide.key]} deviceId={device.id}
                                              type={device.type} data={slide}
                                              displayName={slide.displayName}/>
                            ))}
                        </div>
                        {map.length > 0 && liveData && (
                            <MapView lat={liveData.LAT} lng={liveData.LONG} h={300} w='auto'/>
                        )}

                        {radarData.length > 0 &&
                            <Card style={{margin: '10px', padding: '10px', borderRadius: '12px'}}>
                                <PersonTracker liveData={liveData} radarData={radarData} canvasHeight={300}
                                               canvasWidth={480}/>
                            </Card>

                            // <CustomRadarChart liveData={liveData} radarData={radarData}/>
                        }
                        Aux Data
                        {liveData && (
                            <div style={{
                                // width: '8%',
                                gridTemplateColumns: 'repeat(4, 1fr)',
                                display: 'grid',
                                gap: '4px',
                                marginTop: '10px'
                            }}>

                                {device.attributes.map(attribute => (
                                    (attribute.type !== "DATA|MAIN") && (
                                        <Card key={attribute.id} elevation={0} style={{
                                            borderRadius: '8px',
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
                            </div>
                        )}


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

const useCardGlowEffect = (cardRef) => {
    useEffect(() => {
        const card = cardRef.current;
        if (!card) return;

        const handleMouseMove = (e) => {
            const rect = card.getBoundingClientRect();
            const mouseX = e.clientX - rect.left - rect.width / 2;
            const mouseY = e.clientY - rect.top - rect.height / 2;
            let angle = Math.atan2(mouseY, mouseX) * (180 / Math.PI);
            angle = (angle + 360) % 360;
            card.style.setProperty('--start', angle + 60);
        };

        card.addEventListener('mousemove', handleMouseMove);
        return () => card.removeEventListener('mousemove', handleMouseMove);
    }, [cardRef]);
};

export const Device = React.memo(({id, data, isConnectable}) => {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [actionAck, setActionAck] = useState({});
    const [liveData, setLiveData] = useState(data.live);
    const {messages} = useDeviceLiveData();
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef);

    useEffect(() => {
        if (id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
            setActionAck(messages.ack);
        }
    }, [messages, id]);

    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);

    const handleAction = async (attribute) => {
        try {
            const act = attribute.key;
            await sendAction(data.value.id, {
                key: act,
                [act]: 200,
                device_id: data.value.id,
            }, data.value.type);
        } catch (err) {
            // Handle error if needed
        }
    };

    const {
        attributes,
        name,
        status,
        id: deviceId,
        type: deviceType,
    } = data.value;

    const {
        gaugeData,
        sliderData,
        mapData,
        switchButtons,
        presetButtons,
        mainData,
        actionOutButtons,
        radarData
    } = useMemo(() => {
        const grouped = {
            gaugeData: [],
            sliderData: [],
            mapData: [],
            switchButtons: [],
            presetButtons: [],
            mainData: [],
            actionOutButtons: [],
            radarData: [],
        };

        for (const attr of attributes) {
            if (attr.type === 'DATA|GAUGE') grouped.gaugeData.push(attr);
            else if (attr.type === 'ACTION|SLIDER') grouped.sliderData.push(attr);
            else if (attr.type.startsWith('DATA|MAP')) grouped.mapData.push(attr);
            else if (attr.type.startsWith('ACTION|SWITCH')) grouped.switchButtons.push(attr);
            else if (attr.type.startsWith('ACTION|PRESET')) grouped.presetButtons.push(attr);
            else if (attr.type === 'DATA|MAIN') grouped.mainData.push(attr);
            else if (attr.type === 'ACTION|OUT') grouped.actionOutButtons.push(attr);
            else if (attr.type === 'DATA|RADAR') grouped.radarData.push(attr);
        }

        return grouped;
    }, [attributes]);

    const connectionColor = status === 'ONLINE' ? '#84fd49' : '#ff0000';

    return (
        <div className="card-glow-container text-updater-node" ref={cardRef} key={deviceId}>
            <div className="card-glow"/>
            <div style={{
                borderRadius: '12px',
                backgroundColor: 'transparent',
                backdropFilter: 'blur(7px)'
            }}>
                <Card style={{
                    // display: 'flex',
                    borderRadius: '12px',
                    // marginLeft: '2px',
                    // marginRight: '2px',
                    // padding: '4px',
                    // borderColor: '#797878',
                    borderWidth: '0',
                    background: 'transparent',
                    backgroundColor: 'rgb(255 255 255 / 8%)',
                    // boxShadow: 'rgb(255 225 255 / 6%) 0px 0px 50px 15px'
                }}>

                    <Card elevation={10}
                          style={{padding: '2px', width: '100%', margin: '0px', borderRadius: '12px 12px 0px 0px'}}>
                        <Typography
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                marginLeft: '18px',
                                fontWeight: 'bold',
                                marginRight: '10px'
                            }}
                        >
                            {name}
                            <IconButton onClick={handleOpenModal} style={{marginLeft: '8px'}}>
                                <SettingsIcon/>
                            </IconButton>
                        </Typography>
                    </Card>
                    <CardContent
                        style={{
                            width: '260px',
                            alignItems: 'center',
                            padding: '10px',
                            paddingBottom: '16px',
                            justifyContent: 'center'
                        }}>


                        {/*<SmallBarChart messages={liveData} deviceId={deviceId} attributes={mainData.map(t=> t.key)}/>*/}
                        {/*{deviceType==="sensor" && <CustomRadarChart name={deviceId} messages={liveData} deviceId={deviceId} attributes={mainData.map(t=> t.key)}/>}*/}
                        {actionAck?.command === 'reboot' && (
                            <Card elevation={4} style={{borderRadius: '8px', padding: '8px', margin: '2px'}}>
                                <Typography>Rebooting...</Typography>
                                <LinearProgress/>
                            </Card>
                        )}

                        {mapData.length > 0 && liveData && (
                            <MapView lat={liveData.LAT} lng={liveData.LONG} h="280px" w="100%"/>
                        )}

                        {gaugeData.map((g) => (
                            <GaugeChart key={g.key} value={liveData?.[g.key]} maxValue={g.extras?.max}
                                        displayName={g.displayName}/>
                        ))}

                        {radarData.length > 0 &&
                            <PersonTracker liveData={liveData} radarData={radarData}/>
                            // <CustomRadarChart liveData={liveData} radarData={radarData}/>
                        }
                        {sliderData.map((s) => (
                            <CustomSlider
                                key={s.key}
                                value={liveData?.[s.key]}
                                deviceId={deviceId}
                                type={deviceType}
                                data={s}
                                displayName={s.displayName}
                            />
                        ))}

                        {presetButtons.map((p) => (
                            <Presets
                                key={p.key}
                                value={liveData}
                                deviceId={deviceId}
                                type={deviceType}
                                data={p}
                                displayName={p.displayName}
                            />
                        ))}

                        <div style={{
                            gridTemplateColumns: 'repeat(2, 1fr)',
                            display: 'grid',
                            gap: '8px',
                            marginTop: '10px'
                        }}>
                            {mainData.map((m) => (
                                <Card
                                    key={m.id}
                                    elevation={0}
                                    style={{
                                        borderRadius: '8px',
                                        padding: '6px',
                                        // backgroundColor: 'transparent',
                                        // backdropFilter: 'blur(7px)',
                                        borderColor: '#606060',
                                        // borderWidth: '2px',
                                        borderStyle: 'dashed',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}
                                >

                                    <Typography style={{display: 'flex'}} variant="subtitle" color="primary"
                                                fontWeight="bold">
                                        {/*{m.displayName.includes("Temp") && <TemperatureGauge temp={liveData?.[m.key]}/>}*/}
                                        {liveData?.[m.key]} {m.units}
                                    </Typography>
                                    <Typography variant="subtitle2">{m.displayName}</Typography>
                                </Card>
                            ))}
                            {switchButtons.length > 0 && (
                                <div style={{
                                    gridColumn: (mainData.length + actionOutButtons.length + switchButtons.length) % 2 !== 0 ? 'span 2' : undefined,
                                    display: 'flex',
                                    justifyContent: 'space-around',
                                    borderColor: '#606060',
                                    borderStyle: 'dashed',
                                    borderRadius: '8px',
                                }}>
                                    {switchButtons.map((s) => (
                                        <SwitchButton
                                            key={s.key}
                                            value={liveData?.[s.key]}
                                            deviceId={deviceId}
                                            data={s}
                                            type={deviceType}
                                            displayName={s.displayName}
                                        />
                                    ))}
                                </div>
                            )}
                            {actionOutButtons.length > 0 && (
                                <div style={{
                                    gridColumn: (mainData.length + actionOutButtons.length + switchButtons.length) % 2 !== 0 ? 'span 2' : undefined,
                                    display: 'flex',
                                    justifyContent: 'space-around',
                                    borderColor: '#606060',
                                    borderStyle: 'dashed',
                                    borderRadius: '8px',
                                }}
                                >
                                    {actionOutButtons.map((a) => (
                                        <Button key={a.id} onClick={() => handleAction(a)}>
                                            {a.displayName}
                                        </Button>
                                    ))}
                                </div>
                            )}
                        </div>


                        {/*{actionOutButtons.length > 0 && (*/}
                        {/*    <div style={{*/}
                        {/*        width: '100%',*/}
                        {/*        marginTop: '12px',*/}
                        {/*        display: 'flex',*/}
                        {/*        justifyContent: 'center',*/}
                        {/*        alignItems: 'center'*/}
                        {/*    }}*/}
                        {/*    >*/}
                        {/*        {actionOutButtons.map((a) => (*/}
                        {/*            <Button key={a.id} onClick={() => handleAction(a)}>*/}
                        {/*                {a.displayName}*/}
                        {/*            </Button>*/}
                        {/*        ))}*/}
                        {/*    </div>*/}
                        {/*)}*/}
                    </CardContent>
                </Card>

                {isModalOpen && (
                    <CustomModal
                        map={mapData}
                        isOpen={isModalOpen}
                        liveData={liveData}
                        onClose={handleCloseModal}
                        device={data.value}
                    />
                )}
            </div>

            {isConnectable && <Handle
                type="source"
                position={Position.Right}
                id="b"
                style={{
                    top: 30,
                    right: -3,
                    width: '6px',
                    height: '30px',
                    borderColor: connectionColor,
                    borderRadius: '0px 8px 8px 0px',
                    background: connectionColor,
                }}
                isConnectable={isConnectable}
            />}
        </div>
    );
});

export function AlertNode({data, isConnectable}){

    return(
        <div>
            <Alert variant="outlined" severity={data.value.severity}>
                {data.value.message}
            </Alert>
        </div>
    )
}

export function MainNode({data, isConnectable}) {
    const {devices, numOfDevices, chartNodes} = data.value;
    const boxRef = useRef(null);
    useCardGlowEffect(boxRef);
    // Filter charts from the devices
    // console.log("devices", devices)
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
        <div className="text-updater-node card-glow-container" ref={boxRef}>
            <div className="card-glow"></div>
            <div style={{

                background: 'transparent',
                backdropFilter: 'blur(4px)',
            }}>
                <Card variant="outlined" ref={cardRef} elevation={10} style={{
                    padding: '0px',
                    minHeight: '600px',
                    // borderColor:'rgb(255 155 100 / 8%)',
                    // maxWidth: '95%',
                    // borderColor: '#797878',
                    borderWidth: '0',
                    borderRadius: '12px',
                    background: 'transparent',
                    backgroundColor: 'rgb(255 255 255 / 8%)',
                    // boxShadow: 'rgb(255 255 255 / 8%) 0px 0px 50px 15px'
                }}>

                    <CardContent style={{
                        marginLeft: '10px', display: 'grid', marginTop: '15px',
                        marginRight: '50px',
                        gridTemplateColumns: 'repeat(1, 1fr)', /* 4 columns */
                        gap: '10px', /* Space between items */
                    }}>
                        {charts && charts.map((device, index) => (
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
                            style={{
                                top: handleSpacing * (index + 1),
                                background: '#fce02b',
                                width: '6px',
                                height: '35px',
                                borderColor: '#fce02b',
                                left: -4,
                                borderRadius: '8px 0px 0px 8px',
                            }}
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

    // console.log("visibleAttr", visibleAttr, chartDevice.name)
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
        <div className="nodrag">

            {chartData.attributes.length > 1 && (
                <div style={{display: 'flex', flexDirection: 'column', width: '100%'}}>
                    <div style={{display: 'flex'}}>
                        <Typography variant="body" color="primary">{chartDevice.name}</Typography>
                        <Typography style={{marginLeft: '10px'}}>Attributes</Typography>
                    </div>
                    <div style={{padding: '2px', marginLeft: '30px'}}>
                        {chartData.attributes.map((name) => (
                            <Chip
                                key={name}
                                className="nodrag"
                                clickable
                                onClick={() => handleAttribute(name)}
                                style={{margin: '4px'}}
                                label={name.toUpperCase()}
                                // color="primary"
                            />
                        ))}
                    </div>
                </div>
            )}
            {
                visibleAttr && visibleAttr.length < 0 ? (
                    <CustomLineChart chartData={chartData}/>
                    // <CustomPieChart className="nodrag" chartData={chartData}/>
                ) : (
                    <CustomBarChart chartData={chartData}/>
                )
            }


        </div>
    );
}
