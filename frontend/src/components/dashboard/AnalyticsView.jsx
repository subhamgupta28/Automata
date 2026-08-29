/**
 * AnalyticsView.jsx  –  V2
 *
 * Features:
 *  • Time-travel date picker (presets + custom range)
 *  • Sensor-typed chart panels: Energy, Environment (AQI/CO2/temp/humid/PM),
 *    Presence, Light, Generic
 *  • Per-attribute stat chips with min / max / avg / trend arrow
 *  • Air Quality Score badge for ENV devices
 *  • Sidebar device list + grid overview toggle
 *  • Live WebSocket merge
 */

import React, {memo, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Box,
    Card,
    CardContent,
    Chip,
    Fade,
    Grid,
    IconButton,
    InputAdornment,
    Skeleton,
    Stack,
    Tab,
    Tabs,
    TextField,
    Tooltip,
    Typography,
    useTheme,
} from "@mui/material";
import {
    Air,
    CalendarMonth,
    DeveloperBoard,
    Search,
    SensorsOff,
    ShowChart,
    SignalCellularAlt,
    TrendingDown,
    TrendingFlat,
    TrendingUp,
    ViewModule,
} from "@mui/icons-material";
import {BarChart, LineChart, lineElementClasses} from "@mui/x-charts";
import {DateTimePicker} from "@mui/x-date-pickers/DateTimePicker";
import {LocalizationProvider} from "@mui/x-date-pickers/LocalizationProvider";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";

import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {getAnalyticsV2, getLastDataByDeviceId} from "../../services/apis.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";
import LoadingScreen from "../../utils/LoadingScreen.jsx";

dayjs.extend(relativeTime);

// ─── Design tokens ────────────────────────────────────────────
const ACCENT = "#f8e697";
const ACCENT_DIM = "rgba(248,230,151,0.10)";
const GLASS = "rgba(255,255,255,0.04)";
const GLASS_B = "rgba(255,255,255,0.07)";

const ENV_COLORS = {
    temp: "#ff7043",
    humid: "#42a5f5",
    aqi: "#ab47bc",
    pm25: "#ef5350",
    pm10: "#ec407a",
    co2: "#66bb6a",
    co: "#ffa726",
    tvoc: "#26c6da",
    pressure: "#78909c",
};

const ENERGY_COLORS = {
    totalWh: "#f8e697",
    chargeTotalWh: "#66bb6a",
    percent: "#42a5f5",
    power: "#ff7043",
    current: "#ab47bc",
    voltage: "#26c6da",
};

const CHART_COLORS = [
    "#42a5f5", "#66bb6a", "#ff7043", "#ab47bc", "#ffa726",
    "#26c6da", "#f8e697", "#ef5350", "#78909c", "#ec407a",
];

// ─── Preset range options ─────────────────────────────────────
const PRESETS = [
    {label: "1h", range: "hour"},
    {label: "6h", range: "6h"},
    {label: "24h", range: "day"},
    {label: "7d", range: "week"},
    {label: "30d", range: "month"},
    {label: "90d", range: "3month"},
    {label: "1yr", range: "year"},
    {label: "Custom", range: "custom"},
];

// ─── AQI badge helpers ────────────────────────────────────────
const AQI_LEVELS = [
    {max: 50, label: "Good", color: "#66bb6a"},
    {max: 100, label: "Moderate", color: "#ffa726"},
    {max: 150, label: "Sensitive", color: "#ff7043"},
    {max: 200, label: "Unhealthy", color: "#ef5350"},
    {max: 300, label: "Very Unhly", color: "#ab47bc"},
    {max: Infinity, label: "Haz.", color: "#b71c1c"},
];

function aqiInfo(score) {
    return AQI_LEVELS.find(l => score <= l.max) ?? AQI_LEVELS[AQI_LEVELS.length - 1];
}

// ─── Utility: trend icon ──────────────────────────────────────
function TrendIcon({delta}) {
    if (delta == null) return <TrendingFlat fontSize="small" sx={{color: "#666"}}/>;
    if (delta > 2) return <TrendingUp fontSize="small" sx={{color: "#66bb6a"}}/>;
    if (delta < -2) return <TrendingDown fontSize="small" sx={{color: "#ef5350"}}/>;
    return <TrendingFlat fontSize="small" sx={{color: "#888"}}/>;
}

