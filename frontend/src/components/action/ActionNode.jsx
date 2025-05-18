import {Handle, Position, useHandleConnections, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";


const actionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #0288D1',
};

export const ActionNode= ({id, data, isConnectable}) => {
    const {updateNodeData, setEdges, setNodes} = useReactFlow();
    let actionData = data.actionData || {
        key: '',
        data: '',
        name: '',
        deviceId: ''
    };
    const [selectedDevice, setSelectedDevice] = useState({id: actionData.deviceId, name: ''});
    const {devices, loading, error} = useCachedDevices();
    const [name, setName] = useState(actionData.name);
    const [value, setValue] = useState(actionData.data);
    const [key, setKey] = useState(actionData.key);

    const connections = useHandleConnections({
        type: 'target',
        id: 'b'
    });

    useEffect(() => {
        const ad = data.actionData;
        if (ad){
            setName(ad.name);
            setValue(ad.data);
            setKey(ad.key);
        }

        if (devices) {
            if (actionData.deviceId) {
                const device = devices.filter((d) => d.id === actionData.deviceId);
                setSelectedDevice(device[0]);
            } else {
                setSelectedDevice(devices[0]);
            }
        }
    }, [data.value, devices, data.actionData])
    const handleTriggerKey = (e, select) => {
        if (select === 'name') {
            // setName(e.target.value);
        } else if (select === 'data') {
            setValue(e.target.value);
        } else if (select === 'key') {
            setKey(e.target.value);
        }

    }

    const selectDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        setName(()=>selectedDevice.name);
        setSelectedDevice(dev);
    }

    useEffect(() => {
        updateNodeData(id, {
            actionData: {
                deviceId: selectedDevice.id,
                key: key,
                name: selectedDevice.name,
                data: value,
                isEnabled: connections.length > 0
            }
        })
    }, [selectedDevice, key, value, name, connections]);

    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }

    return (
        <Card style={actionStyle}>
            <Handle
                style={{width: '18px', height: '18px', background: '#0288D1'}}
                type="target"
                position={Position.Left}
                id="b"
                isConnectable={isConnectable}
            />

            <div style={{margin: '2px'}}>
                <IconButton onClick={() => deleteNode(id)} style={{position: 'absolute', top: '0', right: '0'}}>
                    <DeleteIcon/>
                </IconButton>
                <FormControl fullWidth sx={{marginTop: 3}} className='nodrag'>
                    <InputLabel>Trigger Device</InputLabel>
                    <Select
                        variant='outlined'
                        size='small'
                        value={selectedDevice.id}
                        label="Action Device"
                        onChange={(e) => selectDevice(e)}
                        name="deviceId"
                    >
                        {devices && devices.map((device) => (
                            <MenuItem key={device.id} value={device.id} width={100}>
                                {device.name}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
                <FormControl fullWidth sx={{marginBottom: 1, marginTop: 1}} className='nodrag'>
                    <InputLabel>Trigger Key</InputLabel>
                    <Select
                        variant='outlined'
                        size='small'
                        value={key}
                        label="Action Key"
                        onChange={(e) => handleTriggerKey(e, 'key')}
                        name="key"
                    >
                        {selectedDevice.attributes && selectedDevice.attributes.map((actionOption) => (
                            <MenuItem key={actionOption.id} value={actionOption.key}>
                                {actionOption.displayName}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
                <TextField
                    size='small'
                    label="Value"
                    fullWidth
                    value={value}
                    onChange={(e) => handleTriggerKey(e, 'data')}
                    name="data"
                    sx={{marginBottom: 2}}
                />
            </div>
        </Card>
    );
};
