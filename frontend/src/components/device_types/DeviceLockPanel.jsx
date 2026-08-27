import React, {useEffect, useState} from "react";
import {
    Box,
    Button,
    Checkbox,
    CircularProgress,
    Collapse,
    FormControlLabel,
    IconButton,
    InputAdornment,
    Slider,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material";
import LockIcon from "@mui/icons-material/Lock";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";
import {getVirtualDeviceLockedState, lockVirtualDevice} from "../../services/apis.jsx";


// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function StatusPill({locked}) {
    return (
        <Box
            sx={{
                display: "inline-flex",
                alignItems: "center",
                gap: 0.75,
                px: 1.5,
                py: 0.5,
                borderRadius: "20px",
                fontSize: "0.78rem",
                fontWeight: 600,
                letterSpacing: "0.03em",
                background: locked
                    ? "rgba(239,68,68,0.15)"
                    : "rgba(34,197,94,0.15)",
                color: locked ? "#f87171" : "#4ade80",
                border: locked
                    ? "1px solid rgba(239,68,68,0.3)"
                    : "1px solid rgba(34,197,94,0.3)",
                backdropFilter: "blur(4px)",
                transition: "all 0.3s ease",
            }}
        >
            {locked ? (
                <LockIcon sx={{fontSize: "0.9rem"}}/>
            ) : (
                <LockOpenIcon sx={{fontSize: "0.9rem"}}/>
            )}
            {locked ? "Locked" : "Unlocked"}
        </Box>
    );
}

function PinInput({label, value, onChange, error}) {
    const [show, setShow] = useState(false);
    return (
        <TextField
            label={label}
            type={show ? "text" : "password"}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            error={Boolean(error)}
            helperText={error || ""}
            size="small"
            fullWidth
            inputProps={{maxLength: 12}}
            InputProps={{
                endAdornment: (
                    <InputAdornment position="end">
                        <IconButton
                            size="small"
                            onClick={() => setShow((s) => !s)}
                            tabIndex={-1}
                        >
                            {show ? (
                                <VisibilityOffIcon fontSize="small"/>
                            ) : (
                                <VisibilityIcon fontSize="small"/>
                            )}
                        </IconButton>
                    </InputAdornment>
                ),
            }}
            sx={glassField}
        />
    );
}


