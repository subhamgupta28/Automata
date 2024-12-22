import IconButton from "@mui/material/IconButton";
import Avatar from "@mui/material/Avatar";
import React from "react";
import {sendAction} from "../../services/apis.jsx";
import {deepOrange} from "@mui/material/colors";


export default function Presets({data, type, value, deviceId, displayName}) {
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
        <div style={{display:'flex', alignItems: 'center', flexDirection: 'column'}}>
            {displayName}
            <div>
                {Object.values(data.extras).map((ex) => (
                    <IconButton key={ex} onClick={()=>send(ex)}>
                        <Avatar  sx={{ width: 26, height: 26, bgcolor: "#4085ee" }}>{ex}</Avatar>
                    </IconButton>
                ))}
            </div>
        </div>
    )
}