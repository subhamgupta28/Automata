import React, {useEffect, useState} from "react";
import {getDashboardDevices} from "../../services/apis.jsx";
import ChartDetail from "../charts/ChartDetail.jsx";
import {
    Box,
    Typography,
    Card,
    CardContent,
    Grid,
    CircularProgress,
    useTheme, Backdrop, Tabs, Tab,
} from "@mui/material";

function CustomTabPanel(props) {
    const { children, value, index, ...other } = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`simple-tabpanel-${index}`}
            aria-labelledby={`simple-tab-${index}`}
            {...other}
        >
            {value === index && <Box sx={{ p: 1 }}>{children}</Box>}
        </div>
    );
}
export default function AnalyticsView() {
    const [devices, setDevices] = useState([]);
    const [openBackdrop, setOpenBackdrop] = useState(false);
    const theme = useTheme();
    const [value, setValue] = useState(0);

    const handleChange = (event, newValue) => {
        setValue(newValue);
    };

    useEffect(() => {
        setOpenBackdrop(true)
        const fetchDevices = async () => {
            const result = await getDashboardDevices();
            setDevices(result.filter((t) => t.analytics));
            setOpenBackdrop(false);
        };
        fetchDevices();
    }, []);

    return (
        <Box
            sx={{
                // backgroundColor: theme.palette.background.default,
                minHeight: "100vh",
                // padding: 1,
                // paddingRight:2,
                // paddingLeft:2,
                color: theme.palette.text.primary,
            }}
        >
            <Box sx={{paddingTop:'50px'}}>
                <Box>
                    <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
                        {devices.map((device) => (
                            <Tab label={device.name}  />
                        ))}
                    </Tabs>
                </Box>
                {devices.map((device, i) => (
                    <CustomTabPanel value={value} index={i}>
                        <ChartDetail deviceId={device.id} name={device.name}/>
                    </CustomTabPanel>
                ))}
            </Box>

            {/*<Grid container spacing={1} style={{height: '100dvh', padding: '50px 15px', overflow: 'auto'}}>*/}
            {/*    {devices.map((device) => (*/}
            {/*        <Grid size={12} xs={12} md={6} lg={6} key={device.id}>*/}
            {/*            <ChartDetail deviceId={device.id} name={device.name}/>*/}
            {/*        </Grid>*/}
            {/*    ))}*/}
            {/*</Grid>*/}
            <Backdrop
                sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                open={openBackdrop}
            >
                <CircularProgress color="inherit"/>
            </Backdrop>
        </Box>
    );
}
