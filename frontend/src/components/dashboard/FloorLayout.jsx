import React, {useRef, useEffect, useState, useCallback, useMemo, createContext, useContext} from "react";
import './FloorLayout.css';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges, Panel, ReactFlow,
    ReactFlowProvider,
    useEdgesState,
    useNodesState,
    useReactFlow
} from "@xyflow/react";
import Stack from "@mui/material/Stack";
import CustomEdge from "../action/CustomEdge.jsx";
import {TriggerNode} from "../action/TriggerNode.jsx";
import {ActionNode} from "../action/ActionNode.jsx";
import {ConditionNode} from "../action/ConditionNode.jsx";
import {ValueReaderNode} from "../action/ValueReaderNode.jsx";
import Typography from "@mui/material/Typography";
import {Card, CardContent} from "@mui/material";
import {IconBathRoomSink, IconBed, IconTv, IconWordrobe} from "../../assets/Icons.jsx";

let id = 0;
const getId = (type) => `node_${type}_${id++}`;

function FloorPlanBoard() {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [type, setType] = useDnD();
    const reactFlowWrapper = useRef(null);
    const {screenToFlowPosition} = useReactFlow();
    const [rfInstance, setRfInstance] = useState(null);

    const furnishing = [
        {icon:<IconWordrobe/>, name: "Wordrobe"},
        {icon:<IconBathRoomSink/>, name: "IconBathRoomSink"},
        {icon:<IconTv/>, name: "IconTv"},
        {icon:<IconBed/>, name: "IconBed"},
    ];

    const onDragOver = useCallback((event) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onDrop = useCallback(
        (event) => {
            event.preventDefault();

            if (!type) {
                return;
            }

            const position = screenToFlowPosition({
                x: event.clientX,
                y: event.clientY,
            });
            const newNode = {
                id: getId(type),
                type,
                position,
                data: {value: {isNewNode: true, name: type}},
            };

            setNodes((nds) => nds.concat(newNode));
        },
        [screenToFlowPosition, type],
    );

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [setNodes],
    );
    const onEdgesChange = useCallback(
        (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
        [setEdges],
    );
    const onConnect = useCallback(
        (connection) => {
            const edge = {
                ...connection,
                type: 'custom-edge',
            };
            setEdges((eds) => addEdge(edge, eds));
        },
        [setEdges],
    );

    const defaultViewport = useMemo(() => ({x: 0, y: 50, zoom: 0.75}), []);

    const onDragStart = (event, nodeType) => {
        setType(nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };

    return (
        <div style={{backgroundColor: "white"}}>
            <Stack direction="row">
                <div style={{width: '80%', height: '100dvh', borderRadius:'10px', padding:'10px 10px 10px 0px'}} className="reactflow-wrapper" ref={reactFlowWrapper}>
                    <ReactFlow
                        style={{
                            borderRadius:'10px',
                            backgroundColor: 'transparent',
                            borderColor: 'grey',
                            borderStyle:'dashed',
                        }}
                        colorMode="dark"
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onInit={setRfInstance}
                        edgeTypes={{
                            'custom-edge': CustomEdge,
                        }}
                        // isValidConnection={isValidConnection}
                        defaultViewport={defaultViewport}
                        onDrop={onDrop}
                        // connectionLineComponent={EdgeLine}
                        className="validationflow"
                        onDragOver={onDragOver}
                        nodeTypes={{
                            trigger: TriggerNode,
                            action: ActionNode,
                            condition: ConditionNode,
                            valueReader: ValueReaderNode,
                        }}
                    >
                    </ReactFlow>
                </div>
                <div style={{width: '20%', height: '100dvh'}}>
                    <Card style={{
                        height: '97dvh',
                        display: 'flex',
                        flexDirection: 'column',
                        margin:'10px 10px 10px 0px',
                        borderRadius:'10px',
                        backgroundColor: 'transparent',
                        borderColor: 'grey',
                        borderStyle:'dashed',
                    }}>
                        <CardContent style={{
                            flex: 1,
                            padding: '6px',
                            display: 'flex',
                            flexDirection: 'column',
                            overflow: 'hidden',

                        }}>
                            <svg viewBox="0 0 150 150">
                                <IconWordrobe/>
                            </svg>

                            <div style={{
                                padding:'10px',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems:'center'
                            }}>

                                {furnishing.map(i =>(
                                    <div key={i.name} onDragStart={(event) => onDragStart(event, i.name)}
                                         draggable>
                                        <svg viewBox="0 0 150 150">
                                            {i.icon}
                                        </svg>

                                    </div>
                                ))}
                            </div>
                            {/* Scrollable list container */}

                        </CardContent>
                    </Card>
                </div>

            </Stack>

        </div>
    );
};

export default function FloorLayout(){

    return(
        <ReactFlowProvider>
            <DnDProvider>
                <FloorPlanBoard/>
            </DnDProvider>
        </ReactFlowProvider>
    )
}


const DnDContext = createContext([null, (_) => {
}]);

export const DnDProvider = ({children}) => {
    const [type, setType] = useState(null);

    return (
        <DnDContext.Provider value={[type, setType]}>
            {children}
        </DnDContext.Provider>
    );
}

const useDnD = () => {
    return useContext(DnDContext);
}