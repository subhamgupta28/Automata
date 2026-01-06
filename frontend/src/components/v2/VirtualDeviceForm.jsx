import React, {useEffect, useMemo, useState} from "react";
import {
    Box,
    TextField,
    Button,
    Chip,
    Select,
    MenuItem,
    InputLabel,
    FormControl,
    OutlinedInput,
    Typography, CircularProgress, Alert, Card,
} from "@mui/material";
import Stack from "@mui/material/Stack";
import Divider from "@mui/material/Divider";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {getVirtualDeviceList, saveVirtualDevice} from "../../services/apis.jsx";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import List from "@mui/material/List";
import DeviceCreateForm from "./DeviceCreateForm.jsx";

const DashboardEditor = ({change, existingDevice}) => {
    const {devices, loading, error} = useCachedDevices();
    const [selectedDeviceIds, setSelectedDeviceIds] = useState([]);
    // const [form, setForm] = useState({
    //     name: existingDevice.name || "",
    //     x: existingDevice.x || 0,
    //     y: existingDevice.y || 0,
    //     tag: existingDevice.tag || "Other",
    //     height: existingDevice.height || 200,
    //     width: existingDevice.width || 200,
    //     devices: existingDevice.devices || [],
    //     deviceIds: existingDevice.deviceIds || [],
    //     attributes: existingDevice.attributes || {},
    // });

    const [form, setForm] = useState({
        name: "",
        x: 0,
        y: 0,
        tag: "Other",
        height: 200,
        width: 200,
        devices: [],
        deviceIds: [],
        attributes: {},
    });
    const deviceMap = useMemo(() => {
        const map = {};
        devices?.forEach((d) => (map[d.id] = d));
        return map;
    }, [devices]);

    useEffect(() => {
        if (!existingDevice || !devices?.length) return;
        console.log("exist", existingDevice)
        const deviceIds = existingDevice.deviceIds || [];
        const normalizedAttributes = {};
        let ready = true;

        deviceIds.forEach(deviceId => {
            const device = deviceMap[deviceId];

            // 🔴 ATTRIBUTES NOT READY → ABORT NORMALIZATION
            if (!device || !Array.isArray(device.attributes)) {
                ready = false;
                return;
            }

            const savedAttrs = existingDevice.attributes?.[deviceId] || [];
            const deviceAttrs = deviceMap[deviceId]?.attributes || [];

            const keyToIdMap = new Map(
                deviceAttrs.map(a => [a.key, a.id])
            );

            normalizedAttributes[deviceId] = savedAttrs
                .map(a => keyToIdMap.get(a.key))
                .filter(Boolean);
        });

        // 🔴 WAIT until ALL device attributes exist
        if (!ready) return;

        setSelectedDeviceIds(deviceIds);

        console.log("form",{
            ...existingDevice,
            deviceIds,
            attributes: normalizedAttributes,
        } )
        setForm(prev => ({
            ...prev,
            ...existingDevice,
            deviceIds,
            attributes: normalizedAttributes,
        }));
    }, [existingDevice, devices, deviceMap]);






    const handleChange = (key, value) => {
        setForm((prev) => ({...prev, [key]: value}));

    };

    const handleDeviceChange = (ids) => {
        const newAttributes = { ...form.attributes };

        ids.forEach(id => {
            if (!newAttributes[id]) newAttributes[id] = [];
        });

        Object.keys(newAttributes).forEach(id => {
            if (!ids.includes(id)) delete newAttributes[id];
        });

        setSelectedDeviceIds(ids);
        setForm(prev => ({
            ...prev,
            deviceIds: ids,
            attributes: newAttributes,
        }));
    };

    const handleAttributeChange = (deviceId, attrIds) => {
        setForm(prev => ({
            ...prev,
            attributes: {
                ...prev.attributes,
                [deviceId]: attrIds,
            },
        }));
    };

    const submit = () => {
        const expandedAttributes = {};

        Object.entries(form.attributes).forEach(([deviceId, attrIds]) => {
            expandedAttributes[deviceId] =
                attrIds.map(attrId =>
                    deviceMap[deviceId]?.attributes
                        ?.find(a => a.id === attrId)
                ).filter(Boolean);
        });

        const data = {
            ...form,
            attributes: expandedAttributes,
            lastModified: new Date().toISOString(),
        };
        console.log("save", data)
        saveVirtualDevice(data).then(() => change());
    };
    const handleClear = () => {
        setForm(
            {
                name: "",
                x: 0,
                y: 0,
                tag: "Other",
                height: 200,
                width: 200,
                devices: [],
                deviceIds: [],
                attributes: {},
            }
        )
        setSelectedDeviceIds([]);
    }

    if (loading) return <CircularProgress/>;
    if (error) return <Alert severity="error">Failed to load devices</Alert>;

    return (
        <div>
            <TextField
                label="Name"
                fullWidth
                size="small"
                required
                style={{marginTop: '20px'}}
                value={form.name}
                onChange={(e) => handleChange("name", e.target.value)}
            />
            <FormControl fullWidth className='nodrag' sx={{marginBottom: 2, marginTop: 2}}>
                <InputLabel id="demo-simple-select-label">Tag</InputLabel>
                <Select
                    labelId="demo-simple-select-label"
                    id="demo-simple-select"
                    required
                    value={form.tag}
                    size='small'
                    label="Tag"
                    name="tag"
                    onChange={(e) => handleChange("tag", e.target.value)}
                    variant='outlined'>
                    <MenuItem key={'Weather'} value={'Weather'}>Weather</MenuItem>
                    <MenuItem key={'Energy'} value={'Energy'}> Energy </MenuItem>
                    <MenuItem key={'Sensors'} value={'Sensors'}> Sensors </MenuItem>
                    <MenuItem key={'Lights'} value={'Lights'}> Lights </MenuItem>
                    <MenuItem key={'Actions'} value={'Actions'}> Actions </MenuItem>
                    <MenuItem key={'Other'} value={'Other'}> Other </MenuItem>
                </Select>
            </FormControl>
            <FormControl fullWidth style={{marginTop: '20px'}}>
                <InputLabel>Devices</InputLabel>
                <Select
                    multiple
                    variant="standard"
                    value={selectedDeviceIds}
                    onChange={(e) => handleDeviceChange(e.target.value)}
                    renderValue={(selected) => (
                        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                            {selected.map(id => (
                                <Chip key={id} label={deviceMap[id]?.name} />
                            ))}
                        </Box>
                    )}
                >
                    {devices?.map(d => (
                        <MenuItem key={d.id} value={d.id}>
                            {d.name}
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>
            {form.deviceIds.map((deviceId) => (
                <FormControl key={deviceId} fullWidth style={{marginTop: '20px'}}>
                    <InputLabel>
                        Attributes ({deviceMap[deviceId]?.name})
                    </InputLabel>
                    <Select
                        multiple
                        variant="standard"
                        size="small"
                        value={form.attributes[deviceId] || []}
                        onChange={(e) =>
                            handleAttributeChange(deviceId, e.target.value)
                        }
                        renderValue={(selected) => (
                            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                                {selected.map(attrId => {
                                    const attr = deviceMap[deviceId]?.attributes
                                        ?.find(a => a.id === attrId);

                                    return (
                                        <Chip
                                            key={attrId}
                                            label={attr?.key}
                                        />
                                    );
                                })}
                            </Box>
                        )}
                    >
                        {deviceMap[deviceId]?.attributes.map(attr => (
                            <MenuItem key={attr.id} value={attr.id}>
                                {attr.key}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            ))}

            <Button variant="contained" size="small" fullWidth onClick={submit} style={{marginTop: '20px'}}>
                Save Virtual Device
            </Button>
            {selectedDeviceIds.length !== 0 && (
                <Button variant="contained" size="small" fullWidth onClick={handleClear} style={{marginTop: '20px'}}>
                    Clear Selection
                </Button>
            )}

        </div>
    )
}

const VirtualDeviceForm = () => {

    const [virtualDeviceList, setVirtualDeviceList] = useState([]);
    const [selectedDevice, setSelectedDevice] = useState(null)

    const fetch = async () => {
        const list = await getVirtualDeviceList();
        setVirtualDeviceList(list);
    }
    useEffect(() => {

        fetch();
    }, [])

    const openEditor = (a) => {
        setSelectedDevice(a)
    }


    return (
        <Box p={3} style={{height: '100vh'}}>

            <Stack direction="row" style={{height: '80%', marginTop:'20px'}} divider={<Divider orientation="vertical" flexItem/>}
                   spacing={2}>

                <div style={{width: '30%'}}>
                    Create Virtual Device
                    <DashboardEditor change={fetch} existingDevice={selectedDevice}/>
                </div>
                <div style={{width: '50%'}}>
                    <DeviceCreateForm/>
                </div>

                <div style={{width: '20%'}}>
                    Saved Virtual Devices
                    <List>
                        {virtualDeviceList && virtualDeviceList.map((a) => (
                            <ListItem
                                variant="outlined"
                                component={Card}
                                style={{
                                    padding: '6px', marginTop: '8px',
                                    background: 'transparent',
                                    backdropFilter: 'blur(6px)',
                                    borderColor: '#ffffff',
                                    backgroundColor: 'rgb(255 255 255 / 8%)',
                                }}
                                key={a.id}
                            >
                                <ListItemText>{a.name}</ListItemText>
                                <Button size="small" onClick={() => openEditor(a)}>
                                    Edit
                                </Button>
                            </ListItem>
                        ))}
                    </List>
                </div>
            </Stack>
        </Box>
    );
};

export default VirtualDeviceForm;
