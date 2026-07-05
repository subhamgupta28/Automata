import React, {useCallback, useState} from 'react';
import {Handle, Position, useReactFlow} from '@xyflow/react';
import {Box, Stack, Tooltip, Typography} from '@mui/material';

// ─── Theme tokens (mirrors ActionBoard.jsx) ───────────────────────────────────
const T = {
    yellow: '#ffd821',
    blue: '#74b9ff',
    green: '#00e5a0',
    red: '#ff4757',
    orange: '#ff6b35',
    purple: '#a78bfa',
    textDim: '#718096',
    mono: '"JetBrains Mono", monospace',
};

// ─── Node-type metadata ───────────────────────────────────────────────────────
const NODE_META = {
    condition: {
        label: 'Condition',
        icon: '◆',
        color: T.yellow,
        desc: 'Filter or gate the flow',
        handle: 'in:condition',   // prefix for the target handle id
    },
    action: {
        label: 'Action',
        icon: '▶',
        color: T.blue,
        desc: 'Send a command to a device',
        handle: 'action:in',
    },
};

// ─── Suggestion chips shown inside the placeholder ───────────────────────────
// parentType → which node types to suggest next
const SUGGESTIONS = {
    trigger: ['condition'],
    condition: ['action'],
    action: [],                      // actions are leaf nodes; no suggestions
};

// Leaf types never get a follow-on placeholder
const LEAF_TYPES = new Set(['action', 'valueReader']);

// The outgoing source handle for each real node type (used to connect → next placeholder)
const OUT_HANDLE = (type, nodeId) => ({
    condition: `out:cond-positive:${nodeId}`,
})[type] ?? null;

// ─── Counter for new node ids ─────────────────────────────────────────────────
let _placeholderSeq = 0;
const nextId = (type) => `node_${type}_ph_${_placeholderSeq++}`;

// ─── Keyframe injected once ───────────────────────────────────────────────────
if (typeof document !== 'undefined' && !document.getElementById('ph-node-styles')) {
    const style = document.createElement('style');
    style.id = 'ph-node-styles';
    style.textContent = `
        @keyframes ph-pulse {
            0%, 100% { opacity: 0.55; }
            50%       { opacity: 1;    }
        }
        @keyframes ph-spin {
            from { transform: rotate(0deg);   }
            to   { transform: rotate(360deg); }
        }
        .ph-card {
            animation: ph-pulse 2.4s ease-in-out infinite;
        }
        .ph-card:hover {
            animation: none;
            opacity: 1 !important;
        }
        .ph-chip {
            transition: background 0.15s, border-color 0.15s, transform 0.12s;
            cursor: pointer;
        }
        .ph-chip:hover {
            transform: translateY(-2px);
        }
        .ph-chip:active {
            transform: translateY(0);
        }
    `;
    document.head.appendChild(style);
}

// ─── SuggestionChip ───────────────────────────────────────────────────────────
function SuggestionChip({nodeType, onClick}) {
    const meta = NODE_META[nodeType];
    const [hovered, setHovered] = useState(false);

    return (
        <Tooltip title={meta.desc} arrow placement="top">
            <Box
                className="ph-chip"
                onClick={() => onClick(nodeType)}
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    px: '10px',
                    py: '6px',
                    borderRadius: '20px',
                    border: `1.5px solid ${hovered ? meta.color : meta.color + '55'}`,
                    background: hovered ? `${meta.color}18` : `${meta.color}08`,
                    userSelect: 'none',
                }}
            >
                <Typography sx={{
                    fontSize: '13px',
                    color: meta.color,
                    lineHeight: 1,
                    fontFamily: T.mono,
                }}>
                    {meta.icon}
                </Typography>
                <Typography sx={{
                    fontSize: '10px',
                    fontWeight: 700,
                    color: meta.color,
                    fontFamily: T.mono,
                    textTransform: 'uppercase',
                    letterSpacing: '0.5px',
                }}>
                    {meta.label}
                </Typography>
            </Box>
        </Tooltip>
    );
}

