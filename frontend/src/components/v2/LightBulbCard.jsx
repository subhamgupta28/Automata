import * as React from "react";
import {Box, IconButton, Typography} from "@mui/material";
import LightbulbIcon from "@mui/icons-material/Lightbulb";
import {getLastData, sendAction} from "../../services/apis.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";

export default function LightBulbCard({
                                          value,
                                          name,
                                          type,
                                          deviceId,
                                          data,
                                          lastOnline,
                                          onClick,
                                          percent,
                                          sliderData,
                                          mode = "extended"
                                      }) {
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
                margin: '4px',
                borderRadius: "8px",
                padding: "4px 4px",

                boxShadow: "0px 1px 4px rgba(0,0,0,0.12)"
            }}
        >
            <Box display="flex" gap={2} alignItems="center">
                <IconButton
                    onClick={send}
                    sx={{
                        width: 36,
                        height: 36,
                        borderRadius: "50%",
                        backgroundColor: isOn ? "#FFC107" : "#E0E0E0",
                        "&:hover": {
                            backgroundColor: isOn ? "#FFB300" : "#D5D5D5"
                        }
                    }}
                >
                    <LightbulbIcon
                        sx={{
                            width: 16,
                            height: 16,
                            color: isOn ? "#FFFFFF" : "#9E9E9E"
                        }}
                    />
                </IconButton>

                <Box>
                    <Typography onClick={onClick} fontWeight={600} fontSize="16px">
                        {name}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {bright}
                    </Typography>
                </Box>

            </Box>
            {
                mode === "extended" &&
                (sliderData.map((s) => (
                    <CustomSlider
                        key={s.key}
                        value={bright}
                        deviceId={deviceId}
                        type={type}
                        data={s}
                        displayName={s.displayName}
                    />
                )))
            }

        </div>
    );
}
