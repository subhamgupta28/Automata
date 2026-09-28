import React, {useState} from 'react';
import {Box, Paper, Slider, Typography} from '@mui/material';
import {BatteryCharging, RotateCcw, Sliders, Thermometer} from 'lucide-react';

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
        <Box
            sx={{
                minHeight: '100vh',
                backgroundColor: '#0c0d0f',
                backgroundImage: 'radial-gradient(ellipse at 50% 10%, rgba(0, 213, 255, 0.04) 0%, transparent 60%)',
                color: '#e5e7eb',
                p: {xs: 2, md: 5},
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 4,
                fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
            }}
        >
            {/* Top Banner / Breadcrumb */}
            <Box sx={{textAlign: 'center', maxWidth: 640}}>
                <Typography
                    variant="h6"
                    sx={{
                        fontWeight: 700,
                        color: '#f8fafc',
                        letterSpacing: '-0.02em',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 1
                    }}
                >
                    <BatteryCharging className="text-cyan-400" size={22}/>
                    BMS Telemetry Interface
                </Typography>
                <Typography variant="body2" sx={{color: '#6b7280', mt: 0.5}}>
                    Pixel-accurate React & MUI replica of the Energy Storage Battery Module
                </Typography>
            </Box>

            {/* Exact UI Component Preview */}
            <Box sx={{width: '100%', maxWidth: 580}}>
                <BatteryModuleCard
                    name="Module Gamma (Expansion 2)"
                    capacityKWh={5.0}
                    role="Slave"
                    status={status}
                    soc={soc}
                    temp={temp}
                    voltage={voltage}
                    cycles={cycles}
                    soh={soh}
                    onDelete={() => setShowNotification(true)}
                />

                {showNotification && (
                    <Box
                        sx={{
                            mt: 2,
                            p: 1.5,
                            borderRadius: 2,
                            backgroundColor: 'rgba(239, 68, 68, 0.1)',
                            border: '1px solid rgba(239, 68, 68, 0.3)',
                            color: '#f87171',
                            fontSize: '0.8rem',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                        }}
                    >
                        <span>Module deletion action triggered</span>
                        <button
                            onClick={() => setShowNotification(false)}
                            className="text-xs text-neutral-400 hover:text-white underline cursor-pointer"
                        >
                            Dismiss
                        </button>
                    </Box>
                )}
            </Box>

            {/* Interactive Telemetry Tuning Station */}
            <Paper
                elevation={0}
                sx={{
                    width: '100%',
                    maxWidth: 580,
                    backgroundColor: '#141618',
                    border: '1px solid rgba(255, 255, 255, 0.06)',
                    borderRadius: 3,
                    p: 3
                }}
            >
                <Box sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2.5}}>
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                        <Sliders size={18} className="text-cyan-400"/>
                        <Typography variant="subtitle2" sx={{fontWeight: 600, color: '#f3f4f6'}}>
                            Live Telemetry Simulator
                        </Typography>
                    </Box>
                    <button
                        onClick={resetToImageDefaults}
                        className="flex items-center gap-1 text-xs text-cyan-400 hover:text-cyan-300 transition-colors cursor-pointer bg-cyan-950/40 px-2 py-1 rounded border border-cyan-800/50"
                    >
                        <RotateCcw size={12}/> Reset to Original Photo
                    </button>
                </Box>

                {/* State of Charge (SoC) Slider */}
                <Box sx={{mb: 2.5}}>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 0.5}}>
                        <Typography variant="caption" sx={{color: '#9ca3af', fontWeight: 500}}>
                            State of Charge (SoC)
                        </Typography>
                        <Typography variant="caption" sx={{color: '#10b981', fontWeight: 700, fontFamily: 'monospace'}}>
                            {soc}% ({((soc / 100) * 5.0).toFixed(2)} / 5.0 kWh)
                        </Typography>
                    </Box>
                    <Slider
                        value={soc}
                        min={0}
                        max={100}
                        onChange={(_, val) => setSoc(val)}
                        sx={{
                            color: '#10b981',
                            height: 5,
                            '& .MuiSlider-thumb': {
                                width: 14,
                                height: 14,
                                backgroundColor: '#26e8bd',
                                boxShadow: '0 0 8px #26e8bd',
                                '&:hover, &.Mui-focusVisible': {
                                    boxShadow: '0 0 0 8px rgba(38, 232, 189, 0.16)'
                                }
                            },
                            '& .MuiSlider-rail': {
                                backgroundColor: '#2d333b'
                            }
                        }}
                    />
                </Box>

                {/* Operating Status Selector */}
                <Box sx={{mb: 2.5}}>
                    <Typography variant="caption" sx={{color: '#9ca3af', fontWeight: 500, display: 'block', mb: 1}}>
                        Module Status
                    </Typography>
                    <Box sx={{display: 'flex', gap: 1}}>
                        {['DISCHARGING', 'CHARGING', 'IDLE'].map((st) => (
                            <button
                                key={st}
                                onClick={() => setStatus(st)}
                                style={{
                                    flex: 1,
                                    padding: '6px 12px',
                                    borderRadius: '6px',
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    border: status === st ? `1px solid ${STATUS_CONFIGS[st].color}` : '1px solid #2d333b',
                                    backgroundColor: status === st ? STATUS_CONFIGS[st].bgColor : '#1c1f24',
                                    color: status === st ? STATUS_CONFIGS[st].color : '#9ca3af',
                                    transition: 'all 0.2s ease'
                                }}
                            >
                                {st}
                            </button>
                        ))}
                    </Box>
                </Box>

                {/* Dual adjustments for Temp and Voltage */}
                <Box sx={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2}}>
                    {/* Temperature */}
                    <Box>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 0.5}}>
                            <Typography variant="caption" sx={{color: '#9ca3af'}}>
                                Temp: {temp}°C
                            </Typography>
                        </Box>
                        <Slider
                            value={temp}
                            min={10}
                            max={65}
                            step={0.1}
                            onChange={(_, val) => setTemp(val)}
                            sx={{color: '#00d5ff', height: 4}}
                        />
                    </Box>

                    {/* Voltage */}
                    <Box>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 0.5}}>
                            <Typography variant="caption" sx={{color: '#9ca3af'}}>
                                Voltage: {voltage} V
                            </Typography>
                        </Box>
                        <Slider
                            value={voltage}
                            min={42.0}
                            max={58.4}
                            step={0.1}
                            onChange={(_, val) => setVoltage(val)}
                            sx={{color: '#00d5ff', height: 4}}
                        />
                    </Box>
                </Box>
            </Paper>
        </Box>
    );
}