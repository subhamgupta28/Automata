import {debounce, Slider} from "@mui/material";
import {useEffect, useState} from "react";
import {sendAction} from "../../services/apis.jsx";
import {styled} from "@mui/material/styles";


const HomeAssistantSlider = styled(Slider)({
    color: 'orange', // Blue color like Home Assistant slider
    height: 35, // Adjust the height of the slider bar
    borderRadius: 8,
    padding: '15px 0',
    '& .MuiSlider-rail': {
        opacity: 0.2, // Make the rail more subtle
        backgroundColor: '#ffa500', // Light grey color for the rail
    },
    '& .MuiSlider-track': {
        border: 'none', // Remove the track's border
        // paddingRight:'4px',
        backgroundColor: 'orange', // Use the blue color for the active track
    },
    '& .MuiSlider-markLabel': {
        top: 55
    },
    '& .MuiSlider-mark': {
        display: 'none'
    },
    '& .MuiSlider-thumb': {
        backgroundColor: '#ffffff', // White thumb, matching the Home Assistant style
        // border: '2px solid orange', // Blue border around the thumb
        height: 25,
        width: 6,
        left: '50%',
        borderRadius: '4px',
        // marginLeft: '6px',
        transform: 'translate(-150%, -50%)',
        '&:hover': {
            backgroundColor: 'white', // Change thumb color when hovered
        },
        '&:active': {
            backgroundColor: 'orange', // Darker blue when active
        },
    },
});

export function CustomSlider({value, deviceId, displayName, data, type}) {
    const [num, setNum] = useState(value ? value : 0)

    useEffect(() => {
        setNum(value ? value : 0);
    }, [value]);
    const handleChange = debounce((e) => {
        // console.log("handleChange", e.target.value);
        const send = async () => {
            try {
                let act = data.key;
                let val = e.target.value;
                if (val) {

                    setNum(val);
                    await sendAction(deviceId, {
                        "key": data.key,
                        [act]: e.target.value,
                        "device_id": deviceId,
                        direct: true
                    }, type);
                }

            } catch (err) {
                console.error("Action send failed", err);
            }
        };
        send();
    }, 200);

    return (
        <div>
            <HomeAssistantSlider
                className="nodrag"
                onChange={handleChange}
                value={num}
                min={data.extras.min}
                max={data.extras.max}
                valueLabelDisplay="auto"
                marks={[
                    // {
                    // value: data.extras.min,
                    // label: `${data.extras.min}`,
                    // },
                    {
                        value: data.extras.max / 2,
                        label: displayName,
                    },
                    // {
                    //     value: data.extras.max,
                    //     label: `${data.extras.max}`,
                    // }
                ]}
            />
            {/*<Slider*/}
            {/*    className="nodrag"*/}
            {/*    onChange={handleChange}*/}
            {/*    value={num}*/}
            {/*    aria-label="Default"*/}
            {/*    min={data.extras.min}*/}
            {/*    max={data.extras.max}*/}
            {/*    valueLabelDisplay="auto"*/}
            {/*    marks={[{*/}
            {/*        value: data.extras.min,*/}
            {/*        label: `${data.extras.min}`,*/}
            {/*    },*/}
            {/*        {*/}
            {/*            value: data.extras.max / 2,*/}
            {/*            label: displayName,*/}
            {/*        },*/}
            {/*        {*/}
            {/*            value: data.extras.max,*/}
            {/*            label: `${data.extras.max}`,*/}
            {/*        }*/}
            {/*    ]}*/}
            {/*/>*/}
            {/*<Typography textAlign='center'>*/}
            {/*    {displayName}*/}
            {/*</Typography>*/}
        </div>
    )
}