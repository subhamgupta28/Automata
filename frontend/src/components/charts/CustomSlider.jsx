import {debounce, Slider} from "@mui/material";
import React, {useEffect, useMemo, useState} from "react";
import {sendAction} from "../../services/apis.jsx";
import {styled} from "@mui/material/styles";


const CSlider = styled(Slider)({
    color: 'primary', // Blue color like Home Assistant slider
    height: 12, // Adjust the height of the slider bar
    borderRadius: 10,
    padding: '5px 0',
    // marginBottom: 1,
    '& .MuiSlider-rail': {
        opacity: 0.2, // Make the rail more subtle
        backgroundColor: '#fff', // Light grey color for the rail
    },
    '& .MuiSlider-track': {
        border: 'none', // Remove the track's border
        // paddingRight:'4px',
        backgroundColor: '#ffffff', // Use the blue color for the active track
    },
    '& .MuiSlider-markLabel': {
        top: 55
    },
    '& .MuiSlider-mark': {
        display: 'none'
    },
    '& .MuiSlider-thumb': {
        //     backgroundColor: '#d0d0d0', // White thumb, matching the Home Assistant style
        //     // border: '2px solid orange', // Blue border around the thumb
        height: 22,
        width: 22,
        //     // left: '50%',
        //     borderRadius: '4px',
        //     // marginLeft: '6px',
        //     transform: 'translate(-60%, -50%)',
        //     '&:hover': {
        //         backgroundColor: '#fce02b', // Change thumb color when hovered
        //     },
        //     '&:active': {
        //         backgroundColor: '#fce02b', // Darker blue when active
        //     },
    },
});

export const CustomSlider = React.memo(({value, deviceId, displayName, data, type}) => {
    const [num, setNum] = useState(value ? value : 0)

    useEffect(() => {
        setNum(value ? value : 0);
    }, [value]);
    const handleChange = useMemo(
        () => debounce((e) => {
            let val = e.target.value;
            setNum(val);
            const send = async () => {
                try {
                    let act = data.key;

                    if (val) {

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
        }, 100),
        [deviceId, data, type]
    );

    return (
        <div className="nodrag">
            <CSlider
                className="nodrag nopan nowheel"
                onPointerDown={(e) => {
                    console.log("pointer down");
                    e.stopPropagation();
                }}
                onMouseDown={(e) => {
                    console.log("mouse down");
                    e.stopPropagation();
                }}
                onChange={handleChange}
                value={num}
                min={data.extras.min}
                max={data.extras.max}
                valueLabelDisplay="auto"
                marks={[
                    {
                        value: data.extras.max / 2,
                        // label: displayName,
                    },
                ]}
            />
        </div>
    )
});