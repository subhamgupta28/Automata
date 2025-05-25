import IconButton from "@mui/material/IconButton";
import Avatar from "@mui/material/Avatar";
import React from "react";
import {sendAction} from "../../services/apis.jsx";
import {deepOrange} from "@mui/material/colors";
import Typography from "@mui/material/Typography";


export const Presets = React.memo(({data, type, value, deviceId, displayName}) => {
    // console.log("preset", value)
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
    return (
        <div style={{display:'flex', alignItems: 'center', flexDirection: 'column',marginTop:'4px'}}>
            <div>
                {Object.values(data.extras).map((ex) => (
                    <IconButton key={ex} onClick={()=>send(ex)}>
                        <Avatar  sx={{ width: 26, height: 26, background: ex === value.presets? "orange":"", fontWeight: 'bold' }}>{ex}</Avatar>
                    </IconButton>
                ))}
            </div>
            <Typography style={{marginTop:'4px'}}>
                {displayName}
            </Typography>
        </div>
    )
});