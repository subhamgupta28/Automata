import React, {useEffect, useState} from "react";
import {
    refreshDeviceById,
    sendAction,
    updateAttrCharts,
    updateAttribute,
    updateShowCharts
} from "../../services/apis.jsx";
import {Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Switch, Tab, Tabs} from "@mui/material";
import Stack from "@mui/material/Stack";
import Divider from "@mui/material/Divider";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import Checkbox from "@mui/material/Checkbox";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {MapView} from "../charts/MapView.jsx";
import {SwitchButton} from "../charts/SwitchButton.jsx";
import PersonTracker from "../charts/PersonTracker.jsx";
import Typography from "@mui/material/Typography";
import {CustomTabPanel} from "../dashboard/AnalyticsView.jsx";
import {DeviceLockPanel} from "../device_types/DeviceLockPanel.jsx";

dayjs.extend(relativeTime);

export const CustomModal = ({isOpen, onClose, devices, messages, map, virtualId = "", version = "v1"}) => {


    const [value, setValue] = useState(0);
    const fetchData = async (id) => {
        try {
            await refreshDeviceById(id);
        } catch (err) {
            // Handle error gracefully
        }
    };


    const handleAction = async (action, device) => {
        try {
            await sendAction(device.id, {key: action, [action]: true, device_id: device.id, direct: true}, device.type);
        } catch (err) {
            console.log(err)
            // Handle action error
        }
    };

    const handleReboot = (device) => handleAction("reboot", device);

    const handleUpdate = (id) => fetchData(id);

    const handleChange = (event, newValue) => {
        setValue(newValue);
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
            <DialogTitle>
                Device Settings
                {devices && devices.length > 1 && (
                    <Box style={{
                        background: 'transparent',
                        backdropFilter: 'blur(4px)',
                        borderRadius: '8px',
                        backgroundColor: 'rgb(255 255 255 / 8%)',
                    }}>
                        <Tabs value={value}
                              onChange={handleChange}
                              aria-label="devices">
                            {devices?.map((device) => (
                                <Tab label={device.name}/>
                            ))}
                        </Tabs>
                    </Box>
                )}
            </DialogTitle>
            <DialogContent style={{overflow: 'auto',}}>


                {devices?.map((device, i) => (
                    <CustomTabPanel value={value} index={i}>
                        <ModelContent
                            device={device} onClose={onClose} messages={messages}
                            map={map} version={version}
                            handleAction={handleAction}
                        />
                    </CustomTabPanel>
                ))}
                {virtualId && (
                    <div style={{marginTop: '16px'}}>
                        <DeviceLockPanel vid={virtualId}/>
                    </div>
                )}

            </DialogContent>
            <DialogActions>
                <Button onClick={() => handleReboot(devices[value])}>Reboot</Button>
                <Button style={{marginLeft: '12px'}} onClick={() => handleUpdate(devices[0].id)}>Update</Button>
                <div style={{flexGrow: 1}}/>
                <Button variant="secondary" onClick={onClose}>Close</Button>
                <Button variant="primary" onClick={onClose}>Save Changes</Button>
            </DialogActions>
        </Dialog>
    )
}

