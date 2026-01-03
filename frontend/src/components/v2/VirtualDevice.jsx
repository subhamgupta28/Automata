import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import React, {useEffect, useMemo, useState} from "react";
import {Button, Card, CardContent, LinearProgress} from "@mui/material";
import Typography from "@mui/material/Typography";
import {NodeResizer} from "@xyflow/react";

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
    const [liveData, setLiveData] = useState(data.live);
    const [actionAck, setActionAck] = useState({});
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
        if (id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
            setActionAck(messages.ack);
        }
    }, [messages, id]);


    return (
        <>
            <NodeResizer
                color="#ff0000"
                isVisible={selected}
                minWidth={240}
                minHeight={100}
            />

            <div style={{
                borderRadius: '12px',
                backgroundColor: 'transparent',
                height: '100%',
                backdropFilter: 'blur(3px)'
            }}>
                <Card style={{
                    borderRadius: '12px',
                    background: 'transparent',
                    height:'100%',
                }}>

                    <Card
                        style={{
                            padding: '0px', width: '100%', margin: '0px',
                            borderRadius: '12px 12px 0px 0px',
                            background: 'transparent',
                            backgroundColor: 'rgb(255 255 255 / 10%)',
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
                    </Card>
                    <CardContent
                        style={{
                            width: '285px',
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


                    </CardContent>
                </Card>

            </div>
        </>
    )
}