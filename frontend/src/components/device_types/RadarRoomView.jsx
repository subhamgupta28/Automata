/**
 * RadarRoomView.jsx
 *
 * Visualises up to 3 RD-03D radar targets inside a configurable room with
 * 3 rectangular zones. Designed to pair with the RD03D_3Target.ino firmware.
 *
 * Props
 * ─────
 *   sensorData  {object}  – live payload from the sensor (matches JSON shape):
 *     {
 *       targets: [
 *         { id, occupied, distance, angle, speed, x, y, zone },
 *         ...  (3 entries)
 *       ],
 *       zoneA_occupied: bool,
 *       zoneB_occupied: bool,
 *       zoneC_occupied: bool,
 *     }
 *
 * Usage
 * ─────
 *   <RadarRoomView sensorData={sensorData} />
 *
 * The sensor coordinate system:
 *   X: positive = left,  negative = right  (mm → converted to cm internally)
 *   Y: positive = forward (away from sensor)
 *
 * The room canvas maps:
 *   Room X axis → horizontal (sensor X)
 *   Room Y axis → vertical, sensor sits at bottom-centre (Y = 0),
 *                 increasing upward toward max range.
 */

// ─── Demo wrapper (remove when integrating into your app) ────────────────────
// Shows the component with simulated live data cycling through positions.
import {useCallback, useEffect, useState} from "react";
import {Box, Chip, Paper, TextField, Typography,} from "@mui/material";
import PersonIcon from "@mui/icons-material/Person";

// ─── Palette ──────────────────────────────────────────────────────────────────
const PALETTE = {
    bg: "rgb(13 17 23 / 0)",
    surface: "rgb(22 27 34 / 0)",
    border: "#30363D",
    grid: "#1C2128",
    text: "#E6EDF3",
    muted: "#8B949E",

    zone: [
        {fill: "rgba(56,139,253,0.10)", stroke: "#388BFD", label: "#388BFD"},
        {fill: "rgba(63,185,80,0.10)", stroke: "#3FB950", label: "#3FB950"},
        {fill: "rgba(210,153,34,0.10)", stroke: "#D2991F", label: "#D2991F"},
    ],

    target: ["#F78166", "#79C0FF", "#D2A8FF"],
};

// ─── Default sensor-space dimensions (cm) ─────────────────────────────────────
const DEFAULT_ROOM = {width: 600, depth: 600}; // room canvas in sensor-cm

// ─── Default zones (cm, sensor coordinate space) ─────────────────────────────
const DEFAULT_ZONES = [
    {name: "Zone A", xMin: -180, xMax: 180, yMin: 35, yMax: 180},
    {name: "Zone B", xMin: -180, xMax: 180, yMin: 185, yMax: 320},
    {name: "Zone C", xMin: -180, xMax: 180, yMin: 325, yMax: 480},
];

// ─── Coordinate transform ─────────────────────────────────────────────────────
//  sensor (x_cm, y_cm)  →  SVG pixel (px, py)
//  Sensor origin (0,0) placed at bottom-centre of canvas.
function toSVG(x_cm, y_cm, room, svgW, svgH) {
    const scaleX = svgW / room.width;
    const scaleY = svgH / room.depth;
    // x=0 → centre horizontally; y=0 → bottom; y=room.depth → top
    const px = svgW / 2 + x_cm * scaleX;
    const py = svgH - y_cm * scaleY;
    return {px, py};
}

// ─── Zone SVG rect ────────────────────────────────────────────────────────────
function ZoneRect({zone, zoneIdx, room, svgW, svgH, occupied, editing, onChange}) {
    const tl = toSVG(zone.xMin, zone.yMax, room, svgW, svgH);
    const br = toSVG(zone.xMax, zone.yMin, room, svgW, svgH);
    const w = br.px - tl.px;
    const h = br.py - tl.py;
    const c = PALETTE.zone[zoneIdx];

    return (
        <g>
            <rect
                x={tl.px} y={tl.py} width={w} height={h}
                fill={occupied ? c.stroke + "33" : c.fill}
                stroke={c.stroke}
                strokeWidth={occupied ? 2 : 1}
                strokeDasharray={occupied ? "0" : "4 3"}
                rx={3}
                style={{transition: "all 0.4s ease"}}
            />
            {/* Zone label */}
            <text
                x={tl.px + w / 2}
                y={tl.py + 18}
                textAnchor="middle"
                fontSize="11"
                fill={c.label}
                fontFamily="monospace"
                fontWeight="600"
                opacity={0.9}
            >
                {zone.name}
            </text>
            {occupied && (
                <text
                    x={tl.px + w / 2}
                    y={tl.py + 32}
                    textAnchor="middle"
                    fontSize="9"
                    fill={c.label}
                    fontFamily="monospace"
                    opacity={0.7}
                >
                    ● OCCUPIED
                </text>
            )}
        </g>
    );
}

