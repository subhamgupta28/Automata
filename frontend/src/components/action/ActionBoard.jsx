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
import React, {useCallback, useEffect, useState} from "react";
import {getActions} from "../../services/apis.jsx";
import CreateAction from "./CreateAction.jsx";
import {Card, Fab} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";

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
    let x = 40;
    let y = 40;


    let sct = 0;
    let ccd = 0;
    let ax = x + 600;
    let ay = y + 40;
    let cy = y + 40;
    let cx = x + 300;
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


        actions.map(((act, index) => {
            actionNode.push({
                id: "act-id-" + sct,
                type: 'action',
                position: {x: ax, y: ay},
                data: {value: act},
            });
            ay += 60;
            sct++;
        }));


        condition.map((cond, index) => {
            conditionNode.push({
                id: "cond-id-" + ccd,
                type: 'condition',
                position: {x: cx, y: cy},
                data: {value: cond},
            });
            cy += 80;
            ccd++;
        });


    })

    console.log("trigger", triggerNode);
    console.log("action", actionNode);
    console.log("condition", conditionNode);
    return [...triggerNode, ...actionNode, ...conditionNode];
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
    {id: 'e5-6', source: 'cond-id-1', target: 'act-id-1', animated: true},
    {id: 'e6-7', source: '6', target: '7', animated: true},
    {id: 'e7-8', source: '7', target: '8', animated: true},
];


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
                setNodes(createNodes(data)); // Create nodes including the main node
                setEdges(customEdge); // Create edges connecting devices to the main node
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

    return (
        <div style={{height: '92dvh'}}>
            <CreateAction isOpen={isModalOpen} onClose={handleCloseModal} automations={automations}/>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                nodeTypes={{
                    trigger: TriggerNode,
                    action: ActionNode,
                    condition: ConditionNode,
                }}
            >
                <Panel position="bottom-right" style={{marginBottom: '50px'}}>
                    <Fab color="primary" aria-label="add" onClick={handleCreateAction}>
                        <EditIcon/>
                    </Fab>
                </Panel>

            </ReactFlow>
        </div>
    );
}
