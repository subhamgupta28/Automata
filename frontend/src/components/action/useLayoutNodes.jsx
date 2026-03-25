import { useEffect } from 'react';
import ELK from 'elkjs/lib/elk.bundled.js';
import { useNodesInitialized, useReactFlow } from '@xyflow/react';

const elk = new ELK();

const layoutOptions = {
    'elk.algorithm': 'layered',
    'elk.direction': 'RIGHT',
    'elk.spacing.nodeNode': '50',
    'elk.layered.spacing.nodeNodeBetweenLayers': '80',
    'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
};

// 🔥 Main layout function
const getLayoutedNodes = async (nodes, edges) => {
    if (!nodes.length) return nodes;

    const graph = {
        id: 'root',
        layoutOptions,

        children: nodes.map((n) => ({
            id: n.id,
            width: n.measured?.width || 220,
            height: n.measured?.height || 80,
        })),

        edges: edges.map((e) => ({
            id: e.id,
            sources: [e.source],
            targets: [e.target],
        })),
    };

    const layoutedGraph = await elk.layout(graph);

    return nodes.map((node) => {
        const layouted = layoutedGraph.children.find((n) => n.id === node.id);

        return {
            ...node,
            position: {
                x: layouted?.x ?? 0,
                y: layouted?.y ?? 0,
            },
        };
    });
};

// 🔥 Hook
export default function useLayoutNodes() {
    const nodesInitialized = useNodesInitialized();
    const { getNodes, getEdges, setNodes, fitView } = useReactFlow();

    useEffect(() => {
        if (!nodesInitialized) return;

        const layout = async () => {
            const nodes = getNodes();
            const edges = getEdges();

            if (!nodes.length) return;

            const layouted = await getLayoutedNodes(nodes, edges);

            setNodes(layouted);

            // optional but recommended
            setTimeout(() => {
                fitView({ padding: 0.2 });
            }, 50);
        };

        layout();
    }, [nodesInitialized, getNodes, getEdges, setNodes, fitView]);

    return null;
}