// components/welcome/SystemFlow.jsx
// Animated React Flow diagram of how Automata's pieces talk to each other.
// Click (or Enter on) a node to read about it; its edges light up.

import React, {memo, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    BaseEdge,
    EdgeLabelRenderer,
    getBezierPath,
    Handle,
    Position,
    ReactFlow,
    ReactFlowProvider,
    useReactFlow,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {Box, Chip, GlobalStyles, Stack, Typography} from "@mui/material";
import {AnimatePresence, motion} from "motion/react";
import {useIsMobile} from "../../utils/useIsMobile.jsx";
import {FLOW_EDGES, FLOW_LAYOUTS, FLOW_NODES} from "./content.jsx";

const ACCENT = "#f0dc7a";          // same yellow used by the drawer / selected nav items
const ACCENT_DIM = "rgb(162 157 81)"; // theme.palette.secondary.main
const IDLE_LINE = "rgba(255,255,255,0.22)";

// ── Handles ─────────────────────────────────────────────────────────────────
// Every node exposes invisible source+target handles on all four sides, at the
// centre ("") or 30% / 70% along the side ("a" / "b"), so each layout can pick
// the exact attachment points it needs. Ids look like "sra" = source, right, 30%.
const SIDES = [
    ["r", Position.Right],
    ["l", Position.Left],
    ["t", Position.Top],
    ["b", Position.Bottom],
];
const SLOTS = [["", "50%"], ["a", "30%"], ["b", "70%"]];

const hiddenHandle = {opacity: 0, pointerEvents: "none", width: 1, height: 1, minWidth: 0, minHeight: 0};

function AllHandles() {
    return SIDES.flatMap(([side, position]) =>
        SLOTS.flatMap(([slot, pct]) => {
            const style = position === Position.Left || position === Position.Right
                ? {...hiddenHandle, top: pct}
                : {...hiddenHandle, left: pct};
            return [
                <Handle key={`s${side}${slot}`} id={`s${side}${slot}`} type="source" position={position} style={style}/>,
                <Handle key={`t${side}${slot}`} id={`t${side}${slot}`} type="target" position={position} style={style}/>,
            ];
        })
    );
}

// ── Custom node ─────────────────────────────────────────────────────────────
const SystemNode = memo(function SystemNode({id, data}) {
    const {icon: Icon, title, sub, selected, dimmed, width, compact, onSelect} = data;
    return (
        <Box
            role="button"
            tabIndex={0}
            aria-pressed={selected}
            aria-label={`${title}: ${sub}`}
            onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    onSelect(id);
                }
            }}
            sx={{
                width,
                minHeight: compact ? 48 : undefined,
                display: "flex",
                alignItems: "center",
                gap: compact ? 1 : 1.25,
                p: compact ? 1 : 1.25,
                cursor: "pointer",
                borderRadius: "14px",
                color: "text.primary",
                background: selected
                    ? "linear-gradient(135deg, rgba(240,220,122,0.16), rgba(22,22,22,0.92))"
                    : "rgba(22,22,22,0.88)",
                border: "1px solid",
                borderColor: selected ? ACCENT : "rgba(255,255,255,0.14)",
                boxShadow: selected ? "0 0 0 3px rgba(240,220,122,0.12), 0 10px 30px rgba(0,0,0,0.5)" : "0 6px 20px rgba(0,0,0,0.4)",
                backdropFilter: "blur(6px)",
                opacity: dimmed ? 0.78 : 1,
                transition: "border-color .2s, box-shadow .2s, opacity .2s, transform .2s",
                "&:hover": {borderColor: "rgba(240,220,122,0.65)", transform: "translateY(-2px)"},
                "&:focus-visible": {outline: `2px solid ${ACCENT}`, outlineOffset: 2},
            }}
        >
            <Box sx={{
                flexShrink: 0, width: compact ? 28 : 36, height: compact ? 28 : 36, borderRadius: "10px", display: "grid", placeItems: "center",
                color: selected ? "#161616" : ACCENT,
                background: selected ? ACCENT : "rgba(240,220,122,0.10)",
                transition: "all .2s",
            }}>
                <Icon sx={{fontSize: compact ? 16 : 20}}/>
            </Box>
            <Box sx={{minWidth: 0}}>
                <Typography noWrap={!compact} sx={{fontSize: compact ? "0.75rem" : "0.85rem", fontWeight: 600, lineHeight: 1.2}}>{title}</Typography>
                {!compact && (
                    <Typography noWrap sx={{fontSize: "0.68rem", color: "text.secondary", lineHeight: 1.3}}>{sub}</Typography>
                )}
            </Box>
            <AllHandles/>
        </Box>
    );
});

