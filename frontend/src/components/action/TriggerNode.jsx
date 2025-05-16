import {Handle, Position, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";

const triggerStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #6DBF6D',
};

export const TriggerNode = ({id, data, isConnectable}) => {
    const triggerData = data.triggerData || {
        key: '',
        value: '',
        name: '',
        deviceId: '',
        type: 'state'
    };
    const {updateNodeData, setEdges, setNodes} = useReactFlow();
    const [selectedDevice, setSelectedDevice] = useState({id: triggerData.deviceId, name: ''});
    const {devices, loading, error} = useCachedDevices();
    const [name, setName] = useState(triggerData.name);
    const [value, setValue] = useState(triggerData.value);
    const [key, setKey] = useState(triggerData.key);
    const [type, setType] = useState(triggerData.type);
    // const [triggerData, setTriggerData] = useState({key: data.triggerData.key});
    useEffect(() => {
        let td = data.triggerData;
        if (td) {
            setName(td.name);
            setValue(td.value);
            setKey(td.key);
            setType(td.type);
        }
        if (devices) {
            if (data.triggerData && data.triggerData.deviceId) {
                const device = devices.filter((d) => d.id === data.triggerData.deviceId);
                setSelectedDevice(device[0]);
            } else {
                setSelectedDevice(devices[0]);
            }
        }
    }, [devices, data.triggerData])

    useEffect(() => {
        updateNodeData(id, {
            triggerData: {
                deviceId: selectedDevice.id,
                type: type,
                key: key,
                name,
                value,
            }
        })
    }, [selectedDevice, key, value, name, type]);


    const selectTriggerDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        setSelectedDevice(dev);
    }
    const handleTriggerKey = (e, select) => {
        if (select === 'name') {
            setName(e.target.value);
        } else if (select === 'value') {
            setValue(e.target.value);
        } else if (select === 'key') {
            setKey(e.target.value);
        } else if (select === 'type') {
            setType(e.target.value);
        }
        // const {name, value} = e.target;
        // const updatedData = {...triggerData};
        // updatedData[name] = value;
        // setTriggerData(updatedData);
    }
    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    return (
        <Card style={{...triggerStyle, padding: '10px'}}>

            {data.value && (
                <div>
                    <IconButton onClick={() => deleteNode(id)} style={{position: 'absolute', top: '0', right: '0'}}>
                        <DeleteIcon/>
                    </IconButton>
                    <TextField
                        size='small'
                        label="Name for the trigger"
                        fullWidth
                        value={name}
                        onChange={(e) => handleTriggerKey(e, 'name')}
                        name="name"
                        sx={{marginBottom: 2, marginTop: 3}}
                    />
                    <FormControl fullWidth className='nodrag' sx={{marginBottom: 2}}>
                        <InputLabel id="demo-simple-select-label">Type</InputLabel>
                        <Select
                            labelId="demo-simple-select-label"
                            id="demo-simple-select"
                            value={type}
                            size='small'
                            label="Type"
                            name="type"
                            onChange={(e) => handleTriggerKey(e, 'type')}
                            variant='outlined'>
                            <MenuItem value={'state'}> State Change</MenuItem>
                            <MenuItem value={'periodic'}> Periodic </MenuItem>
                            <MenuItem value={'time'}> Specific Time </MenuItem>
                        </Select>
                    </FormControl>

                    {type === 'time' && (
                        <div>
                            <Typography variant="body2" sx={{margin: 1}}>
                                Value to be used run automation and used in condition
                            </Typography>
                            <TextField
                                size='small'
                                label="Value for the trigger key"
                                fullWidth
                                value={value}
                                onChange={(e) => handleTriggerKey(e, 'value')}
                                name="value"
                                sx={{marginBottom: 2}}
                            />
                        </div>
                    )}

                    {type === 'state' && (
                        <div>
                            {/*<Typography variant="body2" sx={{margin: 1}}>*/}
                            {/*    Trigger Device is used to trigger actions based on the value of a key. It's the device*/}
                            {/*    that will*/}
                            {/*    send the trigger an automation.*/}
                            {/*</Typography>*/}
                            <FormControl fullWidth sx={{marginTop: 1}} className='nodrag'>
                                <InputLabel>Trigger Device</InputLabel>
                                <Select
                                    variant='outlined'
                                    size='small'
                                    value={selectedDevice.id}
                                    label="Trigger Device"
                                    onChange={(e) => selectTriggerDevice(e)}
                                    name="deviceId"
                                >
                                    {devices && devices.map((device) => (
                                        <MenuItem key={device.id} value={device.id} width={100}>
                                            {device.name}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                            {/*<Typography variant="body2" sx={{margin: 2}}>*/}
                            {/*    Trigger key is the attribute of the device that will be used to trigger the automation.*/}
                            {/*</Typography>*/}
                            <FormControl className='nodrag' fullWidth sx={{marginBottom: 1, marginTop: 1}}>
                                <InputLabel>Trigger Key</InputLabel>
                                <Select
                                    variant='outlined'
                                    size='small'
                                    value={key}
                                    label="Trigger Key"
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
                        </div>
                    )}

                </div>

            )}
            <Handle
                style={{width: '18px', height: '18px', background: '#6DBF6D', opacity: 0}}
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#6DBF6D', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </Card>
    );
};