function fmtVal(v, unit) {
    if (v == null) return "—";
    const num = typeof v === "number" ? v : parseFloat(v);
    return isNaN(num) ? "—" : `${num.toFixed(1)}${unit ? ` ${unit}` : ""}`;
}

// ─── Stat chip row ────────────────────────────────────────────
const StatChipRow = memo(function StatChipRow({stats = {}}) {
    return (
        <Stack direction="row" flexWrap="wrap" gap={1} mb={2}>
            {Object.values(stats).map(s => (
                <Box key={s.key} sx={{
                    background: GLASS, border: `1px solid ${GLASS_B}`,
                    borderRadius: "10px", px: 1.5, py: 0.75, minWidth: 110,
                }}>
                    <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem", display: "block"}}>
                        {s.key.charAt(0).toUpperCase() + s.key.slice(1)}
                        {s.unit ? ` (${s.unit})` : ""}
                    </Typography>
                    <Stack direction="row" alignItems="center" spacing={0.5}>
                        <Typography sx={{fontWeight: 700, fontSize: "1rem", color: "#fff"}}>
                            {fmtVal(s.current, "")}
                        </Typography>
                        <TrendIcon delta={s.trend}/>
                    </Stack>
                    <Stack direction="row" spacing={1} mt={0.25}>
                        <Typography variant="caption"
                                    sx={{color: "#66bb6a", fontSize: "0.6rem"}}>↓{fmtVal(s.min, "")}</Typography>
                        <Typography variant="caption"
                                    sx={{color: "#ef5350", fontSize: "0.6rem"}}>↑{fmtVal(s.max, "")}</Typography>
                        <Typography variant="caption"
                                    sx={{color: "#aaa", fontSize: "0.6rem"}}>≈{fmtVal(s.avg, "")}</Typography>
                    </Stack>
                </Box>
            ))}
        </Stack>
    );
});

// ─── AQI Score badge ──────────────────────────────────────────
const AqiScoreBadge = memo(function AqiScoreBadge({score, label: qualLabel}) {
    if (score == null) return null;
    const lvl = aqiInfo(score);
    return (
        <Box sx={{
            display: "inline-flex", alignItems: "center", gap: 1,
            background: `${lvl.color}18`, border: `1px solid ${lvl.color}44`,
            borderRadius: "12px", px: 1.5, py: 0.5, mb: 2,
        }}>
            <Air sx={{color: lvl.color, fontSize: 18}}/>
            <Box>
                <Typography variant="caption" sx={{color: "#888", fontSize: "0.62rem", display: "block"}}>
                    Air Quality Score
                </Typography>
                <Typography sx={{fontWeight: 800, fontSize: "1.1rem", color: lvl.color, lineHeight: 1}}>
                    {score}/100 <Typography component="span" variant="caption"
                                            sx={{color: "#aaa"}}>{lvl.label}</Typography>
                </Typography>
            </Box>
        </Box>
    );
});

// ─── Sparkline ────────────────────────────────────────────────
const Spark = memo(function Spark({values, color = ACCENT}) {
    if (!values || values.length < 2) return null;
    const W = 80, H = 28;
    const nums = values.map(v => (typeof v === "number" ? v : parseFloat(v))).filter(v => !isNaN(v));
    if (nums.length < 2) return null;
    const min = Math.min(...nums), max = Math.max(...nums), range = max - min || 1;
    const pts = nums.map((v, i) => `${(i / (nums.length - 1)) * W},${H - ((v - min) / range) * H}`);
    return (
        <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} style={{display: "block"}}>
            <polyline points={pts.join(" ")} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round"/>
        </svg>
    );
});

