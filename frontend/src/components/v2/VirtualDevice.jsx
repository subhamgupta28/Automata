import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import React, {useEffect, useMemo, useState} from "react";
import {Button, Card, CardContent, LinearProgress} from "@mui/material";
import Typography from "@mui/material/Typography";
import {NodeResizer} from "@xyflow/react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {Presets} from "../charts/Presets.jsx";
import {SwitchButton} from "../charts/SwitchButton.jsx";
import LightBulbCard from "./LightBulbCard.jsx";

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

    // console.log(attributes);
    // console.log("att", combineAttributes(attributes))
    useEffect(() => {
        if (deviceIds && devices) {
            const res = devices.filter(d => deviceIds.includes(d.id));
            // console.log("res", res)
            setDeviceList(res);

        }

    }, [devices])


    return (
        <>
            <NodeResizer
                color="#ff0000"
                isVisible={selected}
                minWidth={width}
                minHeight={height}
            />


            <Card style={{minHeight: '200px', height: '100%', minWidth: width, borderRadius: '12px'}}>

                <div
                    style={{
                        padding: '0px', width: '100%', height: '100%', margin: '0px',
                        borderRadius: '12px 12px 0px 0px',
                        background: 'transparent',
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
                        {deviceList.map(device => (
                            <div key={device.id}>
                                {device.type === "WLED" && (
                                    <Wled device={device} messages={messages}/>
                                )}
                            </div>
                        ))}
                    </div>



                </div>
            </Card>
        </>
    )
}

const Wled = ({device, messages}) => {
    // console.log("wled", device)
    const [liveData, setLiveData] = useState(device.lastData);
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
    return (
        <div>

            {/*{sliderData.map((s) => (*/}
            {/*    <CustomSlider*/}
            {/*        key={s.key}*/}
            {/*        value={liveData?.[s.key]}*/}
            {/*        deviceId={device.id}*/}
            {/*        type={device.type}*/}
            {/*        data={s}*/}
            {/*        displayName={s.displayName}*/}
            {/*    />*/}
            {/*))}*/}

            {/*{presetButtons.map((p) => (*/}
            {/*    <Presets*/}
            {/*        key={p.key}*/}
            {/*        value={liveData}*/}
            {/*        deviceId={device.id}*/}
            {/*        type={device.type}*/}
            {/*        data={p}*/}
            {/*        displayName={p.displayName}*/}
            {/*    />*/}
            {/*))}*/}
            {switchButtons.map((s) => (
                <LightBulbCard key={s.key} value={liveData?.[s.key]} name={device.name}/>
                // <SwitchButton
                //     key={s.key}
                //     value={liveData?.[s.key]}
                //     deviceId={device.id}
                //     data={s}
                //     type={device.type}
                //     displayName={s.displayName}
                // />
            ))}

        </div>
    )
}