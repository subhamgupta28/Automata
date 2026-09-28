import {useState} from "react";
import {Box} from "@mui/material";

// ─── Palette ───────────────────────────────────────────────────────────────────
const P = {
    bg: "transparent",
    surface: "rgba(22,27,34,0.6)",
    border: "#30363D",
    grid: "#1C2128",
    text: "#E6EDF3",
    muted: "#8B949E",
    zone: [
        {fill: "rgba(56,139,253,0.08)", stroke: "#388BFD", label: "#388BFD"},
        {fill: "rgba(63,185,80,0.08)", stroke: "#3FB950", label: "#3FB950"},
        {fill: "rgba(210,153,34,0.08)", stroke: "#D2991F", label: "#D2991F"},
    ],
    target: ["#F78166", "#79C0FF", "#D2A8FF"],
};

// ─── Defaults ─────────────────────────────────────────────────────────────────
const DEFAULT_ROOM = {width: 600, depth: 600};
const DEFAULT_ZONES = [
    {name: "Zone A", xMin: -180, xMax: 180, yMin: 35, yMax: 180},
    {name: "Zone B", xMin: -180, xMax: 180, yMin: 185, yMax: 320},
    {name: "Zone C", xMin: -180, xMax: 180, yMin: 325, yMax: 480},
];

// ─── Coordinate transform ──────────────────────────────────────────────────────
function toSVG(x_cm, y_cm, room, svgW, svgH) {
    const scaleX = svgW / room.width;
    const scaleY = svgH / room.depth;
    return {px: svgW / 2 + x_cm * scaleX, py: svgH - y_cm * scaleY};
}

// ─── Zone rect ────────────────────────────────────────────────────────────────
function ZoneRect({zone, zoneIdx, room, svgW, svgH, occupied}) {
    const tl = toSVG(zone.xMin, zone.yMax, room, svgW, svgH);
    const br = toSVG(zone.xMax, zone.yMin, room, svgW, svgH);
    const w = br.px - tl.px;
    const h = br.py - tl.py;
    const c = P.zone[zoneIdx];
    return (
        <g>
            <rect
                x={tl.px} y={tl.py} width={w} height={h}
                fill={occupied ? c.stroke + "22" : c.fill}
                stroke={c.stroke} strokeWidth={occupied ? 1.5 : 0.75}
                strokeDasharray={occupied ? "0" : "4 3"} rx={2}
                style={{transition: "all 0.35s ease"}}
            />
            <text x={tl.px + w / 2} y={tl.py + 13} textAnchor="middle"
                  fontSize="9" fill={c.label} fontFamily="monospace" fontWeight="600" opacity={0.85}>
                {zone.name}
            </text>
            {occupied && (
                <text x={tl.px + w / 2} y={tl.py + 24} textAnchor="middle"
                      fontSize="7.5" fill={c.label} fontFamily="monospace" opacity={0.65}>
                    ● OCC
                </text>
            )}
        </g>
    );
}

// ─── Target dot ───────────────────────────────────────────────────────────────
function TargetDot({target, idx, room, svgW, svgH}) {
    if (!target.occupied) return null;
    const {px, py} = toSVG(target.x, target.y, room, svgW, svgH);
    const col = P.target[idx];
    return (
        <g>
            <circle cx={px} cy={py} r={14} fill={col + "18"} stroke={col + "44"} strokeWidth={1}>
                <animate attributeName="r" values="9;16;9" dur="2.2s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values="0.5;0;0.5" dur="2.2s" repeatCount="indefinite"/>
            </circle>
            <circle cx={px} cy={py} r={5} fill={col} stroke={P.bg} strokeWidth={1.5}/>
            <text x={px} y={py - 9} textAnchor="middle" fontSize="8.5" fill={col}
                  fontFamily="monospace" fontWeight="700">T{target.id}</text>
            {target.zone && target.zone !== "None" && (
                <text x={px} y={py + 16} textAnchor="middle" fontSize="7" fill={P.muted} fontFamily="monospace">
                    {target.zone}
                </text>
            )}
        </g>
    );
}