// ─── ENV sensor panel ─────────────────────────────────────────
const EnvChartPanel = memo(function EnvChartPanel({summary, chartType}) {
    const {labels = [], dataPoints = [], stats = {}, airQualityScore, airQualityLabel, attributes = []} = summary;

    // Split into sub-groups for cleaner charts
    const airKeys = attributes.filter(k => ["aqi", "pm25", "pm10", "co2", "co", "tvoc"].includes(k.toLowerCase()));
    const climateKeys = attributes.filter(k => ["temp", "humid", "pressure"].includes(k.toLowerCase()));
    const otherKeys = attributes.filter(k => !airKeys.includes(k) && !climateKeys.includes(k));

    const makeSeries = (keys) => keys.map((k, i) => ({
        label: k.charAt(0).toUpperCase() + k.slice(1),
        data: dataPoints.map(p => {
            const v = p[k];
            return typeof v === "number" ? v : (v != null ? parseFloat(v) : null);
        }),
        color: ENV_COLORS[k.toLowerCase()] ?? CHART_COLORS[i % CHART_COLORS.length],
        showMark: false,
        area: chartType === "line",
    }));

    const ChartComp = chartType === "line" ? LineChart : BarChart;
    const commonProps = {
        xAxis: [{scaleType: "band", data: labels}],
        height: 220,
        sx: {[`& .${lineElementClasses.root}`]: {strokeWidth: 2}},
    };

    return (
        <Box>
            <AqiScoreBadge score={airQualityScore} label={airQualityLabel}/>
            <StatChipRow stats={stats}/>

            {airKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>
                        AIR QUALITY
                    </Typography>
                    <ChartComp {...commonProps} series={makeSeries(airKeys)}/>
                </Box>
            )}
            {climateKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>
                        CLIMATE
                    </Typography>
                    <ChartComp {...commonProps} series={makeSeries(climateKeys)}/>
                </Box>
            )}
            {otherKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>
                        OTHER
                    </Typography>
                    <ChartComp {...commonProps} series={makeSeries(otherKeys)}/>
                </Box>
            )}
        </Box>
    );
});

// ─── Energy sensor panel ──────────────────────────────────────
const EnergyChartPanel = memo(function EnergyChartPanel({summary, chartType}) {
    const {labels = [], dataPoints = [], stats = {}, attributes = []} = summary;

    const dischargeKeys = attributes.filter(k =>
        ["totalwh", "power", "current", "voltage", "energy"].some(x => k.toLowerCase().includes(x))
        && !k.toLowerCase().includes("charge")
    );
    const chargeKeys = attributes.filter(k => k.toLowerCase().includes("charge"));
    const percentKeys = attributes.filter(k => k.toLowerCase().includes("percent") || k.toLowerCase().includes("battery"));
    const otherKeys = attributes.filter(k =>
        !dischargeKeys.includes(k) && !chargeKeys.includes(k) && !percentKeys.includes(k)
    );

    const makeSeries = (keys) => keys.map((k, i) => ({
        label: k,
        data: dataPoints.map(p => {
            const v = p[k];
            return typeof v === "number" ? v : (v != null ? parseFloat(v) : null);
        }),
        color: ENERGY_COLORS[k] ?? CHART_COLORS[i % CHART_COLORS.length],
        showMark: false,
        area: chartType === "line",
    }));

    const ChartComp = chartType === "line" ? LineChart : BarChart;
    const commonProps = {
        xAxis: [{scaleType: "band", data: labels}], height: 220,
        sx: {[`& .${lineElementClasses.root}`]: {strokeWidth: 2}}
    };

    return (
        <Box>
            <StatChipRow stats={stats}/>
            {dischargeKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption" sx={{
                        color: "#666",
                        fontSize: "0.65rem",
                        letterSpacing: "0.08em"
                    }}>DISCHARGE</Typography>
                    <ChartComp {...commonProps} series={makeSeries(dischargeKeys)}/>
                </Box>
            )}
            {chargeKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption"
                                sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>CHARGE</Typography>
                    <ChartComp {...commonProps} series={makeSeries(chargeKeys)}/>
                </Box>
            )}
            {percentKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>BATTERY
                        %</Typography>
                    <LineChart {...commonProps} series={makeSeries(percentKeys)} yAxis={[{min: 0, max: 100}]}/>
                </Box>
            )}
            {otherKeys.length > 0 && (
                <Box mb={2}>
                    <Typography variant="caption"
                                sx={{color: "#666", fontSize: "0.65rem", letterSpacing: "0.08em"}}>OTHER</Typography>
                    <ChartComp {...commonProps} series={makeSeries(otherKeys)}/>
                </Box>
            )}
        </Box>
    );
});