const formatLiveValue = (value) => {
    if (value === undefined || value === null || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'On' : 'Off';
    if (typeof value === 'number') return Number.isInteger(value) ? String(value) : String(Math.round(value * 100) / 100);
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
};

const VISIBILITY_FLAGS = [
    ['showInDashboard', 'Show in Dashboard'],
    ['analytics', 'Analytics'],
];

const ModelContent = ({device, onClose, messages, map, version, handleAction}) => {
    const [attrs, setAttrs] = useState(device.attributes);
    const [liveData, setLiveData] = useState({});
    const [showCharts, setShowCharts] = useState(device.showCharts);
    const [flags, setFlags] = useState({
        showInDashboard: Boolean(device.showInDashboard),
        analytics: Boolean(device.analytics),
    });

    const handleFlag = async (flag, checked) => {
        setFlags((prev) => ({...prev, [flag]: checked}));
        try {
            await updateAttribute(device.id, flag, checked);
            device[flag] = checked; // keep the shared device object in sync, same as handleAttrUpdate
        } catch (err) {
            setFlags((prev) => ({...prev, [flag]: !checked}));
        }
    };
    const switchBtn = device.attributes.filter((t) => t.type.startsWith("ACTION|MENU|BTN"));
    const sliderData = device.attributes.filter((t) => t.type === "ACTION|MENU|SLIDER" || t.type === "ACTION|SLIDER");
    const radarData = device.attributes.filter((t) => t.type === "DATA|RADAR");
    const switchButtons = device.attributes.filter((t) => t.type === "ACTION|MENU|SWITCH");
    const deviceInfo = device.attributes.filter((t) => t.type === "DATA|INFO");
    const mainData = device.attributes.filter((t) => t.type === "DATA|MAIN");

    useEffect(() => {
        if (device.id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
        }
    }, [messages, device]);

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
        <>

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
                        {device.accessUrl && (
                            <tr>
                                <td>Device URL</td>
                                <td><a href={device.accessUrl} target="_blank"
                                       rel="noopener noreferrer">{device.accessUrl}</a></td>
                            </tr>
                        )}
                        <tr>
                            <td>Status</td>
                            <td>{device.status === 'ONLINE' ? 'Online' : 'Offline'}</td>
                        </tr>
                        <tr>
                            <td>Type</td>
                            <td>{device.type || '—'}</td>
                        </tr>
                        <tr>
                            <td>Registered Node</td>
                            <td>{device.originNode || '—'}</td>
                        </tr>
                        <tr>
                            <td>Mac Address</td>
                            <td>{device.macAddr}</td>
                        </tr>
                        <tr>
                            <td>Update Interval</td>
                            <td>
                                {device.updateInterval}
                                {device.updateInterval ? ` (${device.updateInterval / 1000 / 60} min)` : ''}
                            </td>
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
                        {VISIBILITY_FLAGS.map(([flag, label]) => (
                            <tr key={flag}>
                                <td>{label}</td>
                                <td>
                                    <Switch
                                        size="small"
                                        checked={flags[flag]}
                                        onChange={(e) => handleFlag(flag, e.target.checked)}
                                    />
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                    <Divider style={{marginTop: '10px', marginBottom: '5px'}}></Divider>

                    <div style={{display: "flex", justifyContent: "flex-start", gap: "12px", flexWrap: "wrap"}}>
                        {switchBtn.map((btn, i) => (
                            <Button
                                key={btn["key"]}
                                aria-label="delete"
                                variant="contained"
                                style={{marginTop: "12px"}}
                                onClick={() => handleAction(btn["key"], device)}
                            >
                                {btn["displayName"]}
                            </Button>
                        ))}
                    </div>


                    <div style={{width: '50%'}}>
                        {sliderData && liveData && sliderData.map((slide) => (
                            <CustomSlider key={slide.key} value={liveData[slide.key]} deviceId={device.id}
                                          type={device.type} data={slide}
                                          displayName={slide.displayName}/>
                        ))}
                    </div>

                    {map && map.length > 0 && liveData && (
                        <MapView lat={liveData.LAT} lng={liveData.LONG} h={300} w='auto'/>
                    )}
                    <div style={{display: "flex", justifyContent: "flex-start", gap: "12px", flexWrap: "wrap"}}>
                        {switchButtons.map((s) => (
                            <SwitchButton
                                key={s.key}
                                value={liveData?.[s.key]}
                                deviceId={device.id}
                                data={s}
                                type={device.deviceType}
                                displayName={s.displayName}
                            />
                        ))}
                    </div>


                    {radarData.length > 0 &&
                        <Card style={{margin: '10px', padding: '10px', borderRadius: '12px'}}>
                            <PersonTracker liveData={liveData} radarData={radarData} canvasHeight={300}
                                           canvasWidth={480}/>
                        </Card>

                        // <CustomRadarChart liveData={liveData} radarData={radarData}/>
                    }
                    <Divider style={{marginTop: '10px', marginBottom: '5px'}}></Divider>
                    Aux Data
                    {liveData && (
                        <div style={{
                            // width: '8%',
                            gridTemplateColumns: 'repeat(4, 1fr)',
                            display: 'grid',
                            gap: '4px',
                            marginTop: '10px'
                        }}>

                            {device?.attributes.map(attribute => (
                                (attribute.type !== "DATA|MAIN" && attribute.type !== "DATA|INFO") ? (
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
                                ) : (version === "v2" && attribute.type === "DATA|MAIN" &&
                                    (
                                        <Card
                                            key={attribute.id}
                                            elevation={0}
                                            style={{
                                                borderRadius: '8px',
                                                padding: '4px',
                                                display: 'flex',
                                                flexDirection: 'column',
                                                justifyContent: 'space-between',
                                                alignItems: 'center'
                                            }}
                                        >

                                            <Typography style={{display: 'flex', fontSize: '18px'}} color="primary"
                                                        fontWeight="bold">
                                                {liveData?.[attribute.key]} {attribute.units}
                                            </Typography>
                                            <Typography>{attribute.displayName}</Typography>
                                        </Card>
                                    )
                                )
                            ))}
                        </div>
                    )}
                    <Divider style={{marginTop: '10px', marginBottom: '5px'}}></Divider>
                    {deviceInfo.length > 0 && (
                        <Typography color="primary">
                            Configuration
                        </Typography>
                    )}

                    <table>
                        <tbody>
                        {deviceInfo.map((t) => (
                            <tr>
                                <td>{t.displayName}</td>
                                <td>{t.units}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>

                    <Divider style={{marginTop: '10px', marginBottom: '5px'}}></Divider>
                    <Typography color="primary">All Data Points</Typography>
                    {attrs.length === 0 ? (
                        <Typography variant="body2" style={{opacity: 0.7}}>
                            This device has not reported any attributes.
                        </Typography>
                    ) : (
                        <div style={{maxHeight: '260px', overflow: 'auto', marginTop: '6px'}}>
                            <table style={{width: '100%'}}>
                                <thead>
                                <tr>
                                    <td>Name</td>
                                    <td>Key</td>
                                    <td>Live Value</td>
                                </tr>
                                </thead>
                                <tbody>
                                {attrs.map((attribute) => {
                                    const value = liveData?.[attribute.key];
                                    return (
                                        <tr key={attribute.id ?? attribute.key}>
                                            <td>
                                                {attribute.displayName}
                                                <div style={{fontSize: '10px', opacity: 0.6}}>{attribute.type}</div>
                                            </td>
                                            <td style={{fontFamily: 'monospace', fontSize: '12px', opacity: 0.8}}>
                                                {attribute.key}
                                            </td>
                                            <td style={{whiteSpace: 'nowrap'}}>
                                                <Typography color="primary" fontWeight="bold" component="span">
                                                    {formatLiveValue(value)}
                                                    {attribute.units && value !== undefined && value !== null ? ` ${attribute.units}` : ''}
                                                </Typography>
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
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

        </>
    )
}