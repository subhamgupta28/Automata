import React, {memo, useEffect, useState} from "react";
import {getDevices} from "../../services/apis.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Handle, Position, useHandleConnections, useNodesData, useReactFlow} from "@xyflow/react";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import CancelIcon from "@mui/icons-material/Cancel";

const triggerStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #6DBF6D',
};

const actionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #0288D1',
};

const conditionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

// Custom Trigger Node
const TriggerNode = ({id, data, isConnectable}) => {
    console.log("datta", data)
    const triggerData = data.triggerData ? data.triggerData : {
        key: '',
        value: '',
        name: '',
        deviceId: ''
    };
    const {updateNodeData} = useReactFlow();
    const [selectedDevice, setSelectedDevice] = useState({id: triggerData.deviceId, name: ''});
    const [devices, setDevices] = useState([]);
    const [name, setName] = useState(triggerData.name);
    const [value, setValue] = useState(triggerData.value);
    const [key, setKey] = useState(triggerData.key);
    // const [triggerData, setTriggerData] = useState({key: data.triggerData.key});
    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await getDevices();
                setDevices(res);
                if (data.triggerData.deviceId) {
                    const device = res.filter((d) => d.id === data.triggerData.deviceId);
                    setSelectedDevice(device[0]);
                } else {
                    setSelectedDevice(res[0]);
                }

                console.log("devices", data);
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        if (data.value && data.value.isNewNode) {
            fetchData();
        }
    }, [data.value])

    useEffect(() => {
        // console.log("node id", id, triggerData);
        updateNodeData(id, {
            triggerData: {
                deviceId: selectedDevice.id,
                type: "state",
                key: key,
                name,
                value,
            }
        })
    }, [selectedDevice, key, value, name]);


    const selectTriggerDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        // console.log(name, value, dev)
        setSelectedDevice(dev);
    }
    const handleTriggerKey = (e, select) => {
        if (select === 'name') {
            setName(e.target.value);
        } else if (select === 'value') {
            setValue(e.target.value);
        } else if (select === 'key') {
            setKey(e.target.value);
        }
        // const {name, value} = e.target;
        // const updatedData = {...triggerData};
        // updatedData[name] = value;
        // setTriggerData(updatedData);
        // console.log(name, value)
    }

    return (
        <Card style={{...triggerStyle, padding: '20px'}}>

            {data.value && (
                <div className="nodrag">
                    <TextField
                        size='small'
                        label="Name for the trigger"
                        fullWidth
                        value={name}
                        onChange={(e) => handleTriggerKey(e, 'name')}
                        name="name"
                        sx={{marginBottom: 2, marginTop: 1}}
                    />
                    <TextField
                        size='small'
                        label="Value for the trigger key"
                        fullWidth
                        value={value}
                        onChange={(e) => handleTriggerKey(e, 'value')}
                        name="value"
                        sx={{marginBottom: 2}}
                    />
                    <Typography variant="body2" sx={{marginTop: 1}}>
                        Trigger Device is used to trigger actions based on the value of a key. It's the device that will
                        send the trigger an automation.
                    </Typography>
                    <FormControl fullWidth sx={{marginTop: 1}}>
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
                    <Typography variant="body2" sx={{marginTop: 2}}>
                        Trigger key is the attribute of the device that will be used to trigger the automation.
                    </Typography>
                    <FormControl fullWidth sx={{marginBottom: 1, marginTop: 1}}>
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
            <Handle
                style={{width: '18px', height: '18px', background: '#6DBF6D'}}
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
        </Card>
    );
};

// Custom Action Node
const ActionNode = ({id, data, isConnectable}) => {
    const {updateNodeData} = useReactFlow();
    const actionData = data.actionData ? data.actionData : {
        key: '',
        data: '',
        name: '',
        deviceId: ''
    };
    const [selectedDevice, setSelectedDevice] = useState({id: actionData.deviceId, name: ''});
    const [devices, setDevices] = useState([]);
    const [name, setName] = useState(actionData.name);
    const [value, setValue] = useState(actionData.data);
    const [key, setKey] = useState(actionData.key);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await getDevices();
                setDevices(res);
                if (data.actionData.deviceId) {
                    const device = res.filter((d) => d.id === data.actionData.deviceId);
                    setSelectedDevice(device[0]);
                } else {
                    setSelectedDevice(res[0]);
                }

                console.log("devices", data);
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        if (data.value && data.value.isNewNode) {
            fetchData();
        }
    }, [data.value])
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
        // console.log(name, value, dev)
        setSelectedDevice(dev);
    }

    useEffect(() => {
        // console.log("node id", id, triggerData);
        updateNodeData(id, {
            actionData: {
                deviceId: selectedDevice.id,
                key: key,
                name,
                data: value,
            }
        })
    }, [selectedDevice, key, value, name]);

    return (
        <Card style={actionStyle}>
            <Handle
                style={{width: '18px', height: '18px', background: '#0288D1'}}
                type="target"
                position={Position.Left}
                id="b"
                isConnectable={isConnectable}
            />
            <div className='nodrag' style={{margin: '8px'}}>
                <FormControl fullWidth sx={{marginTop: 1}}>
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
                <FormControl fullWidth sx={{marginBottom: 1, marginTop: 1}}>
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
        below: '',
        above: '',
        value: '',
        isExact: false
    };
    const {updateNodeData} = useReactFlow();
    const [triggerData, setTriggerData] = useState({})
    const [condition, setCondition] = useState(conditionData.condition)
    const [above, setAbove] = useState(conditionData.above)
    const [below, setBelow] = useState(conditionData.below)
    const [isRange, setIsRange] = useState(conditionData.isExact)
    const [conditionValue, setConditionValue] = useState(conditionData.value)
    const connections = useHandleConnections({
        type: 'target',
        id: 'cond-t'
    });
    const nodesData = useNodesData(
        connections.map((connection) => connection.source),
    );
    useEffect(() => {


        const triggerData = nodesData.length > 0 && nodesData[0].data.triggerData ? nodesData[0].data.triggerData : {
            key: '',
            value: '',
            name: '',
            deviceId: ''
        };
        setTriggerData(triggerData);
        console.log(triggerData);
    }, [nodesData]);

    useEffect(() => {
        // console.log("node id", id, triggerData);
        updateNodeData(id, {
            conditionData: {
                condition: condition,
                valueType: 'int',
                below: below,
                above: above,
                value: conditionValue,
                isExact: isRange
            }
        })
    }, [condition, conditionValue, below, above, isRange]);
    // console.log("nodesData", nodesData)

    const handleChange = (e, select) => {
        if (select === 'value') {
            setConditionValue(e.target.value);
        } else if (select === 'condition') {
            setIsRange(e.target.value === 'range');
            setCondition(e.target.value);
        } else if (select === 'above') {
            setAbove(e.target.value);
        } else if (select === 'below') {
            setBelow(e.target.value);
        }
    }

    return (
        <Card style={{...conditionStyle, padding: '20px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            <Typography variant="body1" style={{marginBottom: '18px'}}>
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
                    <MenuItem value={'greater'}> greater than </MenuItem>
                    <MenuItem value={'equal'}> equal to</MenuItem>
                    <MenuItem value={'less'}> less than </MenuItem>
                    <MenuItem value={'range'}> in a range </MenuItem>
                </Select>
            </FormControl>

            {!isRange ? (
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
            <Typography variant="body1" style={{marginBottom: '18px'}}>
                trigger the actions.
            </Typography>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
        </Card>
    );
};
export {TriggerNode, ActionNode, ConditionNode};