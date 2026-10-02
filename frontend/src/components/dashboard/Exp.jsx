import React, {useState} from 'react';
import {Box, Typography} from '@mui/material';
import {Thermometer} from 'lucide-react';
import LiquidLevelCard from "./LiquidLevelCard.jsx";

const STATUS_CONFIGS = {
    DISCHARGING: {
        label: 'DISCHARGING',
        color: '#ffb300', // Amber/gold
        borderColor: '#b27b16',
        bgColor: 'rgba(255, 179, 0, 0.08)',
        glow: 'rgba(255, 179, 0, 0.25)'
    },
    CHARGING: {
        label: 'CHARGING',
        color: '#10b981', // Emerald green
        borderColor: '#059669',
        bgColor: 'rgba(16, 185, 129, 0.08)',
        glow: 'rgba(16, 185, 129, 0.25)'
    },
    IDLE: {
        label: 'STANDBY',
        color: '#94a3b8', // Cool gray
        borderColor: '#475569',
        bgColor: 'rgba(148, 163, 184, 0.08)',
        glow: 'rgba(148, 163, 184, 0.15)'
    }
};

const BatterySegmentBar = ({soc = 82, capacity = 5.0}) => {
    const currentKWh = ((soc / 100) * capacity).toFixed(2);
    const segments = 10;

    return (
        <Box
            sx={{
                position: 'relative',
                width: '100%',
                height: 38,
                borderRadius: '19px',
                backgroundColor: '#0a0d10',
                border: '1px solid rgba(255, 255, 255, 0.07)',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                boxShadow: 'inset 0 2px 4px rgba(0, 0, 0, 0.6)'
            }}
        >
            {/* Filled Teal/Emerald Level */}
            <Box
                sx={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: `${soc}%`,
                    background: 'linear-gradient(90deg, #0e8169 0%, #17a685 70%, #1fc8a3 100%)',
                    borderRadius: '19px 0 0 19px',
                    boxShadow: '0 0 16px rgba(27, 206, 165, 0.35)',
                    transition: 'width 0.4s cubic-bezier(0.4, 0, 0.2, 1)'
                }}
            />

            {/* 10 Segment Divider Lines */}
            {Array.from({length: segments - 1}).map((_, idx) => {
                const leftPercent = ((idx + 1) / segments) * 100;
                const isCovered = leftPercent <= soc;

                return (
                    <Box
                        key={idx}
                        sx={{
                            position: 'absolute',
                            left: `${leftPercent}%`,
                            top: 0,
                            bottom: 0,
                            width: '2px',
                            backgroundColor: isCovered ? '#26e8bd' : 'rgba(255, 255, 255, 0.09)',
                            opacity: isCovered ? 0.8 : 0.25,
                            zIndex: 3,
                            boxShadow: isCovered ? '0 0 4px #26e8bd' : 'none',
                            transform: 'translateX(-50%)',
                            transition: 'background-color 0.3s ease, opacity 0.3s ease'
                        }}
                    />
                );
            })}

            {/* Internal Text Overlay - Left (82% SoC) */}
            <Typography
                component="div"
                sx={{
                    position: 'absolute',
                    left: 16,
                    zIndex: 4,
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    letterSpacing: '0.02em',
                    color: '#ffffff',
                    userSelect: 'none',
                    textShadow: '0 1px 3px rgba(0,0,0,0.85)',
                    display: 'flex',
                    alignItems: 'baseline',
                    gap: 0.5
                }}
            >
                <span>{soc}%</span>
                <span style={{fontSize: '0.78rem', fontWeight: 600, opacity: 0.95}}>SoC</span>
            </Typography>

            {/*/!* Internal Text Overlay - Right (4.10 / 5.0 kWh) *!/*/}
            {/*<Typography*/}
            {/*    component="div"*/}
            {/*    sx={{*/}
            {/*        position: 'absolute',*/}
            {/*        right: 18,*/}
            {/*        zIndex: 4,*/}
            {/*        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',*/}
            {/*        fontWeight: 600,*/}
            {/*        fontSize: '0.82rem',*/}
            {/*        letterSpacing: '0.04em',*/}
            {/*        color: '#ffffff',*/}
            {/*        userSelect: 'none',*/}
            {/*        textShadow: '0 1px 3px rgba(0,0,0,0.85)'*/}
            {/*    }}*/}
            {/*>*/}
            {/*    {currentKWh} / {capacity.toFixed(1)} kWh*/}
            {/*</Typography>*/}
        </Box>
    );
};

