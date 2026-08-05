import {useCallback, useEffect, useState} from "react";
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    IconButton,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Snackbar,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material";
import {Add as AddIcon, Delete as DeleteIcon, Refresh as RefreshIcon} from "@mui/icons-material";
import {createFeature, deleteFeature, getFeatures, toggleFeature} from "../../services/apis.jsx";

// ── Constants ─────────────────────────────────────────────────────────────────

const GROUPS = ["SYSTEM", "UI", "BACKEND", "EXPERIMENT", "RELEASE"];

const DEFAULT_FORM = {featureKey: "", description: "", group: "SYSTEM", isEnabled: false};

// ── Component ─────────────────────────────────────────────────────────────────

export default function FeatureManager() {
    const [features, setFeatures] = useState([]);
    const [loading, setLoading] = useState(true);
    const [toggling, setToggling] = useState(null);   // id currently toggling
    const [deleting, setDeleting] = useState(null);   // id currently deleting
    const [dialogOpen, setDialogOpen] = useState(false);
    const [form, setForm] = useState(DEFAULT_FORM);
    const [formErrors, setFormErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const [snack, setSnack] = useState({open: false, message: "", severity: "success"});

    // ── Notifications ─────────────────────────────────────────────────────────

    const notify = (message, severity = "success") =>
        setSnack({open: true, message, severity});

    // ── Handlers ──────────────────────────────────────────────────────────────

    const fetchFeatures = useCallback(async () => {
        setLoading(true);
        try {
            setFeatures(await getFeatures());
        } catch {
            notify("Failed to load features", "error");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchFeatures();
    }, [fetchFeatures]);

    const handleToggle = async (feature) => {
        setToggling(feature.id);
        try {
            const updated = await toggleFeature(feature.id);
            setFeatures(prev => prev.map(f => f.id === updated.id ? updated : f));
            notify(`${updated.featureKey} ${updated.enabled ? "enabled" : "disabled"}`,
                updated.enabled ? "success" : "warning");
        } catch {
            notify("Toggle failed", "error");
        } finally {
            setToggling(null);
        }
    };

    const handleDelete = async (feature) => {
        if (!window.confirm(`Delete "${feature.featureKey}"?`)) return;
        setDeleting(feature.id);
        try {
            await deleteFeature(feature.id);
            setFeatures(prev => prev.filter(f => f.id !== feature.id));
            notify(`${feature.featureKey} deleted`);
        } catch {
            notify("Delete failed", "error");
        } finally {
            setDeleting(null);
        }
    };

    const validateForm = () => {
        const errors = {};
        if (!form.featureKey.trim())
            errors.featureKey = "Key is required";
        else if (!/^[A-Z0-9_]+$/.test(form.featureKey))
            errors.featureKey = "Use UPPER_SNAKE_CASE (A-Z, 0-9, _)";
        if (!form.description.trim())
            errors.description = "Description is required";
        return errors;
    };

    const handleCreate = async () => {
        const errors = validateForm();
        if (Object.keys(errors).length) {
            setFormErrors(errors);
            return;
        }

        setSubmitting(true);
        try {
            const created = await createFeature(form);
            setFeatures(prev => [created, ...prev]);
            notify(`${created.featureKey} created`);
            setDialogOpen(false);
            setForm(DEFAULT_FORM);
        } catch {
            notify("Create failed", "error");
        } finally {
            setSubmitting(false);
        }
    };

    const openDialog = () => {
        setForm(DEFAULT_FORM);
        setFormErrors({});
        setDialogOpen(true);
    };

    // ── Render ────────────────────────────────────────────────────────────────

    return (
        <Box sx={{p: {xs: 2, md: 4}}}>

            {/* Header */}
            <Box sx={{
                display: "flex", alignItems: "center", justifyContent: "space-between",
                flexWrap: "wrap", gap: 1, mb: 3
            }}>
                <Box>
                    <Typography variant="h6" fontWeight={700}>Feature Flags</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {features.length} flag{features.length !== 1 ? "s" : ""} · changes clear Redis cache instantly
                    </Typography>
                </Box>
                <Box sx={{display: "flex", gap: 1}}>
                    <Button variant="outlined" size="small" startIcon={<RefreshIcon fontSize="small"/>}
                            onClick={fetchFeatures} disabled={loading}>
                        Refresh
                    </Button>
                    <Button variant="contained" size="small" startIcon={<AddIcon fontSize="small"/>}
                            onClick={openDialog}>
                        New Flag
                    </Button>
                </Box>
            </Box>

            {/* Table */}
            <Paper variant="outlined">
                <TableContainer>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Key</TableCell>
                                <TableCell>Description</TableCell>
                                <TableCell>Group</TableCell>
                                <TableCell>Status</TableCell>
                                <TableCell align="center">Enabled</TableCell>
                                <TableCell align="center">Actions</TableCell>
                            </TableRow>
                        </TableHead>

                        <TableBody>
                            {loading ? (
                                <TableRow>
                                    <TableCell colSpan={6}>
                                        <Box sx={{display: "flex", justifyContent: "center", py: 6}}>
                                            <CircularProgress size={28}/>
                                        </Box>
                                    </TableCell>
                                </TableRow>
                            ) : features.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={6}>
                                        <Typography color="text.secondary" textAlign="center" py={6}>
                                            No flags yet — create one above.
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            ) : (
                                features.map(f => (
                                    <TableRow key={f.id} hover>
                                        <TableCell sx={{fontFamily: "monospace", fontSize: "0.8rem"}}>
                                            {f.featureKey}
                                        </TableCell>
                                        <TableCell sx={{color: "text.secondary", fontSize: "0.82rem"}}>
                                            {f.description || "—"}
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={f.group} size="small" variant="outlined"/>
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={f.enabled ? "Enabled" : "Disabled"}
                                                size="small"
                                                color={f.enabled ? "success" : "default"}
                                            />
                                        </TableCell>
                                        <TableCell align="center">
                                            {toggling === f.id ? (
                                                <CircularProgress size={18}/>
                                            ) : (
                                                <Tooltip title={f.enabled ? "Disable" : "Enable"}>
                                                    <Switch checked={!!f.enabled} onChange={() => handleToggle(f)}
                                                            size="small"/>
                                                </Tooltip>
                                            )}
                                        </TableCell>
                                        <TableCell align="center">
                                            {deleting === f.id ? (
                                                <CircularProgress size={18}/>
                                            ) : (
                                                <Tooltip title="Delete flag">
                                                    <IconButton size="small" color="error"
                                                                onClick={() => handleDelete(f)}>
                                                        <DeleteIcon fontSize="small"/>
                                                    </IconButton>
                                                </Tooltip>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Paper>

            {/* Create dialog */}
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
                <DialogTitle>New Feature Flag</DialogTitle>
                <Divider/>

                <DialogContent sx={{display: "flex", flexDirection: "column", gap: 2, pt: 2.5}}>
                    <TextField
                        label="Feature Key"
                        placeholder="e.g. DARK_MODE"
                        value={form.featureKey}
                        onChange={e => setForm(p => ({
                            ...p, featureKey: e.target.value.toUpperCase().replace(/\s/g, "_")
                        }))}
                        error={!!formErrors.featureKey}
                        helperText={formErrors.featureKey || "UPPER_SNAKE_CASE — used in isFeatureEnabled()"}
                        size="small"
                        fullWidth
                    />

                    <TextField
                        label="Description"
                        placeholder="What does this flag control?"
                        value={form.description}
                        onChange={e => setForm(p => ({...p, description: e.target.value}))}
                        error={!!formErrors.description}
                        helperText={formErrors.description}
                        size="small"
                        fullWidth
                        multiline
                        rows={2}
                    />

                    <FormControl size="small" fullWidth>
                        <InputLabel>Group</InputLabel>
                        <Select
                            value={form.group}
                            label="Group"
                            onChange={e => setForm(p => ({...p, group: e.target.value}))}
                        >
                            {GROUPS.map(g => <MenuItem key={g} value={g}>{g}</MenuItem>)}
                        </Select>
                    </FormControl>

                    <Box sx={{
                        display: "flex", alignItems: "center", justifyContent: "space-between",
                        border: 1, borderColor: "divider", borderRadius: 1, px: 1.5, py: 1
                    }}>
                        <Box>
                            <Typography variant="body2">Start enabled</Typography>
                            <Typography variant="caption" color="text.secondary">
                                Flag will be active immediately after creation
                            </Typography>
                        </Box>
                        <Switch
                            checked={form.isEnabled}
                            onChange={e => setForm(p => ({...p, isEnabled: e.target.checked}))}
                            size="small"
                        />
                    </Box>
                </DialogContent>

                <Divider/>
                <DialogActions sx={{px: 2.5, py: 1.5}}>
                    <Button onClick={() => setDialogOpen(false)} color="inherit" size="small">Cancel</Button>
                    <Button onClick={handleCreate} disabled={submitting} variant="contained" size="small"
                            startIcon={submitting ? <CircularProgress size={14} color="inherit"/> : null}>
                        {submitting ? "Creating…" : "Create Flag"}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Toast */}
            <Snackbar open={snack.open} autoHideDuration={3500}
                      onClose={() => setSnack(s => ({...s, open: false}))}
                      anchorOrigin={{vertical: "bottom", horizontal: "center"}}>
                <Alert severity={snack.severity} onClose={() => setSnack(s => ({...s, open: false}))}>
                    {snack.message}
                </Alert>
            </Snackbar>
        </Box>
    );
}
