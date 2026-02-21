import {Handle, Position, useNodeConnections, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";


const actionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #0288D1',
    background: 'transparent',
    backdropFilter: 'blur(6px)',
    backgroundColor: 'rgb(255 255 255 / 8%)',
};

export const ActionNode = ({id, data, isConnectable}) => {
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
    const [valueOptions, setValueOptions] = useState(["op1", "op2"]);
    const actionKeys = selectedDevice.attributes && selectedDevice.attributes.filter(f => f.type.toString().startsWith("ACTION"));
    const [value, setValue] = useState(actionData.data);
    const [key, setKey] = useState(actionData.key);
    const [actionType, setActionType] = useState("");

    const connections = useNodeConnections({
        type: 'target',
        id: 'b'
    });

    useEffect(() => {
        const ad = data.actionData;
        if (ad) {
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
    const handleActionValues = (key) => {
        if (!actionKeys)
            return;
        const action = actionKeys?.filter(f => f.key === key);
        if (action.length === 0) {
            setActionType("")
            return;
        }
        setActionType(action.type)
        switch (action[0].type) {
            case "ACTION|MENU|SWITCH": //on/off
                setValueOptions(
                    [
                        {name: "On", value: "true"},
                        {name: "Off", value: "false"},
                    ]
                )
                break;
            case "ACTION|SWITCH": // on/off
                setValueOptions(
                    [
                        {name: "On", value: "true"},
                        {name: "Off", value: "false"},
                    ]
                )
                break;
            case "ACTION|IN": // on/off
                if (action[0].key === "alert") {
                    setValueOptions(
                        [
                            {name: "Critical", value: "critical"},
                            {name: "Warning", value: "warning"},
                            {name: "Normal", value: "normal"},
                        ]
                    )
                } else {
                    setValueOptions([])
                }

                break;
            case "ACTION|MENU|BTN": //push button
                setValueOptions(
                    [
                        {name: "Toggle", value: "T"},
                    ]
                )
                break;
            case "ACTION|OUT": // push button
                setValueOptions(
                    [
                        {name: "Toggle", value: "T"},
                    ]
                )
                break;
            case "ACTION|COLOR": // push button
                setValueOptions([])
                setActionType("COLOR")
                break;
            case "ACTION|PRESET": // build from extras
                const res = [];
                for (let [key, value] of Object.entries(action[0].extras)) {
                    console.log("a", key, value)
                    res.push({name: key, value})
                }
                setValueOptions(res)
                break;
            default:
                setActionType("")
                setValueOptions([])
                break;
        }
    }
    const handleTriggerValue = (e, select) => {
        setValue(e.target.value);
    }

    const selectDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        setName(() => selectedDevice.name);
        setSelectedDevice(dev);
    }

    useEffect(() => {
        const newData = {
            deviceId: selectedDevice?.id,
            key,
            name: selectedDevice?.name,
            data: value,
            isEnabled: connections.length > 0
        };

        // Only update if something actually changed
        if (JSON.stringify(data.actionData) !== JSON.stringify(newData)) {
            updateNodeData(id, {actionData: newData});
            handleActionValues(key);
        }

    }, [
        selectedDevice?.id,
        selectedDevice?.name,
        key,
        value,
        connections.length
    ]);

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
            <Typography variant="body1" fontWeight="bold" sx={{marginLeft: 1}}>
                Action
            </Typography>
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
                            <MenuItem key={device.name} value={device.id} width={100}>
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
                        {actionKeys && actionKeys.map((actionOption) => (
                            <MenuItem key={actionOption.key} value={actionOption.key}>
                                {actionOption.displayName}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
                {actionType !== "" && valueOptions.length > 0 ? (
                    <FormControl fullWidth sx={{marginBottom: 1, marginTop: 1}} className='nodrag'>
                        <InputLabel>Choose value</InputLabel>
                        <Select
                            variant='outlined'
                            size='small'
                            value={value}
                            label="Choose value"
                            onChange={(e) => handleTriggerValue(e, 'value')}
                            name="key"
                        >
                            {valueOptions && valueOptions.map((actionOption) => (
                                <MenuItem key={actionOption.name} value={actionOption.value}>
                                    {actionOption.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                ) : (
                    actionType ==="COLOR" ? (
                        <input
                            type="color"
                            value={value}
                            onChange={(e) => handleTriggerKey(e, 'data')}
                            style={{
                                width: '100%',
                                height: '50px',
                                border: "none",
                                cursor: "pointer",
                                background: "none",
                            }}
                        />
                    ):(
                        <TextField
                            size='small'
                            label="Value"
                            fullWidth
                            value={value}
                            onChange={(e) => handleTriggerKey(e, 'data')}
                            name="data"
                            sx={{marginBottom: 2}}
                        />
                    )
                )}

            </div>
        </Card>
    );
};
