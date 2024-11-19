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
            style: {stroke: '#ffffff', strokeWidth: '3px'}
        })
        index++;
    });

    index = 0
    charts.map((device) => {
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

export const createNodes = (devices, charts, chart) => {

    const initX = 1150;
    const initY = 120;
    let index = 120;
    let deviceNodes = [];
    let chartNodes = [];


    const startX = 120;
    const startY = 40;
    const stepX = 280; // Increment value
    const stepY = 400; // Increment value
    const n = devices.length;

    const dim = {
        MAP: 480,
    }

    let nextStep = 0;
    let nextI = 0;
    // for (let i = 0; i < n; i++) {
    //     const map = devices[i].attributes.filter(f => f.type === "DATA|MAIN,MAP");
    //     let step = 3;
    //     if (nextStep !== 0) {
    //         step = nextStep;
    //         nextStep = 0;
    //         nextI = 0;
    //     } else {
    //         nextI = i;
    //     }
    //     if (map.length > 0) {
    //         nextStep = 2;
    //         nextI = 1;
    //     }
    //
    //
    //     const x = startX + (nextI % step) * stepX; // Alternates between 120 and 320
    //     const y = startY + Math.floor(i / step) * stepY;
    //     console.log(i, i % step, devices[i].name, step, x, y, nextStep)
    //     deviceNodes.push({
    //         id: devices[i].id,
    //         type: 'deviceNode',
    //         position: {x: x, y: y},
    //         data: {value: devices[i], live: {...devices[i].lastData}},
    //     });
    // }

    devices.map(device => {
        deviceNodes.push({
            id: device.id,
            type: 'deviceNode',
            position: {x: startX + device.x, y:  device.y},
            data: {value: device, live: {...device.lastData}},
        });
    })

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
        position: {x: initX, y: 40}, // Adjust position as needed
        data: {value: {numOfDevices: devices.length, chartNodes: chartNodes.length, chart}},
    };

    return [mainNode, ...deviceNodes, ...chartNodes]; // Include main node with device nodes
};