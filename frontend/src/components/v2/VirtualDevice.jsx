import React, {useEffect, useMemo, useRef, useState} from "react";
import {Card, LinearProgress, Skeleton} from "@mui/material";
import Typography from "@mui/material/Typography";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import ChartDetail from "../charts/ChartDetail.jsx";
import WledDevices from "../device_types/WledDevices.jsx";
import HVACDevices from "../device_types/HVACDevices.jsx";
import {CustomModal} from "../home/CustomModal.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import {useCardGlowEffect} from "../../utils/useCardGlowEffect.jsx";
import '../../App.css'
import {MapView} from "../charts/MapView.jsx";
import SpotifyPlayer from "../integrations/SpotifyPlayer.jsx";
import RadarRoomView from "../device_types/RadarRoomView.jsx";
import ProtectedView from "./ProtectedView.jsx";
import {getVirtualDeviceLockedState, unlockVirtualDevice} from "../../services/apis.jsx";

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


export const VirtualDevice = React.memo(({id, data, isConnectable, selected}) => {
    const {messages} = useDeviceLiveData();
    const {devices, loading, error} = useCachedDevices();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [actionAck, setActionAck] = useState({});
    const [deviceList, setDeviceList] = useState([])
    const [liveData, setLiveData] = useState({});
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
    const showGlow = false;
    const [isLocked, setLocked] = useState(false);
    const glowColor = '#ff0000';
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef, false, glowColor);
    const [unlockError, setUnlockError] = useState('');
    const [unlocking, setUnlocking] = useState(false);

// replaces the commented-out block
    useEffect(() => {
        let cancelled = false;
        getVirtualDeviceLockedState(id)
            .then(locked => {
                if (!cancelled) setLocked(locked);
            })
            .catch(e => console.error("Failed to fetch lock state for", id, e));
        return () => {
            cancelled = true;
        };
    }, [id]);

    const handleUnlock = async (pin) => {
        setUnlocking(true);
        setUnlockError('');
        try {
            await unlockVirtualDevice(id, pin);
            setLocked(false);
        } catch (e) {
            setUnlockError(e?.response?.status === 401 ? 'Incorrect PIN' : 'Unable to unlock device');
        } finally {
            setUnlocking(false);
        }
    };


    // console.log("att", combineAttributes(attributes))
    useEffect(() => {
        if (deviceIds && devices) {
            const res = devices.filter(d => deviceIds.includes(d.id));
            // console.log("res", res)
            setDeviceList(res);


        }

    }, [devices])

    const {
        hvacDevices,
        wledDevices,
        systemDevices,
        chartDevices,
        otherDevices,
        mapDevices,
        mediaDevices,
        radarDevices,
    } = useMemo(() => {
        const grouped = {
            hvacDevices: [],
            wledDevices: [],
            systemDevices: [],
            chartDevices: [],
            otherDevices: [],
            mapDevices: [],
            mediaDevices: [],
            radarDevices: [],
        };

        for (const attr of deviceList) {
            if (attr.category === 'HVAC') grouped.hvacDevices.push(attr);
            else if (attr.type === 'WLED') grouped.wledDevices.push(attr);
            else if (attr.type === 'System') grouped.systemDevices.push(attr);
            else if (attr.type === 'CHART') grouped.chartDevices.push(attr);
            else if (attr.category === 'SENSOR|GPS') grouped.mapDevices.push(attr);
            else if (attr.category === 'MEDIA') grouped.mediaDevices.push(attr);
            else if (attr.category === 'RADAR') grouped.radarDevices.push(attr);
            else grouped.otherDevices.push(attr);
        }

        return grouped;
    }, [deviceList]);

    const mapDeviceIds = useMemo(() => mapDevices.map(d => d.id), [mapDevices]);

    useEffect(() => {
        if (deviceIds.length > 1)
            return;
        if (deviceIds[0] === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
        }
    }, [messages, deviceIds]);


    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    if (isLocked)
        return (
            <div style={{minHeight: height, height: '100%', minWidth: width}}>
                <ProtectedView onUnlock={handleUnlock} error={unlockError} loading={unlocking}/>
            </div>
        )
    return (
        <>
            {/*<NodeResizer*/}
            {/*    color="#ff0000"*/}
            {/*    isVisible={selected}*/}
            {/*    width={width}*/}
            {/*    minHeight={height}*/}
            {/*/>*/}
            {/*<ProtectedView/>*/}

            <Card
                ref={cardRef}
                className={`card-glow-container ${
                    showGlow ? 'glow-active' : ''
                }`}
                variant="elevated"
                style={{
                    background: 'transparent',
                    boxShadow: 'rgb(30 30 30) 0px 0px 36px 10px inset',
                    // border: `1px solid ${C.border}`,
                    backdropFilter: 'blur(4px)',
                    backgroundColor: 'rgb(0 0 0 / 20%)',
                    minHeight: height, height: '100%', minWidth: width,
                    borderRadius: '10px', padding: '0px',
                }}>
                <div className="card-glow"/>
                <div
                    style={{
                        width: '100%',
                        // display:'flex',
                        alignItems: 'center',
                        padding: '0px',
                        // paddingBottom: '6px',
                        justifyContent: 'center'
                    }}>
                    {actionAck?.command === 'reboot' && (
                        <Card elevation={4} style={{borderRadius: '8px', padding: '8px', margin: '2px'}}>
                            <Typography>Rebooting...</Typography>
                            <LinearProgress/>
                        </Card>
                    )}

                    <div style={{display: 'flex', justifyContent: 'center'}}>
                        {deviceList.length !== 0 ? (
                            <div>
                                <WledDevices devices={wledDevices} messages={messages}/>
                                <HVACDevices
                                    devices={hvacDevices} messages={messages}
                                />
                                {/*{systemDevices.length > 0 &&*/}
                                {/*    <SystemDevice devices={systemDevices} messages={messages}/>}*/}
                            </div>
                        ) : (
                            <Skeleton style={{background: 'transparent'}} animation="wave" variant="rectangular"
                                      width={width} height={height}/>
                            // <LinearProgress color="inherit"/>
                        )}
                    </div>
                    {mapDevices.length > 0 && (
                        <MapView lat={liveData.LAT} lng={liveData.LONG} h={height} w={width}
                                 deviceIds={mapDeviceIds}/>
                    )}
                    {radarDevices.length > 0 && (
                        <RadarRoomView sensorData={liveData}/>
                    )}
                    {chartDevices.map(device => (
                        <div key={device.id}>
                            <ChartDetail deviceId={device.attributes[0].extras.id} name={""} width={900}
                                         height={310}
                                         props={{
                                             backgroundColor: "transparent"
                                         }}
                                         deviceAttributes={devices.filter(d => d.id === device.attributes[0].extras.id)[0].attributes}/>
                        </div>
                    ))}
                    {mediaDevices.map(device => (
                        <SpotifyPlayer device={device}/>
                    ))}
                    {isModalOpen && (
                        <CustomModal
                            map={null}
                            isOpen={isModalOpen}
                            messages={messages}
                            onClose={handleCloseModal}
                            devices={deviceList}
                            virtualId={id}
                            version="v2"
                        />
                    )}

                </div>
            </Card>
        </>
    )
});