// ─── Target dot ──────────────────────────────────────────────────────────────
function TargetDot({target, idx, room, svgW, svgH}) {
    if (!target.occupied) return null;
    const {px, py} = toSVG(target.x, target.y, room, svgW, svgH);
    const col = PALETTE.target[idx];

    return (
        <g>
            {/* Pulse ring */}
            <circle cx={px} cy={py} r={18} fill={col + "22"} stroke={col + "55"} strokeWidth={1}>
                <animate attributeName="r" values="12;22;12" dur="2s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values="0.6;0;0.6" dur="2s" repeatCount="indefinite"/>
            </circle>
            {/* Solid dot */}
            <circle cx={px} cy={py} r={7} fill={col} stroke={PALETTE.bg} strokeWidth={2}/>
            {/* Label */}
            <text x={px} y={py - 12} textAnchor="middle" fontSize="10" fill={col} fontFamily="monospace"
                  fontWeight="700">
                T{target.id}
            </text>
            {/* Zone badge */}
            {target.zone && target.zone !== "None" && (
                <text x={px} y={py + 20} textAnchor="middle" fontSize="8" fill={PALETTE.muted} fontFamily="monospace">
                    {target.zone}
                </text>
            )}
        </g>
    );
}

// ─── Grid lines ───────────────────────────────────────────────────────────────
function GridLines({room, svgW, svgH, step = 100}) {
    const lines = [];
    for (let x = -room.width / 2; x <= room.width / 2; x += step) {
        const {px} = toSVG(x, 0, room, svgW, svgH);
        lines.push(<line key={`vx${x}`} x1={px} y1={0} x2={px} y2={svgH} stroke={PALETTE.grid} strokeWidth={0.5}/>);
        if (x !== 0) {
            lines.push(
                <text key={`lx${x}`} x={px} y={svgH - 4} textAnchor="middle" fontSize="8" fill={PALETTE.muted}
                      fontFamily="monospace">
                    {x}
                </text>
            );
        }
    }
    for (let y = 0; y <= room.depth; y += step) {
        const {py} = toSVG(0, y, room, svgW, svgH);
        lines.push(<line key={`vy${y}`} x1={0} y1={py} x2={svgW} y2={py} stroke={PALETTE.grid} strokeWidth={0.5}/>);
        if (y > 0) {
            lines.push(
                <text key={`ly${y}`} x={4} y={py + 3} fontSize="8" fill={PALETTE.muted} fontFamily="monospace">
                    {y}
                </text>
            );
        }
    }
    // Sensor origin marker
    const origin = toSVG(0, 0, room, svgW, svgH);
    lines.push(
        <polygon key="sensor"
                 points={`${origin.px},${origin.py - 10} ${origin.px - 7},${origin.py + 4} ${origin.px + 7},${origin.py + 4}`}
                 fill="#58A6FF" opacity={0.8}
        />
    );
    lines.push(
        <text key="sensor-label" x={origin.px + 10} y={origin.py + 5} fontSize="9" fill="#58A6FF"
              fontFamily="monospace">
            sensor
        </text>
    );
    return <>{lines}</>;
}

