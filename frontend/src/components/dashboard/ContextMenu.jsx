import React, {useCallback} from 'react';
import {useReactFlow} from '@xyflow/react';
import {Card} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import IconButton from "@mui/material/IconButton";

export default function ContextMenu({id, top, left, right, bottom, ...props}) {
    const {getNode, setNodes, addNodes, setEdges} = useReactFlow();

    const node = getNode(id)
    //{ node.data.value.name}

    return (
        <Card
            elevation={1}
            style={{top, left, right, bottom,
                position:'absolute', zIndex:'10', padding:'2px',
                backgroundColor: 'transparent',
                backdropFilter: 'blur(7px)'
        }}
            className="context-menu"
            {...props}
        >

            <IconButton style={{marginLeft: '8px'}}>
                <SettingsIcon/>
            </IconButton>
        </Card>
    );
}