import {useState} from "react";
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    IconButton,
    MenuItem,
    Select,
    Snackbar,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    TextField,
    Typography,
    InputLabel,
    FormControl,
} from "@mui/material";
import {Add, Close, Delete} from "@mui/icons-material";
import {registerDevice} from "../services/apis.jsx";

const ATTRIBUTE_TYPES = [
    "DATA|GAUGE",
    "DATA|MAIN",
    "DATA|RADAR",
    "DATA|MAP",
    "ACTION|SLIDER",
    "ACTION|SWITCH",
    "ACTION|PRESET",
    "ACTION|OUT",
];

const DEFAULT_DEVICE = () => ({
    name: "",
    type: "",
    category: "",
    host: "",
    accessUrl: "",
    macAddr: "",
    updateInterval: 18000,
    showInDashboard: false,
    analytics: false,
    status: "OFFLINE",
    reboot: false,
    sleep: false,
    attributes: [],
});

const DEFAULT_ATTR = () => ({
    displayName: "",
    key: "",
    units: "",
    type: "DATA|GAUGE",
    visible: true,
});

export default function CreateDeviceDialog({open, onClose, onCreated}) {
    const [device, setDevice] = useState(DEFAULT_DEVICE());
    const [loading, setLoading] = useState(false);
    const [snackbar, setSnackbar] = useState({open: false, message: "", severity: "success"});

    const updateField = (field, value) => setDevice(prev => ({...prev, [field]: value}));

    const addAttribute = () =>
        setDevice(prev => ({...prev, attributes: [...prev.attributes, DEFAULT_ATTR()]}));

    const updateAttr = (index, field, value) =>
        setDevice(prev => {
            const attrs = [...prev.attributes];
            attrs[index] = {...attrs[index], [field]: value};
            return {...prev, attributes: attrs};
        });

    const removeAttr = (index) =>
        setDevice(prev => ({...prev, attributes: prev.attributes.filter((_, i) => i !== index)}));

    const handleSave = async () => {
        if (!device.name.trim()) {
            setSnackbar({open: true, message: "Device name is required.", severity: "error"});
            return;
        }
        setLoading(true);
        try {
            const payload = {
                ...device,
                macAddr: device.macAddr.trim() || "VIRTUAL-" + Date.now(),
            };
            await registerDevice(payload);
            setSnackbar({open: true, message: "Device created successfully.", severity: "success"});
            setDevice(DEFAULT_DEVICE());
            onCreated?.();
            onClose();
        } catch (err) {
            setSnackbar({open: true, message: "Failed to create device.", severity: "error"});
        } finally {
            setLoading(false);
        }
    };

    const handleClose = () => {
        setDevice(DEFAULT_DEVICE());
        onClose();
    };

    return (
        <>
            <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
                <DialogTitle sx={{display: "flex", justifyContent: "space-between", alignItems: "center"}}>
                    <Typography variant="h6">Create Device</Typography>
                    <IconButton size="small" onClick={handleClose}>
                        <Close fontSize="small"/>
                    </IconButton>
                </DialogTitle>

                <DialogContent dividers>
                    {/* Basic info */}
                    <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2} mb={3}>
                        <TextField
                            label="Name *"
                            size="small"
                            fullWidth
                            value={device.name}
                            onChange={e => updateField("name", e.target.value)}
                        />
                        <TextField
                            label="Type"
                            size="small"
                            fullWidth
                            value={device.type}
                            onChange={e => updateField("type", e.target.value)}
                            placeholder="e.g. sensor, controller"
                        />
                        <TextField
                            label="Category"
                            size="small"
                            fullWidth
                            value={device.category}
                            onChange={e => updateField("category", e.target.value)}
                        />
                        <TextField
                            label="Host"
                            size="small"
                            fullWidth
                            value={device.host}
                            onChange={e => updateField("host", e.target.value)}
                            placeholder="e.g. 192.168.1.100"
                        />
                        <TextField
                            label="MAC Address"
                            size="small"
                            fullWidth
                            value={device.macAddr}
                            onChange={e => updateField("macAddr", e.target.value)}
                            placeholder="Leave blank to auto-generate"
                        />
                        <TextField
                            label="Access URL"
                            size="small"
                            fullWidth
                            value={device.accessUrl}
                            onChange={e => updateField("accessUrl", e.target.value)}
                        />
                        <TextField
                            label="Update Interval (ms)"
                            size="small"
                            type="number"
                            fullWidth
                            value={device.updateInterval}
                            onChange={e => updateField("updateInterval", Number(e.target.value))}
                        />
                    </Box>

                    {/* Toggles */}
                    <Box mb={3}>
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={device.showInDashboard}
                                    onChange={e => updateField("showInDashboard", e.target.checked)}
                                />
                            }
                            label="Show in Dashboard"
                        />
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={device.analytics}
                                    onChange={e => updateField("analytics", e.target.checked)}
                                />
                            }
                            label="Enable Analytics"
                        />
                    </Box>

                    {/* Attributes */}
                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                        <Typography variant="subtitle2">Attributes</Typography>
                        <Button size="small" startIcon={<Add/>} onClick={addAttribute}>
                            Add Attribute
                        </Button>
                    </Box>

                    {device.attributes.length === 0 ? (
                        <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
                            No attributes yet. Click "Add Attribute" to define device fields.
                        </Typography>
                    ) : (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Display Name</TableCell>
                                    <TableCell>Key</TableCell>
                                    <TableCell>Units</TableCell>
                                    <TableCell>Type</TableCell>
                                    <TableCell>Visible</TableCell>
                                    <TableCell/>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {device.attributes.map((attr, i) => (
                                    <TableRow key={i}>
                                        <TableCell>
                                            <TextField
                                                size="small"
                                                value={attr.displayName}
                                                onChange={e => updateAttr(i, "displayName", e.target.value)}
                                                placeholder="Temperature"
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <TextField
                                                size="small"
                                                value={attr.key}
                                                onChange={e => updateAttr(i, "key", e.target.value)}
                                                placeholder="temp"
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <TextField
                                                size="small"
                                                value={attr.units}
                                                onChange={e => updateAttr(i, "units", e.target.value)}
                                                placeholder="°C"
                                                sx={{width: 70}}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <FormControl size="small" sx={{minWidth: 140}}>
                                                <Select
                                                    value={attr.type}
                                                    onChange={e => updateAttr(i, "type", e.target.value)}
                                                >
                                                    {ATTRIBUTE_TYPES.map(t => (
                                                        <MenuItem key={t} value={t}>{t}</MenuItem>
                                                    ))}
                                                </Select>
                                            </FormControl>
                                        </TableCell>
                                        <TableCell>
                                            <Switch
                                                size="small"
                                                checked={attr.visible}
                                                onChange={e => updateAttr(i, "visible", e.target.checked)}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <IconButton size="small" onClick={() => removeAttr(i)}>
                                                <Delete fontSize="small"/>
                                            </IconButton>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </DialogContent>

                <DialogActions>
                    <Button onClick={handleClose} disabled={loading}>Cancel</Button>
                    <Button variant="contained" onClick={handleSave} disabled={loading}>
                        {loading ? "Creating…" : "Create Device"}
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={4000}
                onClose={() => setSnackbar(s => ({...s, open: false}))}
                anchorOrigin={{vertical: "bottom", horizontal: "center"}}
            >
                <Alert severity={snackbar.severity} onClose={() => setSnackbar(s => ({...s, open: false}))}>
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </>
    );
}
