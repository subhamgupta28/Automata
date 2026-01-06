import { Box, Typography } from "@mui/material";
import dayjs from "dayjs";

const clamp = (v, min, max) => Math.min(max, Math.max(min, v));

const timeToMinutes = time =>
    time.hour() * 60 + time.minute();


export default function SunriseSunsetCard({
                                 sunrise = "06:14",
                                 sunset = "17:33",
                                 width = 360,
                                 height = 140,
                             }) {
    const now = dayjs();

    const sunriseM = timeToMinutes(dayjs(sunrise, "HH:mm"));
    const sunsetM = timeToMinutes(dayjs(sunset, "HH:mm"));
    const nowM = timeToMinutes(now);

    const progress = clamp(
        (nowM - sunriseM) / (sunsetM - sunriseM),
        0,
        1
    );

    const padding = 24;
    const baseY = height - padding;
    const chartWidth = width - padding * 2;
    const chartHeight = baseY - padding;

    const sunX = padding + progress * chartWidth;
    const sunY =
        baseY - Math.sin(progress * Math.PI) * chartHeight;

    const curvePath = `
        M ${padding} ${baseY}
        Q ${width / 2} ${padding - 10}
          ${width - padding} ${baseY}
    `;

    return (
        <svg width={width} height={height}>
            {/* 🌙 Night BEFORE sunrise */}
            <rect
                x="0"
                y={baseY}
                width={padding}
                height={height - baseY}
                fill="#0b2b44"
            />

            {/* 🌙 Night AFTER sunset */}
            <rect
                x={width - padding}
                y={baseY}
                width={padding}
                height={height - baseY}
                fill="#0b2b44"
            />

            {/* 🌞 Daylight fill */}
            <path
                d={`${curvePath} L ${width - padding} ${height}
                   L ${padding} ${height} Z`}
                fill="#9ec3ff"
                opacity="0.18"
            />

            {/* Curve */}
            <path
                d={curvePath}
                fill="none"
                stroke="#9ec3ff"
                strokeWidth="3"
                strokeLinecap="round"
            />

            {/* Sun */}
            <SunIcon
                cx={sunX}
                cy={sunY}
                r="7"
                fill="#FFD54F"
                stroke="#FFE082"
                strokeWidth="2"
                style={{ transition: "cx 1s linear, cy 1s linear" }}
            />
        </svg>
    );
}

const SunIcon = ({ cx, cy, size = 14 }) => {
    const r = size / 2;

    return (
        <g
            transform={`translate(${cx}, ${cy})`}
            style={{ transition: "transform 1s linear" }}
        >
            {/* Rays */}
            {[...Array(8)].map((_, i) => (
                <line
                    key={i}
                    x1={0}
                    y1={-r - 4}
                    x2={0}
                    y2={-r - 8}
                    stroke="#FFD54F"
                    strokeWidth="2"
                    transform={`rotate(${i * 45})`}
                />
            ))}

            {/* Core */}
            <circle
                r={r}
                fill="#FFD54F"
                stroke="#FFECB3"
                strokeWidth="2"
            />
        </g>
    );
};
