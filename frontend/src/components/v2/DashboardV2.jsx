import WeatherCard from "./WeatherCard.jsx";
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {getVirtualDeviceList} from "../../services/apis.jsx";
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges, Panel,
    ReactFlow,
    useEdgesState,
    useNodesState,
    useReactFlow
} from "@xyflow/react";

import VirtualDevice from "./VirtualDevice.jsx";
import {EnergyNode} from "./EnergyNode.jsx";
import NodeInspector from "../home/NodeInspector.jsx";
import {Button} from "@mui/material";
import {ZoomSlider} from "../home/ZoomSlider.jsx";


const nodeTypes = {
    virtualDeviceNode: VirtualDevice,
    weatherNode: WeatherCard,
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
        index+=400;
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
    const [virtualDevice, setVirtualDevice] = useState([]);
    const [rfInstance, setRfInstance] = useState(null);
    const ref = useRef(null);
    const {fitView} = useReactFlow();
    const [editUi, setEditUi] = useState(false);
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
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
            console.log(list)
            setVirtualDevice(list);
            // const weatherCards = [];
            // const energyCards = [];
            // const otherCards = [];
            // for(const dev of list){
            //     if (dev.tag === "Energy") energyCards.push(dev)
            //     else if (dev.tag === "Weather") weatherCards.push(dev)
            //     else otherCards.push(dev)
            // }
            // const weather = list.filter((f) => f.name.toLowerCase().includes("weather"));
            // const others = list.filter((f) => !f.name.toLowerCase().includes("weather"));
            // console.log("weather", weather)
            setNodes(createNodes(list))
        }

        fetch();
    }, [])
    const defaultViewport = useMemo(() => ({x: 0, y: 40, zoom: 0.85}), []);

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
                // edgeTypes={edgeTypes}
                // nodesDraggable={false}
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
                <Panel position="bottom-right" style={{marginRight: '80px', display: 'flex'}}>
                    {editUi && <NodeInspector dashboard={"v2"}/>}
                    <Button variant='outlined' size='small' onClick={handleEdit} style={{marginLeft: '10px'}}>
                        Edit</Button>
                </Panel>
                <ZoomSlider position="bottom-left"/>
            </ReactFlow>

        </div>
    )
}

export default React.memo(DashboardDetail);