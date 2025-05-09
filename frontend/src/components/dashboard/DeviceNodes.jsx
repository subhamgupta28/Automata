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
import {getDashboardDevices, getMainNodePos, rebootAllDevices} from "../../services/apis.jsx";
// import useWebSocket from "../../services/useWebSocket.jsx";
import {DeviceDataProvider} from '../../services/DeviceDataProvider.jsx';
import {AnimatedSVGEdge} from "./AnimatedSVGEdge.jsx";
import {Device, MainNode} from "./Nodes.jsx";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import {Backdrop, Button, Card, CircularProgress, Fab} from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import LayersIcon from '@mui/icons-material/Layers';
import NodeInspector from "./NodeInspector.jsx";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";

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
    const [actionMenu, setActionMenu] = useState(false);
    const {fitView} = useReactFlow();
    const handleEdit = useCallback(() => {
        setEditUi(prev => !prev);
    }, []);

    const handleReboot = async () => {
        await rebootAllDevices();
    }

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
                const devices = await getDashboardDevices();
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

    const defaultViewport = useMemo(() => ({x: 0, y: 40, zoom: 0.65}), []);

    const handleNodeClick = useCallback(
        (_, node) => {
            if (node.id === 'main-node-1')
                fitView({nodes: [node], duration: 550, maxZoom: 0.6});
        },
        [fitView],
    );
    return (
        <div style={{height: '100dvh'}} >
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
                {/*{editUi && (*/}
                {/*    <Panel position="bottom-center" style={{marginBottom: '10px'}}>*/}
                {/*        <Typography variant="body" component="div">*/}
                {/*            Click on any node and use the arrow keys or drag with mouse to move the nodes.*/}
                {/*        </Typography>*/}
                {/*    </Panel>*/}
                {/*)}*/}
                {/*<Panel style={{*/}
                {/*    height: '100dvh',*/}
                {/*    display: 'flex',*/}
                {/*    justifyContent: 'center',*/}
                {/*    alignItems: 'center',*/}
                {/*    flexWrap: 'wrap'*/}
                {/*}} position="top-left">*/}
                {/*    <Menu/>*/}
                {/*</Panel>*/}

                <Panel position="bottom-right" style={{marginBottom: '30px', display: 'flex'}}>
                    {editUi && <NodeInspector/>}
                    {actionMenu && <div>
                        <Button variant='outlined' size='small' onClick={handleReboot}
                                style={{marginLeft: '10px'}}>Reboot</Button>
                    </div>}
                    <Button variant='outlined' size='small' onClick={handleEdit} style={{marginLeft: '10px'}}>
                        <EditIcon/> Edit</Button>
                    <Button variant='outlined' size='small' onClick={() => setActionMenu(a => !a)}
                            style={{marginLeft: '10px'}}>Actions</Button>
                </Panel>

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

const Menu = () => {

    return (
        <div style={{
            height: '60%',
            padding: '12px',
            borderRadius: '50px',
            flexDirection: 'column',
            display: 'flex',
            backgroundColor: 'transparent',
            backdropFilter: 'blur(1px)'

        }}>
            <Fab size="small" color="primary" aria-label="add" style={{marginTop:'10px'}}>
                <EditIcon/>
            </Fab>
            <Fab size="small" color="secondary" aria-label="add" style={{marginTop:'10px'}}>
                <LayersIcon/>
            </Fab>
            <Fab size="small" color="primary" aria-label="add" style={{marginTop:'10px'}}>
                <AddIcon/>
            </Fab>
        </div>
    )
}

const Dashboard = () => {

    return (
        <DeviceDataProvider>
            <ReactFlowProvider>
                <DeviceNodes/>
            </ReactFlowProvider>
        </DeviceDataProvider>

    )
}

export default React.memo(Dashboard);