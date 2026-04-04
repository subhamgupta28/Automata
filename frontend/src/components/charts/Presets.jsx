import IconButton from "@mui/material/IconButton";
import React from "react";
import {sendAction} from "../../services/apis.jsx";
import Typography from "@mui/material/Typography";
import {Chip} from "@mui/material";


export const Presets = React.memo(({data, type, value, deviceId, displayName}) => {

    const send = async (e) => {
        try {
            let act = data.key;
            await sendAction(deviceId, {
                "key": data.key,
                [act]: e,
                "device_id": deviceId,
                direct: true
            }, type);
        } catch (err) {
            console.error("Action send failed", err);
        }
    };
    const extrasArray = Object.entries(data.extras).map(([name, id]) => ({
        name,
        id
    }));
    console.log("preset", extrasArray)
    return (
        <div style={{display: 'flex', alignItems: 'center', flexDirection: 'column', marginTop: '4px'}}>
            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                }}
            >
                {extrasArray.map((ex) => (
                    <IconButton key={ex.id} onClick={() => send(ex.id)}>
                        <Chip
                            size="small"
                            label={ex.name}
                            sx={{
                                background: ex.id === value?.preset ? "orange" : "",
                                fontWeight: "bold"
                            }}
                        >
                            {/* first letter */}
                        </Chip>
                    </IconButton>
                ))}
            </div>
            <Typography style={{marginTop: '4px'}}>
                {displayName}
            </Typography>
        </div>
    )
});