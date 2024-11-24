import React, {useEffect, useState} from "react";
import {Handle, Position} from "@xyflow/react";
import {getChartData, getDevices, refreshDeviceById, sendAction} from "../../services/apis.jsx";
import {
    Alert,
    Button,
    Card, CardActions,
    CardContent,
    CardHeader, Chip,
    Dialog, DialogActions,
    DialogContent,
    DialogTitle,
    Modal, Slider,
    SvgIcon
} from "@mui/material";
import Typography from "@mui/material/Typography";
import SettingsIcon from '@mui/icons-material/Settings';
import MoodIcon from '@mui/icons-material/Mood';
import MoodBadIcon from '@mui/icons-material/MoodBad';
import SentimentDissatisfiedIcon from '@mui/icons-material/SentimentDissatisfied';
import IconButton from "@mui/material/IconButton";
import {GaugeChart} from "../charts/GaugeChart.jsx";
import {CustomSlider} from "../charts/CustomSlider.jsx";
import ChartNode from "../charts/ChartNode.jsx";
import {axisClasses} from "@mui/x-charts/ChartsAxis";
import {BarChart, ChartContainer, lineElementClasses, LinePlot, markElementClasses, MarkPlot} from "@mui/x-charts";
import {createEdges, createNodes} from "./EdgeNode.jsx";
import MapView from "../charts/MapView.jsx";
import {LineChart} from "@mui/x-charts/LineChart";


