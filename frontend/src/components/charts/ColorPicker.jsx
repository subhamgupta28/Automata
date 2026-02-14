import React, {useState} from "react";
import {Card, Stack, TextField, Typography} from "@mui/material";
import {sendAction} from "../../services/apis.jsx";

export default function ColorPicker({
                                        value,
                                        deviceId,
                                        type,
                                        size = 36,
                                    }) {
    const [internalColor, setInternalColor] = useState("#6366f1");

    const color = value ?? internalColor;

    const handleColorChange = async (newColor) => {
        await send(newColor);
        setInternalColor(newColor);
    };
    const send = async (e) => {
        try {
            let act = "color";
            await sendAction(deviceId, {
                "key": act,
                [act]: e,
                "device_id": deviceId,
                direct: true
            }, type);
        } catch (err) {
            console.error("Action send failed", err);
        }
    };

    return (
        <Card variant="outlined" style={{
            backgroundColor: "transparent",
            padding: "12px",
            borderRadius: "12px",
            // width: "200px",

        }}>
            <Stack spacing={1}>
                <Typography variant="body2" sx={{fontWeight: 500}}>
                    Pick a color
                </Typography>

                <Stack direction="row" spacing={1} alignItems="center">
                    <input
                        type="color"
                        value={color}
                        onChange={(e) => handleColorChange(e.target.value)}
                        style={{
                            width: size,
                            height: size,
                            border: "none",
                            cursor: "pointer",
                            background: "none",
                        }}
                    />

                    <TextField
                        value={color}
                        size="small"
                        onChange={(e) => handleColorChange(e.target.value)}
                        sx={{width: 110}}
                    />

                </Stack>

            </Stack>
        </Card>
    );
}