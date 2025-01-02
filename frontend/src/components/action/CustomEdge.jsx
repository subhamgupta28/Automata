import {
    BaseEdge,
    EdgeLabelRenderer, getBezierPath, getSmoothStepPath,
    useReactFlow,
} from '@xyflow/react';
import IconButton from "@mui/material/IconButton";
import CancelIcon from '@mui/icons-material/Cancel';
export default function CustomEdge({ id,
                                       sourceX,
                                       sourceY,
                                       targetX,
                                       targetY,
                                       sourcePosition,
                                       targetPosition, }) {
    const { setEdges } = useReactFlow();
    const [edgePath, labelX, labelY] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    return (
        <>
            <BaseEdge id={id} path={edgePath} style={{stroke: '#ff832a', strokeWidth: '3px', }}/>
            <EdgeLabelRenderer>
                <IconButton

                    style={{
                        backgroundColor: 'rgba(255,165,0,0.51)',
                        position: 'absolute',
                        transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                        pointerEvents: 'all',
                    }}
                    className="nodrag"
                    onClick={() => {
                        setEdges((es) => es.filter((e) => e.id !== id));
                    }}
                >
                    <CancelIcon/>
                </IconButton>
            </EdgeLabelRenderer>
        </>
    );
}