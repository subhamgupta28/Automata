import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {getVirtualDeviceList} from "../../services/apis.jsx";
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Controls,
    Panel,
    ReactFlow,
    useEdgesState,
    useNodesState,
    useReactFlow
} from "@xyflow/react";
import '@xyflow/react/dist/style.css';

import {VirtualDevice} from "./VirtualDevice.jsx";
import {EnergyNode} from "./EnergyNode.jsx";
import NodeInspector from "../home/NodeInspector.jsx";
import {Edit} from "@mui/icons-material";
import IconButton from "@mui/material/IconButton";
import {WeatherCardV2} from "./WeatherCardV2.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import {CustomModal} from "../home/CustomModal.jsx";
import {useDeviceLiveData} from "../../services/DeviceDataProvider.jsx";


const nodeTypes = {
    virtualDeviceNode: VirtualDevice,
    weatherNode: WeatherCardV2,
    energyNode: EnergyNode,
    // energyChildNode: EnergyChildNode
};


const createNodes = (virtualDevices) => {
    let index = 0;
    let nodes = [];

    virtualDevices.map(device => {
        const tp = (device.tag === "Energy") ? "energyNode" : (device.tag === "Weather") ? "weatherNode" : "virtualDeviceNode";
        nodes.push({
            id: device.id,
            type: tp,
            position: {x: device.x, y: device.y},
            data: {value: {...device}},
        });
        index += 400;
    })
    // weather.map(we=>{
    //     nodes.push({
    //         id: we.id,
    //         type: 'weatherNode',
    //         position: {x: 10, y: 10},
    //         data: {value: {...we}},
    //     });
    // })

    return [...nodes];
}


function DashboardDetail() {
    // const [virtualDevice, setVirtualDevice] = useState([]);
    const [rfInstance, setRfInstance] = useState(null);
    const ref = useRef(null);
    const {fitView} = useReactFlow();
    const [editUi, setEditUi] = useState(false);
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [menu, setMenu] = useState(null);
    const {devices} = useCachedDevices();
    const {messages} = useDeviceLiveData();
    const handleEdit = useCallback(() => {
        setEditUi(prev => !prev);
    }, []);
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
    useEffect(() => {
        const fetch = async () => {
            const list = await getVirtualDeviceList();
            setNodes(createNodes(list.filter(f => f.active)))
        }

        fetch();
    }, [])
    const defaultViewport = useMemo(() => ({x: 0, y: 20, zoom: 0.85}), []);

    const onNodeContextMenu = useCallback(
        (event, node) => {
            // Prevent native context menu from showing
            event.preventDefault();

            // Calculate position of the context menu. We want to make sure it
            // doesn't get positioned off-screen.
            const pane = ref.current.getBoundingClientRect();
            setMenu({
                nodeData: node.data,
                devices: devices?.filter(d => node.data.value.deviceIds.includes(d.id)),
                id: node.id,
                top: event.clientY < pane.height - 200 && event.clientY,
                left: event.clientX < pane.width - 200 && event.clientX,
                right: event.clientX >= pane.width - 200 && pane.width - event.clientX,
                bottom:
                    event.clientY >= pane.height - 200 && pane.height - event.clientY,
            });
        },
        [setMenu, devices],
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
                // autoPanOnNodeFocus={false}
                // snapToGrid
                // edgeTypes={edgeTypes}
                nodesDraggable={editUi}
                // onNodeClick={handleNodeClick}
                // fitView
                // fitViewOptions={{ nodes: [{ id: '' }] }}
                onPaneClick={onPaneClick}
                onNodeContextMenu={onNodeContextMenu}
                zoomOnDoubleClick={false}
                onNodesChange={onNodesChange}
                deleteKeyCode={null}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                defaultViewport={defaultViewport}
                nodeTypes={nodeTypes}
            >
                <Panel position="bottom-right" style={{marginRight: '80px', display: 'flex'}}>
                    {editUi && <NodeInspector dashboard={"v2"}/>}
                    <IconButton variant="outlined" size='small' color="primary" onClick={handleEdit}
                                style={{marginLeft: '10px'}}>
                        <Edit fontSize="small"/>
                    </IconButton>
                </Panel>
                {/*<Panel position="bottom-center">*/}
                {/*    <AutomationSummaryBar/>*/}
                {/*</Panel>*/}
                <Controls orientation="horizontal"/>
                {menu && (
                    <CustomModal
                        map={null}
                        isOpen={menu}
                        messages={messages}
                        onClose={() => setMenu(null)}
                        devices={menu.devices}
                        version="v2"
                    />
                )}
                {/*<ZoomSlider position="bottom-left"/>*/}
            </ReactFlow>

        </div>
    )
}

export default React.memo(DashboardDetail);