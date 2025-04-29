import React, { useEffect, useState } from "react";
import {
    Box,
    Button,
    CircularProgress,
    Snackbar,
    TextField,
    Typography,
    Alert,
} from "@mui/material";

export default function Exp() {
    const [filename, setFilename] = useState("src/main.cpp");
        const [code, setCode] = useState("");
    const [loading, setLoading] = useState(false);
    const [output, setOutput] = useState("");
    const [snackbar, setSnackbar] = useState({ open: false, message: "", severity: "success" });

    useEffect(() => {
        fetch(`/platformio/code?file=${filename}`)
            .then((res) => res.text())
            .then(setCode)
            .catch(() => showSnackbar("Failed to load code", "error"));
    }, [filename]);

    const showSnackbar = (message, severity) => {
        setSnackbar({ open: true, message, severity });
    };

    const saveCode = async () => {
        setLoading(true);
        const res = await fetch(`/platformio/code?file=${filename}`, {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: code,
        });
        setLoading(false);
        res.ok ? showSnackbar("Code saved", "success") : showSnackbar("Failed to save", "error");
    };

    const runAction = async (endpoint) => {
        setLoading(true);
        const res = await fetch(`/platformio/${endpoint}`, { method: "POST" });
        const text = await res.text();
        setLoading(false);
        setOutput(text);
        res.ok ? showSnackbar(`${endpoint} successful`, "success") : showSnackbar(`${endpoint} failed`, "error");
    };

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h5" gutterBottom>
                PlatformIO Editor
            </Typography>

            <TextField
                fullWidth
                label="Filename"
                value={filename}
                onChange={(e) => setFilename(e.target.value)}
                sx={{ mb: 2 }}
            />

            <TextField
                fullWidth
                multiline
                minRows={15}
                variant="outlined"
                label="Code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                sx={{ mb: 2 }}
                InputProps={{ style: { fontFamily: "monospace" } }}
            />

            <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                <Button variant="contained" onClick={saveCode} disabled={loading}>
                    Save
                </Button>
                <Button variant="contained" onClick={() => runAction("build")} disabled={loading}>
                    Build
                </Button>
                <Button variant="contained" onClick={() => runAction("upload")} disabled={loading}>
                    Upload
                </Button>
                {loading && <CircularProgress size={24} />}
            </Box>

            <Typography variant="subtitle1" sx={{ mb: 1 }}>
                Output:
            </Typography>
            <TextField
                fullWidth
                multiline
                minRows={8}
                value={output}
                InputProps={{ readOnly: true }}
                variant="outlined"
            />

            <Snackbar
                open={snackbar.open}
                autoHideDuration={4000}
                onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
            >
                <Alert severity={snackbar.severity} onClose={() => setSnackbar((s) => ({ ...s, open: false }))}>
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </Box>
    );
}
