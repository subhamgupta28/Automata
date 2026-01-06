import {
    Box,
    Card,
    CardContent,
    Grid,
    TextField,
    Typography,
    Switch,
    FormControlLabel,
    IconButton,
    Button,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody
} from "@mui/material";
import {Add, Delete} from "@mui/icons-material";
import {useState} from "react";
import {registerDevice} from "../../services/apis.jsx";


export default function DeviceCreateForm() {
    const [device, setDevice] = useState({
        name: "",
        attributes: [{
            displayName: "",
            key: "",
            units: "",
            type: "",
            deviceId: "",
            extras: {},
            visible: true
        }],
        status: "ONLINE",
        reboot: false,
        sleep: false,
        accessUrl: "",
        macAddr: "VIRTUAL-" + Math.random(),
        deviceId: "",
        updateInterval: 18000
    });

    const updateField = (field, value) => {
        setDevice({...device, [field]: value});
    };

    const updateAttribute = (
        index,
        field,
        value
    ) => {
        const updated = [...device.attributes];
        updated[index] = {...updated[index], [field]: value};
        setDevice({...device, attributes: updated});
    };

    const addAttribute = () => {
        setDevice({
            ...device,
            attributes: [
                ...device.attributes,
                {displayName: "", key: "", visible: true}
            ]
        });
    };

    const removeAttribute = (index) => {
        setDevice({
            ...device,
            attributes: device.attributes.filter((_, i) => i !== index)
        });
    };

    const onSave = async (device) => {
        await registerDevice(device)
    }

    return (
        <Card>
            <CardContent>
                <Typography variant="h6" gutterBottom>
                    Device Configuration
                </Typography>

                {/* Device Fields */}
                <Grid container spacing={2}>
                    <Grid item xs={12} md={6}>
                        <TextField
                            label="Name"
                            fullWidth
                            value={device.name}
                            onChange={(e) => updateField("name", e.target.value)}
                        />
                    </Grid>

                    <Grid item xs={12} md={6}>
                        <TextField
                            label="Type"
                            fullWidth
                            value={device.type || ""}
                            onChange={(e) => updateField("type", e.target.value)}
                        />
                    </Grid>

                    <Grid item xs={12} md={6}>
                        <TextField
                            label="Host"
                            fullWidth
                            value={device.host || ""}
                            onChange={(e) => updateField("host", e.target.value)}
                        />
                    </Grid>

                    <Grid item xs={12} md={6}>
                        <TextField
                            label="Update Interval (ms)"
                            type="number"
                            fullWidth
                            value={device.updateInterval || ""}
                            onChange={(e) =>
                                updateField("updateInterval", Number(e.target.value))
                            }
                        />
                    </Grid>

                    <Grid item xs={12}>
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={device.showInDashboard || false}
                                    onChange={(e) =>
                                        updateField("showInDashboard", e.target.checked)
                                    }
                                />
                            }
                            label="Show in Dashboard"
                        />

                        <FormControlLabel
                            control={
                                <Switch
                                    checked={device.analytics || false}
                                    onChange={(e) =>
                                        updateField("analytics", e.target.checked)
                                    }
                                />
                            }
                            label="Enable Analytics"
                        />
                    </Grid>
                </Grid>

                {/* Attributes */}
                <Box mt={4}>
                    <Box display="flex" justifyContent="space-between" mb={1}>
                        <Typography variant="subtitle1">Attributes</Typography>
                        <Button startIcon={<Add/>} onClick={addAttribute}>
                            Add Attribute
                        </Button>
                    </Box>

                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Display Name</TableCell>
                                <TableCell>Key</TableCell>
                                <TableCell>Units</TableCell>
                                <TableCell>Visible</TableCell>
                                <TableCell/>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {device.attributes.map((attr, index) => (
                                <TableRow key={index}>
                                    <TableCell>
                                        <TextField
                                            size="small"
                                            value={attr.displayName}
                                            onChange={(e) =>
                                                updateAttribute(index, "displayName", e.target.value)
                                            }
                                        />
                                    </TableCell>

                                    <TableCell>
                                        <TextField
                                            size="small"
                                            value={attr.key}
                                            onChange={(e) =>
                                                updateAttribute(index, "key", e.target.value)
                                            }
                                        />
                                    </TableCell>

                                    <TableCell>
                                        <TextField
                                            size="small"
                                            value={attr.units || ""}
                                            onChange={(e) =>
                                                updateAttribute(index, "units", e.target.value)
                                            }
                                        />
                                    </TableCell>

                                    <TableCell>
                                        <Switch
                                            checked={attr.visible ?? true}
                                            onChange={(e) =>
                                                updateAttribute(index, "visible", e.target.checked)
                                            }
                                        />
                                    </TableCell>

                                    <TableCell>
                                        <IconButton onClick={() => removeAttribute(index)}>
                                            <Delete/>
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Box>

                {/* Save */}
                <Box mt={3} textAlign="right">
                    <Button variant="contained" onClick={() => onSave(device)}>
                        Save Device
                    </Button>
                </Box>
            </CardContent>
        </Card>
    );
}
