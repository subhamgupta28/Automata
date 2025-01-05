import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges, Panel,
    ReactFlow, ReactFlowProvider,
    useEdgesState, useNodesState, useReactFlow
} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useState, createContext, useContext, useRef} from "react";
import {getActions, getAutomationDetail, saveAutomationDetail} from "../../services/apis.jsx";

import {
    Button,
    Card,
    CardContent,
} from "@mui/material";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import {ActionNode, ConditionNode, TriggerNode} from "./NodeTypes.jsx";
import CustomEdge from "./CustomEdge.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import '@xyflow/react/dist/style.css';

const triggerStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    border: '2px solid #6DBF6D',
};

const actionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    border: '2px solid #0288D1',
};

const conditionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '220px',
    border: '2px solid #FFEB3B',
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
const getId = (type) => `node_${type}_${id++}`;

export function ActionBoard(action) {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [automations, setAutomations] = useState([]);
    const {devices, loading, error} = useCachedDevices();
    const handleCloseModal = () => setIsModalOpen(false);
    const reactFlowWrapper = useRef(null);
    const {screenToFlowPosition} = useReactFlow();
    const [type, setType] = useDnD();
    const [rfInstance, setRfInstance] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
            try {
                // const res = await getDevices();
                // setDevices(res);
                const data = await getActions();
                setAutomations(data)
                // const {nodes, edges} = createNodes(data)
                // setNodes(nodes); // Create nodes including the main node
                // setEdges(edges); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, []);

    const isValidConnection = useCallback((connection) => {

        console.log("connection", connection)
        return connection.target === 'node_1';
    }, []);
    const onSave = useCallback(() => {
        const saveFlow = async (payload) => {
            await saveAutomationDetail(payload)
        }

        if (rfInstance) {
            const flow = rfInstance.toObject();
            saveFlow(JSON.stringify(flow));
            console.log("flow", flow)
            localStorage.setItem("flow", JSON.stringify(flow));
        }
    }, [rfInstance]);

    const onRestore = useCallback(() => {
        const restoreFlow = async () => {
            const flow = JSON.parse(localStorage.getItem("flow"));

            if (flow) {
                const {x = 0, y = 0, zoom = 1} = flow.viewport;
                setNodes(flow.nodes || []);
                setEdges(flow.edges || []);

            }
        };

        restoreFlow();
    }, [setNodes]);

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
                data: {value: {isNewNode: true, name: type} },
            };

            setNodes((nds) => nds.concat(newNode));
        },
        [screenToFlowPosition, type],
    );


    const openAutomation = async (a) => {
      const detail = await getAutomationDetail(a.id);
        setNodes(detail.nodes || []);
        setEdges(detail.edges || []);

    }

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

    const handleCreateAction = () => {
        setIsModalOpen(true)
    }

    const defaultViewport = useMemo(() => ({x: 0, y: 50, zoom: 0.75}), []);

    const onDragStart = (event, nodeType) => {
        setType(nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };
    return (
        <div>
            {/*<CreateAction isOpen={isModalOpen} onClose={handleCloseModal} automations={automations}/>*/}
            {/*<AddActionDialog isOpen={isModalOpen} onClose={handleCloseModal}/>*/}
            <Stack direction="row" divider={<Divider orientation="vertical" flexItem/>}>
                <div style={{width: '80%', height: '100dvh'}} className="reactflow-wrapper" ref={reactFlowWrapper}>
                    <ReactFlow
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
                        className="validationflow"
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
                        <Panel position="bottom-right" style={{marginBottom: '50px'}}>
                            <Button variant='outlined' onClick={onRestore}>Restore</Button>
                            <Button variant='outlined' onClick={onSave} style={{marginLeft: '10px'}}>Save</Button>
                        </Panel>
                    </ReactFlow>
                    {/*<Card style={{margin: '10px', height: '18%', padding:'8px'}} variant='outlined'>*/}
                    {/*    hello*/}
                    {/*</Card>*/}
                </div>
                <div style={{width: '20%', height: '90dvh', marginTop: '50px'}}>
                    <Card style={{height: '100%', margin: '10px'}}>
                        <CardContent>
                            <Typography variant="h6" component="div">
                                Automation Playground
                            </Typography>
                            <Typography gutterBottom sx={{color: 'text.secondary', fontSize: 14}}>
                                Drag and drop nodes to create automations.
                            </Typography>
                            <div style={triggerStyle} onDragStart={(event) => onDragStart(event, 'trigger')}
                                 draggable>
                                Add Trigger
                            </div>
                            <div style={{...conditionStyle, marginTop: '10px'}}
                                 onDragStart={(event) => onDragStart(event, 'condition')} draggable>
                                Add Condition
                            </div>
                            <div style={{...actionStyle, marginTop: '10px'}}
                                 onDragStart={(event) => onDragStart(event, 'action')}
                                 draggable>
                                Add Action
                            </div>
                            <div style={{overflow: 'auto'}}>
                                <Typography>
                                    Saved Automations
                                </Typography>
                                {automations.map(a=>(
                                    <Card variant='outlined' style={{padding: '10px', marginTop: '10px'}} key={a.id} >
                                        {a.name}
                                        <Button size='small' onClick={()=> openAutomation(a)}>
                                            Open
                                        </Button>
                                    </Card>
                                ))}
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