// ─── Generic chart panel ──────────────────────────────────────
const GenericChartPanel = memo(function GenericChartPanel({summary, chartType}) {
    const {labels = [], dataPoints = [], stats = {}, attributes = []} = summary;
    const series = attributes.map((k, i) => ({
        label: k.charAt(0).toUpperCase() + k.slice(1),
        data: dataPoints.map(p => {
            const v = p[k];
            return typeof v === "number" ? v : (v != null ? parseFloat(v) : null);
        }),
        color: CHART_COLORS[i % CHART_COLORS.length],
        showMark: false,
        area: chartType === "line",
    }));
    const ChartComp = chartType === "line" ? LineChart : BarChart;
    return (
        <Box>
            <StatChipRow stats={stats}/>
            {series.length > 0 && (
                <ChartComp
                    xAxis={[{scaleType: "band", data: labels}]}
                    series={series}
                    height={360}
                    sx={{[`& .${lineElementClasses.root}`]: {strokeWidth: 2}}}
                />
            )}
        </Box>
    );
});

// ─── Sensor panel router ──────────────────────────────────────
function SensorPanel({summary, chartType}) {
    if (!summary || !summary.dataPoints || summary.dataPoints.length === 0) {
        return (
            <Box sx={{display: "flex", flexDirection: "column", alignItems: "center", py: 6, color: "#444"}}>
                <SensorsOff sx={{fontSize: 40, mb: 1}}/>
                <Typography variant="body2">No data for this period</Typography>
            </Box>
        );
    }
    switch (summary.sensorType) {
        case "ENV":
            return <EnvChartPanel summary={summary} chartType={chartType}/>;
        case "ENERGY":
            return <EnergyChartPanel summary={summary} chartType={chartType}/>;
        default:
            return <GenericChartPanel summary={summary} chartType={chartType}/>;
    }
}

// ─── Time Travel Toolbar ──────────────────────────────────────
const TimeTravelBar = memo(function TimeTravelBar({query, onChange}) {
    const [showPicker, setShowPicker] = useState(false);
    const [fromVal, setFromVal] = useState(dayjs().subtract(1, "day"));
    const [toVal, setToVal] = useState(dayjs());

    const applyCustom = () => {
        onChange({range: "custom", from: fromVal.toISOString(), to: toVal.toISOString()});
        setShowPicker(false);
    };

    return (
        <LocalizationProvider dateAdapter={AdapterDayjs}>
            <Box>
                {/* Preset chips */}
                <Stack direction="row" flexWrap="wrap" gap={0.75} alignItems="center">
                    <CalendarMonth sx={{color: "#555", fontSize: 16, mr: 0.5}}/>
                    {PRESETS.map(p => {
                        const isActive = p.range === "custom"
                            ? query.range === "custom"
                            : query.range === p.range;
                        return (
                            <Chip
                                key={p.label}
                                label={p.label}
                                size="small"
                                onClick={() => {
                                    if (p.range === "custom") {
                                        setShowPicker(v => !v);
                                    } else {
                                        onChange({range: p.range});
                                        setShowPicker(false);
                                    }
                                }}
                                sx={{
                                    fontSize: "0.7rem", height: 26,
                                    bgcolor: isActive ? ACCENT_DIM : GLASS,
                                    color: isActive ? ACCENT : "#888",
                                    border: `1px solid ${isActive ? ACCENT : GLASS_B}`,
                                    cursor: "pointer",
                                    "&:hover": {borderColor: ACCENT},
                                }}
                            />
                        );
                    })}

                    {query.range === "custom" && query.from && (
                        <Typography variant="caption" sx={{color: "#666", ml: 0.5}}>
                            {dayjs(query.from).format("DD MMM HH:mm")} → {dayjs(query.to).format("DD MMM HH:mm")}
                        </Typography>
                    )}
                </Stack>

                {/* Custom date range picker */}
                {showPicker && (
                    <Fade in>
                        <Box sx={{
                            mt: 1.5, p: 2, background: "#1a1a1a", border: `1px solid ${GLASS_B}`,
                            borderRadius: "12px", display: "flex", flexWrap: "wrap", gap: 2, alignItems: "flex-end",
                        }}>
                            <DateTimePicker
                                label="From"
                                value={fromVal}
                                onChange={setFromVal}
                                maxDateTime={toVal}
                                slotProps={{textField: {size: "small"}}}
                            />
                            <DateTimePicker
                                label="To"
                                value={toVal}
                                onChange={setToVal}
                                minDateTime={fromVal}
                                maxDateTime={dayjs()}
                                slotProps={{textField: {size: "small"}}}
                            />
                            <Stack direction="row" spacing={1}>
                                <Chip label="Apply" onClick={applyCustom}
                                      sx={{
                                          bgcolor: ACCENT_DIM,
                                          color: ACCENT,
                                          border: `1px solid ${ACCENT}`,
                                          cursor: "pointer"
                                      }}/>
                                <Chip label="Cancel" onClick={() => setShowPicker(false)}
                                      sx={{
                                          bgcolor: GLASS,
                                          color: "#888",
                                          border: `1px solid ${GLASS_B}`,
                                          cursor: "pointer"
                                      }}/>
                            </Stack>
                        </Box>
                    </Fade>
                )}
            </Box>
        </LocalizationProvider>
    );
});

