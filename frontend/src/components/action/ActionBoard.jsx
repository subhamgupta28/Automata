import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Controls, Handle, Panel, Position,
    ReactFlow, ReactFlowProvider,
    useEdgesState,
    useNodesState, useReactFlow
} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useState, createContext, useContext, useRef} from "react";
import {getActions, getDevices} from "../../services/apis.jsx";
import CreateAction from "./CreateAction.jsx";
import {Button, Card, CardActions, CardContent, CardHeader, Fab} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import AddActionDialog from "./AddActionDialog.jsx";



const triggerStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '200px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #6DBF6D',
};

const actionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '200px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #0288D1',
};

const conditionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '200px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

// Custom Trigger Node
const TriggerNode = ({data, isConnectable}) => {
    // const fetchData = async () => {
    //     try {
    //         const data = await getDevices();
    //         console.log("devices", data);
    //     } catch (err) {
    //         console.error("Failed to fetch devices:", err);
    //     }
    // };
    //
    // if (data.value && data.value.isNewNode) {
    //     fetchData();
    // }






    return (
        <Card style={triggerStyle}>
            {data.value && (
                <strong>{data.value.name}</strong>
            )}

            <Handle
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
        </Card>
    );
};

// Custom Action Node
const ActionNode = ({data, isConnectable}) => {
    return (
        <Card style={actionStyle}>
            <Handle
                type="target"
                position={Position.Left}
                id="b"

                isConnectable={isConnectable}
            />
            {data.value && (
                <strong>{data.value.name}</strong>
            )}

        </Card>
    );
};

// Custom Condition Node
const ConditionNode = ({data, isConnectable}) => {
    return (
        <Card style={conditionStyle}>
            <Handle
                type="target"
                position={Position.Left}
                id="b"
                isConnectable={isConnectable}
            />
            {data.value && (
                <strong>{data.value.name}</strong>
            )}

            <Handle
                type="source"
                position={Position.Right}
                id="b"
                isConnectable={isConnectable}
            />
        </Card>
    );
};

const createNodes = (data) => {
    let triggerNode = [];
    let actionNode = [];
    let conditionNode = [];
    let edge = [];
    let x = 40;
    let y = 40;


    let sct = 0;
    let ccd = 0;
    let ax = x + 600;
    let ay = y + 40;
    let cy = y;
    let cx = x + 300;
    let edgeId = 0;

    data.map(action => {
        let trigger = action.trigger;
        let actions = action.actions;
        let condition = action.conditions;

        triggerNode.push({
            id: action.id,
            type: 'trigger',
            position: {x: x, y: y * 2},
            data: {value: action},
        });
        y += 80;

        condition.map((cond, index) => {
            conditionNode.push({
                id: "cond-id-" + ccd,
                type: 'condition',
                position: {x: cx, y: cy * 2},
                data: {value: cond},
            });
            edge.push({
                id: `edge-${edgeId}`,
                source: action.id,
                target: "cond-id-" + ccd,
                animated: true,
            });
            edgeId++;
            cy += 80;
            ccd++;
        });

        actions.map(((act, index) => {
            actionNode.push({
                id: "act-id-" + sct,
                type: 'action',
                position: {x: ax, y: ay},
                data: {value: act},
            });
            edge.push({
                id: `edge-${edgeId}`,
                source: "cond-id-" + (ccd - condition.length),
                target: "act-id-" + sct,
                animated: true,
            });
            edgeId++;
            ay += 60;
            sct++;
        }));
        ay += 40;

    })

    console.log("edge", edge);
    return {nodes: [...triggerNode, ...actionNode, ...conditionNode], edges: edge};
}

const createEdges = (data) => {
    let edges = [];
    let index = 0;

    data.map(device => {
        edges.push({
            id: `edge-${device.id}`, // Unique edge ID
            source: `${device.id}`,     // The ID of the main node
            target: 'main-node-1',
            // type: 'animatedSvg',// The ID of the device node
            targetHandle: 'main-node-' + index,       // Source handle ID if applicable
            animated: true,
            style: {stroke: '#ffffff', strokeWidth: '3px'}
        })
        index++;
    });

    return [...edges]
}

