import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges, Panel,
    ReactFlow, ReactFlowProvider,
    useEdgesState, useNodesState, useReactFlow
} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useState, createContext, useContext, useRef} from "react";
import {disableAutomation, getActions, getAutomationDetail, saveAutomationDetail} from "../../services/apis.jsx";

import {
    Button,
    Card,
    CardContent, Switch,
} from "@mui/material";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import {ActionNode, ConditionNode, TriggerNode} from "./NodeTypes.jsx";
import CustomEdge from "./CustomEdge.jsx";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";
import '@xyflow/react/dist/style.css';
import EdgeLine from "./EdgeLine.jsx";

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

let id = 0;
const getId = (type) => `node_${type}_${id++}`;

export function ActionBoard(action) {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [automations, setAutomations] = useState([]);
    const [selectedAutomation, setSelectedAutomation] = useState({});
    const [automationDetail, setAutomationDetail] = useState({});
    const {devices, loading, error} = useCachedDevices();
    const handleCloseModal = () => setIsModalOpen(false);
    const reactFlowWrapper = useRef(null);
    const {screenToFlowPosition} = useReactFlow();
    const [type, setType] = useDnD();
    const [rfInstance, setRfInstance] = useState(null);
    const fetchData = async () => {
        try {
            // const res = await getDevices();
            // setDevices(devices);
            const data = await getActions();
            setAutomations(data)
            // const {nodes, edges} = createNodes(data)
            // setNodes(nodes); // Create nodes including the main node
            // setEdges(edges); // Create edges connecting devices to the main node
        } catch (err) {
            console.error("Failed to fetch devices:", err);
        }
    };
    useEffect(() => {
        fetchData();
    }, []);

    const isValidConnection = useCallback((connection) => {

        console.log("connection", connection)
        return connection.target === 'node_1';
    }, []);
    const onSave = useCallback(() => {
        const saveFlow = async (payload) => {
            console.log("saveFlow", payload);
            await saveAutomationDetail(payload);
            fetchData();
        }

        if (rfInstance) {
            const flow = rfInstance.toObject();
            console.log("detail", automationDetail)
            saveFlow({...flow, id: automationDetail.id ||''});
            // console.log("flow", flow)
            localStorage.setItem("flow", JSON.stringify(flow));
        }
    }, [rfInstance, automationDetail]);

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

    const handleDisableAutomation = async (e) => {
        await disableAutomation(selectedAutomation.id, e.target.checked)
    }


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


    const openAutomation = async (a) => {
        setSelectedAutomation(a);
        const detail = await getAutomationDetail(a.id);
        console.log("detail", detail);
        setAutomationDetail(detail);
        setNodes(n=> []);
        setEdges(e=> []);
        setNodes(detail.nodes || []);
        setEdges(detail.edges || []);
        id = detail.nodes.length + 1;
    }

    const clearBoard = () => {
        setSelectedAutomation({});
        setAutomationDetail({})
        setNodes(n=> []);
        setEdges(e=> []);
        fetchData();
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
                        // connectionLineComponent={EdgeLine}
                        className="validationflow"
                        onDragOver={onDragOver}
                        nodeTypes={{
                            trigger: TriggerNode,
                            action: ActionNode,
                            condition: ConditionNode,
                        }}
                    >
                        {selectedAutomation && selectedAutomation.id && (
                            <Panel position="bottom-left" style={{marginBottom: '50px'}}>
                                <Card variant='outlined' style={{padding: '10px'}}>
                                    <Typography variant="body2" color="textSecondary">
                                        Enabled: <Switch defaultChecked size="small"
                                                         checked={selectedAutomation.isEnabled}
                                                         onChange={handleDisableAutomation}/>
                                    </Typography>
                                </Card>
                            </Panel>
                        )}
                        <Panel position="bottom-right" style={{marginBottom: '50px'}}>
                            {/*<Button variant='outlined' onClick={onRestore}>Restore</Button>*/}
                            <Button variant='outlined' onClick={onSave} style={{marginLeft: '10px'}}>Save</Button>
                            <Button variant='outlined' onClick={clearBoard} style={{marginLeft: '10px'}}>Clear</Button>
                        </Panel>
                    </ReactFlow>
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
                                {automations.map(a => (
                                    <Card variant='outlined' style={{padding: '10px', marginTop: '10px'}} key={a.id}>
                                        {a.name}
                                        <Button size='small' onClick={() => openAutomation(a)}>
                                            Open
                                        </Button>
                                    </Card>
                                ))}
                            </div>
                        </CardContent>
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
