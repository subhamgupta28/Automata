import React from 'react';
// Dummy data following the EnergyStat Java model (one entry per day)
// Dummy data following the EnergyStat Java model (one entry per day)
import {Box, Card, CardContent, LinearProgress, Typography} from "@mui/material";
import {styled} from "@mui/material/styles";
import BoltIcon from "@mui/icons-material/Bolt";


const BatteryBar = styled(LinearProgress)(({theme, value}) => {
    let color = theme.palette.success.main;
    if (value < 20) color = theme.palette.error.main;
    else if (value < 50) color = theme.palette.warning.main;

    return {
        height: 50,
        borderRadius: 8,
        backgroundColor: theme.palette.grey[300],
        "& .MuiLinearProgress-bar": {
            borderRadius: 8,
            backgroundColor: color,
        },
    };
});

function BatteryGaugeCard({level = 75}) {
    return (
        <Card
            sx={{
                width: "100%",
                maxWidth: 420,
                borderRadius: 3,
            }}
            elevation={3}
        >
            <CardContent>
                <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="subtitle1" fontWeight={600}>
                        Battery Level
                    </Typography>
                    <Typography variant="subtitle1" fontWeight={600}>
                        {level}%
                    </Typography>
                </Box>

                <BatteryBar variant="determinate" value={level}/>
            </CardContent>
        </Card>
    );
}

const series = [
    {label: 'Battery 250Wh', data: [2000, 1700, 1400, 1159, 1850, 1653]},
    {label: 'Battery 270Wh', data: [1250, 980, 860, 1199, 485, 965]},
    {label: 'Battery 500Wh', data: [1000, 700, 400, 159, 850, 653]},
];

const TOTAL_SEGMENTS = 10;
const LOW_BATTERY_THRESHOLD = 20;

function BatteryWidget({
                           batteryPercent = 52,
                           remainingTime = "2.5 hours",
                           timeLabel = "for full charge",
                           lowBatteryThreshold = LOW_BATTERY_THRESHOLD,
                       }) {
    const percent = Math.max(0, Math.min(100, batteryPercent));
    const isLowBattery = percent <= lowBatteryThreshold;
    return (
        <Box
            sx={{
                width: 307,
                height: 320,
                borderRadius: "48px",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                pt: 5,
                boxSizing: "border-box",
            }}
        >
            {/* Header */}
            <Box
                sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                }}
            >
                <Box
                    sx={{
                        width: 26,
                        height: 26,
                        borderRadius: "50%",
                        background: "#E6C7FF",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        boxShadow: "0 0 12px rgba(217,168,255,.7)",
                    }}
                >
                    <BoltIcon sx={{color: "#6E3AA8", fontSize: 18}}/>
                </Box>

                <Typography
                    sx={{
                        color: "#fff",
                        fontSize: 26,
                        fontWeight: 700,
                    }}
                >
                    {percent}%
                </Typography>
            </Box>

            {/* Battery Container */}
            <Box
                sx={{
                    mt: 1,
                    p: "4px",
                    borderRadius: "8px",
                    background: isLowBattery
                        ? "rgb(100 24 20 / 0.71)"
                        : "rgba(190,130,255,0.18)",

                    boxShadow: isLowBattery
                        ? "0 0 30px rgba(255,59,48,.45)"
                        : "0 0 30px rgba(177,119,255,.45)",
                }}
            >
                <Box
                    sx={{
                        width: 140,
                        height: 40,
                        display: "flex",
                        gap: "8px",
                    }}
                >
                    {Array.from({length: TOTAL_SEGMENTS}).map((_, index) => {
                        const segmentStart = index * (100 / TOTAL_SEGMENTS);

                        const segmentProgress = Math.max(
                            0,
                            Math.min(
                                100,
                                ((percent - segmentStart) / (100 / TOTAL_SEGMENTS)) * 100
                            )
                        );

                        return (
                            <Box
                                key={index}
                                sx={{
                                    flex: 1,
                                    height: "100%",
                                    borderRadius: "6px",
                                    background: "#1A1A20",
                                    overflow: "hidden",
                                    position: "relative",
                                }}
                            >
                                <Box
                                    sx={{
                                        position: "absolute",
                                        inset: 0,
                                        width: `${segmentProgress}%`,
                                        borderRadius: "6px",
                                        transition: "all .5s ease",
                                        background: isLowBattery
                                            ? "linear-gradient(180deg, #FF9E9E 0%, #FF3B30 100%)"
                                            : "linear-gradient(180deg, #F0D8FF 0%, #DDBBFF 100%)",

                                        // optional glow
                                        boxShadow: isLowBattery
                                            ? "0 0 12px rgba(255,59,48,.5)"
                                            : "0 0 12px rgba(221,187,255,.4)",
                                    }}
                                />
                            </Box>
                        );
                    })}
                </Box>
            </Box>

            {/* Footer */}
            <Typography
                sx={{
                    mt: 1,
                    color: "#fff",
                    fontSize: 24,
                    fontWeight: 500,
                }}
            >
                {remainingTime}
            </Typography>
        </Box>
    );
}

const Exp = () => {
    const weather = {
        location: "Cortes, Madrid, Spain",
        time: "Tuesday, 3:00 PM",
        temp: 20,
        condition: "Cloud",
        humidity: 41,
        precipitation: 7,
        wind: 23,
        aqi: 358,
    };

    return (
        <div style={{marginTop: '10px', fontSize: '32px'}}>


            {/*<AutomationSummaryBar/>*/}
            {/*<AutomationAnalyticsList/>*/}
            {/*<AutomationFlowInspector/>*/}
            {/*// Single pin (existing behavior)*/}

            <BatteryWidget
                batteryPercent={51}
                remainingTime="2.5 hours"
                // timeLabel="for full charge"
            />
            {/*// With route*/}
            {/*<MapView*/}
            {/*    lat={17.385}*/}
            {/*    lng={78.486}*/}
            {/*    h="400px"*/}
            {/*    w="100%"*/}
            {/*    route={[*/}
            {/*        [17.385, 78.486],*/}
            {/*        [17.390, 78.491],*/}
            {/*        [17.395, 78.498],*/}
            {/*        [17.400, 78.505],*/}
            {/*    ]}*/}
            {/*/>*/}

            {/*<SpotifyPlayer/>*/}
            {/*<SmartHomeDashboard2/>*/}

        </div>
    );
};

export default Exp;
