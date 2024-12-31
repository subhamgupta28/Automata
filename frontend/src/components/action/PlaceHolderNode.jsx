import React from "react";
import {Handle, Position, useReactFlow} from "@xyflow/react";
import { BaseNode } from "@/registry/components/base-node";


export function PlaceholderNode({ data, id, selected }) {

    const { setNodes, setEdges } = useReactFlow();

    const handleClick = () => {
        setEdges((edges) =>
            edges.map((edge) =>
                edge.target === id ? { ...edge, animated: false } : edge
            )
        );

        setNodes((nodes) => {
            return nodes.map((node) => {
                if (node.id === id) {
                    return {
                        ...node,
                        data: {...node.data, label: "New Node"},
                        type: "default",
                    };
                }
                return node;
            });
        });
    };

    return (
        <BaseNode
            id={id}
            selected={selected}
            className="bg-card text-center w-[150px] border-dashed border-gray-400 text-gray-400 shadow-none p-2"
            onClick={handleClick}
        >
            {data.label}
            <Handle
                type="target"
                style={{ visibility: 'hidden' }}
                position={Position.Top}
                isConnectable={false}
            />
            <Handle
                type="source"
                style={{ visibility: 'hidden' }}
                position={Position.Bottom}
                isConnectable={false}
            />
        </BaseNode>
    );
}

PlaceholderNode.displayName = "PlaceholderNode";