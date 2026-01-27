import {addEdge, applyEdgeChanges, applyNodeChanges, ReactFlow, useEdgesState, useNodesState} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {getVirtualDeviceList} from "../../services/apis.jsx";
import NodeInspector from "../home/NodeInspector.jsx";

const nodeTypes = {}
const dashboards = [
    {
        id: "home",
        name: "Home",
        type: "main",
        x: 150,
        y: 300,
        data: {}
    },
    {
        id: "analytics",
        name: "Analytics",
        type: "main",
        x: 400,
        y: 200,
        data: {}
    },
    {
        id: "devices",
        name: "Devices",
        type: "main",
        x: 400,
        y: 400,
        data: {}
    },
    {
        id: "automations",
        name: "Automations",
        type: "main",
        x: 600,
        y: 100,
        data: {}
    },
    {
        id: "virtual",
        name: "Virtual Device",
        type: "main",
        x: 600,
        y: 300,
        data: {}
    },
    {
        id: "dashboard",
        name: "Dashboard",
        type: "main",
        x: 600,
        y: 500,
        data: {}
    },
    {
        id: "configure",
        name: "Configure",
        type: "main",
        x: 800,
        y: 200,
        data: {}
    },
    {
        id: "automata",
        name: "Automata",
        type: "main",
        x: 800,
        y: 400,
        data: {}
    },
]
const createMainNodes = () => {
    let index = 0;

    let nodes = [];

    dashboards.map(device => {
        nodes.push({
            id: device.id,
            // type: tp,
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
    const defaultViewport = useMemo(() => ({x: 0, y: 40, zoom: 0.10}), []);

    useEffect(() => {
        const fetch = async () => {
            // const list = await getVirtualDeviceList();
            // console.log(list)

            setNodes(createMainNodes())
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