
import {Switch} from "@mui/material";
import {sendAction} from "../../services/apis.jsx";
import FormControlLabel from "@mui/material/FormControlLabel";
import React from "react";


export default function SwitchButton({value, deviceId, displayName, data}) {

    const send = async (e) => {
        try {
            let act = data.key;
            await sendAction(deviceId, {
                "key": data.key,
                [act]: e.target.value,
                "device_id": deviceId,
                direct: true
            });
        } catch (err) {
            console.error("Action send failed", err);
        }
    };

    return(
        <div>
            <Switch color="warning" onChange={send}/>
            <span style={{fontSize:'small'}} >{displayName}</span>
        </div>
    )
}