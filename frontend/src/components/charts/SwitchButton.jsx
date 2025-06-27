
import {Switch} from "@mui/material";
import {sendAction} from "../../services/apis.jsx";
import FormControlLabel from "@mui/material/FormControlLabel";
import React from "react";
import IconButton from "@mui/material/IconButton";
import BrightnessHighIcon from "@mui/icons-material/BrightnessHigh";
import BrightnessLowIcon from "@mui/icons-material/BrightnessLow";
import BoltIcon from "@mui/icons-material/Bolt";
import FlashOffIcon from "@mui/icons-material/FlashOff";


export const SwitchButton = React.memo(({value, deviceId, displayName, data, type}) => {
    const [on, setOn] = React.useState(value === true);

    React.useEffect(() => {
        setOn(value === true);
    }, [value])
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
    const handleIcon = (key, value) => {
        console.log("keyv", key, value)
        if (key.includes("Light"))
            return value ? <BrightnessHighIcon/> : <BrightnessLowIcon/>
        if (key.includes("power"))
            return value ? <BoltIcon/> : <FlashOffIcon/>
    }
    return(
        <div style={{display:'flex', flexDirection:'column',justifyContent: 'space-between', alignItems: 'center'}}>
            <div style={{display:'flex', flexDirection:'row',justifyContent: 'space-between', alignItems: 'center'}}>
                <IconButton onClick={send} aria-label="delete" color={on? "primary":"secondary"}>
                    {handleIcon(displayName, value)}
                </IconButton>
                <Switch onChange={send} checked={on}/>
            </div>
            <span style={{fontSize:'small'}} >{displayName}</span>
        </div>
    )
});