let id = 0;
const getId = () => `dndnode_${id++}`;

export function ActionBoard(action) {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [automations, setAutomations] = useState([]);
    const handleCloseModal = () => setIsModalOpen(false);
    const reactFlowWrapper = useRef(null);
    const { screenToFlowPosition } = useReactFlow();
    const [type, setType] = useDnD();


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
                id: getId(),
                type,
                position,
                data: { label: `${type} node` , value:{isNewNode: true, name: type}},
            };

            setNodes((nds) => nds.concat(newNode));
        },
        [screenToFlowPosition, type],
    );

    useEffect(() => {
        const fetchData = async () => {
            try {
                // const data = await getActions();
                // setAutomations(data)
                // const {nodes, edges} = createNodes(data)
                // setNodes(nodes); // Create nodes including the main node
                // setEdges(edges); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, []);

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [setNodes],
    );
    const onEdgesChange = useCallback(
        (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
        [setEdges],
    );
    const onConnect = useCallback(
        (connection) => setEdges((eds) => addEdge(connection, eds)),
        [setEdges],
    );

    const handleCreateAction = () => {
        setIsModalOpen(true)
    }

    const defaultViewport = useMemo(() => ({ x: 0, y: 50, zoom: 0.75 }), []);

    const onDragStart = (event, nodeType) => {
        setType(nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };
    return (
        <div >
            {/*<CreateAction isOpen={isModalOpen} onClose={handleCloseModal} automations={automations}/>*/}
            {/*<AddActionDialog isOpen={isModalOpen} onClose={handleCloseModal}/>*/}
            <Stack direction="row" divider={<Divider orientation="vertical" flexItem/>}>
                <div style={{width: '75%', height: '100dvh'}} className="reactflow-wrapper" ref={reactFlowWrapper}>
                    <ReactFlow
                        colorMode="dark"
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        defaultViewport={defaultViewport}
                        onDrop={onDrop}
                        onDragOver={onDragOver}
                        nodeTypes={{
                            trigger: TriggerNode,
                            action: ActionNode,
                            condition: ConditionNode,
                        }}
                    >
                        {/*<Panel position="bottom-right" style={{marginBottom: '50px'}}>*/}
                        {/*    <Fab color="primary" aria-label="add" onClick={handleCreateAction}>*/}
                        {/*        <EditIcon/>*/}
                        {/*    </Fab>*/}
                        {/*</Panel>*/}

                    </ReactFlow>
                </div>
                <div style={{ width: '25%', height: '90dvh', marginTop:'50px' }}>
                    <Card style={{height: '100%', margin:'10px'}}>
                        <CardContent>
                            <Typography variant="h6" component="div">
                                Automation Editor
                            </Typography>
                            <Typography gutterBottom sx={{color: 'text.secondary', fontSize: 14}}>
                                Automations Playground. Drag and drop nodes to create automations.
                            </Typography>
                            <div style={triggerStyle} onDragStart={(event) => onDragStart(event, 'trigger')}
                                 draggable>
                                Add Trigger
                            </div>
                            <div style={{...conditionStyle, marginTop:'10px'}} onDragStart={(event) => onDragStart(event, 'condition')} draggable>
                                Add Condition
                            </div>
                            <div style={{...actionStyle, marginTop:'10px'}} onDragStart={(event) => onDragStart(event, 'action')}
                                 draggable>
                                Add Action
                            </div>
                        </CardContent>
                        {/*<CardActions>*/}
                        {/*    <Button size="small" onClick={handleCreateAction}>Add Action</Button>*/}
                        {/*</CardActions>*/}
                    </Card>
                </div>
            </Stack>

        </div>
    );
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
export default () => (
    <ReactFlowProvider>
        <DnDProvider>
            <ActionBoard/>
        </DnDProvider>
    </ReactFlowProvider>
);