// ── Custom edge: dashed line + travelling pulses + topic label ──────────────
const PulseEdge = memo(function PulseEdge({
                                              id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data,
                                          }) {
    const [path, labelX, labelY] = getBezierPath({
        sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, curvature: 0.3,
    });
    const active = data?.active;
    const dimmed = data?.dimmed;
    const color = active ? ACCENT : ACCENT_DIM;

    return (
        <>
            <BaseEdge
                id={id}
                path={path}
                style={{
                    stroke: active ? ACCENT : IDLE_LINE,
                    strokeWidth: active ? 2 : 1.4,
                    opacity: dimmed ? 0.35 : 1,
                    strokeDasharray: "5 6",
                    transition: "stroke .2s, stroke-width .2s, opacity .2s",
                }}
                className="automata-flow-dash"
            />
            {/* Two pulses, half a cycle apart, so traffic looks continuous */}
            {!dimmed && [0, 1].map((i) => (
                <circle key={i} r={active ? 3.8 : 3} fill={color} className="automata-flow-pulse" opacity="0"
                        style={{filter: `drop-shadow(0 0 4px ${color})`}}>
                    <set attributeName="opacity" to="1" begin={`${i * 1.5}s`}/>
                    <animateMotion
                        dur={data?.dur || "3s"}
                        begin={`${i * 1.5}s`}
                        repeatCount="indefinite"
                        path={path}
                        calcMode="linear"
                    />
                </circle>
            ))}
            {!(data?.compact && !active) && <EdgeLabelRenderer>
                <Box
                    sx={{
                        position: "absolute",
                        transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
                        pointerEvents: "none",
                        px: 0.75, py: 0.1,
                        borderRadius: "999px",
                        fontSize: "0.62rem",
                        fontFamily: "monospace",
                        letterSpacing: ".02em",
                        color: active ? "#161616" : "text.secondary",
                        background: active ? ACCENT : "rgba(22,22,22,0.92)",
                        border: "1px solid",
                        borderColor: active ? ACCENT : "rgba(255,255,255,0.14)",
                        opacity: dimmed ? 0.4 : 1,
                        transition: "all .2s",
                    }}
                >
                    {data?.label}
                </Box>
            </EdgeLabelRenderer>}
        </>
    );
});

const nodeTypes = {system: SystemNode};
const edgeTypes = {pulse: PulseEdge};

// ── Keeps the diagram fitted when its container is resized ──────────────────
function AutoFit({containerRef}) {
    const {fitView} = useReactFlow();
    useEffect(() => {
        const el = containerRef.current;
        if (!el || typeof ResizeObserver === "undefined") return undefined;
        let raf = 0;
        const ro = new ResizeObserver(() => {
            cancelAnimationFrame(raf);
            raf = requestAnimationFrame(() => fitView({padding: 0.08, duration: 0}));
        });
        ro.observe(el);
        return () => {
            cancelAnimationFrame(raf);
            ro.disconnect();
        };
    }, [containerRef, fitView]);
    return null;
}

