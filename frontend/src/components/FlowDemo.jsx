import React, {useCallback, useState} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Background, MarkerType,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';

import '@xyflow/react/dist/style.css';
import Node from "./Node.jsx";

const styles = {
    width: '80%',
    height: 300,
};

const initialNodes = [
    {
        id: 'node-1',
        type: 'textUpdater',
        position: {x: 200, y: 220},
        data: {value: 123},
    },
    {
        id: 'node-2',
        type: 'input',
        sourcePosition: 'left',
        position: {x: 400, y: 80},
        data: {label: 'node 2'},
    },
    {
        id: 'node-3',
        type: 'input',
        sourcePosition: 'left',
        position: {x: 400, y: 180},
        data: {label: 'node 3'},
    },
    {
        id: 'node-4',
        type: 'output',
        targetPosition: 'left',
        position: {x: 400, y: 280},
        data: {label: 'node 4'},
    },
    {
        id: 'node-5',
        type: 'output',
        targetPosition: 'left',
        position: {x: 400, y: 380},
        data: {label: 'node 5'},
    },
];

const initialEdges = [
    {
        id: 'edge-1',
        source: 'node-2',
        target: 'node-1',
        targetHandle: 'a', animated: true,
        label: 'online',
        style: {
            strokeWidth: 2,
            stroke: '#FF0072',
        }
    },
    {
        id: 'edge-2', source: 'node-3', target: 'node-1', targetHandle: 'a', animated: true,
        label: 'online',
        style: {
            strokeWidth: 2,
            stroke: '#FF0072',
        },
    },
    {
        id: 'edge-3', source: 'node-1', target: 'node-4', sourceHandle: 'b', animated: true,
        style: {
            strokeWidth: 2,
            stroke: '#006fff',
        }
    },
    {
        id: 'edge-4', source: 'node-1', target: 'node-5', sourceHandle: 'b', animated: true,
        style: {
            strokeWidth: 2,
            stroke: '#006fff',
        }
    },
];


const nodeTypes = {textUpdater: Node};

export default function FlowDemo() {
    const [nodes, setNodes] = useState(initialNodes);
    const [edges, setEdges] = useState(initialEdges);

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

                style={styles}
            >
                <Background style={{width: '80%', height: '80%'}}/>
            </ReactFlow>
        </div>
    );
}