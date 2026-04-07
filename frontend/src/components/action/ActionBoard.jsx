import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Panel,
    ReactFlow,
    useEdgesState,
    useNodesState,
    useReactFlow
} from "@xyflow/react";
import React, {createContext, memo, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {disableAutomation, getActions, getAutomationDetail, saveAutomationDetail} from "../../services/apis.jsx";

import {Button, Card, CardContent, Chip, Switch,} from "@mui/material";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import CustomEdge from "./CustomEdge.jsx";
import '@xyflow/react/dist/style.css';
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import {TriggerNode} from "./TriggerNode.jsx";
import {ActionNode} from "./ActionNode.jsx";
import {ConditionNode} from "./ConditionNode.jsx";
import {ValueReaderNode} from "./ValueReaderNode.jsx";
import {And, Or} from "./Conditions.jsx";


const triggerStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '100%',
    border: '2px solid #6DBF6D',
};

const actionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '100%',
    border: '2px solid #0288D1',
};

const conditionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '100%',
    border: '2px solid #FFEB3B',
};
const valueReaderStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '100%',
    border: '2px solid #9C27B0', // Purple tone
};
let id = 0;
const getId = (type) => `node_${type}_${id++}`;

function ActionBoardDetailComponent() {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    // useLayoutNodes();
    // const [isModalOpen, setIsModalOpen] = useState(false);
    const [automations, setAutomations] = useState([]);
    const [selectedAutomation, setSelectedAutomation] = useState({});
    const [automationDetail, setAutomationDetail] = useState({});
    // const {devices, loading, error} = useCachedDevices();
    // const handleCloseModal = () => setIsModalOpen(false);
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
            await fetchData();
        }

        if (rfInstance) {
            const flow = rfInstance.toObject();

            // Remove duplicate nodes by id
            const seenNodeIds = new Set();
            const uniqueNodes = flow.nodes.filter(node => {
                if (seenNodeIds.has(node.id)) return false;
                seenNodeIds.add(node.id);
                return true;
            });

            // Remove stale edges — only keep edges using current handle ids
            const validSourceHandles = new Set(['b', 'cond-positive', 'cond-negative', 'action-out']);
            const validTargetHandles = new Set(['b', 'cond-t', 'cond-positive', 'cond-negative', 'action-out']);

            // Also remove edges whose source/target node no longer exists
            const nodeIds = new Set(uniqueNodes.map(n => n.id));
            const cleanEdges = flow.edges.filter(edge => {
                const sourceValid = nodeIds.has(edge.source);
                const targetValid = nodeIds.has(edge.target);
                // Drop old cond-s handle edges — replaced by cond-positive/cond-negative
                const notLegacy = edge.sourceHandle !== 'cond-s';
                return sourceValid && targetValid && notLegacy;
            });

            // Remove duplicate edges by id
            const seenEdgeIds = new Set();
            const uniqueEdges = cleanEdges.filter(edge => {
                if (seenEdgeIds.has(edge.id)) return false;
                seenEdgeIds.add(edge.id);
                return true;
            });

            const cleanFlow = {
                ...flow,
                nodes: uniqueNodes,
                edges: uniqueEdges,
                id: automationDetail.id || ''
            };

            console.log("Saving clean flow", cleanFlow);
            saveFlow(cleanFlow);
            localStorage.setItem("flow", JSON.stringify(cleanFlow));
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
        setAutomationDetail(detail);

        const nodeIds = new Set((detail.nodes || []).map(n => n.id));

        // Filter out legacy edges on load
        const cleanEdges = (detail.edges || []).filter(edge => {
            return nodeIds.has(edge.source)
                && nodeIds.has(edge.target)
                && edge.sourceHandle !== 'cond-s';
        });

        setNodes(detail.nodes || []);
        setEdges(cleanEdges);
        id = detail.nodes.length + 1;
    }

    const clearBoard = () => {
        setSelectedAutomation({});
        setAutomationDetail({})
        setNodes(n => []);
        setEdges(e => []);
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

            const isNegative = connection.sourceHandle === 'cond-negative';
            const edge = {
                ...connection,
                type: 'custom-edge',
                data: {
                    color: isNegative ? '#f44336' : '#4caf50',
                }
            };
            console.log("connections", edge)
            setEdges((eds) => addEdge(edge, eds));
        },
        [setEdges],
    );

    const handleCreateAction = () => {
        setIsModalOpen(true)
    }
    useEffect(() => {
        console.log("ActionBoard mounted at:", window.location.pathname);
        return () => console.log("ActionBoard unmounted");
    }, []);

    const defaultViewport = useMemo(() => ({x: 0, y: 50, zoom: 0.75}), []);

    const onDragStart = (event, nodeType) => {
        setType(nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };
    return (
        <div style={{position: 'relative', zIndex: 0}}>
            <Stack direction="row">
                <div style={{width: '80%', height: '100dvh', borderRadius: '10px', padding: '10px 10px 10px 0px'}}
                     className="reactflow-wrapper" ref={reactFlowWrapper}>
                    <ReactFlow
                        style={{
                            borderRadius: '10px',
                            backgroundColor: 'transparent',
                            borderColor: 'rgb(255 255 255 / 18%)',
                            borderWidth: '2px',
                            borderStyle: 'dashed',
                            position: 'relative',
                            zIndex: 0,
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
                            and: And,
                            or: Or
                        }}
                    >
                        {/*<ZoomSlider position="bottom-left"/>*/}
                        {selectedAutomation && selectedAutomation.id && (
                            <Panel position="bottom-left" style={{marginBottom: '20px'}}>
                                <Typography variant="body2" color="textSecondary">
                                    Enabled: <Switch defaultChecked size="small"
                                                     checked={selectedAutomation.isEnabled}
                                                     onChange={handleDisableAutomation}/>
                                </Typography>
                            </Panel>
                        )}
                        <Panel position="bottom-right" style={{marginBottom: '20px'}}>
                            {/*<Button variant='outlined' onClick={onRestore}>Restore</Button>*/}
                            <Button size="small" variant='outlined' onClick={onSave}
                                    style={{marginLeft: '10px'}}>Save</Button>
                            <Button size="small" variant='outlined' onClick={clearBoard}
                                    style={{marginLeft: '10px'}}>Clear</Button>
                        </Panel>
                    </ReactFlow>
                </div>
                <div style={{width: '20%', height: '100dvh'}}>
                    <Card style={{
                        height: '97dvh',
                        display: 'flex',
                        flexDirection: 'column',
                        margin: '10px 10px 10px 0px',
                        borderRadius: '10px',
                        background: 'transparent',
                        backdropFilter: 'blur(6px)',
                        // backgroundColor: 'rgb(255 255 255 / 8%)',
                        borderColor: 'rgb(255 255 255 / 18%)',
                        borderWidth: '2px',
                        borderStyle: 'dashed',
                    }}>
                        <CardContent style={{
                            flex: 1,
                            padding: '16px',
                            display: 'flex',
                            flexDirection: 'column',
                            overflow: 'hidden',

                        }}>
                            <Typography variant="h6" component="div">
                                Automation Playground
                            </Typography>
                            <Typography gutterBottom sx={{color: 'text.secondary', fontSize: 14}}>
                                Drag and drop nodes to create automations.
                            </Typography>
                            <div style={{
                                padding: '10px',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center'
                            }}>

                                <div style={triggerStyle} onDragStart={(event) => onDragStart(event, 'trigger')}
                                     draggable>
                                    Add Trigger
                                </div>
                                <div
                                    style={{...conditionStyle, marginTop: '10px'}}
                                    onDragStart={(event) => onDragStart(event, 'condition')}
                                    draggable
                                >
                                    Add Condition
                                </div>
                                <div
                                    style={{...actionStyle, marginTop: '10px'}}
                                    onDragStart={(event) => onDragStart(event, 'action')}
                                    draggable
                                >
                                    Add Action
                                </div>
                                <div
                                    style={{...conditionStyle, marginTop: '10px'}}
                                    onDragStart={(event) => onDragStart(event, 'and')}
                                    draggable
                                >
                                    Add AND
                                </div>

                                <div
                                    style={{...conditionStyle, marginTop: '10px'}}
                                    onDragStart={(event) => onDragStart(event, 'or')}
                                    draggable
                                >
                                    Add OR
                                </div>
                            </div>
                            {/* Scrollable list container */}
                            <div style={{
                                flex: 1,
                                overflow: 'auto',
                                scrollbarWidth: "none",
                                marginTop: '16px',
                                padding: '10px'
                            }}>

                                <Typography>Saved Automations</Typography>
                                <List>
                                    {automations.map((a) => (
                                        <ListItem
                                            variant="outlined"
                                            component={Card}
                                            style={{
                                                padding: '6px', marginTop: '8px',
                                                background: 'transparent',
                                                backdropFilter: 'blur(6px)',
                                                borderColor: '#ffffff',
                                                backgroundColor: 'rgb(255 255 255 / 8%)',
                                            }}
                                            key={a.id}
                                        >
                                            <ListItemText>{a.name}</ListItemText>
                                            <Chip
                                                size="small"
                                                variant="outlined"
                                                key={a.id}
                                                color={a.isEnabled ? "primary" : "error"}
                                                label={a.isEnabled ? "Enabled" : "Disabled"}>
                                            </Chip>
                                            <Button size="small" onClick={() => openAutomation(a)}>
                                                Open
                                            </Button>
                                        </ListItem>
                                    ))}
                                </List>
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

const DnDProvider = ({children}) => {
    const [type, setType] = useState(null);

    return (
        <DnDContext.Provider value={[type, setType]}>
            {children}
        </DnDContext.Provider>
    );
}

const ActionBoardDetail = memo(ActionBoardDetailComponent);

const useDnD = () => {
    return useContext(DnDContext);
}
const ActionBoard = () => (

    // <ReactFlowProvider>
    <DnDProvider>
        <ActionBoardDetail/>
    </DnDProvider>
    // </ReactFlowProvider>

);
export default React.memo(ActionBoard);