// ── Public component ────────────────────────────────────────────────────────
function Diagram() {
    const isMobile = useIsMobile("sm");
    const layoutKey = isMobile ? "mobile" : "desktop";
    const layout = FLOW_LAYOUTS[layoutKey];
    const [selectedId, setSelectedId] = useState("backend");
    const containerRef = useRef(null);

    const onSelect = useCallback((id) => setSelectedId(id), []);

    const nodes = useMemo(() => Object.keys(FLOW_NODES).map((id) => {
        const connected = FLOW_EDGES.some((e) => (e.source === id || e.target === id)
            && (e.source === selectedId || e.target === selectedId));
        return {
            id,
            type: "system",
            position: layout.positions[id],
            draggable: false,
            data: {
                ...FLOW_NODES[id],
                width: layout.nodeWidth,
                compact: !!layout.compact,
                selected: id === selectedId,
                dimmed: !connected,
                onSelect,
            },
        };
    }), [layout, selectedId, onSelect]);

    const edges = useMemo(() => FLOW_EDGES.map((e, i) => {
        const active = e.source === selectedId || e.target === selectedId;
        return {
            id: e.id,
            source: e.source,
            target: e.target,
            sourceHandle: e[layoutKey].sh,
            targetHandle: e[layoutKey].th,
            type: "pulse",
            selectable: false,
            focusable: false,
            data: {label: e.label, compact: !!layout.compact, active, dimmed: !active, dur: `${2.6 + (i % 3) * 0.4}s`},
        };
    }), [layoutKey, layout, selectedId]);

    const selected = FLOW_NODES[selectedId];

    return (
        <Box>
            <GlobalStyles styles={{
                "@keyframes automataDash": {to: {strokeDashoffset: -22}},
                ".automata-flow-dash": {animation: "automataDash 1.4s linear infinite"},
                "@media (prefers-reduced-motion: reduce)": {
                    ".automata-flow-dash": {animation: "none"},
                    ".automata-flow-pulse": {display: "none"},
                },
                // Let the page scroll when a finger or wheel is over the diagram
                ".automata-flow .react-flow__pane": {cursor: "default"},
                ".automata-flow .react-flow__attribution": {background: "transparent", color: "rgba(255,255,255,0.3)"},
                ".automata-flow .react-flow__node": {cursor: "pointer"},
            }}/>

            <Box
                ref={containerRef}
                className="automata-flow"
                sx={{
                    height: layout.height,
                    borderRadius: "18px",
                    overflow: "hidden",
                    border: "1px dashed rgba(255,255,255,0.18)",
                    background:
                        "radial-gradient(ellipse at 50% 0%, rgba(240,220,122,0.07), transparent 60%), rgba(22,22,22,0.55)",
                    backdropFilter: "blur(4px)",
                }}
            >
                <ReactFlow
                    key={layoutKey}
                    nodes={nodes}
                    edges={edges}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    colorMode="dark"
                    fitView
                    fitViewOptions={{padding: 0.08}}
                    minZoom={0.2}
                    nodesDraggable={false}
                    nodesConnectable={false}
                    edgesFocusable={false}
                    elementsSelectable={false}
                    onNodeClick={(_, node) => onSelect(node.id)}
                    zoomOnScroll={false}
                    zoomOnPinch={false}
                    zoomOnDoubleClick={false}
                    panOnDrag={false}
                    panOnScroll={false}
                    preventScrolling={false}
                    proOptions={{hideAttribution: false}}
                />
                <AutoFit containerRef={containerRef}/>
            </Box>

            <Box
                sx={{
                    mt: 2, p: {xs: 2, sm: 2.5}, borderRadius: "16px",
                    background: "rgba(22,22,22,0.72)", border: "1px solid rgba(255,255,255,0.10)",
                    backdropFilter: "blur(8px)", minHeight: 132,
                }}
                aria-live="polite"
            >
                <AnimatePresence mode="wait">
                    <motion.div
                        key={selectedId}
                        initial={{opacity: 0, y: 8}}
                        animate={{opacity: 1, y: 0}}
                        exit={{opacity: 0, y: -8}}
                        transition={{duration: 0.18}}
                    >
                        <Stack direction="row" spacing={1.5} alignItems="center" sx={{mb: 1}}>
                            <selected.icon sx={{color: ACCENT}}/>
                            <Typography variant="h6" sx={{fontSize: "1.05rem", fontWeight: 600}}>
                                {selected.title}
                            </Typography>
                            <Typography variant="body2" sx={{color: "text.secondary"}}>{selected.sub}</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{color: "#cfcfcf", lineHeight: 1.65, maxWidth: 820}}>
                            {selected.description}
                        </Typography>
                        <Stack direction="row" useFlexGap flexWrap="wrap" spacing={0} sx={{gap: 0.75, mt: 1.5}}>
                            {selected.tags.map((t) => (
                                <Chip key={t} label={t} size="small" variant="outlined"
                                      sx={{
                                          fontFamily: "monospace", fontSize: "0.7rem",
                                          borderColor: "rgba(240,220,122,0.35)", color: ACCENT,
                                      }}/>
                            ))}
                        </Stack>
                    </motion.div>
                </AnimatePresence>
            </Box>
        </Box>
    );
}

export default function SystemFlow() {
    // Own provider so this diagram never shares a store with the app's other flows
    return (
        <ReactFlowProvider>
            <Diagram/>
        </ReactFlowProvider>
    );
}
