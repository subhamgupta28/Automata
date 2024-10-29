import {Slider} from "@mui/material";
import Typography from "@mui/material/Typography";
import {useState} from "react";
import {sendAction} from "../../services/apis.jsx";

export function CustomSlider({value, deviceId, displayName, data}){
    const handleChange = (e) => {
        console.log("handleChange", e.target.value, data);
        const send = async () => {
            try {
                let act = data.key;
                await sendAction(deviceId, {"key": data.key, [act]: e.target.value, "device_id": deviceId, direct: true});

            } catch (err) {
                console.error("Action send failed", err);
            }
        };
        send();
    }

    return(
        <>
            <Slider className="nodrag" onChange={handleChange} value={value} aria-label="Default" min={data.extras.min} max={data.extras.max} valueLabelDisplay="auto" />
            <Typography textAlign='center'>
                {displayName}
            </Typography>
        </>
    )
}