const CustomModal = ({isOpen, onClose, device}) => {
    const fetchData = async () => {
        try {
            const data = await refreshDeviceById(device.id);
        } catch (err) {
            // console.error("Failed to fetch devices:", err);
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
        <React.Fragment>
            <Dialog onClose={onClose} open={isOpen}>
                <DialogTitle>
                    <Button onClick={handleReboot}>
                        Reboot
                    </Button>
                    <Button style={{marginLeft: '12px'}} onClick={handleSleep}>
                        Sleep
                    </Button>
                    <Button style={{marginLeft: '12px'}} onClick={handleUpdate}>
                        Update
                    </Button>
                </DialogTitle>
                <DialogContent style={{width: '400px', overflow: 'auto'}}>
                    <p>Attributes</p>
                    {device && device.attributes.map(attribute => (
                        <Chip className='nodrag' clickable
                              style={{margin: '4px'}} label={attribute.displayName} color="info"/>
                    ))}
                </DialogContent>
                <DialogActions>
                    <Button variant="secondary" onClick={onClose}>
                        Close
                    </Button>
                    <Button variant="primary" onClick={onClose}>
                        Save Changes
                    </Button>
                </DialogActions>
            </Dialog>
        </React.Fragment>
    );
};

export function Device({data, isConnectable}) {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const handleOpenModal = () => setIsModalOpen(true);
    const handleCloseModal = () => setIsModalOpen(false);
    let icon;
    let state;

    const gaugeData = data.value.attributes.filter((t) => t.type === "DATA|GAUGE");
    const sliderData = data.value.attributes.filter((t) => t.type === "DATA|SLIDER");
    const map = data.value.attributes.filter((t) => t.type.startsWith("DATA|MAIN,MAP"));


    const handleAction = (attribute) => {
        const send = async () => {
            try {
                let act = attribute.key;
                await sendAction(data.value.id, {"key": attribute.key, [act]: 200, "device_id": data.value.id});
            } catch (err) {
                // console.error("Action send failed", err);
            }
        };
        send();
    }
    if (data.value["status"])
        if (data.value.status === 'ONLINE') {
            icon = MoodIcon; // Icon for connected
            state = 'success'; // Icon for connected
        } else if (data.value.status === 'OFFLINE') {
            icon = MoodBadIcon; // Icon for disconnected
            state = 'error'; // Icon for disconnected
        } else {
            icon = SentimentDissatisfiedIcon; // Default icon
            state = 'warning'; // Default icon
        }


    return (
        <div className="text-updater-node" key={data.value.id}>
            <Alert icon={false} variant="filled" severity={state}
                   style={{borderRadius: '16px', padding: '1px'}}>

                <Card style={{display: 'flex', borderRadius: '12px', marginLeft: '2px', marginRight: '2px'}}>
                    <CardContent
                        style={{minWidth: '200px', alignItems: 'center', paddingTop: '6px', paddingBottom: '6px'}}>
                        <Typography style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                            {data.value.name}
                            {/*<SvgIcon component={icon} inheritViewBox style={{marginLeft: '8px',}}/>*/}
                            <IconButton onClick={handleOpenModal} variant='text' style={{marginLeft: '8px'}}>
                                <SettingsIcon/>
                            </IconButton>
                        </Typography>

                        {/*<ChartNode/>*/}

                        {map.length > 0 && data.live && (
                            <MapView lat={data.live.LAT} lng={data.live.LONG}/>
                        )}

                        {gaugeData && data.live && gaugeData.map((gauge) => (
                            <GaugeChart value={data.live[gauge.key]} maxValue={gauge.extras.max}
                                        displayName={gauge.displayName}/>
                        ))}

                        {sliderData && data.live && sliderData.map((slide) => (
                            <CustomSlider value={data.live[slide.key]} deviceId={data.value.id} data={slide}
                                          displayName={slide.displayName}/>
                        ))}


                        {data.live && (
                            <table style={{width: '100%', marginTop: '12px'}}>
                                <tbody style={{padding: '0'}}>
                                {
                                    data.value.attributes.map(attribute => (
                                        (attribute.type === "DATA|MAIN") && (
                                            <tr key={attribute.id}>
                                                <td>{attribute["displayName"]}</td>
                                                <td>{data.live[attribute["key"]]}</td>
                                                <td>{attribute["units"]}</td>
                                            </tr>
                                        )
                                    ))
                                }
                                {
                                    data.value.attributes.map(attribute => (
                                        (attribute.type === "ACTION|OUT") && (
                                            <tr style={{width: '100%'}} key={attribute.id}>
                                                <th colSpan="3">
                                                    <Button aria-label="delete" style={{width: '100%'}}
                                                            onClick={() => handleAction(attribute)}>
                                                        {attribute["displayName"]}
                                                    </Button>
                                                </th>
                                            </tr>
                                        )
                                    ))
                                }
                                </tbody>
                            </table>
                        )}
                    </CardContent>
                </Card>

                <CustomModal isOpen={isModalOpen} onClose={handleCloseModal} device={data.value}/>
            </Alert>
            <Handle
                type="source"
                position={Position.Right}
                id="b"
                style={{top: 30}}
                isConnectable={isConnectable}
            />
        </div>
    );
}

export function MainNode({data, isConnectable}) {
    let nodeIds = []
    let chartIds = []
    const charts = data.value.devices.filter(device =>
        device.attributes.some(attr => attr.type === "DATA|CHART")
    );
    console.log("main", charts[0].attributes[0].key)
    const [chartData, setChartData] = useState({
        dataKey: "p",
        data: [0],
        label: "p",
        attributes: [],
        timestamps: [""]
    });
    const [selectedAttribute, setAttribute] = useState(charts[0].attributes[0].key)
    for (let i = 0; i < data.value.numOfDevices; i++) {
        nodeIds.push("main-node-" + i)
    }
    for (let i = 0; i < data.value.chartNodes; i++) {
        chartIds.push("chart-node-" + i)
    }

    useEffect(() => {
        const fetch = async () => {
            const data = await getChartData("6713fd6118af335020f90f73", selectedAttribute);
            console.log("chart", data)
            setChartData(data);
        }

        fetch();
    }, [selectedAttribute]);

    const handleAttribute = (key) => {
        setAttribute(key);
        console.log(key)
    }


    return (
        <div className="text-updater-node">
            <div style={{borderRadius: '16px'}}>
                <Card elevation={12} style={{
                    padding: '0px',
                    minHeight: '500px',
                    minWidth: '400px',
                    // height: '800px',
                    // width: '1550px',
                    borderRadius: '18px',
                }}>
                    {chartIds && chartIds.map((id, index) => (
                        <Handle
                            type="target"
                            position={Position.Right}
                            id={id}
                            style={{top: 10 + index * 50}}
                            isConnectable={isConnectable}
                        />
                    ))}
                    <CardContent style={{margin: '12px'}}>
                        <BarChartComp data={data.value.devices} chartData={chartData}/>

                    </CardContent>
                    <CardActions style={{display: 'flex', justifyContent: 'center'}}>
                        <Typography>
                            Attributes
                        </Typography>
                        <div style={{margin: '14px'}}>
                            {chartData && chartData.attributes.map((name) => (
                                <Chip className='nodrag' clickable onClick={() => handleAttribute(name)}
                                      style={{margin: '4px'}} label={name} color="warning"/>
                            ))}
                        </div>
                    </CardActions>
                    {nodeIds && nodeIds.map((id, index) => (
                        <Handle
                            type="target"
                            position={Position.Left}
                            id={id}
                            style={{top: 140 + index * 50}}
                            isConnectable={isConnectable}
                        />
                    ))}
                </Card>
            </div>
        </div>
    );
}


function BarChartComp({chartData}) {

    const valueFormatter = (value) => {
        return `${value} ${chartData.unit}`;
    }

    const chartSetting = {
        yAxis: [
            {
                label: chartData.label,
            },
        ],
        series: [{dataKey: chartData.dataKey, valueFormatter}],
        height: 500,
        width: 1300,
        sx: {
            [`& .${axisClasses.directionY} .${axisClasses.label}`]: {
                transform: 'translateX(-10px)',
            },
        },
    };


    return (
        <div>
            <BarChart className="nodrag"
                      dataset={chartData.data}
                      xAxis={[
                          {scaleType: 'band', dataKey: chartData.dataKey, data: chartData.timestamps},
                      ]}
                      borderRadius={10}
                      {...chartSetting}
            />
        </div>
    )
}