export const BatteryModuleCard = ({
                                      name = 'Module Gamma (Expansion 2)',
                                      capacityWh = 5.0,

                                      status = 'DISCHARGING',
                                      soc = 82,
                                      temp = 27.8,
                                      voltage = 53.8,
                                      cycles = 180,
                                      soh = 99,
                                  }) => {
    const currentStatus = STATUS_CONFIGS[status] || STATUS_CONFIGS.DISCHARGING;

    return (
        <Box sx={{position: 'relative', width: '100%', maxWidth: 580, mx: 'auto',}}>

            {/* Main Container Card */}
            <Box
                sx={{
                    // backgroundColor: '#1b1d20',
                    borderRadius: '16px',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    boxShadow: '0 10px 30px -5px rgba(0, 0, 0, 0.65)',
                    px: 2.75,
                    py: 2.25,
                    position: 'relative',
                    overflow: 'hidden'
                }}
            >
                {/* Header Row */}
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        mb: 1.75
                    }}
                >
                    {/* Left: Indicator Dot, Module Name & Capacity Badge */}
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'nowrap'}}>


                        {/* Title */}
                        <Typography
                            variant="body1"
                            sx={{
                                color: '#f8fafc',
                                fontWeight: 700,
                                fontSize: '0.94rem',
                                letterSpacing: '-0.01em',
                                whiteSpace: 'nowrap'
                            }}
                        >
                            {name}
                        </Typography>

                    </Box>

                    {/* Right: Status Tag & Delete Icon */}
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1.25}}>
                        {/* Status Pill Badge with Outline */}
                        <Box
                            sx={{
                                px: 1.5,
                                py: 0.35,
                                borderRadius: '14px',
                                border: `1.5px solid ${currentStatus.borderColor}`,
                                backgroundColor: currentStatus.bgColor,
                                color: currentStatus.color,
                                fontWeight: 800,
                                fontSize: '0.72rem',
                                letterSpacing: '0.04em',
                                textTransform: 'uppercase',
                                boxShadow: `0 0 10px ${currentStatus.glow}`,
                                userSelect: 'none'
                            }}
                        >
                            {currentStatus.label}
                        </Box>


                    </Box>
                </Box>

                {/* Battery SoC Visual Meter */}
                <Box sx={{mb: 1.75}}>
                    <BatterySegmentBar soc={soc} capacity={capacityWh}/>
                </Box>

                {/* Bottom Telemetry Row */}
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        pt: 0.25,
                        fontSize: '0.82rem',
                        color: '#8b949e',
                        userSelect: 'none'
                    }}
                >
                    {/* Left stats: Temperature & Voltage */}
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 2}}>
                        <Box sx={{display: 'flex', alignItems: 'center', gap: 0.6}}>
                            <Thermometer size={15} strokeWidth={1.8} style={{color: '#7e8794'}}/>
                            <Typography
                                component="span"
                                sx={{
                                    fontSize: '0.81rem',
                                    color: '#9ca3af',
                                    fontWeight: 500,
                                    fontFamily: 'ui-monospace, monospace'
                                }}
                            >
                                {temp}°C
                            </Typography>
                        </Box>

                        <Typography
                            component="span"
                            sx={{
                                fontSize: '0.81rem',
                                color: '#9ca3af',
                                fontWeight: 500,
                                fontFamily: 'ui-monospace, monospace'
                            }}
                        >
                            {voltage} V
                        </Typography>
                    </Box>

                </Box>
            </Box>
        </Box>
    );
};

export default function Exp() {
    // Default values matching user image directly:
    const [soc, setSoc] = useState(82);
    const [status, setStatus] = useState('DISCHARGING');
    const [temp, setTemp] = useState(27.8);
    const [voltage, setVoltage] = useState(53.8);
    const [cycles, setCycles] = useState(180);
    const [soh, setSoh] = useState(99);
    const [showNotification, setShowNotification] = useState(false);

    const resetToImageDefaults = () => {
        setSoc(82);
        setStatus('DISCHARGING');
        setTemp(27.8);
        setVoltage(53.8);
        setCycles(180);
        setSoh(99);
    };

    return (
        <Box sx={{
            margin: "100px"
        }}>

            <LiquidLevelCard
                width={140}
                height={200}
                level={5}
                threshold={20}
                title="Battery"
                subtitleTop="Discharging"
                subtitleBottom="Live 320 W"
                footerLeft="Today 1,240 Wh"
                footerRight="Updated 2 min ago"
            />

        </Box>
    );
}