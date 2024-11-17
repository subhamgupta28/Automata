import React, {useCallback, useEffect, useState} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {getChartData, getDevices, getLastDataByDeviceId} from "../../services/apis.jsx";
import useWebSocket from "../../services/useWebSocket.jsx";
import {AnimatedSVGEdge} from "./AnimatedSVGEdge.jsx";
import {Device, MainNode} from "./Nodes.jsx";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import {Card} from "@mui/material";
import ChartNode from "../charts/ChartNode.jsx";

const edgeTypes = {animatedSvg: AnimatedSVGEdge};
const nodeTypes = {
    deviceNode: Device,
    mainNode: MainNode,
    lineChartNode: ChartNode
};

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
                const devices = await getDevices();
                const charts = devices.filter(device =>
                    device.attributes.some(attr => attr.type === "DATA|CHART")
                );
                const chart = await getChartData("6713fd6118af335020f90f73");
                setNodes(createNodes(devices, [], chart)); // Create nodes including the main node
                setEdges(createEdges(devices, [])); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
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
        <div style={{height: '90vh'}}>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
                // edgeTypes={edgeTypes}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                defaultViewport={{x: 0, y: 0, zoom: 0.6}}
                nodeTypes={nodeTypes}

                // style={{
                //     width: '100%', height: '100%', borderRadius: '12px',
                //     backgroundColor: 'rgba(255, 255, 255, 0.01)',
                //     backdropFilter: 'blur(7px)',
                // }}
            >
                {/*<Background style={{width: '80%', height: '80%'}}/>*/}
                {/*<Controls/>*/}
            </ReactFlow>
        </div>
    );
}
