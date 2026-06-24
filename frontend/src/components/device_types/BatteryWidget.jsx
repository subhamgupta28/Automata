import {Box, Typography} from "@mui/material";
import React from "react";

const TOTAL_SEGMENTS = 10;
const LOW_BATTERY_THRESHOLD = 20;

export default function BatteryWidget({
                                          name,
                                          batteryPercent = 52,
                                          remainingTime = "2.5 hours",
                                          timeLabel = "for full charge",
                                          lowBatteryThreshold = LOW_BATTERY_THRESHOLD,
                                      }) {
    const percent = Math.max(0, Math.min(100, batteryPercent));
    const isLowBattery = percent <= lowBatteryThreshold;
    return (
        <Box
            style={{
                width: 200,
                borderRadius: "10px",
                borderWidth: "2px",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                padding: "12px",
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
                {/*<Box*/}
                {/*    sx={{*/}
                {/*        width: 22,*/}
                {/*        height: 22,*/}
                {/*        borderRadius: "50%",*/}
                {/*        background: "#E6C7FF",*/}
                {/*        display: "flex",*/}
                {/*        alignItems: "center",*/}
                {/*        justifyContent: "center",*/}
                {/*        boxShadow: "0 0 12px rgba(217,168,255,.7)",*/}
                {/*    }}*/}
                {/*>*/}
                {/*    <BoltIcon sx={{color: "#6E3AA8", fontSize: 18}}/>*/}
                {/*</Box>*/}

                <Typography
                    sx={{
                        color: "#fff",
                        fontSize: 18,
                        fontWeight: 700,
                    }}
                >
                    {name}
                </Typography>
            </Box>

            {/* Battery Container */}
            <Box
                sx={{
                    mt: 1,
                    p: "6px",
                    borderRadius: "8px",
                    background: isLowBattery
                        ? "rgb(100 24 20 / 0.71)"
                        : "rgba(190,130,255,0.18)",
                    //
                    // boxShadow: isLowBattery
                    //     ? "0 0 30px rgba(255,59,48,.45)"
                    //     : "0 0 30px rgba(177,119,255,.45)",
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
                    fontSize: 16,
                    fontWeight: 500,
                }}
            >
                {percent}%{" "}{remainingTime}
            </Typography>
        </Box>
    );
}