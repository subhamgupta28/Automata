import * as React from "react";
import {Box, IconButton, Typography} from "@mui/material";
import LightbulbIcon from "@mui/icons-material/Lightbulb";
import {getLastData, sendAction} from "../../services/apis.jsx";

export default function LightBulbCard({value, name, type, deviceId, data, lastOnline, onClick, percent}) {
    const [isOn, setIsOn] = React.useState(Boolean(value));
    const [bright, setBright] = React.useState(percent);

    // Sync external value → local UI
    React.useEffect(() => {
        getLastData(deviceId).then(res => {
            setBright(res?.bright);
            setIsOn(Boolean(res?.onOff));
        });

    }, [value]);

    const send = async () => {
        const nextState = !isOn;

        // Optimistic UI update
        setIsOn(nextState);

        try {
            const act = data.key;

            await sendAction(
                deviceId,
                {
                    key: act,
                    [act]: nextState,
                    device_id: deviceId,
                    direct: true,
                },
                type
            );
        } catch (err) {
            console.error("Action send failed", err);
            // rollback if API fails
            setIsOn(isOn);
        }
    };

    return (
        <div

            style={{
                margin: '6px',
                width: 230,
                borderRadius: "8px",
                padding: "8px 6px",
                boxShadow: "0px 1px 4px rgba(0,0,0,0.12)"
            }}
        >
            <Box display="flex" alignItems="center" gap={2}>
                <IconButton
                    onClick={send}
                    sx={{
                        width: 48,
                        height: 48,
                        borderRadius: "50%",
                        backgroundColor: isOn ? "#FFC107" : "#E0E0E0",
                        "&:hover": {
                            backgroundColor: isOn ? "#FFB300" : "#D5D5D5"
                        }
                    }}
                >
                    <LightbulbIcon
                        sx={{
                            color: isOn ? "#FFFFFF" : "#9E9E9E"
                        }}
                    />
                </IconButton>

                <Box onClick={onClick}>
                    <Typography fontWeight={600} fontSize="16px">
                        {name}
                    </Typography>
                    <Typography
                        fontSize="14px"
                        color={isOn ? "text.primary" : "text.secondary"}
                    >
                        {isOn ? `${parseInt(((bright / 255) * 100).toString())}%` : "Off"}
                    </Typography>
                    {/*<Typography variant="body2" color="text.secondary">*/}
                    {/*    {dayjs(lastOnline).fromNow()}*/}
                    {/*</Typography>*/}
                </Box>

            </Box>

        </div>
    );
}
