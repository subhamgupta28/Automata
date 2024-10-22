import React, {useCallback, useEffect, useState} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Background, Controls,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {getDevices} from "../../services/apis.jsx";
import useWebSocket from "../../services/useWebSocket.jsx";
import {AnimatedSVGEdge} from "./AnimatedSVGEdge.jsx";
import {Device, MainNode} from "./Nodes.jsx";



const createNodes = (devices) => {
    const mainNode = {
        id: 'main-node-1',
        type: 'mainNode',
        position: {x: 30, y: 20}, // Adjust position as needed
        data: {value: {numOfDevices: devices.length}},
    };

    let index = 30;
    let deviceNodes = [];
    devices.map((device) => {
        deviceNodes.push({
            id: device.id,
            type: 'deviceNode',
            position: {x: index, y: 220},
            data: {value: device},
        });
        index += 260
    });

    return [mainNode, ...deviceNodes]; // Include main node with device nodes
};

const createEdges = (devices) => {
    let edges = [];
    let index = 0;

    devices.map(device => {
        edges.push({
            id: `edge-${device.id}`, // Unique edge ID
            source: `${device.id}`,     // The ID of the main node
            target: 'main-node-1',
            type: 'animatedSvg',// The ID of the device node
            targetHandle: 'main-node-' + index,       // Source handle ID if applicable
            animated: true,
            style: { stroke: '#006fff'}
        })
        index++;
    });
    return edges
};
const edgeTypes = {animatedSvg: AnimatedSVGEdge};
const nodeTypes = {deviceNode: Device, mainNode: MainNode};

export default function DeviceNodes() {
    const {messages, sendMessage} = useWebSocket('/topic/data');
    const {messages: data, sendMessage: sendData} = useWebSocket('/topic/devices');
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
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
                        style: {
                            ...node.style,
                        },
                        data: {value: dt, live: messages.data}
                    };
                }
                return node;
            }),
        );
    }, [messages, setNodes]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getDevices();
                // setDevices(data);
                setNodes(createNodes(data)); // Create nodes including the main node
                setEdges(createEdges(data)); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [data]);


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

    return (
        <div className={'card'} style={{height: '80vh', borderRadius: '12px'}}>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
                // edgeTypes={edgeTypes}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                nodeTypes={nodeTypes}

                style={{width: '100%', height: '100%', borderRadius: '12px'}}
            >
                {/*<Background style={{width: '80%', height: '80%'}}/>*/}
                <Controls/>
            </ReactFlow>
        </div>
    );
}
