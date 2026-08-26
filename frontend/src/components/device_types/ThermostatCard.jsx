import React, {useEffect, useState} from "react";
import {Box, CardContent, IconButton, Typography,} from "@mui/material";
import AirIcon from '@mui/icons-material/Air';
import AddIcon from "@mui/icons-material/Add";
import RemoveIcon from "@mui/icons-material/Remove";
import {sendAction} from "../../services/apis.jsx";

export default function ThermostatCard({device, messages}) {
    const [temp, setTemp] = useState(21.0);
    const [data, setData] = useState({
        room: device?.name,
        mode: "Eco",
        currentTemp: 0,
        speed: 0,
    });
    useEffect(() => {
        if (messages.deviceId === device.id) {
            const data = messages.data;
            if (data) {
                const speed = parseInt(data["speed"]);
                setData(prev => {
                    return {
                        ...prev,
                        currentTemp: data["temp"],
                        mode: speed > 100 ? "Cooling" : speed < 100 && speed > 50 ? "Eco" : "Idle",
                        speed,
                        humidity: data["humid"]
                    }
                })
            }
        }
    }, [messages])

    const send = async (e) => {
        try {
            let act = "speed";
            await sendAction(device.id, {
                "key": act,
                [act]: e,
                "device_id": device.id,
                direct: true
            }, device.type);
        } catch (err) {
            console.error("Action send failed", err);
        }
    };
    const onDecrease = () => {
        data.speed -= 5;
        send(data.speed)
    }
    const onIncrease = () => {
        data.speed += 5;
        send(data.speed)
    }


    return (
        <div
            style={{
                minWidth: 220,
                borderRadius: '8px',
            }}
        >
            <CardContent>
                {/* Header */}
                <Box display="flex" alignItems="center" justifyContent="center" gap={1}>
                    <AirIcon/>
                    <Typography fontWeight={600}>{data.room}</Typography>
                    <Typography fontSize={12} fontWeight={200} color="#8a8a8e">
                        {data.currentTemp} °C · {data.humidity}%
                    </Typography>
                </Box>
                {/* Temperature control */}
                <Box
                    display="flex"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{
                        borderRadius: 2,
                        // backgroundColor: "#f5f5f5",
                        px: 1,
                    }}
                >
                    <IconButton onClick={onDecrease} size="small">
                        <RemoveIcon/>
                    </IconButton>

                    <Typography fontWeight={600}>
                        {(data.speed * 100 / 255).toFixed(1)} %
                    </Typography>

                    <IconButton onClick={onIncrease} size="small">
                        <AddIcon/>
                    </IconButton>
                </Box>
            </CardContent>
        </div>
    );
}