// ─── Device sidebar card ──────────────────────────────────────
const SidebarCard = memo(function SidebarCard({device, isSelected, onClick, liveMessages}) {
    const [lastData, setLastData] = useState(null);

    useEffect(() => {
        getLastDataByDeviceId(device.id).then(setLastData).catch(() => {
        });
    }, [device.id]);

    useEffect(() => {
        if (liveMessages?.deviceId === device.id && liveMessages?.data) {
            setLastData(prev => ({...prev, ...liveMessages.data}));
        }
    }, [liveMessages, device.id]);

    const topAttrs = (device.attributes ?? []).slice(0, 3);

    return (
        <Card onClick={onClick} elevation={0} sx={{
            cursor: "pointer", borderRadius: "14px", mb: 1,
            border: `1px solid ${isSelected ? ACCENT : GLASS_B}`,
            background: isSelected ? ACCENT_DIM : GLASS,
            transition: "all 0.18s",
            "&:hover": {borderColor: ACCENT, background: ACCENT_DIM},
        }}>
            <CardContent sx={{p: "12px !important"}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Box flex={1} minWidth={0}>
                        <Typography variant="body2" sx={{
                            fontWeight: 700, fontSize: "0.78rem",
                            color: isSelected ? ACCENT : "#fff",
                            overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                        }}>
                            {device.name}
                        </Typography>
                        <Typography variant="caption" sx={{color: "#666", fontSize: "0.65rem"}}>
                            {device.attributes?.length ?? 0} attrs
                        </Typography>
                    </Box>
                    <Box sx={{
                        width: 7, height: 7, borderRadius: "50%", bgcolor: "#66bb6a",
                        boxShadow: "0 0 5px #66bb6a", mt: 0.5, flexShrink: 0
                    }}/>
                </Stack>
                <Stack direction="row" flexWrap="wrap" gap={0.5} mt={0.75}>
                    {topAttrs.map(a => (
                        <Chip key={a.key} size="small"
                              label={lastData?.[a.key] != null
                                  ? `${parseFloat(lastData[a.key]).toFixed(1)} ${a.units ?? ""}`
                                  : a.key}
                              sx={{
                                  fontSize: "0.6rem", height: 18,
                                  bgcolor: "rgba(255,255,255,0.05)", color: "#aaa",
                                  border: "1px solid rgba(255,255,255,0.08)"
                              }}/>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    );
});

// ─── Grid overview card ───────────────────────────────────────
const GridCard = memo(function GridCard({device, onClick}) {
    const [lastData, setLastData] = useState(null);
    const {messages} = useDeviceLiveData();

    useEffect(() => {
        getLastDataByDeviceId(device.id).then(setLastData).catch(() => {
        });
    }, [device.id]);
    useEffect(() => {
        if (messages?.deviceId === device.id && messages?.data)
            setLastData(prev => ({...prev, ...messages.data}));
    }, [messages, device.id]);

    const firstAttr = device.attributes?.[0];
    const firstVal = firstAttr && lastData?.[firstAttr.key];
    const sparkNums = device.attributes?.slice(0, 1)
        .map(() => Array.from({length: 8}, () => Math.random() * 10)) // placeholder sparkline
        .flat() ?? [];

    return (
        <Card onClick={onClick} elevation={0} sx={{
            cursor: "pointer", borderRadius: "16px", height: "100%",
            border: `1px solid ${GLASS_B}`, background: GLASS,
            transition: "all 0.2s",
            "&:hover": {borderColor: ACCENT, background: ACCENT_DIM, transform: "translateY(-2px)"},
        }}>
            <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={1}>
                    <Box>
                        <Typography variant="body2" sx={{fontWeight: 700, color: "#fff", fontSize: "0.82rem"}}>
                            {device.name}
                        </Typography>
                        <Typography variant="caption" sx={{color: "#555", fontSize: "0.65rem"}}>
                            {device.attributes?.length ?? 0} sensors
                        </Typography>
                    </Box>
                    <Box sx={{
                        width: 7,
                        height: 7,
                        borderRadius: "50%",
                        bgcolor: "#66bb6a",
                        boxShadow: "0 0 5px #66bb6a",
                        mt: 0.5
                    }}/>
                </Stack>
                {firstAttr && (
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-end">
                        <Box>
                            <Typography variant="caption"
                                        sx={{color: "#666", fontSize: "0.62rem"}}>{firstAttr.key}</Typography>
                            <Typography sx={{fontWeight: 800, color: ACCENT, fontSize: "1.4rem", lineHeight: 1}}>
                                {firstVal != null ? parseFloat(firstVal).toFixed(1) : "—"}
                                <Typography component="span" variant="caption" sx={{color: "#888", ml: 0.3}}>
                                    {firstAttr.units ?? ""}
                                </Typography>
                            </Typography>
                        </Box>
                    </Stack>
                )}
                <Stack direction="row" flexWrap="wrap" gap={0.5} mt={1}>
                    {(device.attributes ?? []).slice(1, 4).map(a => (
                        <Chip key={a.key} size="small"
                              label={`${a.key}: ${lastData?.[a.key] != null ? parseFloat(lastData[a.key]).toFixed(1) : "—"} ${a.units ?? ""}`}
                              sx={{
                                  fontSize: "0.58rem",
                                  height: 18,
                                  bgcolor: "rgba(255,255,255,0.04)",
                                  color: "#888",
                                  border: "1px solid rgba(255,255,255,0.07)"
                              }}/>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    );
});

// ─── Tab panel wrapper ────────────────────────────────────────
const CustomTabPanel = memo(function CustomTabPanel({children, value, index, ...other}) {
    return (
        <div role="tabpanel" hidden={value !== index} {...other}>
            {value === index && <Box>{children}</Box>}
        </div>
    );
});
export {CustomTabPanel};

// ─── Main chart pane ──────────────────────────────────────────
function ChartPane({device, query}) {
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(false);
    const [chartType, setChartType] = useState("line");
    const {messages} = useDeviceLiveData();
    const abortRef = useRef(null);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            abortRef.current?.abort();
            const ctrl = new AbortController();
            abortRef.current = ctrl;
            const data = await getAnalyticsV2(device.id, query);
            setSummary(data);
        } catch (e) {
            if (e.name !== "CanceledError" && e.name !== "AbortError") console.error(e);
        } finally {
            setLoading(false);
        }
    }, [device.id, query.range, query.from, query.to, query.granularity]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    // Merge live data into current reading in stats
    useEffect(() => {
        if (messages?.deviceId === device.id && messages?.data && summary) {
            setSummary(prev => {
                if (!prev) return prev;
                const newStats = {...prev.stats};
                for (const [k, v] of Object.entries(messages.data)) {
                    if (newStats[k]) {
                        newStats[k] = {...newStats[k], current: parseFloat(v)};
                    }
                }
                return {...prev, stats: newStats};
            });
        }
    }, [messages, device.id]);

    return (
        <Box>
            {/* Chart type toggle */}
            <Stack direction="row" gap={0.75} mb={1.5} alignItems="center">
                <Typography variant="caption" sx={{color: "#555", fontSize: "0.65rem", mr: 0.5}}>Chart</Typography>
                {["line", "bar"].map(t => (
                    <Chip key={t} label={t.charAt(0).toUpperCase() + t.slice(1)}
                          size="small"
                          onClick={() => setChartType(t)}
                          sx={{
                              fontSize: "0.7rem", height: 24, cursor: "pointer",
                              bgcolor: chartType === t ? ACCENT_DIM : GLASS,
                              color: chartType === t ? ACCENT : "#666",
                              border: `1px solid ${chartType === t ? ACCENT : GLASS_B}`,
                          }}
                    />
                ))}
            </Stack>

            {loading ? (
                <Box>
                    <Stack direction="row" spacing={1} mb={2}>
                        {[110, 110, 110, 110].map((w, i) => (
                            <Skeleton key={i} variant="rounded" width={w} height={72} sx={{borderRadius: "10px"}}/>
                        ))}
                    </Stack>
                    <Skeleton variant="rounded" height={260} sx={{borderRadius: "12px"}}/>
                </Box>
            ) : (
                <SensorPanel summary={summary} chartType={chartType}/>
            )}
        </Box>
    );
}

// ─── Root component ───────────────────────────────────────────
function AnalyticsViewComponent() {
    const theme = useTheme();
    const {devices, loading} = useCachedDevices();
    const {messages} = useDeviceLiveData();

    const [viewMode, setViewMode] = useState("chart");
    const [search, setSearch] = useState("");
    const [selIdx, setSelIdx] = useState(0);
    const [mobileTab, setMobileTab] = useState(0);
    const [query, setQuery] = useState({range: "day"});

    const analyticsDevices = useMemo(
        () => (devices ? devices.filter(d => d.analytics) : []),
        [devices]
    );

    const filtered = useMemo(() => {
        if (!search.trim()) return analyticsDevices;
        const q = search.toLowerCase();
        return analyticsDevices.filter(d => d.name.toLowerCase().includes(q));
    }, [analyticsDevices, search]);

    const selectedDevice = filtered[selIdx] ?? null;

    const selectDevice = useCallback((i) => {
        setSelIdx(i);
        setMobileTab(i);
        if (viewMode === "grid") setViewMode("chart");
    }, [viewMode]);

    return (
        <Box sx={{
            height: "100vh",
            // pt: "48px",
            color: theme.palette.text.primary,
            display: "flex",
            flexDirection: "column",
            scrollbarWidth: "0px"
        }}>

            {/* ── Header ── */}
            <Box sx={{
                px: 2, py: 1.25,
                display: "flex", alignItems: "center", justifyContent: "space-between",
                borderBottom: `1px solid ${GLASS_B}`,
                background: "rgba(22,22,22,0.75)", backdropFilter: "blur(10px)",
                flexWrap: "wrap", gap: 1,
            }}>
                <Stack direction="row" alignItems="center" spacing={1.5}>
                    <SignalCellularAlt sx={{color: ACCENT, fontSize: 22}}/>
                    <Box>
                        <Typography variant="subtitle1" sx={{fontWeight: 700, color: "#fff", lineHeight: 1}}>
                            Analytics
                        </Typography>
                        <Typography variant="caption" sx={{color: "#555", fontSize: "0.65rem"}}>
                            {analyticsDevices.length} devices · {selectedDevice?.name ?? "—"}
                        </Typography>
                    </Box>
                </Stack>

                <Stack direction="row" spacing={1} alignItems="center">
                    <TextField size="small" placeholder="Search…" value={search}
                               onChange={e => setSearch(e.target.value)}
                               sx={{
                                   width: 160,
                                   "& .MuiOutlinedInput-root": {
                                       borderRadius: "10px", fontSize: "0.75rem", background: GLASS,
                                       "& fieldset": {borderColor: GLASS_B}, "&:hover fieldset": {borderColor: ACCENT}
                                   },
                                   "& input": {color: "#fff", py: "6px"},
                               }}
                               InputProps={{
                                   startAdornment: <InputAdornment position="start"><Search
                                       sx={{fontSize: 15, color: "#555"}}/></InputAdornment>
                               }}
                    />
                    {[["chart", <ShowChart key="a" fontSize="small"/>], ["grid",
                        <ViewModule key="b" fontSize="small"/>]].map(([mode, icon]) => (
                        <Tooltip key={mode} title={mode === "chart" ? "Chart view" : "Grid overview"}>
                            <IconButton size="small" onClick={() => setViewMode(mode)} sx={{
                                borderRadius: "8px",
                                border: `1px solid ${viewMode === mode ? ACCENT : GLASS_B}`,
                                color: viewMode === mode ? ACCENT : "#555",
                                background: viewMode === mode ? ACCENT_DIM : "transparent",
                            }}>{icon}</IconButton>
                        </Tooltip>
                    ))}
                </Stack>
            </Box>

            {/* ── Body ── */}
            {loading ? (
                <Box sx={{display: "flex", justifyContent: "center", alignItems: "center", flex: 1, pt: 8}}>
                    <LoadingScreen/>
                </Box>
            ) : analyticsDevices.length === 0 ? (
                <Box sx={{display: "flex", flexDirection: "column", alignItems: "center", pt: 10, color: "#444"}}>
                    <DeveloperBoard sx={{fontSize: 48, mb: 1.5}}/>
                    <Typography variant="body2" sx={{fontWeight: 600}}>No analytics-enabled devices</Typography>
                    <Typography variant="caption" sx={{mt: 0.5, color: "#555"}}>Enable analytics on a device to see data
                        here.</Typography>
                </Box>
            ) : viewMode === "grid" ? (
                <Fade in>
                    <Box sx={{p: 2}}>
                        <Grid container spacing={1.5}>
                            {filtered.map((device, i) => (
                                <Grid item xs={12} sm={6} md={4} lg={3} key={device.id}>
                                    <GridCard device={device} onClick={() => selectDevice(i)}/>
                                </Grid>
                            ))}
                        </Grid>
                    </Box>
                </Fade>
            ) : (
                <Fade in>
                    <Box sx={{display: "flex", flex: 1, overflow: "hidden"}}>

                        {/* Sidebar */}
                        <Box sx={{
                            width: 220, flexShrink: 0,
                            borderRight: `1px solid ${GLASS_B}`,
                            overflowY: "auto", p: 1.5,
                            display: {xs: "none", md: "block"},
                        }}>
                            <Typography variant="caption" sx={{
                                color: "#444",
                                fontWeight: 700,
                                fontSize: "0.62rem",
                                letterSpacing: "0.08em",
                                mb: 1,
                                display: "block"
                            }}>
                                DEVICES
                            </Typography>
                            {filtered.map((device, i) => (
                                <SidebarCard key={device.id} device={device}
                                             isSelected={selIdx === i}
                                             onClick={() => selectDevice(i)}
                                             liveMessages={messages}/>
                            ))}
                        </Box>

                        {/* Main panel */}
                        <Box sx={{flex: 1, overflow: "auto", p: {xs: 1, md: 2}}}>

                            {/* Mobile tab strip */}
                            <Box sx={{display: {xs: "block", md: "none"}, mb: 1.5}}>
                                <Box sx={{background: GLASS, borderRadius: "10px", border: `1px solid ${GLASS_B}`}}>
                                    <Tabs value={mobileTab}
                                          onChange={(_, v) => selectDevice(v)}
                                          variant="scrollable" scrollButtons="auto"
                                          TabIndicatorProps={{style: {background: ACCENT}}}
                                          sx={{
                                              "& .MuiTab-root": {color: "#777", fontSize: "0.72rem"},
                                              "& .Mui-selected": {color: ACCENT}
                                          }}>
                                        {filtered.map(d => <Tab key={d.id} label={d.name}/>)}
                                    </Tabs>
                                </Box>
                            </Box>

                            {selectedDevice ? (
                                <Box>
                                    {/* Device header */}
                                    <Stack direction="row" alignItems="center" justifyContent="space-between" mb={1.5}
                                           flexWrap="wrap" gap={1}>
                                        <Box>
                                            <Typography variant="h6"
                                                        sx={{fontWeight: 700, color: "#fff", fontSize: "1rem"}}>
                                                {selectedDevice.name}
                                            </Typography>
                                            <Typography variant="caption" sx={{color: "#555"}}>
                                                {selectedDevice.attributes?.length} attributes
                                            </Typography>
                                        </Box>
                                    </Stack>

                                    {/* Time travel bar */}
                                    <Box sx={{mb: 2}}>
                                        <TimeTravelBar query={query} onChange={setQuery}/>
                                    </Box>

                                    {/* Chart */}
                                    <Card elevation={0} sx={{
                                        background: GLASS, border: `1px solid ${GLASS_B}`,
                                        borderRadius: "16px",
                                    }}>
                                        <CardContent>
                                            <ChartPane key={`${selectedDevice.id}-${JSON.stringify(query)}`}
                                                       device={selectedDevice} query={query}/>
                                        </CardContent>
                                    </Card>
                                </Box>
                            ) : (
                                <Box sx={{
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    height: 300,
                                    color: "#333"
                                }}>
                                    <Typography>Select a device</Typography>
                                </Box>
                            )}
                        </Box>
                    </Box>
                </Fade>
            )}
        </Box>
    );
}

const AnalyticsView = memo(AnalyticsViewComponent);
export default AnalyticsView;