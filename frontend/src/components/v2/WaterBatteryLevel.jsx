import React from "react";
import {Box, Typography, useTheme} from "@mui/material";

/**
 * Rectangle Liquid Battery Gauge (SVG-based)
 * Wave behavior closely matches react-liquid-gauge (continuous sine waves)
 */
export default function WaterBatteryLevel({value = 50}) {
    const theme = useTheme();

    const width = 120;
    const height = 200;

    // Clamp value
    const level = Math.max(0, Math.min(100, Number(value) || 0));
    const fillHeight = (level / 100) * height;
    const surfaceY = height - fillHeight;

    const color =
        level <= 20
            ? theme?.palette?.error?.main || "#d32f2f"
            : level <= 50
                ? theme?.palette?.warning?.main || "#ed6c02"
                : theme?.palette?.success?.main || "#2e7d32";

    // Build a long sine-like wave so horizontal translation looks continuous
    const wavePath = (amplitude, offsetY = 0) => `
    M -${width} ${surfaceY + offsetY}
    C -${width * 0.5} ${surfaceY - amplitude + offsetY},
      -${width * 0.5} ${surfaceY + amplitude + offsetY},
      0 ${surfaceY + offsetY}
    S ${width * 0.5} ${surfaceY - amplitude + offsetY},
      ${width} ${surfaceY + offsetY}
    S ${width * 1.5} ${surfaceY + amplitude + offsetY},
      ${width * 2} ${surfaceY + offsetY}
    V ${height}
    H -${width}
    Z
  `;

    return (
        <Box display="flex" flexDirection="column" alignItems="center" gap={1}>
            <Box
                sx={{
                    width,
                    height,
                    borderRadius: 2,
                    border: `3px solid ${theme?.palette?.divider || "#ccc"}`,
                    overflow: "hidden",
                    position: "relative",
                    backgroundColor: theme?.palette?.background?.paper || "#fff",
                }}
            >
                <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
                    <defs>
                        <clipPath id="clip">
                            <rect x="0" y={surfaceY} width={width} height={fillHeight}/>
                        </clipPath>
                    </defs>

                    {/* Liquid waves */}
                    <g clipPath="url(#clip)">
                        {/* Back wave */}
                        <path d={wavePath(6, 4)} fill={color} opacity="0.45">
                            <animateTransform
                                attributeName="transform"
                                type="translate"
                                from="0 0"
                                to="-120 0"
                                dur="8s"
                                repeatCount="indefinite"
                            />
                        </path>

                        {/* Front wave */}
                        <path d={wavePath(10, 0)} fill={color} opacity="0.85">
                            <animateTransform
                                attributeName="transform"
                                type="translate"
                                from="0 0"
                                to="120 0"
                                dur="4s"
                                repeatCount="indefinite"
                            />
                        </path>
                    </g>
                </svg>

                {/* Percentage label */}
                <Box
                    position="absolute"
                    inset={0}
                    display="flex"
                    alignItems="center"
                    justifyContent="center"
                >
                    <Typography variant="h6" fontWeight={600}>
                        {level}%
                    </Typography>
                </Box>
            </Box>

            <Typography variant="caption" color="text.secondary">
                Battery Level
            </Typography>
        </Box>
    );
}
/*
Test cases:

<WaterBatteryLevel value={0} />
<WaterBatteryLevel value={15} />
<WaterBatteryLevel value={40} />
<WaterBatteryLevel value={75} />
<WaterBatteryLevel value={100} />
*/
