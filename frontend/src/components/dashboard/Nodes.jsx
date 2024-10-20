import React, {useState} from "react";
import {Handle, Position} from "@xyflow/react";
import {refreshDeviceById} from "../../services/apis.jsx";
import Modal from "react-bootstrap/Modal";
import Button from "react-bootstrap/Button";

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

export function Device({data, isConnectable}) {
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
                    <h6 style={{display: 'contents'}}>
                        {data.value.name}
                        <i className={icon} style={{marginLeft: '8px'}}></i>
                        <button onClick={handleOpenModal} className={'btn-sm btn'} style={{ marginLeft: '8px'}}>
                            <i className={'bi bi-gear-fill'}></i>
                        </button>
                    </h6>

                </div>
                <div className={'card-body'}>

                    {data.live && (

                        <table>
                        <tbody>
                            {
                                data.value.attributes.map(attribute => (
                                    <tr key={attribute.id}>
                                        <th>{attribute["displayName"]}</th>
                                        <td>{data.live[attribute["key"]]}</td>
                                        <td>{attribute["units"]}</td>
                                    </tr>
                                ))
                            }
                            </tbody>
                        </table>

                    )}
                </div>
                <div className={'card-footer'}>
                </div>

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

export function MainNode({data, isConnectable}) {
    let nodeIds = []
    for (let i = 0; i < data.value.numOfDevices; i++) {
        nodeIds.push("main-node-" + i)
    }
    console.log("ids", data)


    return (
        <div className="text-updater-node">
            <div className={'card alert alert-warning'} style={{
                padding: '0px',
                width: '300px',
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
                <div className={'card-body'}>
                    All systems working properly
                </div>
                {nodeIds.map((id, index) => (
                    <Handle
                        type="target"
                        position={Position.Bottom}
                        id={id}
                        style={{left: 10 + index * 30}}
                        isConnectable={isConnectable}
                    />
                ))}
            </div>

        </div>
    );
}
