import {Card, Typography} from "@mui/material";
import {Handle, Position, useReactFlow} from "@xyflow/react";
import AddIcon from "@mui/icons-material/Add";
import React from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";


const conditionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '100px',

    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

export const And = ({id, data, isConnectable}) => {

    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    return(
        <Card style={{...conditionStyle, padding: '10px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <div style={{  display: 'flex', justifyContent: 'center', alignItems:'center', marginLeft: '14px'}}>
                <Typography variant="h5" style={{textAlign:'center'}}>
                    And
                </Typography>
                <IconButton onClick={() => deleteNode(id)}>
                    <DeleteIcon/>
                </IconButton>
            </div>

            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </Card>
    )
}

export const Or = ({id, data, isConnectable}) => {
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    return(
        <Card style={{...conditionStyle, padding: '10px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <div style={{  display: 'flex', justifyContent: 'center', alignItems:'center',marginLeft: '14px'}}>
                <Typography variant="h5" style={{textAlign:'center'}}>
                    Or
                </Typography>
                <IconButton onClick={() => deleteNode(id)}>
                    <DeleteIcon/>
                </IconButton>
            </div>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </Card>
    )
}