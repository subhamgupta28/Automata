import React, {useEffect, useState, memo, useMemo} from "react";

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
import {useCachedDevices} from "../../services/AppCacheContext.jsx";

const CustomTabPanel = memo(function CustomTabPanel(props) {
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
});

export { CustomTabPanel };
function AnalyticsViewComponent() {
    const [devicesList, setDevices] = useState([]);
    const {devices, loading, error} = useCachedDevices();
    const [openBackdrop, setOpenBackdrop] = useState(false);
    const theme = useTheme();
    const [value, setValue] = useState(0);

    const handleChange = (event, newValue) => {
        setValue(newValue);
    };

    const analyticsDevices = useMemo(
        () => devices ? devices.filter((t) => t.analytics) : [],
        [devices]
    );

    useEffect(() => {
        setOpenBackdrop(true);
        setDevices(analyticsDevices);
        setOpenBackdrop(false);
    }, [analyticsDevices]);

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
                <Box style={{
                    background: 'transparent',
                    backdropFilter: 'blur(4px)',
                    marginRight: '10px',
                    marginLeft:'10px',
                    borderRadius:'8px',
                    backgroundColor: 'rgb(255 255 255 / 8%)',
                }}>
                    <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
                        {devicesList.map((device) => (
                            <Tab key={device.id} label={device.name}  />
                        ))}
                    </Tabs>
                </Box>
                {devicesList.map((device, i) => (
                    <CustomTabPanel key={device.id} value={value} index={i}>
                        {value === i && (
                            <ChartDetail deviceId={device.id} name={device.name} width={1000} height={600} deviceAttributes={device.attributes}/>
                        )}
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

const AnalyticsView = memo(AnalyticsViewComponent);
export default AnalyticsView;
