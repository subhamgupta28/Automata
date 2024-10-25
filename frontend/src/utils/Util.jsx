

export const createEdges = (devices) => {
    let edges = [];
    let index = 0;

    devices.map(device => {
        edges.push({
            id: `edge-${device.id}`, // Unique edge ID
            source: `${device.id}`,     // The ID of the main node
            target: 'main-node-1',
            type: 'animatedSvg',// The ID of the device node
            targetHandle: 'main-node-' + index,       // Source handle ID if applicable
            animated: true,
            style: { stroke: '#006fff'}
        })
        index++;
    });
    return edges
};

export const createNodes = (devices) => {
    const mainNode = {
        id: 'main-node-1',
        type: 'mainNode',
        position: {x: 120, y: 20}, // Adjust position as needed
        data: {value: {numOfDevices: devices.length}},
    };

    let index = 120;
    let deviceNodes = [];
    devices.map((device) => {
        deviceNodes.push({
            id: device.id,
            type: 'deviceNode',
            position: {x: index, y: 220},
            data: {value: device},
        });
        index += 320
    });

    return [mainNode, ...deviceNodes]; // Include main node with device nodes
};