import React, {memo, useCallback, useEffect, useState} from "react";
import {getDevices} from "../../services/apis.jsx";
import {Button, Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Handle, Position, useHandleConnections, useNodes, useNodesData, useReactFlow} from "@xyflow/react";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import CancelIcon from "@mui/icons-material/Cancel";
import AddIcon from "@mui/icons-material/Add";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import DeleteIcon from '@mui/icons-material/Delete';
import {LocalizationProvider, MobileTimePicker} from "@mui/x-date-pickers";
import dayjs from "dayjs";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";


const triggerStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #6DBF6D',
};

const actionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #0288D1',
};

const conditionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

// Custom Trigger Node
const TriggerNode = ({id, data, isConnectable}) => {
    const triggerData = data.triggerData ? data.triggerData : {
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
        if (devices) {
            if (data.triggerData && data.triggerData.deviceId) {
                const device = devices.filter((d) => d.id === data.triggerData.deviceId);
                setSelectedDevice(device[0]);
            } else {
                setSelectedDevice(devices[0]);
            }
        }
    }, [devices])

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
                            <Typography variant="body2" sx={{margin: 1}}>
                                Trigger Device is used to trigger actions based on the value of a key. It's the device
                                that will
                                send the trigger an automation.
                            </Typography>
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
                            <Typography variant="body2" sx={{margin: 2}}>
                                Trigger key is the attribute of the device that will be used to trigger the automation.
                            </Typography>
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


// Custom Action Node
const ActionNode = ({id, data, isConnectable}) => {
    const {updateNodeData, setEdges, setNodes} = useReactFlow();
    const actionData = data.actionData ? data.actionData : {
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
        if (devices) {
            if (actionData.deviceId) {
                const device = devices.filter((d) => d.id === actionData.deviceId);
                setSelectedDevice(device[0]);
            } else {
                setSelectedDevice(devices[0]);
            }
        }
    }, [data.value, devices])
    const handleTriggerKey = (e, select) => {
        if (select === 'name') {
            setName(e.target.value);
        } else if (select === 'data') {
            setValue(e.target.value);
        } else if (select === 'key') {
            setKey(e.target.value);
        }

    }

    const selectDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        setSelectedDevice(dev);
    }

    useEffect(() => {
        updateNodeData(id, {
            actionData: {
                deviceId: selectedDevice.id,
                key: key,
                name,
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

// Custom Condition Node
const ConditionNode = ({id, data, isConnectable}) => {
    const conditionData = data.conditionData ? data.conditionData : {
        condition: 'numeric',
        valueType: 'int',
        below: '0',
        above: '0',
        value: '0',
        time: '2:20:05 AM',
        isExact: false
    };
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [triggerData, setTriggerData] = useState({})
    const [condition, setCondition] = useState(conditionData.condition)
    const [above, setAbove] = useState(conditionData.above)
    const [below, setBelow] = useState(conditionData.below)
    const [isRange, setIsRange] = useState(conditionData.isExact)
    const [conditionValue, setConditionValue] = useState(conditionData.value)
    const [time, setTime] = useState(dayjs(conditionData.time))
    const [type, setType] = useState(null);
    const connections = useHandleConnections({
        type: 'target',
        id: 'cond-t'
    });
    const nodesData = useNodesData(
        connections.map((connection) => connection.source),
    );

    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    useEffect(() => {
        const triggerData = nodesData.length > 0 && nodesData[0].data.triggerData ? nodesData[0].data.triggerData : {
            key: '',
            value: '',
            name: '',
            deviceId: '',
            type: ''
        };
        setTriggerData(triggerData);
        setType(triggerData.type);

    }, [nodesData]);

    useEffect(() => {

        updateNodeData(id, {
            conditionData: {
                condition: condition,
                valueType: 'int',
                below: below,
                above: above,
                value: conditionValue,
                isExact: isRange,
                time: time.format()
            }
        })
    }, [condition, conditionValue, below, above, isRange, time]);

    const handleChange = (e, select) => {
        if (select === 'value') {
            setConditionValue(e.target.value);
        } else if (select === 'condition') {
            setIsRange(e.target.value === 'equal');
            setCondition(e.target.value);
        } else if (select === 'above') {
            setAbove(e.target.value);
        } else if (select === 'below') {
            setBelow(e.target.value);
        } else if (select === 'time') {
            setTime(e);

            console.log("time", time.format());

        }
    }

    return (
        <Card style={{...conditionStyle, padding: '10px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <IconButton onClick={() => deleteNode(id)} style={{position: 'absolute', top: '0', right: '0'}}>
                <DeleteIcon/>
            </IconButton>

            {type === 'time' ? (
                <div style={{marginTop: '18px'}}>
                    <Typography variant="body2" sx={{margin: 1}}>
                        Run automation at specific time of the day
                    </Typography>
                    <LocalizationProvider dateAdapter={AdapterDayjs}>
                        <MobileTimePicker format="hh:mm:ss A" value={time} onChange={(e) => handleChange(e, 'time')}/>
                    </LocalizationProvider>

                </div>

            ) : (
                <div style={{marginBottom: '18px'}}>
                    <Typography variant="body1">
                        When {triggerData.key} is
                    </Typography>
                    <FormControl fullWidth className='nodrag' sx={{marginBottom: 2}}>
                        <InputLabel id="demo-simple-select-label">Condition</InputLabel>
                        <Select
                            labelId="demo-simple-select-label"
                            id="demo-simple-select"
                            value={condition}
                            size='small'
                            label="Condition"
                            name="condition"
                            onChange={(e) => handleChange(e, 'condition')}
                            variant='outlined'>
                            <MenuItem value={'equal'}> equal to</MenuItem>
                            <MenuItem value={'range'}> between </MenuItem>
                        </Select>
                    </FormControl>

                    {isRange ? (
                        <TextField
                            size='small'
                            label="Value"
                            fullWidth
                            value={conditionValue}
                            onChange={(e) => handleChange(e, 'value')}
                            name="value"
                            sx={{marginBottom: 2}}
                        />
                    ) : (
                        <div>
                            <TextField
                                size='small'
                                label="Below"
                                fullWidth
                                value={below}
                                onChange={(e) => handleChange(e, 'below')}
                                name="value"
                                sx={{marginBottom: 2}}
                            />
                            <TextField
                                size='small'
                                label="Above"
                                fullWidth
                                value={above}
                                onChange={(e) => handleChange(e, 'above')}
                                name="value"
                                sx={{marginBottom: 2}}
                            />
                        </div>
                    )}
                    <Typography variant="body1" style={{margin: '18px'}}>
                        trigger the actions.
                    </Typography>
                </div>
            )}

            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </Card>
    );
};
export {TriggerNode, ActionNode, ConditionNode};