import React, {useCallback, useEffect, useState} from 'react';
import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Background, Controls,
    ReactFlow,
    useEdgesState,
    useNodesState
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {Handle, Position} from '@xyflow/react';
import {getDevices, refreshDeviceById} from "../services/apis.jsx";
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import useWebSocket from "../services/useWebSocket.jsx";

const CustomModal = ({isOpen, onClose, device}) => {
    const fetchData = async () => {
        try {
            const data = await refreshDeviceById(device.id);
        } catch (err) {
            console.error("Failed to fetch devices:", err);
        }
    };
    const handleReboot = () => {

    }
    const handleUpdate = () => {


        fetchData();
    }

    const handleSleep = () => {

    }

    return (
        <>
            <Modal show={isOpen} onHide={onClose} centered>
                <Modal.Header>
                    <h5>Setting</h5>
                </Modal.Header>
                <Modal.Body>
                    <button className={'btn-sm btn btn-secondary'} onClick={handleReboot}>
                        Reboot
                    </button>
                    <button style={{marginLeft: '12px'}} className={'btn-sm btn btn-secondary'} onClick={handleSleep}>
                        Sleep
                    </button>
                    <button style={{marginLeft: '12px'}} className={'btn-sm btn btn-secondary'} onClick={handleUpdate}>
                        Update
                    </button>

                    <p>Attributes</p>
                    {device && device.attributes.map(attribute => (
                        <div key={attribute.id} className="mb-2">
                            <p>{attribute.displayName}: {attribute.units}</p>
                        </div>
                    ))}

                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={onClose}>
                        Close
                    </Button>
                    <Button variant="primary" onClick={onClose}>
                        Save Changes
                    </Button>
                </Modal.Footer>
            </Modal>
        </>
    );
};

function Device({data, isConnectable}) {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    let icon;
    let state;
    if (data.value["status"])
    if (data.value.status === 'ONLINE') {
        icon = 'bi bi-emoji-laughing-fill'; // Icon for connected
        state = 'alert alert-success'; // Icon for connected
    } else if (data.value.status === 'OFFLINE') {
        icon = 'bi bi-emoji-frown-fill'; // Icon for disconnected
        state = 'alert alert-danger'; // Icon for disconnected
    } else {
        icon = 'bi bi-emoji-neutral-fill'; // Default icon
        state = 'alert alert-warning'; // Default icon
    }


    return (
        <div className="text-updater-node">
            <div className={'card ' + state} style={{padding: '0px'}}>
                <div className="card-header" style={{display: 'flex', alignItems: 'center'}}>
                    <h6>
                        {data.value.name}
                        <i className={icon} style={{marginLeft: '8px'}}></i>
                    </h6>
                </div>
                <div className={'card-body'}>
                    <button onClick={handleOpenModal} className={'btn-sm btn'} style={{width: 'fit-content'}}>
                        <i className={'bi bi-gear-fill'}></i>
                    </button>
                    <div>


                    </div>
                </div>

                {data.live && (
                    <div className={'card-footer'}>
                        <table>
                            <tbody>
                            {
                                data.value.attributes.map(attribute => (
                                    <tr key={attribute.id}>
                                        <th>{attribute["displayName"]}</th>
                                        <td>{data.live[attribute["key"]]}</td>
                                    </tr>
                                ))
                            }
                            </tbody>
                        </table>
                    </div>
                )}


                <CustomModal isOpen={isModalOpen} onClose={handleCloseModal} device={data.value}/>
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

function MainNode({data, isConnectable}) {


    return (
        <div className="text-updater-node">
            <div className={'card'} style={{
                padding: '12px',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                <label>
                    <i className={'bi bi-motherboard-fill'} style={{marginRight: '8px'}}></i>
                    Automata
                </label>

            </div>
            <Handle
                type="target"
                position={Position.Bottom}
                id="main-node"
                isConnectable={isConnectable}
            />
        </div>
    );
}

const createNodes = (devices) => {
    const mainNode = {
        id: 'main-node-1',
        type: 'mainNode',
        position: {x: 300, y: 30}, // Adjust position as needed
        data: {value: {name: 'Main Node'}},
    };

    let index = 0;
    const deviceNodes = devices.map((device) => ({
        id: device.id,
        type: 'deviceNode',
        position: {x: index += 190, y: 260},
        data: {value: device},
    }));

    return [mainNode, ...deviceNodes]; // Include main node with device nodes
};

const createEdges = (devices) => {
    return devices.map(device => ({
        id: `edge-${device.id}`, // Unique edge ID
        source: `${device.id}`,     // The ID of the main node
        target: 'main-node-1',       // The ID of the device node
        targetHandle: 'main-node',       // Source handle ID if applicable
        animated: true,
        style: {strokeWidth: 2, stroke: '#006fff'}
    }));
};

const nodeTypes = {deviceNode: Device, mainNode: MainNode};

export default function DeviceNodes() {
    const [loading, setLoading] = useState(true);
    const {messages, sendMessage} = useWebSocket('/topic/data');
    const {messages: data, sendMessage: sendData} = useWebSocket('/topic/devices');
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    useEffect(() => {
        setNodes((nds) =>
            nds.map((node) => {
                if (node.id === messages.deviceId) {
                    let dt = node.data.value;
                    if (messages.deviceConfig !== undefined) {
                        dt = messages.deviceConfig;
                    }
                    return {
                        ...node,
                        style: {
                            ...node.style,
                        },
                        data: {value: dt, live: messages.data}
                    };
                }
                return node;
            }),
        );
    }, [messages, setNodes]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getDevices();
                // setDevices(data);
                setNodes(createNodes(data)); // Create nodes including the main node
                setEdges(createEdges(data)); // Create edges connecting devices to the main node
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [data]);


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

    return (
        <div className={'card'} style={{height: '80vh', borderRadius: '12px'}}>
            <ReactFlow
                colorMode="dark"
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                nodeTypes={nodeTypes}

                style={{width: '100%', height: '100%', borderRadius: '12px'}}
            >
                {/*<Background style={{width: '80%', height: '80%'}}/>*/}
                <Controls/>
            </ReactFlow>
        </div>
    );
}
