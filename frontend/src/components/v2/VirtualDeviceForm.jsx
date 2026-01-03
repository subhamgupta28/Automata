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

const DashboardEditor = ({change, existingDevice}) => {
    const {devices, loading, error} = useCachedDevices();
    const [selectedDevice, setSelectedDevice] = useState([]);

    const [tag, setTag] = useState("Other");
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
    useEffect(() => {
        if (existingDevice) {
            const ids = existingDevice.deviceIds;
            const device = devices?.filter((f) => ids.includes(f.id))
            setSelectedDevice(device)
            setForm(existingDevice)
        }
    }, [existingDevice])


    const deviceMap = useMemo(() => {
        const map = {};
        devices?.forEach((d) => (map[d.id] = d));
        return map;
    }, [devices]);
    const handleChange = (key, value) => {
        setForm((prev) => ({...prev, [key]: value}));

    };

    const handleDeviceChange = (selectedIds) => {
        // console.log(selectedIds.map(d=> d.id))
        const ids = selectedIds.map(d => d.id);
        const newAttributes = {...form.attributes};

        ids.forEach((id) => {
            if (!newAttributes[id]) newAttributes[id] = [];
        });

        Object.keys(newAttributes).forEach((id) => {
            if (!ids.includes(id)) delete newAttributes[id];
        });
        setSelectedDevice(selectedIds)
        setForm({
            ...form,
            // devices: selectedIds,
            deviceIds: ids,
            attributes: newAttributes,
        });
    };

    const handleAttributeChange = (deviceId, attrs) => {
        setForm((prev) => ({
            ...prev,
            attributes: {...prev.attributes, [deviceId]: attrs},
        }));
    };

    const submit = () => {
        const data = {
            ...form,
            lastModified: new Date(),
        }
        saveVirtualDevice(data)
            .then(
                () => {
                    console.log(data)
                    change();
                }
            )

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
        setSelectedDevice([])
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
                    variant="standard"
                    multiple
                    size="small"
                    value={selectedDevice}
                    onChange={(e) => handleDeviceChange(e.target.value)}
                    input={<OutlinedInput label="Devices"/>}
                    renderValue={(selected) => (
                        <Box sx={{display: "flex", flexWrap: "wrap", gap: 0.5}}>
                            {selected.map((value) => (
                                <Chip key={value.id} label={value.name}/>
                            ))}
                        </Box>
                    )}
                >
                    {devices?.map((d) => (
                        <MenuItem key={d.id} value={d}>
                            {d.name}
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>
            {form.deviceIds.map((deviceId) => (
                <FormControl fullWidth style={{marginTop: '20px'}}>
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
                        input={
                            <OutlinedInput
                                label={`Attributes (${deviceMap[deviceId]?.name})`}
                            />
                        }
                        renderValue={(selected) => (
                            <Box sx={{display: "flex", flexWrap: "wrap", gap: 0.5}}>
                                {selected.map((attr) => (
                                    <Chip key={attr.id} label={attr.key}/>
                                ))}
                            </Box>
                        )}
                    >
                        {(deviceMap && deviceMap[deviceId]?.attributes || []).map((attr) => (
                            <MenuItem key={attr.id} value={attr}>
                                {attr.key}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            ))}

            <Button variant="contained" size="small" fullWidth onClick={submit} style={{marginTop: '20px'}}>
                Save Virtual Device
            </Button>
            {selectedDevice.length !== 0 && (
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
            <Typography variant="h6" mb={2} pt={5}>
                Virtual Device
            </Typography>

            <Stack direction="row" style={{height: '80%'}} divider={<Divider orientation="vertical" flexItem/>}
                   spacing={2}>
                <div style={{width: '30%'}}>
                    <DashboardEditor change={fetch} existingDevice={selectedDevice}/>
                </div>
                <div style={{width: '50%'}}>

                </div>

                <div style={{width: '20%'}}>
                    <List>
                        {virtualDeviceList.map((a) => (
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