// ─── Zone Editor row ─────────────────────────────────────────────────────────
function ZoneEditor({zone, zoneIdx, onChange}) {
    const c = PALETTE.zone[zoneIdx];
    const field = (label, key, min, max) => (
        <Box sx={{display: "flex", flexDirection: "column", gap: 0.5, flex: 1, minWidth: 60}}>
            <Typography variant="caption" sx={{color: PALETTE.muted, fontFamily: "monospace"}}>{label}</Typography>
            <TextField
                size="small"
                type="number"
                value={zone[key]}
                inputProps={{
                    min,
                    max,
                    step: 10,
                    style: {color: PALETTE.text, fontFamily: "monospace", fontSize: 12, padding: "4px 8px"}
                }}
                sx={{
                    "& .MuiOutlinedInput-root": {
                        background: PALETTE.bg,
                        "& fieldset": {borderColor: PALETTE.border},
                        "&:hover fieldset": {borderColor: c.stroke},
                        "&.Mui-focused fieldset": {borderColor: c.stroke},
                    },
                }}
                onChange={e => onChange({...zone, [key]: Number(e.target.value)})}
            />
        </Box>
    );

    return (
        <Box sx={{p: 1.5, border: `1px solid ${c.stroke}33`, borderRadius: 1, background: c.fill, mb: 1}}>
            <Typography variant="caption"
                        sx={{color: c.label, fontFamily: "monospace", fontWeight: 700, mb: 1, display: "block"}}>
                ■ {zone.name}
            </Typography>
            <Box sx={{display: "flex", gap: 1, flexWrap: "wrap"}}>
                {field("X min (cm)", "xMin", -600, 0)}
                {field("X max (cm)", "xMax", 0, 600)}
                {field("Y min (cm)", "yMin", 0, 600)}
                {field("Y max (cm)", "yMax", 0, 600)}
            </Box>
            <Box sx={{mt: 1}}>
                <Typography variant="caption" sx={{color: PALETTE.muted, fontFamily: "monospace"}}>Zone
                    name</Typography>
                <TextField
                    size="small"
                    fullWidth
                    value={zone.name}
                    inputProps={{
                        style: {
                            color: PALETTE.text,
                            fontFamily: "monospace",
                            fontSize: 12,
                            padding: "4px 8px"
                        }
                    }}
                    sx={{
                        mt: 0.5,
                        "& .MuiOutlinedInput-root": {
                            background: PALETTE.bg,
                            "& fieldset": {borderColor: PALETTE.border},
                            "&:hover fieldset": {borderColor: c.stroke},
                            "&.Mui-focused fieldset": {borderColor: c.stroke},
                        },
                    }}
                    onChange={e => onChange({...zone, name: e.target.value})}
                />
            </Box>
        </Box>
    );
}

// ─── Target Info Card ─────────────────────────────────────────────────────────
function TargetCard({target, idx}) {
    const col = PALETTE.target[idx];
    const active = target.occupied;

    return (
        <Box sx={{
            p: 1.5,
            border: `1px solid ${active ? col + "66" : PALETTE.border}`,
            borderRadius: 1,
            background: active ? col + "0D" : PALETTE.bg,
            transition: "all 0.4s ease",
            position: "relative",
            overflow: "hidden",
        }}>
            {active && (
                <Box sx={{
                    position: "absolute", top: 0, left: 0, right: 0, height: 2,
                    background: `linear-gradient(90deg, transparent, ${col}, transparent)`,
                }}/>
            )}
            <Box sx={{display: "flex", alignItems: "center", gap: 1, mb: 1}}>
                <PersonIcon sx={{fontSize: 14, color: active ? col : PALETTE.muted}}/>
                <Typography variant="caption"
                            sx={{color: active ? col : PALETTE.muted, fontFamily: "monospace", fontWeight: 700}}>
                    TARGET {target.id}
                </Typography>
                <Chip
                    label={active ? "ACTIVE" : "—"}
                    size="small"
                    sx={{
                        ml: "auto",
                        height: 16,
                        fontSize: 9,
                        fontFamily: "monospace",
                        background: active ? col + "33" : PALETTE.border,
                        color: active ? col : PALETTE.muted,
                        border: `1px solid ${active ? col + "55" : "transparent"}`,
                    }}
                />
            </Box>
            {active ? (
                <Box sx={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: 0.5}}>
                    {[
                        ["X", `${target.x?.toFixed(1)} cm`],
                        ["Y", `${target.y?.toFixed(1)} cm`],
                        ["Dist", `${target.distance?.toFixed(1)} cm`],
                        ["Speed", `${target.speed} cm/s`],
                        ["Angle", `${target.angle?.toFixed(1)}°`],
                        ["Zone", target.zone || "None"],
                    ].map(([k, v]) => (
                        <Box key={k}>
                            <Typography variant="caption" sx={{
                                color: PALETTE.muted,
                                fontSize: 9,
                                fontFamily: "monospace"
                            }}>{k}</Typography>
                            <Typography variant="caption" sx={{
                                color: PALETTE.text,
                                fontSize: 10,
                                fontFamily: "monospace",
                                display: "block",
                                fontWeight: 600
                            }}>{v}</Typography>
                        </Box>
                    ))}
                </Box>
            ) : (
                <Typography variant="caption" sx={{color: PALETTE.muted, fontFamily: "monospace", fontSize: 10}}>
                    Not detected
                </Typography>
            )}
        </Box>
    );
}

