import React from 'react';
import {BaseEdge, getBezierPath} from '@xyflow/react';

export function AnimatedSVGEdge({
                                    id,
                                    sourceX,
                                    sourceY,
                                    targetX,
                                    targetY,
                                    sourcePosition,
                                    targetPosition,
                                }) {
    const [edgePath] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    return (
        <>
            <BaseEdge id={id} path={edgePath} style={{stroke: 'rgba(255,255,255,0.41)', strokeWidth: '2px'}}/>
            <circle r="6" fill="#fce02b">
                <animateMotion dur="6s" repeatCount="indefinite" path={edgePath}/>
            </circle>
        </>
    );
}
