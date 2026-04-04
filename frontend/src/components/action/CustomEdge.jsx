import {BaseEdge, EdgeLabelRenderer, getBezierPath, useReactFlow,} from '@xyflow/react';
import IconButton from "@mui/material/IconButton";
import CancelIcon from '@mui/icons-material/Cancel';
import {useCallback} from "react";

export default function CustomEdge({
                                       id,
                                       sourceX,
                                       sourceY,
                                       targetX,
                                       targetY,
                                       sourcePosition,
                                       targetPosition,
                                       data
                                   }) {
    const {setEdges} = useReactFlow();
    const [edgePath, labelX, labelY] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });


    const color = data?.color || '#ff832a';

    // Define handler outside EdgeLabelRenderer so it captures the correct context
    const handleDelete = useCallback(() => {
        setEdges((es) => es.filter((e) => e.id !== id));
    }, [id, setEdges]);
    return (
        <>
            <BaseEdge id={id} path={edgePath} style={{stroke: color, strokeWidth: '3px',}}/>
            <EdgeLabelRenderer>
                <IconButton

                    style={{
                        backgroundColor: 'rgba(255,165,0,0.51)',
                        position: 'absolute',
                        transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                        pointerEvents: 'all',
                    }}
                    className="nodrag"
                    onClick={handleDelete}
                >
                    <CancelIcon/>
                </IconButton>
            </EdgeLabelRenderer>
        </>
    );
}