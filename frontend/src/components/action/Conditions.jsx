import {Card, Typography} from "@mui/material";
import {Handle, Position, useReactFlow, useNodeConnections} from "@xyflow/react";
import AddIcon from "@mui/icons-material/Add";
import React, {useEffect} from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";


const conditionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '100px',

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
        updateNodeData(id, {
            // ...data,
            operators : {
                logicType: 'AND',
                type: "operator"
            }
        });
    }, []);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <Card style={conditionStyle}>
            <Handle type="target" position={Position.Left} />

            <IconButton onClick={deleteNode} style={{position:'absolute', right:0}}>
                <DeleteIcon/>
            </IconButton>

            <Typography fontWeight="bold">AND</Typography>
            <Typography variant="caption">
                {connections.length} inputs
            </Typography>

            <Handle type="source" position={Position.Right} />
        </Card>
    );
};



export const Or = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();

    const connections = useNodeConnections({
        handleType: 'target'
    });

    useEffect(() => {
        updateNodeData(id, {
            operators : {
                logicType: 'OR',
                type: "operator"
            }
        });
    }, []);

    const deleteNode = () => {
        setNodes(nodes => nodes.filter(n => n.id !== id));
        setEdges(edges => edges.filter(e => e.source !== id && e.target !== id));
    };

    return (
        <Card style={conditionStyle}>
            <Handle type="target" position={Position.Left} />

            <IconButton onClick={deleteNode} style={{position:'absolute', right:0}}>
                <DeleteIcon/>
            </IconButton>

            <Typography fontWeight="bold">OR</Typography>
            <Typography variant="caption">
                {connections.length} inputs
            </Typography>

            <Handle type="source" position={Position.Right} />
        </Card>
    );
};