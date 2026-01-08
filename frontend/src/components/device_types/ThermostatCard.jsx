import React, {useEffect, useState} from "react";
import {
    Card,
    CardContent,
    Typography,
    Box,
    IconButton,
} from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import AddIcon from "@mui/icons-material/Add";
import RemoveIcon from "@mui/icons-material/Remove";

export default function ThermostatCard({device, messages}) {
    const [temp, setTemp] = useState(21.0);
    const [data, setData] = useState({
        room: device?.name,
        mode: "Eco",
        currentTemp: 21.7,
    });
    useEffect(()=>{
        if (messages.deviceId && messages.deviceId === device.id){
            const data = messages.data;
            if (data){
                setData(prev=>{
                    return{
                        ...prev,
                        currentTemp: data["temp"]
                    }
                })
            }
        }
    }, [messages])

    const onDecrease = () => {
        setTemp(p => p - 1)
    }
    const onIncrease = () => {
        setTemp(p => p + 1)
    }


    return (
        <Card
            variant="outlined"
            sx={{
                width: 220,
                borderRadius: '8px',
                boxShadow: 2,
            }}
        >
            <CardContent>
                {/* Header */}
                <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <HomeIcon sx={{color: "orange"}}/>
                    <Typography fontWeight={600}>{data.room}</Typography>
                </Box>

                {/* Mode + current temp */}
                <Typography
                    variant="body2"
                    color="text.secondary"
                    mb={2}
                >
                    {data.mode} · {data.currentTemp.toFixed(1)} °C
                </Typography>

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
                        {temp.toFixed(1)} °C
                    </Typography>

                    <IconButton onClick={onIncrease} size="small">
                        <AddIcon/>
                    </IconButton>
                </Box>
            </CardContent>
        </Card>
    );
}
