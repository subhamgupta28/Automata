import React, {useEffect, useMemo, useState} from "react";
import LightBulbCard from "../v2/LightBulbCard.jsx";
import {Popover} from "@mui/material";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import {Presets} from "../charts/Presets.jsx";
import ColorPicker from "../charts/ColorPicker.jsx";
import Typography from "@mui/material/Typography";
import {getRecentDeviceData} from "../../services/apis.jsx";

export default function WledDevices({devices, messages}) {
    const [lastData, setLastData] = useState({});
    useEffect(() => {
        const deviceIds = devices.map(d => d.id)
        if (deviceIds.length === 0)
            return;
        const get = async () => {
            return await getRecentDeviceData(deviceIds);
        }
        get().then(res => {
            setLastData(res)
            // console.log("DDDD", res)
        });

    }, [])
    return (
        <div style={{
            gridTemplateColumns: 'repeat(1, 2fr)',
            display: 'grid',
            // gap: '2px',
        }}>
            {devices.map(device => (
                <div key={device.id}>
                    <Wled device={device} messages={messages} lastData={lastData[device.id]}/>
                </div>
            ))}
        </div>
    )
}

const Wled = ({device, messages, lastData}) => {
    // console.log("wled", lastData)
    const [liveData, setLiveData] = useState(lastData);
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
        // console.log("grouped", grouped, lastData)

        return grouped;
    }, [device.attributes, lastData]);

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    return (
        <div key={device.id}>
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
        <>
            <Popover
                id="basic-menu"
                style={{
                    borderRadius: '12px',
                    width: '360px',
                    backgroundColor: "transparent"
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

                    <Typography variant="body2" sx={{fontWeight: 500}}>
                        Color palette
                    </Typography>
                    <ColorPicker
                        value={liveData?.["color1"]}
                        keyName="color1"
                        deviceId={deviceId}
                        type={deviceType}
                    />
                    <ColorPicker
                        value={liveData?.["color2"]}
                        keyName="color2"
                        deviceId={deviceId}
                        type={deviceType}
                    />
                </div>
            </Popover>
        </>
    )
}