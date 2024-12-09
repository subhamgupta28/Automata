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
            <BaseEdge id={id} path={edgePath} style={{stroke: '#ffffff', strokeWidth: '3px'}}/>
            <circle r="4" fill="#fff">
                <animateMotion dur="6s" repeatCount="indefinite" path={edgePath}/>
            </circle>
        </>
    );
}
