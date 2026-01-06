import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import React, {useEffect, useMemo, useState} from "react";
import {Button, Card, CardContent, CircularProgress, LinearProgress, Menu} from "@mui/material";
import Typography from "@mui/material/Typography";
import {NodeResizer} from "@xyflow/react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {Presets} from "../charts/Presets.jsx";
import {SwitchButton} from "../charts/SwitchButton.jsx";
import LightBulbCard from "./LightBulbCard.jsx";
import SettingsIcon from "@mui/icons-material/Settings";
import IconButton from "@mui/material/IconButton";
import {CustomModal} from "../home/Nodes.jsx";
import ChartDetail from "../charts/ChartDetail.jsx";

export const combineAttributes = (attributesByDevice) => {
    const map = new Map();

    Object.values(attributesByDevice).flat().forEach(attr => {
        if (!map.has(attr.key)) {
            map.set(attr.key, {
                key: attr.key,
                displayName: attr.displayName,
                units: attr.units,
                type: attr.type,
                visible: attr.visible,
                deviceIds: [attr.deviceId],
            });
        } else {
            const existing = map.get(attr.key);

            existing.deviceIds.push(attr.deviceId);

            // If any device has it visible → visible
            existing.visible = existing.visible || attr.visible;
            existing.type = attr.type;

        }
    });

    return Array.from(map.values());
};


export default function VirtualDevice({id, data, isConnectable, selected}) {
    const {messages} = useDeviceLiveData();
    const {devices, loading, error} = useCachedDevices();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [actionAck, setActionAck] = useState({});
    const [deviceList, setDeviceList] = useState([])
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


    // console.log("att", combineAttributes(attributes))
    useEffect(() => {
        if (deviceIds && devices) {
            const res = devices.filter(d => deviceIds.includes(d.id));
            console.log("res", res)
            setDeviceList(res);

        }

    }, [devices])

    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    return (
        <>
            {/*<NodeResizer*/}
            {/*    color="#ff0000"*/}
            {/*    isVisible={selected}*/}
            {/*    minWidth={width}*/}
            {/*    minHeight={height}*/}
            {/*/>*/}


            <Card style={{minHeight: '100px', height: '100%', minWidth: width, borderRadius: '10px',padding: '0px',}}>

                <div
                    style={{
                        padding: '0px', width: '100%', height: '100%', marginTop: '8px',
                        paddingRight: '6px',
                        borderRadius: '12px 12px 0px 0px',
                        background: 'transparent',
                        display: 'flex',
                        justifyContent: 'space-between'
                    }}>
                    <Typography
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            marginLeft: '18px',
                            fontWeight: 'bold',
                            fontSize: '18px',
                            marginRight: '10px'
                        }}
                    >
                        {name}
                    </Typography>
                    <IconButton onClick={handleOpenModal} style={{marginLeft: '8px'}}>
                        <SettingsIcon/>
                    </IconButton>
                </div>
                <div
                    style={{
                        width: '100%',
                        alignItems: 'center',
                        padding: '8px',
                        paddingBottom: '10px',
                        justifyContent: 'center'
                    }}>
                    {actionAck?.command === 'reboot' && (
                        <Card elevation={4} style={{borderRadius: '8px', padding: '8px', margin: '2px'}}>
                            <Typography>Rebooting...</Typography>
                            <LinearProgress/>
                        </Card>
                    )}

                    <div style={{
                        gridTemplateColumns: 'repeat(2, 1fr)',
                        display: 'grid',
                        gap: '4px',
                    }}>
                        {deviceList.length !== 0 ? deviceList.map(device => (
                            <div key={device.id}>
                                {device.type === "WLED" && (
                                    <Wled device={device} messages={messages}/>
                                )}
                            </div>
                        )) : (
                            <CircularProgress color="inherit"/>
                        )}
                    </div>
                    {deviceList.length !== 0 &&
                        deviceList.map(device => (
                            <div key={device.id}>
                                {device.type === "CHART" && (
                                    <ChartDetail deviceId={device.attributes[0].extras.id} name={""} width={500}
                                                 height={220}
                                                 deviceAttributes={devices.filter(d => d.id === device.attributes[0].extras.id)[0].attributes}/>
                                )}
                            </div>
                        ))
                    }
                    {isModalOpen && (
                        <CustomModal
                            map={null}
                            isOpen={isModalOpen}
                            liveData={messages?.data}
                            onClose={handleCloseModal}
                            device={deviceList?.[0]}
                        />
                    )}

                </div>
            </Card>
        </>
    )
}

const DetailMenu = ({anchorEl, setAnchorEl, sliderData, presetButtons, deviceId, deviceType, liveData}) => {

    const open = Boolean(anchorEl);
    const handleClose = () => {
        setAnchorEl(null);
    };
    return (
        <div>
            <Menu
                id="basic-menu"
                style={{
                    borderRadius: '12px',
                    width: '260px'
                }}
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                slotProps={{
                    list: {
                        'aria-labelledby': 'basic-button',
                    },
                }}
            >
                <div style={{padding: '10px'}}>
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
                </div>
            </Menu>
        </div>
    )
}

const Wled = ({device, messages}) => {
    // console.log("wled", device)
    const [liveData, setLiveData] = useState(device.lastData);
    const [anchorEl, setAnchorEl] = React.useState(null);
    useEffect(() => {
        if (device.id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
        }
    }, [messages, device.id]);
    const {
        sliderData,
        switchButtons,
        presetButtons,
    } = useMemo(() => {
        const grouped = {
            sliderData: [],
            switchButtons: [],
            presetButtons: [],
        };

        for (const attr of device.attributes) {
            if (attr.type === 'ACTION|SLIDER') grouped.sliderData.push(attr);
            else if (attr.type.startsWith('ACTION|SWITCH')) grouped.switchButtons.push(attr);
            else if (attr.type.startsWith('ACTION|PRESET')) grouped.presetButtons.push(attr);
        }

        return grouped;
    }, [device.attributes]);

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    return (
        <div>
            <DetailMenu
                anchorEl={anchorEl}
                setAnchorEl={setAnchorEl}
                presetButtons={presetButtons}
                sliderData={sliderData}
                deviceType={device.type}
                deviceId={device.id}
                liveData={liveData}
            />

            {switchButtons.map((s) => (
                <LightBulbCard onClick={handleClick} key={s.key} data={s} lastOnline={device.lastOnline}
                               value={liveData?.[s.key]}
                               name={device.name} deviceId={device.id} type={device.type}/>
            ))}

        </div>
    )
}