// ─── PlaceholderNode ──────────────────────────────────────────────────────────
export const PlaceholderNode = ({id, data, isConnectable}) => {
    const {setNodes, setEdges, getNode} = useReactFlow();

    // data shape expected:
    //   parentType  — 'trigger' | 'condition' | 'and' | 'or'
    //   parentId    — id of the node this placeholder is hanging off
    //   sourceHandle — the full sourceHandle string from the parent → placeholder edge
    const parentType = data?.parentType ?? 'trigger';
    const parentId = data?.parentId ?? null;
    const sourceHandle = data?.sourceHandle ?? null;

    const suggestions = SUGGESTIONS[parentType] ?? [];

    // Replace this placeholder with a real node of the chosen type,
    // reconnect the parent edge, and spawn the next placeholder — all atomically.
    const handlePick = useCallback((chosenType) => {
        const placeholder = getNode(id);
        if (!placeholder) return;

        const newId = nextId(chosenType);
        const pos = {...placeholder.position};

        // ── 1. The real node lands exactly where the placeholder was ──────────
        const meta = NODE_META[chosenType];
        const targetHandle = meta ? `${meta.handle}:${newId}` : undefined;
        const isNeg = sourceHandle?.includes('cond-negative');
        const edgeColor = isNeg ? '#f44336' : '#4caf50';

        const realNode = {
            id: newId,
            type: chosenType,
            position: pos,
            data: {value: {isNewNode: true, name: chosenType}},
        };

        // ── 2. Optionally build the next placeholder ──────────────────────────
        const nextOutHandle = OUT_HANDLE(chosenType, newId);
        const isLeaf = LEAF_TYPES.has(chosenType) || !nextOutHandle;
        const nextPhId = isLeaf ? null : `ph_${newId}`;
        const nextPhPosition = isLeaf ? null : {x: pos.x + 300, y: pos.y};

        const nextPh = isLeaf ? null : {
            id: nextPhId,
            type: 'placeholder',
            position: nextPhPosition,
            data: {
                parentType: chosenType,
                parentId: newId,
                sourceHandle: nextOutHandle,
            },
        };

        // ── 3. Atomic node update ─────────────────────────────────────────────
        setNodes(nds => {
            const without = nds.filter(n => n.id !== id);        // drop this placeholder
            return nextPh ? [...without, realNode, nextPh] : [...without, realNode];
        });

        // ── 4. Atomic edge update ─────────────────────────────────────────────
        setEdges(eds => {
            // Drop the edge that pointed to THIS placeholder
            const base = eds.filter(e => e.target !== id);

            const edges = [...base];

            // Re-connect parent → real node
            if (parentId) {
                edges.push({
                    id: `edge_${parentId}_${newId}`,
                    source: parentId,
                    target: newId,
                    sourceHandle: sourceHandle ?? undefined,
                    targetHandle: targetHandle,
                    type: 'custom-edge',
                    animated: false,
                    data: {color: edgeColor},
                });
            }

            // Connect real node → next placeholder
            if (nextPh) {
                edges.push({
                    id: `edge_${newId}_${nextPhId}`,
                    source: newId,
                    target: nextPhId,
                    sourceHandle: nextOutHandle,
                    targetHandle: `ph:in:${nextPhId}`,
                    type: 'custom-edge',
                    animated: true,
                    data: {color: '#718096'},
                });
            }

            return edges;
        });
    }, [id, parentId, sourceHandle, getNode, setNodes, setEdges]);

    return (
        <>
            {/* Receive connection from parent */}
            <Handle
                type="target"
                position={Position.Left}
                id={`ph:in:${id}`}
                isConnectable={isConnectable}
                style={{visibility: 'hidden'}}
            />

            <Box
                className="ph-card nodrag"
                sx={{
                    width: '240px',
                    borderRadius: '12px',
                    border: `2px dashed ${T.textDim}55`,
                    background: 'rgba(255,255,255,0.02)',
                    backdropFilter: 'blur(6px)',
                    p: '14px 16px',
                    pointerEvents: suggestions.length === 0 ? 'none' : 'auto',
                }}
            >
                {/* Header */}
                <Stack direction="row" alignItems="center" spacing={1} sx={{mb: '10px'}}>
                    {/* Animated ring */}
                    <Box sx={{
                        width: '22px',
                        height: '22px',
                        borderRadius: '50%',
                        border: `2px dashed ${T.textDim}66`,
                        flexShrink: 0,
                        animation: 'ph-spin 4s linear infinite',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}>
                        <Box sx={{
                            width: '6px', height: '6px',
                            borderRadius: '50%',
                            background: T.textDim,
                        }}/>
                    </Box>

                    <Box>
                        <Typography sx={{
                            fontSize: '10px',
                            fontWeight: 700,
                            color: T.textDim,
                            fontFamily: T.mono,
                            textTransform: 'uppercase',
                            letterSpacing: '0.8px',
                        }}>
                            What's next?
                        </Typography>
                        <Typography sx={{
                            fontSize: '9px',
                            color: T.textDim + '99',
                            fontFamily: T.mono,
                            mt: '1px',
                        }}>
                            Click to add a node here
                        </Typography>
                    </Box>
                </Stack>

                {/* Suggestion chips */}
                {suggestions.length > 0 ? (
                    <Stack
                        direction="row"
                        flexWrap="wrap"
                        gap="6px"
                    >
                        {suggestions.map(s => (
                            <SuggestionChip
                                key={s}
                                nodeType={s}
                                onClick={handlePick}
                            />
                        ))}
                    </Stack>
                ) : (
                    <Typography sx={{
                        fontSize: '10px',
                        color: T.textDim + '77',
                        fontFamily: T.mono,
                        textAlign: 'center',
                        pt: '4px',
                    }}>
                        — leaf node —
                    </Typography>
                )}
            </Box>

            {/* Allow further chaining from placeholder (e.g. if user manually connects) */}
            <Handle
                type="source"
                position={Position.Right}
                id={`ph:out:${id}`}
                isConnectable={isConnectable}
                style={{visibility: 'hidden'}}
            />
        </>
    );
};