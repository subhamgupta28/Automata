import useWebSocket from "../../services/useWebSocket.jsx";
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Controls, Handle, Position,
    ReactFlow,
    useEdgesState,
    useNodesState
} from "@xyflow/react";
import React, {useCallback, useEffect, useState} from "react";
import {getActions} from "../../services/apis.jsx";
import {createEdges, createNodes} from "../../utils/Util.jsx";

export function ProducerNode({data, isConnectable}) {

    return (
        <div className="text-updater-node">
            <div className={'card ' + state} style={{padding: '0px'}}>
                <div className="card-header" style={{display: 'flex', alignItems: 'center'}}>
                    <h6 style={{display: 'contents'}}>
                        source
                    </h6>

                </div>
                <div className={'card-body'}>

                </div>
                <div className={'card-footer'}>
                </div>

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

export function ConsumerNode({data, isConnectable}) {
    let nodeIds = []
    for (let i = 0; i < data.value.numOfDevices; i++) {
        nodeIds.push("main-node-" + i)
    }
    console.log("ids", data)


    return (
        <div className="text-updater-node">
            <div className={'card alert alert-warning'} style={{
                padding: '0px',
                width: '500px',
                borderRadius: '8px',
            }}>
                <div className={'card-header'}>
                    <div className="spinner-grow spinner-grow-sm" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </div>
                    <label>
                        <i className={'bi bi-motherboard-fill'} style={{marginLeft: '8px', marginRight: '12px'}}></i>
                        Automata
                    </label>
                </div>

                <Handle
                    type="target"
                    position={Position.Bottom}
                    id="consumer"
                    style={{left: 10 + index * 50}}
                    isConnectable={isConnectable}
                />

            </div>

        </div>
    );
}


const nodeTypes = {producerNode: ProducerNode, consumerNode: ConsumerNode};

export default function ActionBoard(action) {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);


    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getActions();

                setNodes(createNodes(data)); // Create nodes including the main node
                setEdges(createEdges(data)); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
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
        <div className={'card'} style={{height: '80vh', borderRadius: '12px'}}>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
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