// ─── Grid ─────────────────────────────────────────────────────────────────────
function GridLines({room, svgW, svgH, step = 100}) {
    const lines = [];
    for (let x = -room.width / 2; x <= room.width / 2; x += step) {
        const {px} = toSVG(x, 0, room, svgW, svgH);
        lines.push(<line key={`vx${x}`} x1={px} y1={0} x2={px} y2={svgH} stroke={P.grid} strokeWidth={0.4}/>);
        if (x !== 0)
            lines.push(<text key={`lx${x}`} x={px} y={svgH - 2} textAnchor="middle" fontSize="6.5" fill={P.muted}
                             fontFamily="monospace">{x}</text>);
    }
    for (let y = 0; y <= room.depth; y += step) {
        const {py} = toSVG(0, y, room, svgW, svgH);
        lines.push(<line key={`vy${y}`} x1={0} y1={py} x2={svgW} y2={py} stroke={P.grid} strokeWidth={0.4}/>);
        if (y > 0)
            lines.push(<text key={`ly${y}`} x={3} y={py + 3} fontSize="6.5" fill={P.muted}
                             fontFamily="monospace">{y}</text>);
    }
    const origin = toSVG(0, 0, room, svgW, svgH);
    lines.push(
        <polygon key="sensor"
                 points={`${origin.px},${origin.py - 8} ${origin.px - 5},${origin.py + 3} ${origin.px + 5},${origin.py + 3}`}
                 fill="#58A6FF" opacity={0.8}
        />
    );
    lines.push(
        <text key="sensor-label" x={origin.px + 8} y={origin.py + 4} fontSize="7.5" fill="#58A6FF"
              fontFamily="monospace">sensor</text>
    );
    return <>{lines}</>;
}

// ─── Main ─────────────────────────────────────────────────────────────────────
export default function RadarRoomView({sensorData}) {
    const SVG_W = 220;
    const SVG_H = 200;

    const targets = (sensorData?.targets ?? []).map(t => ({
        id: t.id, occupied: !!t.occupied,
        x: t.x ?? 0, y: t.y ?? 0,
        distance: t.distance ?? 0, angle: t.angle ?? 0,
        speed: t.speed ?? 0, zone: t.zone ?? "None",
    }));
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

    const [zones] = useState(DEFAULT_ZONES);
    const [room] = useState(DEFAULT_ROOM);

    const totalActive = targets.filter(t => t.occupied).length;

    return (
        <Box sx={{
            fontFamily: "monospace",
            p: "10px",
            display: "inline-flex",
            flexDirection: "column",
            gap: "8px",
            minWidth: 0
        }}>
            {/* ── Main row: map + targets ── */}
            <Box sx={{display: "flex", gap: "8px", alignItems: "flex-start"}}>

                {/* Map canvas */}
                <Box sx={{
                    border: `1px solid ${P.border}`,
                    borderRadius: "8px",
                    overflow: "hidden",
                    flexShrink: 0,
                    lineHeight: 0,
                }}>
                    <svg width={SVG_W} height={SVG_H} style={{display: "block"}}>
                        <rect x={0} y={0} width={SVG_W} height={SVG_H} fill="rgba(13,17,23,0)"/>
                        <GridLines room={room} svgW={SVG_W} svgH={SVG_H} step={60}/>
                        {zones.map((z, i) => (
                            <ZoneRect key={i} zone={z} zoneIdx={i} room={room} svgW={SVG_W} svgH={SVG_H}
                                      occupied={zoneOccupied[i]}/>
                        ))}
                        {targets.map((t, i) => (
                            <TargetDot key={t.id} target={t} idx={i} room={room} svgW={SVG_W} svgH={SVG_H}/>
                        ))}
                        <text x={SVG_W - 4} y={10} textAnchor="end" fontSize="6.5" fill={P.muted}
                              fontFamily="monospace">cm
                        </text>
                    </svg>
                </Box>
            </Box>

        </Box>
    );
}