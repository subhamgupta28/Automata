import { Handle, Position, useNodes, useReactFlow } from "@xyflow/react";
import React, { useEffect, useState } from "react";
import { useCachedDevices } from "../../services/AppCacheContext.jsx";
import {
    Card,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
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
    background: 'transparent',
    backdropFilter: 'blur(6px)',
    backgroundColor: 'rgb(255 255 255 / 8%)',
};

export const TriggerNode = ({ id, data, isConnectable }) => {
    const nodes = useNodes();
    const conditionNodes = nodes.filter(node => node.type === 'condition');

    const initialTriggerData = data.triggerData || {
        name: '',
        deviceId: '',
        type: 'state',
        keys: []
    };

    const { updateNodeData, setEdges, setNodes } = useReactFlow();
    const { devices, loading, error } = useCachedDevices();

    const [triggerKeys, setTriggerKeys] = useState(initialTriggerData.keys || []);
    const [name, setName] = useState(initialTriggerData.name);
    const [type, setType] = useState(initialTriggerData.type);
    const [selectedDevice, setSelectedDevice] = useState({ id: initialTriggerData.deviceId, name: '' });

    useEffect(() => {
        if (devices) {
            if (initialTriggerData.deviceId) {
                const device = devices.find(d => d.id === initialTriggerData.deviceId);
                setSelectedDevice(device || devices[0]);
            } else {
                setSelectedDevice(devices[0]);
            }
        }
    }, [devices]);

    // Sync number of keys with number of condition nodes
    useEffect(() => {
        const updatedKeys = [];

        for (let i = 0; i < conditionNodes.length; i++) {
            const existing = triggerKeys.find(tk => tk.conditionId === conditionNodes[i].id);
            if (existing) {
                updatedKeys.push(existing);
            } else {
                updatedKeys.push({
                    conditionId: conditionNodes[i].id,
                    key: '',
                    value: ''
                });
            }
        }

        setTriggerKeys(updatedKeys);
    }, [conditionNodes]);

    useEffect(() => {
        const data = {
            triggerData: {
                deviceId: selectedDevice?.id || '',
                type,
                name,
                keys: triggerKeys
            }
        }
        updateNodeData(id, data);
    }, [selectedDevice, triggerKeys, name, type]);

    const selectTriggerDevice = (e) => {
        const dev = devices.find(d => d.id === e.target.value);
        setSelectedDevice(dev);
    };

    const handleTriggerKeyChange = (index, field, value) => {
        const updated = [...triggerKeys];
        updated[index][field] = value;
        setTriggerKeys(updated);
    };

    const handleTypeChange = (e) => {
        setType(e.target.value);
        setTriggerKeys([]); // Reset keys on type change
    };

    const deleteNode = (nodeId) => {
        setNodes(nodes => nodes.filter(node => node.id !== nodeId));
        setEdges(eds => eds.filter(edge => edge.source !== nodeId && edge.target !== nodeId));
    };

    return (
        <Card style={{ ...triggerStyle, padding: '10px' }}>
            <Typography variant="body1" fontWeight="bold" sx={{marginLeft:1}}>
                Trigger
            </Typography>
            <IconButton onClick={() => deleteNode(id)} style={{ position: 'absolute', top: '0', right: '0' }}>
                <DeleteIcon />
            </IconButton>

            <TextField
                size='small'
                label="Name for the trigger"
                fullWidth
                value={name}
                onChange={(e) => setName(e.target.value)}
                sx={{ marginBottom: 2, marginTop: 3 }}
            />

            <FormControl fullWidth className='nodrag' sx={{ marginBottom: 2 }}>
                <InputLabel id="type-select-label">Type</InputLabel>
                <Select
                    labelId="type-select-label"
                    id="type-select"
                    value={type}
                    size='small'
                    label="Type"
                    onChange={handleTypeChange}
                    variant='outlined'
                >
                    <MenuItem value={'state'}>State Change</MenuItem>
                    <MenuItem value={'periodic'}>Periodic</MenuItem>
                    <MenuItem value={'time'}>Specific Time</MenuItem>
                </Select>
            </FormControl>

            {type === 'time' && (
                <div>
                    <Typography variant="body2" sx={{ margin: 1 }}>
                        Value to be used to run automation and in condition
                    </Typography>
                    {triggerKeys.map((tk, idx) => (
                        <TextField
                            key={idx}
                            size='small'
                            label={`Value #${idx + 1}`}
                            fullWidth
                            value={tk.value}
                            onChange={(e) => handleTriggerKeyChange(idx, 'value', e.target.value)}
                            sx={{ marginBottom: 2 }}
                        />
                    ))}
                </div>
            )}

            {type === 'state' && (
                <div>
                    <FormControl fullWidth sx={{ marginTop: 1, marginBottom: 2 }} className='nodrag'>
                        <InputLabel>Trigger Device</InputLabel>
                        <Select
                            variant='outlined'
                            size='small'
                            value={selectedDevice?.id || ''}
                            label="Trigger Device"
                            onChange={selectTriggerDevice}
                        >
                            {devices && devices.map(device => (
                                <MenuItem key={device.id} value={device.id}>
                                    {device.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    {triggerKeys.map((tk, idx) => (
                        <React.Fragment key={idx}>
                            <FormControl className='nodrag' fullWidth sx={{ marginTop: 2 }}>
                                <InputLabel>Trigger Key #{idx + 1}</InputLabel>
                                <Select
                                    variant='outlined'
                                    size='small'
                                    value={tk.key}
                                    label={`Trigger Key #${idx + 1}`}
                                    onChange={(e) => handleTriggerKeyChange(idx, 'key', e.target.value)}
                                >
                                    {selectedDevice?.attributes?.map(attr => (
                                        <MenuItem key={attr.id} value={attr.key}>
                                            {attr.displayName}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </React.Fragment>
                    ))}
                </div>
            )}

            <Handle
                style={{ width: '18px', height: '18px', background: '#6DBF6D', opacity: 0 }}
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
