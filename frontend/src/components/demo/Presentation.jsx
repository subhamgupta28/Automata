import {addEdge, applyEdgeChanges, applyNodeChanges, ReactFlow, useEdgesState, useNodesState} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {getVirtualDeviceList} from "../../services/apis.jsx";
import NodeInspector from "../home/NodeInspector.jsx";
import VirtualDevice from "../v2/VirtualDevice.jsx";
import WeatherCard from "../v2/WeatherCard.jsx";
import {EnergyNode} from "../v2/EnergyNode.jsx";
import {MainNode} from "./MainNode.jsx";

const nodeTypes = {
    main: MainNode,
    virtualDeviceNode: VirtualDevice,
    weatherNode: WeatherCard,
    energyNode: EnergyNode,
}
const dashboards = [
    {
        id: "home",
        name: "Home",
        type: "main",
        x: 50,
        y: 50,
        data: {name:'Home'}
    },
    {
        id: "analytics",
        name: "Analytics",
        type: "main",
        x: 1500,
        y: -1000,
        data: {name:'Analytics'}
    },
    {
        id: "devices",
        name: "Devices",
        type: "main",
        x: 1500,
        y: 1200,
        data: {name:'Devices'}
    },
    {
        id: "automations",
        name: "Automations",
        type: "main",
        x: 3500,
        y: -1000,
        data: {name: 'Automations'}
    },
    {
        id: "virtual",
        name: "Virtual Device",
        type: "main",
        x: 4000,
        y: 50,
        data: {name:'Virtual Device'}
    },
    {
        id: "dashboard",
        name: "Dashboard",
        type: "main",
        x: 3500,
        y: 1200,
        data: {name: 'Dashboard'}
    },
    {
        id: "configure",
        name: "Configure",
        type: "main",
        x: 5500,
        y: 1200,
        data: {name: 'Configure'}
    },
    {
        id: "automata",
        name: "Automata",
        type: "main",
        x: 2000,
        y: 50,
        data: {name: 'Automata'}
    },
]
const createMainNodes = (virtualDevices) => {
    let nodes = [];

    dashboards.map(device => {
        nodes.push({
            id: device.id,
            type: device.type,
            position: {x: device.x, y: device.y},
            data: {value: {...device}},
        });

    })
    virtualDevices.map(device => {
        const tp = (device.tag === "Energy") ? "energyNode" : (device.tag === "Weather") ? "weatherNode" : "virtualDeviceNode";
        nodes.push({
            id: device.id,
            type: tp,
            parentId: 'home',
            extent: 'parent',
            position: {x: device.x, y: device.y},
            data: {value: {...device}},
        });

    })

    return [...nodes];
}

function Presentation({}) {
    const [rfInstance, setRfInstance] = useState(null);
    const ref = useRef(null);
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
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
    const defaultViewport = useMemo(() => ({x: 0, y: 40, zoom: 0.80}), []);

    useEffect(() => {
        const fetch = async () => {
            const list = await getVirtualDeviceList();
            // console.log(list)

            setNodes(createMainNodes(list))
        }

        fetch();
    }, [])
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
                autoPanOnNodeFocus
                // snapToGrid
                // edgeTypes={edgeTypes}
                // nodesDraggable={editUi}
                // onNodeClick={handleNodeClick}
                // fitView
                // fitViewOptions={{ nodes: [{ id: '' }] }}
                // onPaneClick={onPaneClick}
                // onNodeContextMenu={onNodeContextMenu}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                defaultViewport={defaultViewport}
                nodeTypes={nodeTypes}

            >
                {/*<NodeInspector dashboard={"v2"}/>*/}
            </ReactFlow>
        </div>
    )
}

export default React.memo(Presentation);