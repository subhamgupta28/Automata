import React, {memo, useEffect, useState} from "react";
import {getDevices} from "../../services/apis.jsx";
import {Card, FormControl, InputLabel, MenuItem, Select} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Handle, Position, useHandleConnections, useNodesData} from "@xyflow/react";

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
const TriggerNode = ({data, isConnectable}) => {
    const [selectedDevice, setSelectedDevice] = useState({id: '', name: ''});
    const [devices, setDevices] = useState([]);
    const [triggerData, setTriggerData] = useState({key: 'Select a key'});
    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getDevices();
                setDevices(data);
                setSelectedDevice(data[0]);
                console.log("devices", data);
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        if (data.value && data.value.isNewNode) {
            fetchData();
        }
    }, [data.value])


    const selectTriggerDevice = (e) => {
        const {name, value} = e.target;
        let dev = devices.filter((d) => d.id === value)[0];
        console.log(name, value, dev)
        setSelectedDevice(dev);
    }
    const handleTriggerData = (e) => {
        const {name, value} = e.target;
        const updatedData = {...triggerData};
        updatedData[name] = value;
        setTriggerData(updatedData);
        console.log(name, value)
    }

    return (
        <Card style={{...triggerStyle, padding: '20px'}} >
            {data.value && (
                <div className="nodrag">
                    <Typography variant="body2" sx={{marginTop: 1}}>
                        Trigger Device is used to trigger actions based on the value of a key. It's the device that will send the trigger an automation.
                    </Typography>
                    <FormControl fullWidth sx={{marginTop: 1}} >
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
                    <FormControl fullWidth sx={{marginBottom: 1, marginTop: 1}} >
                        <InputLabel>Trigger Key</InputLabel>
                        <Select
                            variant='outlined'
                            size='small'
                            value={triggerData.key}
                            label="Trigger Key"
                            onChange={(e) => handleTriggerData(e )}
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
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
        </Card>
    );
};

// Custom Action Node
const ActionNode = ({data, isConnectable}) => {
    return (
        <Card style={actionStyle}>
            <Handle
                type="target"
                position={Position.Left}
                id="b"

                isConnectable={isConnectable}
            />
            {data.value && (
                <strong>{data.value.name}</strong>
            )}

        </Card>
    );
};

// Custom Condition Node
const ConditionNode = ({data, isConnectable}) => {

    const connections = useHandleConnections({
        type: 'target',
    });
    const nodesData = useNodesData(
        connections.map((connection) => connection.source),
    );

    console.log("nodesData", connections)

    return (
        <Card style={conditionStyle}>
            <Handle
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            {data.value && (
                <strong>{data.value.name}</strong>
            )}

            <Handle
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
        </Card>
    );
};
export {TriggerNode, ActionNode, ConditionNode};