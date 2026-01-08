import React, {useEffect, useMemo, useState} from "react";
import LightBulbCard from "../v2/LightBulbCard.jsx";
import {Menu} from "@mui/material";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {Presets} from "../charts/Presets.jsx";

export default function WledDevices ({devices, messages}){

    return(
        <div style={{
            gridTemplateColumns: 'repeat(2, 1fr)',
            display: 'grid',
            // gap: '2px',
        }}>
            {devices.map(device=>(
                <Wled device={device} messages={messages}/>
            ))}
        </div>
    )
}

const Wled = ({device, messages}) => {
    // console.log("wled", device)
    const [liveData, setLiveData] = useState(device.lastData);
    const [anchorEl, setAnchorEl] = React.useState(null);
    useEffect(() => {
        if (device.id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);
        }
    }, [messages, device.id]);
    const {
        sliderData,
        switchButtons,
        presetButtons,
    } = useMemo(() => {
        const grouped = {
            sliderData: [],
            switchButtons: [],
            presetButtons: [],
        };

        for (const attr of device.attributes) {
            if (attr.type === 'ACTION|SLIDER') grouped.sliderData.push(attr);
            else if (attr.type.startsWith('ACTION|SWITCH')) grouped.switchButtons.push(attr);
            else if (attr.type.startsWith('ACTION|PRESET')) grouped.presetButtons.push(attr);
        }

        return grouped;
    }, [device.attributes]);

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    return (
        <div>
            <DetailMenu
                anchorEl={anchorEl}
                setAnchorEl={setAnchorEl}
                presetButtons={presetButtons}
                sliderData={sliderData}
                deviceType={device.type}
                deviceId={device.id}
                liveData={liveData}
            />

            {switchButtons.map((s) => (
                <LightBulbCard onClick={handleClick} key={s.key} data={s} lastOnline={device.lastOnline}
                               value={liveData?.[s.key]}
                               name={device.name} deviceId={device.id} type={device.type}/>
            ))}

        </div>
    )
}

const DetailMenu = ({anchorEl, setAnchorEl, sliderData, presetButtons, deviceId, deviceType, liveData}) => {

    const open = Boolean(anchorEl);
    const handleClose = () => {
        setAnchorEl(null);
    };
    return (
        <div>
            <Menu
                id="basic-menu"
                style={{
                    borderRadius: '12px',
                    width: '260px'
                }}
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                slotProps={{
                    list: {
                        'aria-labelledby': 'basic-button',
                    },
                }}
            >
                <div style={{padding: '10px'}}>
                    {sliderData.map((s) => (
                        <CustomSlider
                            key={s.key}
                            value={liveData?.[s.key]}
                            deviceId={deviceId}
                            type={deviceType}
                            data={s}
                            displayName={s.displayName}
                        />
                    ))}

                    {presetButtons.map((p) => (
                        <Presets
                            key={p.key}
                            value={liveData}
                            deviceId={deviceId}
                            type={deviceType}
                            data={p}
                            displayName={p.displayName}
                        />
                    ))}
                </div>
            </Menu>
        </div>
    )
}