import {useEffect, useState} from "react";
import {getDashboardDevices, getMainNodePos} from "../../services/apis.jsx";
import {createNodes} from "./EdgeNode.jsx";
import {Device} from "./Nodes.jsx";
import {DeviceDataProvider} from '../../services/DeviceDataProvider.jsx';
import {ReactFlowProvider} from "@xyflow/react";

export default function MobileView() {
    const [nodes, setNodes] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const pos = await getMainNodePos();
                const devices = await getDashboardDevices();
                const dev = devices.filter((d) => d.showInDashboard === true);
                const n = createNodes(dev, [], pos.x, pos.y);
                setNodes(n);
                console.log(n)
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, [setNodes]);

    return (
        <div style={{
            paddingTop:'50px',
            width:'250px',
            gridTemplateColumns: 'repeat(1, 1fr)',
            display: 'grid',
            gap: '4px',
        }}>
            <DeviceDataProvider>
                <ReactFlowProvider>
                    {nodes && nodes.map((node, index) => (
                        (node.id) !== 'main-node-1' && (
                                <div key={index}>
                                    <Device id={node.id} data={node.data} isConnectable={false}></Device>
                                </div>
                            )
                    ))}
                </ReactFlowProvider>

            </DeviceDataProvider>

        </div>
    )
}