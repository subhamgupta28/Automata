import React from "react";
import {useSnackbar} from "notistack";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import Button from "@mui/material/Button";
import CloseIcon from "@mui/icons-material/Close";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";

const SEVERITY = {
    info: {icon: InfoOutlinedIcon, color: "#47abf5", label: "Notice"},
    success: {icon: CheckCircleOutlineIcon, color: "#9ccc65", label: "Done"},
    warning: {icon: WarningAmberIcon, color: "#f8e697", label: "Warning"},
    error: {icon: ErrorOutlineIcon, color: "#e57373", label: "Error"},
    automation: {icon: SmartToyOutlinedIcon, color: "rgb(162,157,81)", label: "Automation"},
};

const AutomataSnackbar = React.forwardRef(({id, message, header, variant = "info", onStop, onSnooze}, ref) => {
    const {closeSnackbar} = useSnackbar();
    const cfg = SEVERITY[variant] ?? SEVERITY.info;
    const Icon = cfg.icon;
    return (
        <Box
            ref={ref}
            sx={{
                display: "flex",
                alignItems: "flex-start",
                gap: "12px",
                backgroundColor: "#1e1e1e",
                border: "1px solid #2a2a2a",
                borderRadius: "10px",
                p: "14px 16px",
                minWidth: 320,
                maxWidth: 400,
                position: "relative",
                overflow: "hidden",
                "&::before": {
                    content: '""',
                    position: "absolute",
                    left: 0, top: 0, bottom: 0,
                    width: "3px",
                    background: cfg.color,
                    borderRadius: "10px 0 0 10px",
                },
            }}
        >
            {/* Icon badge */}
            <Box
                sx={{
                    width: 30, height: 30,
                    borderRadius: "7px",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    flexShrink: 0,
                    backgroundColor: `${cfg.color}1f`,
                    color: cfg.color,
                }}
            >
                <Icon sx={{fontSize: 16}}/>
            </Box>

            {/* Body */}
            <Box sx={{flex: 1, minWidth: 0}}>
                <Typography sx={{fontSize: 13, fontWeight: 500, color: "#fff", mb: "2px"}}>
                    {header ?? cfg.label}
                </Typography>
                <Typography sx={{fontSize: 12, color: "#b0b0b0", lineHeight: 1.5}}>
                    {message}
                </Typography>

                {variant === "automation" && (
                    <Box sx={{display: "flex", gap: "8px", mt: "10px"}}>
                        <Button
                            size="small"
                            onClick={() => {
                                onStop?.();
                                closeSnackbar(id);
                            }}
                            sx={{
                                fontSize: 11, fontWeight: 500,
                                px: "10px", py: "3px",
                                backgroundColor: "#f8e697", color: "#161616",
                                border: "none",
                                "&:hover": {backgroundColor: "#e7d372"},
                            }}
                        >
                            Stop
                        </Button>
                        <Button
                            size="small"
                            onClick={() => {
                                onSnooze?.();
                                closeSnackbar(id);
                            }}
                            sx={{
                                fontSize: 11, fontWeight: 500,
                                px: "10px", py: "3px",
                                backgroundColor: "#2a2a2a", color: "#e0e0e0",
                                border: "1px solid #444",
                                "&:hover": {backgroundColor: "#333"},
                            }}
                        >
                            Snooze 1 hr
                        </Button>
                    </Box>
                )}
            </Box>

            {/* Close */}
            <IconButton
                size="small"
                onClick={() => closeSnackbar(id)}
                aria-label="Dismiss"
                sx={{color: "#555", flexShrink: 0, "&:hover": {color: "#b0b0b0", backgroundColor: "#2a2a2a"}}}
            >
                <CloseIcon sx={{fontSize: 16}}/>
            </IconButton>
        </Box>
    );
});

export default AutomataSnackbar;