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
            <BaseEdge id={id} path={edgePath} style={{strokeWidth: 1.5, stroke: '#006fff'}}/>

            <circle r="3" fill="#fff">
                <animateMotion dur="6s" repeatCount="indefinite" path={edgePath}/>
            </circle>
        </>
    );
}
