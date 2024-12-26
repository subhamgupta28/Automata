import useWebSocket from "../../services/useWebSocket.jsx";
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Controls, Handle, Panel, Position,
    ReactFlow,
    useEdgesState,
    useNodesState
} from "@xyflow/react";
import React, {useCallback, useEffect, useMemo, useState} from "react";
import {getActions} from "../../services/apis.jsx";
import CreateAction from "./CreateAction.jsx";
import {Button, Card, CardActions, CardContent, CardHeader, Fab} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import AddActionDialog from "./AddActionDialog.jsx";

export function ProducerNode({data, isConnectable}) {

    return (
        <div className="text-updater-node">
            <div className={'card ' + state} style={{padding: '0px'}}>
                <div className="card-header" style={{display: 'flex', alignItems: 'center'}}>
                    <h6 style={{display: 'contents'}}>
                        source
                    </h6>

                </div>
                <div className={'card-body'}>

                </div>
                <div className={'card-footer'}>
                </div>

            </div>
            <Handle
                type="source"
                position={Position.Top}
                id="b"
                isConnectable={isConnectable}
            />
        </div>
    );
}

export function ConsumerNode({data, isConnectable}) {


    return (
        <div className="text-updater-node">
            <div className={'card alert alert-warning'} style={{
                padding: '0px',
                width: '500px',
                borderRadius: '8px',
            }}>
                <div className={'card-header'}>
                    <div className="spinner-grow spinner-grow-sm" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </div>
                    <label>
                        <i className={'bi bi-motherboard-fill'} style={{marginLeft: '8px', marginRight: '12px'}}></i>
                        Automata
                    </label>
                </div>

                <Handle
                    type="target"
                    position={Position.Bottom}
                    id="consumer"
                    style={{left: 50}}
                    isConnectable={isConnectable}
                />

            </div>

        </div>
    );
}

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
    return (
        <Card style={triggerStyle}>
            <strong>{data.value.name}</strong>
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
            <strong>{data.value.key}</strong>
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
            <strong>{data.value.condition}</strong>
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

const customEdge = [
    {id: 'e1-2', source: '6759f552e4c261194473ef04', target: 'cond-id-0', animated: true},
    {id: 'e2-3', source: '676496740a15d707f30ed021', target: 'cond-id-1', animated: true},
    {id: 'e3-4', source: 'cond-id-0', target: 'act-id-0', animated: true},
    {id: 'e5-5', source: 'cond-id-0', target: 'act-id-1', animated: true},
    {id: 'e5-6', source: 'cond-id-1', target: 'act-id-2', animated: true},
    {id: 'e5-7', source: 'cond-id-1', target: 'act-id-3', animated: true},
    {id: 'e5-8', source: 'cond-id-1', target: 'act-id-4', animated: true},
    {id: 'e5-9', source: 'cond-id-1', target: 'act-id-5', animated: true},
    {id: 'e5-10', source: 'cond-id-1', target: 'act-id-6', animated: true},
    {id: 'e5-11', source: 'cond-id-1', target: 'act-id-7', animated: true},
];
const bull = (
    <Box
        component="span"
        sx={{ display: 'inline-block', mx: '2px', transform: 'scale(0.8)' }}
    >
        •
    </Box>
);

export default function ActionBoard(action) {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [automations, setAutomations] = useState([]);
    const handleCloseModal = () => setIsModalOpen(false);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getActions();
                setAutomations(data)
                const {nodes, edges} = createNodes(data)
                setNodes(nodes); // Create nodes including the main node
                setEdges(edges); // Create edges connecting devices to the main node
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


    return (
        <div >
            {/*<CreateAction isOpen={isModalOpen} onClose={handleCloseModal} automations={automations}/>*/}
            <AddActionDialog isOpen={isModalOpen} onClose={handleCloseModal}/>
            <Stack direction="row" divider={<Divider orientation="vertical" flexItem/>}>
                <div style={{width: '75%', height: '100dvh'}}>
                    <ReactFlow
                        colorMode="dark"
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        defaultViewport={defaultViewport}
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
                            <Typography gutterBottom sx={{ color: 'text.secondary', fontSize: 14 }}>
                                Create and manage automations
                            </Typography>

                        </CardContent>
                        <CardActions>
                            <Button size="small" onClick={handleCreateAction}>Add Node</Button>
                        </CardActions>
                    </Card>
                </div>
            </Stack>

        </div>
    );
}
