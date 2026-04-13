import {Card, Typography} from "@mui/material";
import {Handle, Position, useNodeConnections, useReactFlow} from "@xyflow/react";
import AddIcon from "@mui/icons-material/Add";
import React, {useEffect} from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";


const conditionStyle = {
    padding: '20px',
    borderRadius: '10px',
    width: '150px',

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

    const connections = useNodeConnections({
        handleType: 'target'
    });

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
                previousNodeRef: previousNodes
            }
        });
    }, [connections]);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <Card style={conditionStyle}>

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
            <IconButton onClick={deleteNode} style={{position: 'absolute', right: 10}}>
                <DeleteIcon/>
            </IconButton>

            <Typography fontWeight="bold">AND</Typography>
            <Typography variant="caption">
                {connections.length} inputs
            </Typography>
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

        </Card>
    );
};


export const Or = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();

    const connections = useNodeConnections({
        handleType: 'target'
    });

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
                previousNodeRef: previousNodes
            }
        });
    }, [connections]);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <Card style={conditionStyle}>
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

            <IconButton onClick={deleteNode} style={{position: 'absolute', right: 0}}>
                <DeleteIcon/>
            </IconButton>

            <Typography fontWeight="bold">OR</Typography>
            <Typography variant="caption">
                {connections.length} inputs
            </Typography>

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
        </Card>
    );
};