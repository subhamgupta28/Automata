import React, {useCallback, useEffect, useState} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Background,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {Handle, Position} from '@xyflow/react';
import {getDevices} from "../services/apis.jsx";


function Device({data, isConnectable}) {
    return (
        <div className="text-updater-node">
            <div className={'card'} style={{padding: '8px', width: '100px'}}>
                <p style={{fontSize: '12px'}}>{data.value.name}</p>
                <p style={{fontSize: '12px'}}>{data.value.status}</p>
                <button style={{fontSize: '12px'}}>Setting</button>
            </div>
            <Handle
                type="source"
                position={Position.Top}
                id="b"
                isConnectable={isConnectable}
            />
        </div>
    );
}

function MainNode({data, isConnectable}) {
    const onChange = useCallback((evt) => {
        console.log(evt.target.value);
    }, []);

    return (
        <div className="text-updater-node">
            <div className={'card'} style={{
                padding: '12px',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                <label htmlFor="text">Automata</label>

            </div>
            <Handle
                type="target"
                position={Position.Bottom}
                id="main-node"
                isConnectable={isConnectable}
            />
        </div>
    );
}

const createNodes = (devices) => {
    const mainNode = {
        id: 'main-node-1',
        type: 'mainNode',
        position: {x: 300, y: 50}, // Adjust position as needed
        data: {value: {name: 'Main Node'}},
    };

    let index = 0;
    const deviceNodes = devices.map((device) => ({
        id: device.id,
        type: 'deviceNode',
        position: {x: index += 120, y: 350},
        data: {value: device},
    }));

    return [mainNode, ...deviceNodes]; // Include main node with device nodes
};

const createEdges = (devices) => {
    return devices.map(device => ({
        id: `edge-${device.id}`, // Unique edge ID
        source: `${device.id}`,     // The ID of the main node
        target: 'main-node-1',       // The ID of the device node
        targetHandle: 'main-node',       // Source handle ID if applicable
        animated: true,
        style: {strokeWidth: 2, stroke: '#006fff'}
    }));
};

const nodeTypes = {deviceNode: Device, mainNode: MainNode};

export default function DeviceNodes() {
    // const [devices, setDevices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);

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

    return (
        <div style={{width: '90vw', height: '80vh'}}>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                nodeTypes={nodeTypes}
                minZoom
                style={{width: '100%', height: '100%'}}
            >
                <Background style={{width: '80%', height: '80%'}}/>
            </ReactFlow>
        </div>
    );
}
