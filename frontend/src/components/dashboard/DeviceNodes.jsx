import React, {useCallback, useEffect, useState, useMemo} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges, Panel,
    ReactFlow, ReactFlowProvider,
    useEdgesState,
    useNodesState, useReactFlow
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {getDevices, getMainNodePos} from "../../services/apis.jsx";
// import useWebSocket from "../../services/useWebSocket.jsx";
import {WebSocketProvider} from '../../services/WebSocketProvider.jsx';
import {AnimatedSVGEdge} from "./AnimatedSVGEdge.jsx";
import {Device, MainNode} from "./Nodes.jsx";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import {Backdrop, Card, CircularProgress, Fab} from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import NodeInspector from "./NodeInspector.jsx";
import Typography from "@mui/material/Typography";

const edgeTypes = {animatedSvg: AnimatedSVGEdge};
const nodeTypes = {
    deviceNode: Device,
    mainNode: MainNode
};

const DeviceNodes = () => {
    const [openBackdrop, setOpenBackdrop] = useState(false);
    // const {messages} = useWebSocket('/topic/data');
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [editUi, setEditUi] = useState(false);
    const { fitView } = useReactFlow();
    const handleEdit = useCallback(() => {
        setEditUi(prev => !prev);
    }, []);

    // useEffect(() => {
    //     setNodes((nds) =>
    //         nds.map((node) => {
    //             if (node.id === messages.deviceId) {
    //                 let dt = node.data.value;
    //                 if (messages.deviceConfig) {
    //                     dt = messages.deviceConfig;
    //                 }
    //                 return {
    //                     ...node,
    //                     data: {value: dt, live: messages.data}
    //                 };
    //             }
    //             return node;
    //         }),
    //     );
    // }, [messages, setNodes]);

    useEffect(() => {
        setOpenBackdrop(true);
        const fetchData = async () => {
            try {
                const pos = await getMainNodePos();
                const devices = await getDevices();
                const dev = devices.filter((d) => d.showInDashboard === true);
                setNodes(createNodes(dev, [], pos.x, pos.y));
                setEdges(createEdges(dev, []));
                setOpenBackdrop(false);
            } catch (err) {
                setOpenBackdrop(false);
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, [setNodes, setEdges]);

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

    const defaultViewport = useMemo(() => ({x: 0, y: 70, zoom: 0.65}), []);

    const handleNodeClick = useCallback(
        (_, node) => {
            fitView({ nodes: [node], duration: 150, maxZoom:1 });
        },
            [fitView],
    );
    return (
        <div style={{height: '100dvh'}}>
                <ReactFlow
                    colorMode="dark"
                    nodes={nodes}
                    edges={edges}
                    edgeTypes={edgeTypes}
                    // nodesDraggable={editUi}
                    // onNodeClick={handleNodeClick}
                    // fitView
                    // fitViewOptions={{ nodes: [{ id: '' }] }}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    defaultViewport={defaultViewport}
                    nodeTypes={nodeTypes}
                >
                    {editUi && (
                        <Panel position="bottom-center" style={{marginBottom: '10px'}}>
                            <Typography variant="body" component="div">
                                Click on any node and use the arrow keys or drag with mouse to move the nodes.
                            </Typography>
                        </Panel>
                    )}
                    <Fab color="primary" aria-label="add" onClick={handleEdit}
                         style={{position: 'absolute', bottom: '50px', right: '14px'}}>
                        <EditIcon/>
                    </Fab>
                    {editUi && <NodeInspector/>}
                </ReactFlow>
            <Backdrop
                sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                open={openBackdrop}
            >
                <CircularProgress color="inherit"/>
            </Backdrop>
        </div>
    );
};

const Dashboard = () => {

    return (
        <WebSocketProvider>
            <ReactFlowProvider>
                <DeviceNodes/>
            </ReactFlowProvider>
        </WebSocketProvider>

    )
}

export default React.memo(Dashboard);