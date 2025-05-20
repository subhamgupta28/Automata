import {
    useNodes,
    ViewportPortal,
    useReactFlow, Panel
} from '@xyflow/react';
import Button from "@mui/material/Button";
import {updatePosition} from "../../services/apis.jsx";
import {Card} from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import React from "react";

export default function NodeInspector() {
    const {getInternalNode} = useReactFlow();
    const nodes = useNodes();

    const handleUpdate = async () => {
        nodes.map(async (node) => {
            const internalNode = getInternalNode(node.id);
            if (!internalNode) {
                return null;
            }
            await updatePosition(node.id, node.position.x.toFixed(1), node.position.y.toFixed(1))
        });
    }

    return (
        <div>
            <Button size="small" variant='outlined' color="primary" style={{marginLeft: '10px'}} aria-label="add" onClick={handleUpdate}>
                <SaveIcon/> Save
            </Button>
            <ViewportPortal>
                <div className="react-flow__devtools-nodeinspector">
                    {nodes.map((node) => {
                        const internalNode = getInternalNode(node.id);
                        if (!internalNode) {
                            return null;
                        }

                        const absPosition = internalNode?.internals.positionAbsolute;

                        return (
                            <NodeInfo
                                key={node.id}
                                id={node.id}
                                selected={!!node.selected}
                                type={node.type || 'default'}
                                position={node.position}
                                absPosition={absPosition}
                                width={node.measured?.width ?? 0}
                                height={node.measured?.height ?? 0}
                                data={node.data}
                            />
                        );
                    })}
                </div>
            </ViewportPortal>
        </div>

    );
}

function NodeInfo({id, type, selected, position, absPosition, width, height, data}) {
    if (!width || !height) {
        return null;
    }




    return (
        <Card
            elevation={12}
            className="react-flow__devtools-nodeinfo"
            style={{
                position: 'absolute',
                transform: `translate(${absPosition.x}px, ${absPosition.y + height}px)`,
                // width: width * 2,
                padding: '10px',
                marginLeft: '15px',
                borderRadius: '0px 0px 18px 18px',
            }}
        >


            {/*<div>id: {id}</div>*/}
            {/*<div>type: {type}</div>*/}
            {/*<div>selected: {selected ? 'true' : 'false'}</div>*/}
            <div>
                position: {position.x.toFixed(1)}, {position.y.toFixed(1)}
            </div>
            {/*<div>*/}
            {/*    dimensions: {width} × {height}*/}
            {/*</div>*/}
            {/*<div>data: {JSON.stringify(data, null, 2)}</div>*/}
        </Card>
    );
}