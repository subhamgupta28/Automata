import React, {useEffect, useState} from "react";
import {Handle, Position} from "@xyflow/react";
import {getDevices, refreshDeviceById, sendAction} from "../../services/apis.jsx";
import {
    Alert,
    Button,
    Card,
    CardContent,
    CardHeader,
    Dialog, DialogActions,
    DialogContent,
    DialogTitle,
    Modal,
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
                    <Button className={'btn-sm btn btn-secondary'} onClick={handleReboot}>
                        Reboot
                    </Button>
                    <Button style={{marginLeft: '12px'}} className={'btn-sm btn btn-secondary'} onClick={handleSleep}>
                        Sleep
                    </Button>
                    <Button style={{marginLeft: '12px'}} className={'btn-sm btn btn-secondary'} onClick={handleUpdate}>
                        Update
                    </Button>
                </DialogTitle>
                <DialogContent style={{width: '400px', overflow: 'auto'}}>
                    <p>Attributes</p>
                    {device && device.attributes.map(attribute => (
                        <div key={attribute.id} className="mb-2">
                            <p>{attribute.displayName}: {attribute.units}</p>
                        </div>
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

    const [gaugeData, setGaugeData] = useState([{key: "", extras: {max:0, min:0}}]);
    const [sliderData, setSliderData] = useState([{key: "", extras: {max:0, min:0}}]);


    useEffect(() => {
        setGaugeData(data.value.attributes.filter((t) => t.type === "DATA|GAUGE"));
        setSliderData(data.value.attributes.filter((t) => t.type === "DATA|SLIDER"));
    }, [data.live]);


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
        <div className="text-updater-node">
            <Alert icon={false} variant="filled" severity={state}
                   style={{borderRadius: '16px', padding: '1px'}}>

                <Card style={{display: 'flex', borderRadius: '12px', marginLeft: '3px', marginRight: '3px'}}>
                    <CardContent style={{minWidth: '200px', alignItems: 'center'}}>
                        <Typography style={{display: 'flex', alignItems: 'center'}}>
                            {data.value.name}
                            <SvgIcon component={icon} inheritViewBox style={{marginLeft: '8px',}}/>
                            <IconButton onClick={handleOpenModal} variant='text' style={{marginLeft: '8px'}}>
                                <SettingsIcon/>
                            </IconButton>
                        </Typography>

                        {/*<ChartNode/>*/}

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
                                            <tr style={{width: '100%'}}>
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
    for (let i = 0; i < data.value.numOfDevices; i++) {
        nodeIds.push("main-node-" + i)
    }
    for (let i = 0; i < data.value.chartNodes; i++) {
        chartIds.push("chart-node-" + i)
    }
    // console.log("ids", chartIds, nodeIds)


    return (
        <div className="text-updater-node">
            <div style={{borderRadius: '16px'}}>


                <Card elevation={12} style={{
                    padding: '0px',
                    height: '400px',
                    borderRadius: '8px',
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
                    <CardContent style={{margin: '20px'}}>
                        <Typography>
                            Automata
                        </Typography>
                    </CardContent>
                    {nodeIds && nodeIds.map((id, index) => (
                        <Handle
                            type="target"
                            position={Position.Left}
                            id={id}
                            style={{top: 10 + index * 50}}
                            isConnectable={isConnectable}
                        />
                    ))}
                </Card>
            </div>
        </div>
    );
}
