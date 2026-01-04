import {NodeResizer, useEdges, useNodes, useReactFlow} from "@xyflow/react";
import React, {useEffect} from "react";
import {Card} from "@mui/material";
import Typography from "@mui/material/Typography";

export function EnergyChildNode({id, data, isConnectable, selected}) {
    const {messages} = useDeviceLiveData();
    const [liveData, setLiveData] = useState(null);
    const {
        attributes,
    } = data.value;
    useEffect(() => {
        if (id === messages.deviceId) {
            if (messages.data) setLiveData(messages.data);

        }
    }, [messages, id]);
    const {
        mainData,
    } = useMemo(() => {
        const grouped = {
            mainData: [],
        };

        for (const attr of attributes) {
            if (attr.type === 'DATA|MAIN') grouped.mainData.push(attr);
        }

        return grouped;
    }, [attributes]);
    return (
        <Card>
            <CardContent>
                <div style={{
                    gridTemplateColumns: 'repeat(2, 1fr)',
                    display: 'grid',
                    gap: '4px',
                    marginTop: '10px'
                }}>
                    {attributes.map((m) => (
                        <Card
                            key={m.id}
                            elevation={0}
                            style={{
                                borderRadius: '8px',
                                padding: '4px',
                                display: 'flex',
                                flexDirection: 'column',
                                justifyContent: 'space-between',
                                alignItems: 'center'
                            }}
                        >

                            <Typography style={{display: 'flex', fontSize: '18px'}}
                                        fontWeight="bold">
                                {/*{m.displayName.includes("Temp") && <TemperatureGauge temp={liveData?.[m.key]}/>}*/}
                                {liveData?.[m.key]} {m.units}
                            </Typography>
                            <Typography>{m.displayName}</Typography>
                        </Card>
                    ))}
                </div>
            </CardContent>
        </Card>
    )
}


const initialNodes = [
    {
        id: 'B',
        type: 'input',
        data: {label: 'child node 1'},
        position: {x: 10, y: 10},
        parentId: '695925995435a3287696e30b',
        extent: 'parent',
    },
    {
        id: 'C',
        data: {label: 'child node 2'},
        position: {x: 10, y: 90},
        parentId: '695925995435a3287696e30b',
        extent: 'parent',
    },
];

const initialEdges = [
    {
        id: 'b-c',
        source: 'B',
        target: 'C',
        animated: true,
        style: {stroke: '#ffa500', strokeWidth: '4px'}
    }
];

const createNodes = (parentId, deviceIds, attributes) => {
    let nodes = [];
    deviceIds.map((device) => {
        console.log("attrib", )
        nodes.push({
            id: device,
            type: 'energyChildNode',
            position: {x: 50, y: 50},
            data: {value: {attributes: attributes[device]}},
            parentId,
            extent: 'parent',
        });
    })

    return nodes;
}

const {getInternalNode, setNodes, setEdges} = useReactFlow();
// const {messages} = useDeviceLiveData();

const nodes = useNodes();
const edges = useEdges();

useEffect(() => {
    setNodes((nodes) => {
        const exists = nodes.some((n) => n.id === 'A');
        if (exists) return nodes;

        return [...nodes, ...createNodes(id, deviceIds, attributes)];
    });

    setEdges((edges) => {
        const exists = edges.some((n) => n.id === 'b-c');
        if (exists) return edges;
        return [...edges, ...initialEdges]
    })
}, [setNodes, setEdges]);