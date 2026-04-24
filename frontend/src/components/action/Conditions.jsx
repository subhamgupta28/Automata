import {Chip, Typography} from "@mui/material";
import {Handle, Position, useNodeConnections, useReactFlow} from "@xyflow/react";
import AddIcon from "@mui/icons-material/Add";
import React, {useEffect, useState} from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";
import NumberSpinner from "../charts/NumberSpinner.jsx";


const conditionStyle = {
    padding: '8px',
    borderRadius: '10px',
    // width: '200px',
    background: 'transparent',
    backgroundColor: 'rgb(0 0 0 / 28%)',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

const style = {
    padding: '10px',
    borderRadius: '10px',
    width: '200px',
    border: '2px solid #4CAF50',
    background: 'rgb(255 255 255 / 8%)'
};

export const And = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [priority, setPriority] = useState(data.operators?.priority || 5);
    const [conditionGroup, setConditionGroup] = useState(data.operators?.conditionGroup || "none");

    const connections = useNodeConnections({
        handleType: 'target'
    });
    useEffect(() => {
        if (connections.length === 0) return;

        // Deduplicate: if multiple connections from same source, take the latest
        const seen = new Map();
        for (const conn of connections) {
            seen.set(conn.source, conn); // last one wins
        }
        const dedupedConnections = [...seen.values()];

        const sourceHandle = dedupedConnections[0]?.sourceHandle ?? '';
        const group = sourceHandle.includes('cond-negative') ? 'negative'
            : sourceHandle.includes('cond-positive') ? 'positive'
                : 'none';
        setConditionGroup(group);
    }, [connections]);

    useEffect(() => {
        const previousNodes = connections.map(conn => ({
            nodeId: conn.source,
            handle: conn.sourceHandle
        }));
        // console.log("connection: operator", previousNodes)
        updateNodeData(id, {
            // ...data,
            operators: {
                nodeId: id,
                logicType: 'AND',
                type: "operator",
                previousNodeRef: previousNodes,
                priority,
                conditionGroup
            }
        });
    }, [connections, priority]);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'center', gap: '6px', margin: '4px', alignItems: 'center'}}>
                <Chip size="small" label={"Priority: " + priority}> </Chip>
                <IconButton onClick={() => deleteNode(id)}>
                    <DeleteIcon sx={{color: '#ff0000'}}/>
                </IconButton>
            </div>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id={"in:operator:" + id}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <div style={{
                ...conditionStyle,
                border: `2px solid ${conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#FFF'}`
            }}>
                <div style={{display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '5px'}}>
                    <Typography fontWeight="bold">AND</Typography>
                    <NumberSpinner
                        label="Priority"
                        min={0}
                        max={10}
                        value={priority}
                        size="small"
                        onChange={setPriority}
                    />
                </div>
            </div>

            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="source"
                id={"out:operator:" + id}
                position={Position.Right}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>

        </div>
    );
};


export const Or = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [priority, setPriority] = useState(data.operators?.priority || 5);
    const [conditionGroup, setConditionGroup] = useState(data.operators?.conditionGroup || "none");
    const connections = useNodeConnections({
        handleType: 'target'
    });
    useEffect(() => {
        if (connections.length === 0) return;

        // Deduplicate: if multiple connections from same source, take the latest
        const seen = new Map();
        for (const conn of connections) {
            seen.set(conn.source, conn); // last one wins
        }
        const dedupedConnections = [...seen.values()];

        const sourceHandle = dedupedConnections[0]?.sourceHandle ?? '';
        const group = sourceHandle.includes('cond-negative') ? 'negative'
            : sourceHandle.includes('cond-positive') ? 'positive'
                : 'none';
        setConditionGroup(group);
    }, [connections]);
    useEffect(() => {
        const previousNodes = connections.map(conn => ({
            nodeId: conn.source,
            handle: conn.sourceHandle
        }));
        updateNodeData(id, {
            operators: {
                nodeId: id,
                logicType: 'OR',
                type: "operator",
                previousNodeRef: previousNodes,
                priority,
                conditionGroup
            }
        });
    }, [connections, priority]);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <div>

            <div style={{display: 'flex', justifyContent: 'center', gap: '6px', margin: '4px', alignItems: 'center'}}>
                <Chip size="small" label={"Priority: " + priority}> </Chip>
                <IconButton onClick={() => deleteNode(id)}>
                    <DeleteIcon sx={{color: '#ff0000'}}/>
                </IconButton>
            </div>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id={"in:operator:" + id}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <div style={{
                ...conditionStyle,
                border: `2px solid ${conditionGroup === 'negative' ? '#f44336' : conditionGroup === 'positive' ? '#4caf50' : '#FFF'}`
            }}>
                <div style={{display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '10px'}}>
                    <Typography fontWeight="bold">OR</Typography>
                    <NumberSpinner
                        label="Priority"
                        min={0}
                        max={10}
                        value={priority}
                        size="small"
                        onChange={setPriority}
                    />

                </div>
            </div>


            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="source"
                id={"out:operator:" + id}
                position={Position.Right}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </div>
    );
};