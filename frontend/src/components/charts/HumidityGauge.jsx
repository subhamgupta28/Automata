import React, { useEffect, useState } from "react";
import LiquidFillGauge from "react-liquid-gauge";
import Typography from "@mui/material/Typography";

function interpolateColor(color1, color2, factor) {
    const hex = (color) => color.replace(/^#/, "");
    const hexToRgb = (hex) => ({
        r: parseInt(hex.substring(0, 2), 16),
        g: parseInt(hex.substring(2, 4), 16),
        b: parseInt(hex.substring(4, 6), 16)
    });
    const rgbToHex = ({ r, g, b }) =>
        "#" +
        [r, g, b]
            .map((x) => x.toString(16).padStart(2, "0"))
            .join("");

    const c1 = hexToRgb(hex(color1));
    const c2 = hexToRgb(hex(color2));

    const result = {
        r: Math.round(c1.r + factor * (c2.r - c1.r)),
        g: Math.round(c1.g + factor * (c2.g - c1.g)),
        b: Math.round(c1.b + factor * (c2.b - c1.b))
    };

    return rgbToHex(result);
}

export default function HumidityGauge({ humidity = 50, size = 140, displayName }) {
    const lowColor = "#fc2626"; // blue
    const highColor = "#F4FF57FF"; // red

    const [animatedHumidity, setAnimatedHumidity] = useState(humidity);

    // Animate humidity value transition
    useEffect(() => {
        const step = (humidity - animatedHumidity) / 10;
        const interval = setInterval(() => {
            setAnimatedHumidity((prev) => {
                const next = prev + step;
                if ((step > 0 && next >= humidity) || (step < 0 && next <= humidity)) {
                    clearInterval(interval);
                    return humidity;
                }
                return next;
            });
        }, 20); // 20ms x 10 = ~200ms animation
        return () => clearInterval(interval);
    }, [humidity]);

    const fillColor = interpolateColor(lowColor, highColor, animatedHumidity / 100);
    const circleColor = interpolateColor(highColor, lowColor, 1 - animatedHumidity / 100);

    const gradientStops = [
        {
            key: "0%",
            stopColor: fillColor,
            stopOpacity: 1,
            offset: "0%"
        },
        {
            key: "100%",
            stopColor: circleColor,
            stopOpacity: 1,
            offset: "100%"
        }
    ];

    return (
        <div style={{ textAlign: "center" }}>

            <LiquidFillGauge
                width={size}
                height={size}
                value={animatedHumidity}
                percent="%"
                textSize={1}
                textOffsetX={0}
                textOffsetY={0}
                textRenderer={({ value, textSize, width, height, percent }) => {
                    const radius = Math.min(height / 2, width / 2);
                    const textPixels = (textSize * radius) / 2;
                    const valueStyle = { fontSize: textPixels };
                    const percentStyle = { fontSize: textPixels * 0.6 };
                    return (
                        <tspan>
                            <tspan className="value" style={valueStyle}>
                                {Math.round(value)}
                            </tspan>
                            <tspan style={percentStyle}>{percent}</tspan>
                        </tspan>
                    );
                }}
                riseAnimation
                waveAnimation
                waveFrequency={2}
                waveAmplitude={1}
                gradient
                gradientStops={gradientStops}
                circleStyle={{
                    fill: circleColor,
                    transition: "fill 0.3s ease"
                }}
                waveStyle={{
                    transition: "fill 0.3s ease"
                }}
                textStyle={{
                    fill: "#fff",
                    fontFamily: "Arial"
                }}
                waveTextStyle={{
                    fill: "#fff",
                    fontFamily: "Arial"
                }}
            />
            <Typography textAlign='center'>
                {displayName}
            </Typography>
        </div>
    );
}