// ---------------------------------------------------------------------------
// LockSetupForm — shown when the device is unlocked; lets you create a lock
// ---------------------------------------------------------------------------
function LockSetupForm({vid, onLocked}) {
    const [pin, setPin] = useState("");
    const [pinConfirm, setPinConfirm] = useState("");
    const [permanent, setPermanent] = useState(false);
    const [duration, setDuration] = useState(30);
    const [error, setError] = useState({});
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState("");

    const validate = () => {
        const errs = {};
        if (!pin) errs.pin = "PIN is required";
        else if (pin.length < 4) errs.pin = "Minimum 4 characters";
        if (pin !== pinConfirm) errs.pinConfirm = "PINs do not match";
        return errs;
    };

    const handleSubmit = async () => {
        const errs = validate();
        setError(errs);
        if (Object.keys(errs).length) return;

        setLoading(true);
        setSuccess("");
        try {
            await lockVirtualDevice({
                vid,
                password: pin,
                permanentUnlock: permanent,
                unlockDurationMinutes: permanent ? 0 : duration,
            });
            setSuccess("Lock created. Device is now protected.");
            setPin("");
            setPinConfirm("");
            onLocked();
        } catch (err) {
            setError({submit: err.message || "Failed to create lock"});
        } finally {
            setLoading(false);
        }
    };

    const durationLabel = (v) =>
        v < 60 ? `${v} min` : `${(v / 60).toFixed(1).replace(/\.0$/, "")} hr`;

    return (
        <Box sx={sectionBox}>
            <Typography variant="subtitle2" sx={sectionLabel}>
                Set a Lock
            </Typography>
            <Box sx={{display: "flex", flexDirection: "column", gap: 1.5, mt: 1}}>
                <PinInput
                    label="New PIN"
                    value={pin}
                    onChange={setPin}
                    error={error.pin}
                />
                <PinInput
                    label="Confirm PIN"
                    value={pinConfirm}
                    onChange={setPinConfirm}
                    error={error.pinConfirm}
                />

                <FormControlLabel
                    control={
                        <Checkbox
                            checked={permanent}
                            onChange={(e) => setPermanent(e.target.checked)}
                            size="small"
                        />
                    }
                    label={
                        <Typography variant="body2" color="text.secondary">
                            Permanent unlock (stays unlocked until manually locked)
                        </Typography>
                    }
                />

                <Collapse in={!permanent}>
                    <Box sx={{px: 1}}>
                        <Box
                            sx={{
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center",
                                mb: 0.5,
                            }}
                        >
                            <Typography variant="caption" color="text.secondary">
                                Auto-lock after
                            </Typography>
                            <Typography
                                variant="caption"
                                color="primary.main"
                                fontWeight={600}
                            >
                                {durationLabel(duration)}
                            </Typography>
                        </Box>
                        <Slider
                            min={5}
                            max={480}
                            step={5}
                            value={duration}
                            onChange={(_, v) => setDuration(v)}
                            size="small"
                            marks={[
                                {value: 30, label: "30m"},
                                {value: 120, label: "2h"},
                                {value: 480, label: "8h"},
                            ]}
                        />
                    </Box>
                </Collapse>

                {error.submit && (
                    <Typography variant="caption" color="error">
                        {error.submit}
                    </Typography>
                )}
                {success && (
                    <Typography variant="caption" color="success.main">
                        {success}
                    </Typography>
                )}

                <Button
                    variant="contained"
                    size="small"
                    onClick={handleSubmit}
                    disabled={loading}
                    startIcon={
                        loading ? (
                            <CircularProgress size={14} color="inherit"/>
                        ) : (
                            <LockIcon fontSize="small"/>
                        )
                    }
                    sx={actionBtn}
                >
                    Create Lock
                </Button>
            </Box>
        </Box>
    );
}

// ---------------------------------------------------------------------------
// DeviceLockPanel — the exported component to drop into ModelContent
// ---------------------------------------------------------------------------
export function DeviceLockPanel({vid}) {
    const [locked, setLocked] = useState(true); // null = loading
    const [error, setError] = useState("");

    const loadState = async () => {
        try {
            const state = await getVirtualDeviceLockedState(vid);
            console.log("locked", state)
            setLocked(state);
        } catch {
            console.log("Could not load lock state");
        }
    };

    useEffect(() => {
        loadState();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [vid]);

    if (error) {
        return (
            <Typography variant="caption" color="error">
                {error}
            </Typography>
        );
    }

    if (locked === null) {
        return (
            <Box sx={{display: "flex", justifyContent: "center", py: 2}}>
                <CircularProgress size={20}/>
            </Box>
        );
    }

    return (
        <Box>
            {/* Header row */}
            <Box
                sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    mb: 1.5,
                }}
            >
                <Typography variant="subtitle2" color="text.secondary">
                    Device Lock
                </Typography>
                <Tooltip
                    title={
                        locked
                            ? "Enter your PIN to unlock"
                            : "Create a PIN lock for this device"
                    }
                >
                  <span>
                    <StatusPill locked={locked}/>
                  </span>
                </Tooltip>
            </Box>

            {!locked && (<LockSetupForm vid={vid} onLocked={() => setLocked(true)}/>)}

        </Box>
    );
}

// ---------------------------------------------------------------------------
// Shared styles (match the modal's glassmorphism theme)
// ---------------------------------------------------------------------------
const sectionBox = {
    background: "rgba(255,255,255,0.06)",
    backdropFilter: "blur(6px)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "10px",
    p: 1.5,
};

const sectionLabel = {
    fontWeight: 600,
    letterSpacing: "0.04em",
    color: "text.secondary",
    textTransform: "uppercase",
    fontSize: "0.7rem",
};

const glassField = {
    "& .MuiOutlinedInput-root": {
        background: "rgba(255,255,255,0.06)",
        backdropFilter: "blur(4px)",
        borderRadius: "8px",
    },
};

const actionBtn = {
    borderRadius: "8px",
    textTransform: "none",
    fontWeight: 600,
    alignSelf: "flex-start",
};