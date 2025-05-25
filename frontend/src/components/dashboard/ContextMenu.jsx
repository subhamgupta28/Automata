import React, {useCallback} from 'react';
import {useReactFlow} from '@xyflow/react';
import {Card} from "@mui/material";

export default function ContextMenu({id, top, left, right, bottom, ...props}) {
    const {getNode, setNodes, addNodes, setEdges} = useReactFlow();


    return (
        <Card
            elevation={1}
            style={{top, left, right, bottom,
                position:'absolute', zIndex:'10', padding:'15px',
                backgroundColor: 'transparent',
                backdropFilter: 'blur(7px)'
        }}
            className="context-menu"
            {...props}
        >
            Menu
        </Card>
    );
}