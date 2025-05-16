import React, {useEffect, useState} from "react";
import {Button, Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import Typography from "@mui/material/Typography";
import {Handle, Position, useReactFlow} from "@xyflow/react";
import IconButton from "@mui/material/IconButton";
import AddIcon from "@mui/icons-material/Add";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import DeleteIcon from '@mui/icons-material/Delete';


export const ValueReaderNode = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [selectedDevice, setSelectedDevice] = useState({});
    const [selectedKey, setSelectedKey] = useState('');
    const [valueName, setValueName] = useState('');
    const {devices} = useCachedDevices();

    useEffect(() => {
        const dt = data.valueReaderData;
        console.log("dt", dt)
        if (dt) {
            setSelectedKey(dt?.key);
            setValueName(dt?.name);
        }
        if (devices) {
            const device = devices.find(d => d.id === data?.deviceId) || devices[0];
            setSelectedDevice(device);
        }
    }, [devices, data.valueReaderData]);

    useEffect(() => {
        console.log("key", selectedKey);
        console.log("name", valueName)
        updateNodeData(id, {
            valueReaderData: {
                deviceId: selectedDevice.id,
                key: selectedKey,
                name: valueName
            }
        });
    }, [selectedDevice, selectedKey, valueName]);

    const handleDeviceChange = (e) => {
        const device = devices.find(d => d.id === e.target.value);
        setSelectedDevice(device);
    };

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <Card style={{...conditionStyle, border: '2px solid #9C27B0'}}>
            <Handle
                type="source"
                position={Position.Right}
                id="b"
                style={{width: 18, height: 18, background: '#9C27B0'}}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#9C27B0', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>

            <IconButton onClick={deleteNode} style={{position: 'absolute', top: 0, right: 0}}>
                <DeleteIcon/>
            </IconButton>
            <Typography variant="body2" sx={{marginTop: 3}}>Read value from a device</Typography>
            <TextField
                size="small"
                label="Name"
                fullWidth
                value={valueName}
                onChange={(e) => setValueName(e.target.value)}
                sx={{marginTop: 2}}
            />
            <FormControl fullWidth className="nodrag" sx={{marginTop: 2}}>
                <InputLabel>Device</InputLabel>
                <Select
                    size="small"
                    variant="outlined"
                    value={selectedDevice?.id || ''}
                    label="Device"
                    onChange={handleDeviceChange}
                >
                    {devices && devices.map(device => (
                        <MenuItem key={device.id} value={device.id}>{device.name}</MenuItem>
                    ))}
                </Select>
            </FormControl>
            <FormControl fullWidth className="nodrag" sx={{marginTop: 2}}>
                <InputLabel>Attribute</InputLabel>
                <Select
                    size="small"
                    variant="outlined"
                    value={selectedKey}
                    label="Attribute"
                    onChange={(e) => setSelectedKey(e.target.value)}
                >
                    {selectedDevice?.attributes?.map(attr => (
                        <MenuItem key={attr.id} value={attr.key}>{attr.displayName}</MenuItem>
                    ))}
                </Select>
            </FormControl>
        </Card>
    );
};