// ─── Main Component ───────────────────────────────────────────────────────────
export default function RadarRoomView({sensorData}) {
    const SVG_W = 360;
    const SVG_H = 220;

    // Normalise incoming prop — fall back to empty targets if no data yet
    const targets = (sensorData?.targets ?? []).map(t => ({
        id: t.id, occupied: !!t.occupied,
        x: t.x ?? 0, y: t.y ?? 0,
        distance: t.distance ?? 0, angle: t.angle ?? 0,
        speed: t.speed ?? 0, zone: t.zone ?? "None",
    }));

    // Pad to exactly 3 entries
    while (targets.length < 3) targets.push({
        id: targets.length + 1,
        occupied: false,
        x: 0,
        y: 0,
        distance: 0,
        angle: 0,
        speed: 0,
        zone: "None"
    });

    const zoneOccupied = [
        !!sensorData?.zoneA_occupied,
        !!sensorData?.zoneB_occupied,
        !!sensorData?.zoneC_occupied,
    ];

    const [zones, setZones] = useState(DEFAULT_ZONES);
    const [room, setRoom] = useState(DEFAULT_ROOM);
    const [showSettings, setShowSettings] = useState(false);

    const updateZone = useCallback((i, z) => setZones(prev => prev.map((old, idx) => idx === i ? z : old)), []);

    const totalActive = targets.filter(t => t.occupied).length;

    return (
        <Box sx={{background: PALETTE.bg, p: 1, fontFamily: "monospace"}}>
            {/* ── Header ── */}
            {/*<Box sx={{display: "flex", alignItems: "center", gap: 2, mb: 3}}>*/}
            {/*    <Box sx={{ml: "auto", display: "flex", gap: 1, alignItems: "center"}}>*/}
            {/*        <Chip*/}
            {/*            icon={<PersonIcon sx={{fontSize: 14}}/>}*/}
            {/*            label={`${totalActive} / 3 active`}*/}
            {/*            size="small"*/}
            {/*            sx={{*/}
            {/*                background: totalActive > 0 ? "#388BFD22" : PALETTE.surface,*/}
            {/*                color: totalActive > 0 ? "#388BFD" : PALETTE.muted,*/}
            {/*                fontFamily: "monospace",*/}
            {/*                border: `1px solid ${totalActive > 0 ? "#388BFD55" : PALETTE.border}`*/}
            {/*            }}*/}
            {/*        />*/}
            {/*        <Tooltip title="Configure zones & room">*/}
            {/*            <IconButton size="small" onClick={() => setShowSettings(s => !s)}*/}
            {/*                        sx={{*/}
            {/*                            color: showSettings ? "#388BFD" : PALETTE.muted,*/}
            {/*                            background: showSettings ? "#388BFD22" : "transparent",*/}
            {/*                            border: `1px solid ${showSettings ? "#388BFD55" : PALETTE.border}`,*/}
            {/*                            borderRadius: 1*/}
            {/*                        }}>*/}
            {/*                <SettingsIcon fontSize="small"/>*/}
            {/*            </IconButton>*/}
            {/*        </Tooltip>*/}
            {/*    </Box>*/}
            {/*</Box>*/}

            <Box sx={{display: "flex", gap: 3, alignItems: "flex-start", flexWrap: "wrap"}}>
                {/* ── Canvas ── */}
                <Paper elevation={0} sx={{
                    background: PALETTE.surface,
                    border: `1px solid ${PALETTE.border}`,
                    borderRadius: 2,
                    overflow: "hidden",
                    flex: "0 0 auto"
                }}>
                    <svg width={SVG_W} height={SVG_H} style={{display: "block"}}>
                        {/* Room background */}
                        <rect x={0} y={0} width={SVG_W} height={SVG_H} fill={PALETTE.bg}/>
                        {/* Grid */}
                        <GridLines room={room} svgW={SVG_W} svgH={SVG_H} step={60}/>
                        {/* Zones */}
                        {zones.map((z, i) => (
                            <ZoneRect key={i} zone={z} zoneIdx={i} room={room} svgW={SVG_W} svgH={SVG_H}
                                      occupied={zoneOccupied[i]}/>
                        ))}
                        {/* Targets */}
                        {targets.map((t, i) => (
                            <TargetDot key={t.id} target={t} idx={i} room={room} svgW={SVG_W} svgH={SVG_H}/>
                        ))}
                        {/* Axes label */}
                        <text x={SVG_W / 2 + 4} y={12} fontSize="9" fill={PALETTE.muted} fontFamily="monospace">cm
                        </text>
                    </svg>
                </Paper>

                {/* ── Right panel ── */}
                {/*    <Box sx={{flex: 1, minWidth: 220}}>*/}
                {/*        /!* Target cards *!/*/}
                {/*        <Typography variant="caption" sx={{*/}
                {/*            color: PALETTE.muted,*/}
                {/*            fontFamily: "monospace",*/}
                {/*            fontWeight: 700,*/}
                {/*            mb: 1,*/}
                {/*            display: "block"*/}
                {/*        }}>*/}
                {/*            TARGETS*/}
                {/*        </Typography>*/}
                {/*        <Box sx={{display: "flex", flexDirection: "column", gap: 1, mb: 2}}>*/}
                {/*            {targets.map((t, i) => <TargetCard key={t.id} target={t} idx={i}/>)}*/}
                {/*        </Box>*/}

                {/*        /!* Zone summary *!/*/}
                {/*        <Typography variant="caption" sx={{*/}
                {/*            color: PALETTE.muted,*/}
                {/*            fontFamily: "monospace",*/}
                {/*            fontWeight: 700,*/}
                {/*            mb: 1,*/}
                {/*            display: "block"*/}
                {/*        }}>*/}
                {/*            ZONES*/}
                {/*        </Typography>*/}
                {/*        <Box sx={{display: "flex", flexDirection: "column", gap: 0.75}}>*/}
                {/*            {zones.map((z, i) => {*/}
                {/*                const c = PALETTE.zone[i];*/}
                {/*                const occ = zoneOccupied[i];*/}
                {/*                return (*/}
                {/*                    <Box key={i} sx={{*/}
                {/*                        display: "flex", alignItems: "center", gap: 1.5,*/}
                {/*                        p: 1, borderRadius: 1,*/}
                {/*                        background: occ ? c.fill : PALETTE.bg,*/}
                {/*                        border: `1px solid ${occ ? c.stroke + "66" : PALETTE.border}`,*/}
                {/*                        transition: "all 0.3s ease",*/}
                {/*                    }}>*/}
                {/*                        <Box sx={{*/}
                {/*                            width: 8,*/}
                {/*                            height: 8,*/}
                {/*                            borderRadius: "50%",*/}
                {/*                            background: occ ? c.stroke : PALETTE.border,*/}
                {/*                            transition: "background 0.3s"*/}
                {/*                        }}/>*/}
                {/*                        <Typography variant="caption" sx={{*/}
                {/*                            color: occ ? c.label : PALETTE.muted,*/}
                {/*                            fontFamily: "monospace",*/}
                {/*                            fontWeight: 600,*/}
                {/*                            flex: 1*/}
                {/*                        }}>*/}
                {/*                            {z.name}*/}
                {/*                        </Typography>*/}
                {/*                        <Typography variant="caption" sx={{*/}
                {/*                            color: occ ? c.label : PALETTE.muted,*/}
                {/*                            fontFamily: "monospace",*/}
                {/*                            fontSize: 9*/}
                {/*                        }}>*/}
                {/*                            {occ ? "OCCUPIED" : "EMPTY"}*/}
                {/*                        </Typography>*/}
                {/*                    </Box>*/}
                {/*                );*/}
                {/*            })}*/}
                {/*        </Box>*/}
                {/*    </Box>*/}
            </Box>

            {/* ── Settings panel ── */}
            {/*<Collapse in={showSettings}>*/}
            {/*    <Paper elevation={0} sx={{*/}
            {/*        mt: 3,*/}
            {/*        background: PALETTE.surface,*/}
            {/*        border: `1px solid ${PALETTE.border}`,*/}
            {/*        borderRadius: 2,*/}
            {/*        p: 2*/}
            {/*    }}>*/}
            {/*        <Typography variant="caption" sx={{*/}
            {/*            color: PALETTE.text,*/}
            {/*            fontFamily: "monospace",*/}
            {/*            fontWeight: 700,*/}
            {/*            mb: 2,*/}
            {/*            display: "block"*/}
            {/*        }}>*/}
            {/*            ⚙ CONFIGURATION*/}
            {/*        </Typography>*/}

            {/*        /!* Room size *!/*/}
            {/*        <Typography variant="caption"*/}
            {/*                    sx={{color: PALETTE.muted, fontFamily: "monospace", mb: 1, display: "block"}}>*/}
            {/*            Room size (cm) — width × depth*/}
            {/*        </Typography>*/}
            {/*        <Box sx={{display: "flex", gap: 2, mb: 2.5, flexWrap: "wrap"}}>*/}
            {/*            {["width", "depth"].map(dim => (*/}
            {/*                <Box key={dim} sx={{flex: 1, minWidth: 140}}>*/}
            {/*                    <Typography variant="caption" sx={{*/}
            {/*                        color: PALETTE.muted,*/}
            {/*                        fontFamily: "monospace",*/}
            {/*                        textTransform: "capitalize"*/}
            {/*                    }}>{dim}</Typography>*/}
            {/*                    <Slider*/}
            {/*                        value={room[dim]}*/}
            {/*                        min={200} max={1000} step={50}*/}
            {/*                        valueLabelDisplay="auto"*/}
            {/*                        onChange={(_, v) => setRoom(r => ({...r, [dim]: v}))}*/}
            {/*                        sx={{*/}
            {/*                            color: "#388BFD",*/}
            {/*                            "& .MuiSlider-valueLabel": {background: "#388BFD", fontFamily: "monospace"}*/}
            {/*                        }}*/}
            {/*                    />*/}
            {/*                </Box>*/}
            {/*            ))}*/}
            {/*        </Box>*/}

            {/*        <Divider sx={{borderColor: PALETTE.border, mb: 2}}/>*/}

            {/*        /!* Zone editors *!/*/}
            {/*        <Typography variant="caption"*/}
            {/*                    sx={{color: PALETTE.muted, fontFamily: "monospace", mb: 1, display: "block"}}>*/}
            {/*            Zone bounds — changes take effect immediately. Sync X/Y min-max values to firmware ZONES[] to*/}
            {/*            keep detection consistent.*/}
            {/*        </Typography>*/}
            {/*        <Box sx={{display: "flex", gap: 2, flexWrap: "wrap"}}>*/}
            {/*            {zones.map((z, i) => (*/}
            {/*                <Box key={i} sx={{flex: 1, minWidth: 240}}>*/}
            {/*                    <ZoneEditor zone={z} zoneIdx={i} onChange={updated => updateZone(i, updated)}/>*/}
            {/*                </Box>*/}
            {/*            ))}*/}
            {/*        </Box>*/}
            {/*    </Paper>*/}
            {/*</Collapse>*/}
        </Box>
    );
}

