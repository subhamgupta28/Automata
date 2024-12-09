
import {Switch} from "@mui/material";
import {sendAction} from "../../services/apis.jsx";
import FormControlLabel from "@mui/material/FormControlLabel";
import React from "react";


export default function SwitchButton({value, deviceId, displayName, data, type}) {
    const [on, setOn] = React.useState(value === true);
    const send = async (e) => {
        try {
            let act = data.key;
            console.log("send", e.target.checked);
            setOn(e.target.checked);
            await sendAction(deviceId, {
                "key": data.key,
                [act]: e.target.checked,
                "device_id": deviceId,
                direct: true
            }, type);
        } catch (err) {
            console.error("Action send failed", err);
        }
    };

    return(
        <div>
            <Switch color="warning" onChange={send} checked={on}/>
            <span style={{fontSize:'small'}} >{displayName}</span>
        </div>
    )
}