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
import ChartDetail from "../charts/ChartDetail.jsx";
import ThermostatCard from "../device_types/ThermostatCard.jsx";
import WledDevices from "../device_types/WledDevices.jsx";
import HVACDevices from "../device_types/HVACDevices.jsx";
import SystemDevice from "../device_types/SystemDevice.jsx";
import {CustomModal} from "../home/CustomModal.jsx";

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

    const {
        hvacDevices,
        wledDevices,
        systemDevices,
        chartDevices,
        otherDevices,
    } = useMemo(() => {
        const grouped = {
            hvacDevices: [],
            wledDevices: [],
            systemDevices: [],
            chartDevices: [],
            otherDevices: [],
        };

        for (const attr of deviceList) {
            if (attr.type === 'HVAC') grouped.hvacDevices.push(attr);
            else if (attr.type === 'WLED') grouped.wledDevices.push(attr);
            else if (attr.type === 'System') grouped.systemDevices.push(attr);
            else if (attr.type === 'CHART') grouped.chartDevices.push(attr);
            else grouped.otherDevices.push(attr);
        }

        return grouped;
    }, [deviceList]);

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


            <Card variant="outlined" style={{
                background: 'transparent',
                // backgroundColor: 'rgb(0 0 0 / 20%)',
                minHeight: height, height: '100%', minWidth: width, borderRadius: '10px', padding: '0px',
            }}>

                <div
                    style={{
                        // padding: '0px', width: '100%', height: '100%',
                        // paddingRight: '6px',
                        // borderRadius: '12px 12px 0px 0px',
                        // background: 'transparent',
                        marginRight:'4px',
                        alignItems:'center',
                        display: 'flex',
                        justifyContent: 'space-between'
                    }}>
                    <Typography
                        variant="body2"
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            marginLeft: '18px',
                            // fontWeight: 'bold',
                            // fontSize: '18px',
                            paddingTop: '12px'
                        }}
                    >
                        {name}
                    </Typography>
                    <IconButton onClick={handleOpenModal} style={{marginLeft: '8px'}} size="small">
                        <SettingsIcon style={{fontSize: '18px'}}/>
                    </IconButton>
                </div>
                <div
                    style={{
                        width: '100%',
                        // display:'flex',
                        alignItems: 'center',
                        padding:'8px',
                        paddingBottom: '10px',
                        justifyContent: 'center'
                    }}>
                    {actionAck?.command === 'reboot' && (
                        <Card elevation={4} style={{borderRadius: '8px', padding: '8px', margin: '2px'}}>
                            <Typography>Rebooting...</Typography>
                            <LinearProgress/>
                        </Card>
                    )}

                    <div>
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
                            <LinearProgress color="inherit"/>
                        )}
                    </div>
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
                    {isModalOpen && (
                        <CustomModal
                            map={null}
                            isOpen={isModalOpen}
                            messages={messages}
                            onClose={handleCloseModal}
                            devices={deviceList}
                            version="v2"
                        />
                    )}

                </div>
            </Card>
        </>
    )
}



