import {Handle, Position, useNodes, useReactFlow} from "@xyflow/react";
import React, {useEffect, useMemo, useRef, useState} from "react";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {Button, Card, Divider, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import NumberSpinner from "../charts/NumberSpinner.jsx";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";

const triggerStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '340px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #6DBF6D',
    background: 'transparent',
    backdropFilter: 'blur(6px)',
    backgroundColor: 'rgb(0 0 0 / 28%)',
    overflow: 'visible'
};

export const TriggerNode = ({id, data, isConnectable}) => {
    const nodes = useNodes();
    const conditionNodes = useMemo(
        () => nodes.filter(n => n.type === 'condition'),
        [nodes]
    );

    const initialTriggerData = data.triggerData || {
        name: '',
        deviceId: '',
        type: 'state',
        keys: [],
        priority: 5,
        sources: [],        // NEW: [{ deviceId, keys: [string], role: 'primary'|'secondary' }]
    };

    const {updateNodeData, setEdges, setNodes} = useReactFlow();
    const {devices, loading} = useCachedDevices();

    const [triggerKeys, setTriggerKeys] = useState(initialTriggerData.keys || []);
    const [name, setName] = useState(initialTriggerData.name);
    const [type, setType] = useState(initialTriggerData.type);
    const [priority, setPriority] = useState(initialTriggerData.priority || 5);
    const [selectedDevice, setSelectedDevice] = useState({id: initialTriggerData.deviceId, name: ''});

    // NEW: secondary devices — each entry: { deviceId: string, key: string }
    const [secondaryDevices, setSecondaryDevices] = useState(
        () => {
            const sources = initialTriggerData.sources || [];
            return sources
                .filter(s => s.role === 'secondary')
                .map(s => ({deviceId: s.deviceId, key: s.keys?.[0] || ''}));
        }
    );

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

    // Sync trigger keys with non-scheduled condition nodes.
    // Conditions that have selected a secondary device are excluded —
    // they read their own device's key and must not appear in the
    // primary device key picker.
    useEffect(() => {
        const updated = conditionNodes
            .filter(node => {
                const cd = node?.data?.conditionData;
                if (!cd) return false;
                if (cd.condition === "scheduled") return false;
                // Exclude if condition has explicitly chosen a secondary device
                if (cd.deviceId && cd.deviceId !== selectedDevice?.id) return false;
                return true;
            })
            .map(node => {
                const existing = triggerKeys.find(tk => tk.conditionId === node.id);
                return existing || {conditionId: node.id, key: '', value: ''};
            });

        if (JSON.stringify(updated) !== JSON.stringify(triggerKeys)) {
            setTriggerKeys(updated);
        }
    }, [conditionNodes, selectedDevice?.id]);

    const lastDataRef = useRef(null);

    useEffect(() => {
        // Build sources array: primary device + secondary devices
        const sources = [
            {
                deviceId: selectedDevice?.id || '',
                keys: triggerKeys.map(tk => tk.key).filter(Boolean),
                role: 'primary'
            },
            ...secondaryDevices
                .filter(sd => sd.deviceId)
                .map(sd => ({
                    deviceId: sd.deviceId,
                    keys: sd.key ? [sd.key] : [],
                    role: 'secondary'
                }))
        ];

        // subscriberDeviceIds = primary only (secondary is fetched on demand from Redis)
        const subscriberDeviceIds = [selectedDevice?.id].filter(Boolean);

        const newData = {
            triggerData: {
                deviceId: selectedDevice?.id || '',
                type,
                name,
                keys: triggerKeys,
                priority,
                nodeId: id,
                rootNode: true,
                sources,
                subscriberDeviceIds,
            }
        };

        const serialized = JSON.stringify(newData);
        if (serialized !== lastDataRef.current) {
            lastDataRef.current = serialized;
            updateNodeData(id, newData);
        }
    }, [selectedDevice?.id, triggerKeys, name, type, priority, secondaryDevices]);

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
        setTriggerKeys([]);
    };

    // Secondary device handlers
    const addSecondaryDevice = () => {
        setSecondaryDevices(prev => [...prev, {deviceId: '', key: ''}]);
    };

    const removeSecondaryDevice = (index) => {
        setSecondaryDevices(prev => prev.filter((_, i) => i !== index));
    };

    const updateSecondaryDevice = (index, field, value) => {
        setSecondaryDevices(prev => {
            const updated = [...prev];
            updated[index] = {...updated[index], [field]: value};
            // Reset key when device changes
            if (field === 'deviceId') updated[index].key = '';
            return updated;
        });
    };

    const getDeviceAttributes = (deviceId) => {
        if (!devices || !deviceId) return [];
        const dev = devices.find(d => d.id === deviceId);
        return dev?.attributes || [];
    };

    const deleteNode = (nodeId) => {
        setNodes(nodes => nodes.filter(node => node.id !== nodeId));
        setEdges(eds => eds.filter(edge => edge.source !== nodeId && edge.target !== nodeId));
    };

    return (
        <Card style={{...triggerStyle, padding: '10px'}}>
            <Typography variant="body1" fontWeight="bold" sx={{marginLeft: 1}}>
                Trigger / Start
            </Typography>
            <IconButton onClick={() => deleteNode(id)} style={{position: 'absolute', top: '0', right: '0'}}>
                <DeleteIcon/>
            </IconButton>

            <TextField
                className="nodrag"
                size='small'
                label="Name for the trigger"
                fullWidth
                value={name}
                onChange={(e) => setName(e.target.value)}
                sx={{marginBottom: 2, marginTop: 3}}
            />

            <FormControl fullWidth className='nodrag' sx={{marginBottom: 2}}>
                <InputLabel>Type</InputLabel>
                <Select
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
                    <Typography variant="body2" sx={{margin: 1}}>
                        Value to be used in condition
                    </Typography>
                    {triggerKeys.map((tk, idx) => (
                        <TextField
                            key={idx}
                            size='small'
                            label={`Value #${idx + 1}`}
                            fullWidth
                            value={tk.value}
                            onChange={(e) => handleTriggerKeyChange(idx, 'value', e.target.value)}
                            sx={{marginBottom: 2}}
                        />
                    ))}
                </div>
            )}

            {type === 'state' && (
                <div>
                    {/* ── Primary Device ── */}
                    <Typography color="text.secondary" sx={{ml: 1}}>
                        Primary Device
                    </Typography>
                    <FormControl fullWidth sx={{mt: 1, mb: 2}} className='nodrag'>
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
                            <FormControl className='nodrag' fullWidth sx={{mt: 1, mb: 1}}>
                                <InputLabel>Key #{idx + 1}</InputLabel>
                                <Select
                                    variant='outlined'
                                    size='small'
                                    value={tk.key}
                                    label={`Key #${idx + 1}`}
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

                    {/* ── Secondary Devices ── */}
                    {secondaryDevices.length > 0 && (
                        <>
                            <Divider sx={{my: 1.5}}/>


                            {secondaryDevices.map((sd, idx) => (
                                <div key={idx} style={{
                                    marginTop: 8,
                                }}>

                                    <div style={{
                                        display: 'flex', justifyContent: 'space-between', alignItems: 'center'
                                    }}>
                                        <Typography color="text.secondary" sx={{ml: 0.5}}>
                                            Secondary Devices {idx + 1}
                                        </Typography>
                                        <IconButton
                                            style={{
                                                color: "red"
                                            }}
                                            size="small"

                                            onClick={() => removeSecondaryDevice(idx)}
                                        >
                                            <RemoveCircleOutlineIcon fontSize="small"/>
                                        </IconButton>
                                    </div>
                                    <FormControl fullWidth size='small' sx={{mb: 1}} className='nodrag'>
                                        <InputLabel>Device</InputLabel>
                                        <Select
                                            variant='outlined'
                                            value={sd.deviceId}
                                            label="Device"
                                            onChange={(e) => updateSecondaryDevice(idx, 'deviceId', e.target.value)}
                                        >
                                            {devices && devices
                                                .filter(d => d.id !== selectedDevice?.id)
                                                .map(device => (
                                                    <MenuItem key={device.id} value={device.id}>
                                                        {device.name}
                                                    </MenuItem>
                                                ))}
                                        </Select>
                                    </FormControl>

                                    {sd.deviceId && (
                                        <FormControl fullWidth size='small' className='nodrag'>
                                            <InputLabel>Watch Key (optional)</InputLabel>
                                            <Select
                                                variant='outlined'
                                                value={sd.key}
                                                label="Watch Key (optional)"
                                                onChange={(e) => updateSecondaryDevice(idx, 'key', e.target.value)}
                                            >
                                                <MenuItem value="">
                                                    <em>Any / fetched by condition</em>
                                                </MenuItem>
                                                {getDeviceAttributes(sd.deviceId).map(attr => (
                                                    <MenuItem key={attr.id} value={attr.key}>
                                                        {attr.displayName}
                                                    </MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                    )}
                                </div>
                            ))}
                        </>
                    )}

                    {/* Add secondary device button */}
                    <Button
                        style={{
                            marginTop: 18,
                        }}
                        variant="outlined"
                        onClick={addSecondaryDevice}
                        className="nodrag"
                    >
                        <Typography>Add secondary device</Typography>
                    </Button>

                    <NumberSpinner
                        label="Priority"
                        min={0}
                        max={10}
                        value={priority}
                        size="small"
                        onChange={setPriority}
                    />
                </div>
            )}

            <Handle
                style={{width: '18px', height: '18px', background: '#6DBF6D', opacity: 0}}
                type="source"
                position={Position.Right}
                id={"rootNode:triggerNode:" + id}
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