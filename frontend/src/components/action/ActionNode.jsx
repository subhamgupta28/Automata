import {Handle, Position, useNodeConnections, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select, Switch, TextField} from "@mui/material";
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
    overflow: 'visible'
};

export const ActionNode = ({id, data, isConnectable}) => {
    const {updateNodeData, setEdges, setNodes} = useReactFlow();
    let actionData = data.actionData || {
        key: '',
        data: '',
        name: '',
        deviceId: '',
        revert: false,
        conditionGroup: 'none',
        order: 1,
        delaySeconds: 0,
    };
    const [selectedDevice, setSelectedDevice] = useState({id: actionData.deviceId, name: ''});
    const {devices, loading, error} = useCachedDevices();
    const [name, setName] = useState(actionData.name);
    const [valueOptions, setValueOptions] = useState(["op1", "op2"]);
    const actionKeys = selectedDevice.attributes && selectedDevice.attributes.filter(f => f.type.toString().startsWith("ACTION"));
    const [value, setValue] = useState(actionData.data);
    const [key, setKey] = useState(actionData.key);
    const [actionType, setActionType] = useState("");
    const [revert, setRevert] = useState(actionData.revert);
    const [conditionGroup, setConditionGroup] = useState(actionData.conditionGroup);
    const [order, setOrder] = useState(actionData.order);
    const [delaySeconds, setDelaySeconds] = useState(actionData.delaySeconds);


    const connections = useNodeConnections({handleType: 'target'});
    useEffect(() => {
        console.log("connections", connections, id)
        if (connections.length > 0) {
            const sourceHandle = connections[0]?.sourceHandle;

            const group = sourceHandle === 'cond-negative' ? 'negative' : sourceHandle === 'cond-positive' ? 'positive' : 'none';
            console.log("sourceHandle", sourceHandle, group)
            setConditionGroup(group);

        }
    }, [connections]);
    useEffect(() => {
        const ad = data.actionData;
        if (ad) {
            setName(ad.name);
            setValue(ad.data);
            setKey(ad.key);
            setOrder(ad.order || 1);
            setDelaySeconds(ad.delaySeconds || 0);
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
        } else if (select === 'revert') {
            setRevert(e.target.checked)
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
        // console.log("action", action)
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
        // if (valueOptions.length > 0)
        //     setValue(valueOptions[0])
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
            isEnabled: connections.length > 0,
            revert,
            conditionGroup,
            order,
            delaySeconds
        };

        // Only update if something actually changed
        console.log(newData)
        handleActionValues(key);
        if (JSON.stringify(data.actionData) !== JSON.stringify(newData)) {
            updateNodeData(id, {actionData: newData});

        }

    }, [
        selectedDevice?.id,
        selectedDevice?.name,
        key,
        value,
        connections,
        revert,
        conditionGroup,
        order,
        delaySeconds
    ]);

    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }

    return (
        <Card style={{
            ...actionStyle,
            border: `2px solid ${conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#FFF'}`,
        }}>
            <Handle
                style={{
                    width: '18px',
                    height: '18px',
                    background: conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#808080'  // ← was always #0288D1
                }}
                type="target"
                position={Position.Left}
                id="b"
                isConnectable={isConnectable}
            />
            <Typography variant="body1" fontWeight="bold" sx={{marginLeft: 1}}>
                {conditionGroup === 'negative' ? 'Negative Action' : conditionGroup === 'positive' ? 'Positive Action' : 'Not Action'}
            </Typography>
            {/*<div style={{*/}
            {/*    display: 'inline-block',*/}
            {/*    // marginLeft: '8px',*/}
            {/*    marginBottom: '4px',*/}
            {/*    padding: '2px 8px',*/}
            {/*    borderRadius: '4px',*/}
            {/*    width: '100%',*/}
            {/*    fontSize: '11px',*/}
            {/*    fontWeight: 'bold',*/}
            {/*    // background: conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#808080',*/}
            {/*    color: conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#FFF',*/}
            {/*    border: `1px solid ${conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#FFF'}`*/}
            {/*}}>*/}

            {/*</div>*/}
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
                            <MenuItem key={actionOption.id} value={actionOption.key}>
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
                    actionType === "COLOR" ? (
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
                    ) : (
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
                <TextField
                    size='small'
                    label="Execution Order"
                    type="number"
                    fullWidth
                    value={order}
                    inputProps={{
                        min: 0,
                        max: 50,
                        step: 1
                    }}
                    onChange={(e) => setOrder(parseInt(e.target.value) || 1)}
                    sx={{marginBottom: 1}}
                />

                <TextField
                    size='small'
                    label="Delay (seconds)"
                    type="number"
                    fullWidth
                    maxRows={5}
                    inputProps={{
                        min: 0,
                        max: 30,
                        step: 1
                    }}
                    value={delaySeconds}
                    onChange={(e) => setDelaySeconds(parseInt(e.target.value) || 0)}
                    sx={{marginBottom: 2}}
                />
                Revert action
                <Switch
                    onChange={(e) => handleTriggerKey(e, 'revert')}
                    checked={revert}
                ></Switch>
            </div>
        </Card>
    );
};
