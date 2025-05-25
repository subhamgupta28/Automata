import React, { forwardRef } from "react";

import {
    Panel,
    useViewport,
    useStore,
    useReactFlow,
} from "@xyflow/react";
import {Button, Slider} from "@mui/material";
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import RemoveTwoToneIcon from '@mui/icons-material/RemoveTwoTone';
import FullscreenTwoToneIcon from '@mui/icons-material/FullscreenTwoTone';
import IconButton from "@mui/material/IconButton";

export const ZoomSlider = forwardRef(({ ...props }) => {
    const { zoom } = useViewport();
    const { zoomTo, zoomIn, zoomOut, fitView } = useReactFlow();

    const { minZoom, maxZoom } = useStore(
        (state) => ({
            minZoom: state.minZoom,
            maxZoom: state.maxZoom,
        }),
        (a, b) => a.minZoom !== b.minZoom || a.maxZoom !== b.maxZoom,
    );
    const handleChange = (event, newValue) => {
        console.log(newValue)
        zoomTo(newValue)
    };
    return (
        <Panel
            style={{display:'flex', flexDirection:'row', alignItems:'center', justifyContent:'center'}}
            {...props}
        >
            <IconButton
                size="small"
                onClick={() => zoomOut({ duration: 300 })}
            >
                <RemoveTwoToneIcon />
            </IconButton>
            <Slider
                value={zoom}
                min={minZoom}
                step={0.1}
                max={maxZoom}
                style={{width:'100px', marginLeft:'10px', marginRight:'10px'}}
                onChange={handleChange}
            />
            <IconButton
                size="small"
                onClick={() => zoomIn({ duration: 300 })}
            >
                <AddTwoToneIcon />
            </IconButton>
            <Button
                size="small"
                onClick={() => zoomTo(1, { duration: 300 })}
            >
                {(100 * zoom).toFixed(0)}%
            </Button>
            <IconButton
                size="small"
                onClick={() => fitView({ duration: 300 })}
            >
                <FullscreenTwoToneIcon />
            </IconButton>
        </Panel>
    );
});
