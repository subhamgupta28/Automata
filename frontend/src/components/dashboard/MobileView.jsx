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
    }, []);

    return (
        <div style={{
            paddingTop: '50px',
            columnCount: 2, // Change number of columns as needed
            columnGap: '12px',
            maxWidth: '100%',
        }}>
            <DeviceDataProvider>
                <ReactFlowProvider>
                    {nodes && nodes.map((node, index) => (
                        node.id !== 'main-node-1' && (
                            <div key={index} style={{
                                breakInside: 'avoid',
                                marginBottom: '12px'
                            }}>
                                <Device id={node.id} data={node.data} isConnectable={false} />
                            </div>
                        )
                    ))}
                </ReactFlowProvider>
            </DeviceDataProvider>
        </div>
    );
}
