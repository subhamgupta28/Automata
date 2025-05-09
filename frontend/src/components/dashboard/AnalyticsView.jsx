import React, { useEffect, useState } from "react";
import { getDashboardDevices } from "../../services/apis.jsx";
import ChartDetail from "../charts/ChartDetail.jsx";
import {
    Box,
    Typography,
    Card,
    CardContent,
    Grid,
    CircularProgress,
    useTheme, Backdrop,
} from "@mui/material";

export default function AnalyticsView() {
    const [devices, setDevices] = useState([]);
    const [openBackdrop, setOpenBackdrop] = useState(false);
    const theme = useTheme();

    useEffect(() => {
        setOpenBackdrop(true)
        const fetchDevices = async () => {
            const result = await getDashboardDevices();
            setDevices(result.filter((t)=>t.analytics));
            setOpenBackdrop(false);
        };
        fetchDevices();
    }, []);

    return (
        <Box
            sx={{
                backgroundColor: theme.palette.background.default,
                minHeight: "100vh",
                paddingTop: 4,
                paddingRight:2,
                paddingLeft:2,
                color: theme.palette.text.primary,
            }}
        >

                <Grid container spacing={1} style={{ height:'90dvh'}}>
                    {devices.map((device) => (
                        <Grid size={6} xs={12} md={6} lg={6} key={device.id}>
                            <ChartDetail deviceId={device.id} name={device.name}/>
                        </Grid>
                    ))}
                </Grid>
            <Backdrop
                sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                open={openBackdrop}
            >
                <CircularProgress color="inherit"/>
            </Backdrop>
        </Box>
    );
}
