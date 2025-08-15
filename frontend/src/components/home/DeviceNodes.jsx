import React, {useCallback, useEffect, useState, useMemo, useRef} from 'react';
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

import {AnimatedSVGEdge} from "./AnimatedSVGEdge.jsx";
import {AlertNode, Device, MainNode} from "./Nodes.jsx";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import {Backdrop, Button, CircularProgress} from "@mui/material";
import NodeInspector from "./NodeInspector.jsx";
import {ZoomSlider} from "./ZoomSlider.jsx";
import ContextMenu from "./ContextMenu.jsx";

const edgeTypes = {animatedSvg: AnimatedSVGEdge};
const nodeTypes = {
    deviceNode: Device,
    mainNode: MainNode,
    alertNode: AlertNode
};

const DeviceNodes = () => {
    const [openBackdrop, setOpenBackdrop] = useState(false);
    // const {messages} = useWebSocket('/topic/data');
    const [rfInstance, setRfInstance] = useState(null);
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [editUi, setEditUi] = useState(false);
    const [actionMenu, setActionMenu] = useState(false);
    const [menu, setMenu] = useState(null);
    const ref = useRef(null);
    const {fitView} = useReactFlow();
    const handleEdit = useCallback(() => {
        setEditUi(prev => !prev);
    }, []);

    const handleReboot = async () => {
        await rebootAllDevices();
    }

    const alert = (message) => {
        const alertNode = {
            id: 'alert-node',
            type: 'alertNode',
            position: {
                x: 1000,
                y: -80,
            },
            // hidden: true,
            data: {value: {message, severity: 'error'}},
        };

        setNodes((nds) => nds.concat(alertNode));
        fitView({nodes: [{id: 'alert-node'}], duration: 750});
    }

    const handleSave = () => {

        alert("hello")
        if (rfInstance) {
            const flow = rfInstance.toObject();
            console.log("detail", JSON.stringify(flow))
        }

    }

    useEffect(() => {
        setOpenBackdrop(true);
        const fetchData = async () => {
            try {
                const pos = await getMainNodePos();
                const devices = await getDashboardDevices();
                // const dev = devices.filter((d) => d.showInDashboard === true);
                setNodes(createNodes(devices, [], pos.x, pos.y));
                setEdges(createEdges(devices, []));
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
            if (node.id === 'alert-node')
                fitView({nodes: [node], duration: 750, maxZoom: 0.8});
        },
        [fitView],
    );


    const onNodeContextMenu = useCallback(
        (event, node) => {
            // Prevent native context menu from showing
            event.preventDefault();

            // Calculate position of the context menu. We want to make sure it
            // doesn't get positioned off-screen.
            const pane = ref.current.getBoundingClientRect();
            setMenu({
                id: node.id,
                top: event.clientY < pane.height - 200 && event.clientY,
                left: event.clientX < pane.width - 200 && event.clientX,
                right: event.clientX >= pane.width - 200 && pane.width - event.clientX,
                bottom:
                    event.clientY >= pane.height - 200 && pane.height - event.clientY,
            });
        },
        [setMenu],
    );

    // Close the context menu if it's open whenever the window is clicked.
    const onPaneClick = useCallback(() => setMenu(null), [setMenu]);
    return (
        <div style={{height: '100dvh', padding: '0px 0px 0px 0px'}}>
            <ReactFlow
                ref={ref}
                colorMode="dark"
                nodes={nodes}
                onInit={setRfInstance}
                edges={edges}
                style={{
                    backgroundColor: 'transparent'
                }}
                edgeTypes={edgeTypes}
                // nodesDraggable={editUi}
                // onNodeClick={handleNodeClick}
                // fitView
                // fitViewOptions={{ nodes: [{ id: '' }] }}
                onPaneClick={onPaneClick}
                onNodeContextMenu={onNodeContextMenu}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                defaultViewport={defaultViewport}
                nodeTypes={nodeTypes}
            >

                <Panel position="bottom-right" style={{marginRight: '80px', display: 'flex'}}>
                    {editUi && <NodeInspector/>}
                    {actionMenu && <div>
                        <Button variant='outlined' size='small' onClick={handleReboot}
                                style={{marginLeft: '10px'}}>Reboot</Button>
                    </div>}
                    <Button variant='outlined' size='small' onClick={handleEdit} style={{marginLeft: '10px'}}>
                        Edit</Button>
                    <Button variant='outlined' size='small' onClick={handleSave} style={{marginLeft: '10px'}}>
                        Edit Dashboard</Button>
                    <Button variant='outlined' size='small' onClick={() => setActionMenu(a => !a)}
                            style={{marginLeft: '10px'}}>Actions</Button>
                </Panel>
                <ZoomSlider position="bottom-left"/>
                {menu && <ContextMenu onClick={onPaneClick} {...menu} />}
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
        // <DeviceDataProvider>
        <ReactFlowProvider>
            <DeviceNodes/>
        </ReactFlowProvider>
        // </DeviceDataProvider>

    )
}

export default React.memo(Dashboard);