export function RadarRoomViewDemo() {
    const [data, setData] = useState({
        targets: [
            {id: 1, occupied: true, x: 40, y: 120, distance: 126, angle: 18, speed: 5, zone: "Zone A"},
            {id: 2, occupied: true, x: -90, y: 240, distance: 255, angle: -21, speed: -3, zone: "Zone B"},
            {id: 3, occupied: false, x: 0, y: 0, distance: 0, angle: 0, speed: 0, zone: "None"},
        ],
        zoneA_occupied: true,
        zoneB_occupied: true,
        zoneC_occupied: false,
    });

    useEffect(() => {
        let t = 0;
        const id = setInterval(() => {
            t += 0.05;
            setData({
                targets: [
                    {
                        id: 1,
                        occupied: true,
                        x: Math.sin(t) * 100,
                        y: 100 + Math.cos(t * 0.7) * 40,
                        distance: 120,
                        angle: 20,
                        speed: 4,
                        zone: "Zone A"
                    },
                    {
                        id: 2,
                        occupied: true,
                        x: Math.cos(t * 0.8) * 80,
                        y: 220 + Math.sin(t * 0.5) * 50,
                        distance: 240,
                        angle: -18,
                        speed: -2,
                        zone: "Zone B"
                    },
                    {
                        id: 3,
                        occupied: Math.sin(t * 0.3) > 0,
                        x: 60,
                        y: 360,
                        distance: 364,
                        angle: 9,
                        speed: 1,
                        zone: "Zone C"
                    },
                ],
                zoneA_occupied: true,
                zoneB_occupied: true,
                zoneC_occupied: Math.sin(t * 0.3) > 0,
            });
        }, 100);
        return () => clearInterval(id);
    }, []);

    return <RadarRoomView sensorData={data}/>;
}