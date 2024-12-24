import React, { useCallback, useEffect, useState, useMemo } from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { getDevices } from "../../services/apis.jsx";
import useWebSocket from "../../services/useWebSocket.jsx";
import { AnimatedSVGEdge } from "./AnimatedSVGEdge.jsx";
import { Device, MainNode } from "./Nodes.jsx";
import { createEdges, createNodes } from "./EdgeNode.jsx";
import { Backdrop, CircularProgress, Fab } from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import NodeInspector from "./NodeInspector.jsx";

const edgeTypes = { animatedSvg: AnimatedSVGEdge };
const nodeTypes = {
    deviceNode: Device,
    mainNode: MainNode
};

const DeviceNodes = () => {
    const [openBackdrop, setOpenBackdrop] = useState(false);
    const { messages } = useWebSocket('/topic/data');
    const { messages: data } = useWebSocket('/topic/devices');
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [editUi, setEditUi] = useState(false);

    const handleEdit = useCallback(() => {
        setEditUi(prev => !prev);
    }, []);

    useEffect(() => {
        setNodes((nds) =>
            nds.map((node) => {
                if (node.id === messages.deviceId) {
                    let dt = node.data.value;
                    if (messages.deviceConfig) {
                        dt = messages.deviceConfig;
                    }
                    return {
                        ...node,
                        data: { value: dt, live: messages.data }
                    };
                }
                return node;
            }),
        );
    }, [messages, setNodes]);

    useEffect(() => {
        setOpenBackdrop(true);
        const fetchData = async () => {
            try {
                const devices = await getDevices();
                const dev = devices.filter((d) => d.showInDashboard === true);
                setNodes(createNodes(dev, []));
                setEdges(createEdges(dev, []));
                setOpenBackdrop(false);
            } catch (err) {
                setOpenBackdrop(false);
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, [data, setNodes, setEdges]);

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [setNodes],
    );
    const onEdgesChange = useCallback(
        (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
        [setEdges],
    );
    const onConnect = useCallback(
        (connection) => setEdges((eds) => addEdge(connection, eds)),
        [setEdges],
    );

    const defaultViewport = useMemo(() => ({ x: 0, y: 0, zoom: 0.65 }), []);

    return (
        <div style={{ height: '92dvh' }}>
            <ReactFlow
                colorMode="light"
                nodes={nodes}
                edges={edges}
                edgeTypes={edgeTypes}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                defaultViewport={defaultViewport}
                nodeTypes={nodeTypes}
            >
                <Fab color="primary" aria-label="add" onClick={handleEdit} style={{ position: 'absolute', bottom: '50px', right: '50px' }}>
                    <EditIcon />
                </Fab>
                {editUi && <NodeInspector />}
            </ReactFlow>
            <Backdrop
                sx={(theme) => ({ color: '#fff', zIndex: theme.zIndex.drawer + 1 })}
                open={openBackdrop}
            >
                <CircularProgress color="inherit" />
            </Backdrop>
        </div>
    );
};

export default React.memo(DeviceNodes);