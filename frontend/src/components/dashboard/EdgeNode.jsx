export const createEdges = (devices, charts) => {
    let edges = [];
    let index = 0;

    devices.map(device => {
        edges.push({
            id: `edge-${device.id}`, // Unique edge ID
            source: `${device.id}`,     // The ID of the main node
            target: 'main-node-1',
            //type: 'animatedSvg',// The ID of the device node
            targetHandle: 'main-node-' + index,       // Source handle ID if applicable
            animated: true,
            style: {stroke: '#006fff', strokeWidth: '4px'}
        })
        index++;
    });

    index = 0
    charts.map((device)=>{
        edges.push({
            id: `edge-chart-${device.id}`,
            source: `chart-${device.id}`,
            target: 'main-node-1',
            //type: 'animatedSvg',// The ID of the device node
            targetHandle: 'chart-node-' + index,       // Source handle ID if applicable
            animated: true,
            style: {stroke: '#ff832a', strokeWidth: '4px'}
        })
        index++;
    })

    console.log(edges)

    return [...edges]
};

export const createNodes = (devices, charts) => {

    const initX = 900;
    const initY = 120;
    let index = 120;
    let deviceNodes = [];
    let chartNodes = [];


    const startX = 120;
    const startY = 100;
    const step = 350; // Increment value
    const n = devices.length;

    for (let i = 0; i < n; i++) {
        const x = startX + (i % 2) * step; // Alternates between 120 and 320
        const y = startY + Math.floor(i / 2) * step; // Increments y every two points
        deviceNodes.push({
            id: devices[i].id,
            type: 'deviceNode',
            position: {x: x, y: y},
            data: {value: devices[i], live: {...devices[i].lastData}},
        });
    }

    index = 1320;
    charts.map((device) => {
        chartNodes.push({
            id: 'chart-' + device.id,
            type: 'lineChartNode',
            position: {x: index, y: 100},
            data: {value: device},
        });
        index += 540
    })


    const mainNode = {
        id: 'main-node-1',
        type: 'mainNode',
        position: {x: initX, y: 200}, // Adjust position as needed
        data: {value: {numOfDevices: devices.length, chartNodes: chartNodes.length}},
    };

    return [mainNode, ...deviceNodes, ...chartNodes]; // Include main node with device nodes
};