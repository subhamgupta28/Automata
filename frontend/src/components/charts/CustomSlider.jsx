import {debounce, Slider} from "@mui/material";
import Typography from "@mui/material/Typography";
import {useState} from "react";
import {sendAction} from "../../services/apis.jsx";

export function CustomSlider({value, deviceId, displayName, data}){
    const [num, setNum] = useState(value)
    const handleChange = debounce((e) => {
        // console.log("handleChange", e.target.value);
        const send = async () => {
            try {
                let act = data.key;
                setNum(e.target.value);
                await sendAction(deviceId, {"key": data.key, [act]: e.target.value, "device_id": deviceId, direct: true});
            } catch (err) {
                console.error("Action send failed", err);
            }
        };
        send();
    }, 300);

    return(
        <>
            <Slider className="nodrag" onChange={handleChange} value={num} aria-label="Default" min={data.extras.min} max={data.extras.max} valueLabelDisplay="auto" />
            <Typography textAlign='center'>
                {displayName}
            </Typography>
        </>
    )
}