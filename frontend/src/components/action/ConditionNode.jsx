import {Handle, Position, useHandleConnections, useNodes, useNodesData, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import dayjs from "dayjs";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";
import {LocalizationProvider, MobileTimePicker} from "@mui/x-date-pickers";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";


const conditionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '220px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
};

export const ConditionNode = ({id, data, isConnectable}) => {
    const conditionData = data.conditionData || {
        condition: 'equal',
        valueType: 'int',
        below: '0',
        above: '0',
        value: '0',
        triggerKey: '',
        time: '2:20:05 AM',
        isExact: false,
        type: 'state'
    };
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [triggerData, setTriggerData] = useState({})
    const [triggerKey, setTriggerKey] = useState(conditionData.triggerKey)
    const [condition, setCondition] = useState(conditionData.condition)
    const [above, setAbove] = useState(conditionData.above)
    const [below, setBelow] = useState(conditionData.below)
    const [isRange, setIsRange] = useState(conditionData.isExact)
    const [conditionValue, setConditionValue] = useState(conditionData.value)
    const [time, setTime] = useState(dayjs(conditionData.time))
    const [type, setType] = useState(conditionData.type);
    const connections = useHandleConnections({
        type: 'target',
        id: 'cond-t'
    });
    const nodesData = useNodesData(
        connections.map((connection) => connection.source),
    );

    const nodes = useNodes();

    const conditionNodes = nodes.filter(node => node.type === 'trigger');


    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    useEffect(() => {
        let cd = data.conditionData;
        if (cd) {
            setCondition(cd.condition);
            setAbove(cd.above);
            setBelow(cd.below);
            setTriggerKey(cd.triggerKey)
            setIsRange(cd.isExact);
            setConditionValue(cd.value);
            setTime(dayjs(cd.time));
            setType(cd.type);
        }


    }, [data.conditionData])
    useEffect(() => {
        const triggerData = conditionNodes.length > 0 && conditionNodes[0].data.triggerData ? conditionNodes[0].data.triggerData : {
            keys: [],
            value: '',
            name: '',
            deviceId: '',
            type: ''
        };
        if (triggerData.keys.length > 0)
            setTriggerKey(triggerData.keys.filter(f => f.conditionId === id)[0].key)
        setTriggerData(triggerData);
        setType(triggerData.type);

    }, [conditionNodes]);

    useEffect(() => {

        updateNodeData(id, {
            conditionData: {
                condition: condition,
                triggerKey: triggerKey,
                valueType: 'int',
                below: below,
                above: above,
                type: type,
                value: conditionValue,
                isExact: isRange,
                time: time.format()
            }
        })
    }, [condition, conditionValue, below, above, isRange, time, type, triggerKey]);

    const handleChange = (e, select) => {
        const value = e.target.value;
        if (select === 'value') {
            setConditionValue(value);
        } else if (select === 'condition') {
            setIsRange(value === 'equal');
            setCondition(value);
        } else if (select === 'above') {
            setAbove(value);
        } else if (select === 'below') {
            setBelow(value);
        } else if (select === 'time') {
            setTime(e);

            console.log("time", time.format());

        }
    }

    return (
        <Card style={{...conditionStyle, padding: '10px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id="cond-t"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>
            <IconButton onClick={() => deleteNode(id)} style={{position: 'absolute', top: '0', right: '0'}}>
                <DeleteIcon/>
            </IconButton>

            {type === 'time' ? (
                <div style={{marginTop: '18px'}}>
                    <Typography variant="body2" sx={{margin: 1}}>
                        Run automation at specific time of the day
                    </Typography>
                    <LocalizationProvider dateAdapter={AdapterDayjs}>
                        <MobileTimePicker format="hh:mm:ss A" value={time} onChange={(e) => handleChange(e, 'time')}/>
                    </LocalizationProvider>

                </div>

            ) : (
                <div style={{marginBottom: '18px'}}>
                    <Typography variant="body1" fontWeight="bold" sx={{marginLeft:1}}>
                        When {triggerKey} is
                    </Typography>
                    <FormControl fullWidth className='nodrag' sx={{marginBottom: 2, marginTop: 2}}>
                        <InputLabel id="demo-simple-select-label">Condition</InputLabel>
                        <Select
                            labelId="demo-simple-select-label"
                            id="demo-simple-select"
                            value={condition}
                            size='small'
                            label="Condition"
                            name="condition"
                            onChange={(e) => handleChange(e, 'condition')}
                            variant='outlined'>
                            <MenuItem value={'equal'}> equal to</MenuItem>
                            <MenuItem value={'range'}> between </MenuItem>
                            <MenuItem value={'above'}> above </MenuItem>
                            <MenuItem value={'below'}> below </MenuItem>
                        </Select>
                    </FormControl>

                    {isRange || condition === 'above' || condition === 'below' ? (
                        <TextField
                            size='small'
                            label="Value"
                            fullWidth
                            value={conditionValue}
                            onChange={(e) => handleChange(e, 'value')}
                            name="value"
                        />
                    ) : (
                        <div>
                            <TextField
                                size='small'
                                label="Below"
                                fullWidth
                                value={below}
                                onChange={(e) => handleChange(e, 'below')}
                                name="value"
                                sx={{marginBottom: 2}}
                            />
                            <TextField
                                size='small'
                                label="Above"
                                fullWidth
                                value={above}
                                onChange={(e) => handleChange(e, 'above')}
                                name="value"
                            />
                        </div>
                    )}
                    {/*<Typography variant="body1" style={{margin: '18px'}}>*/}
                    {/*    trigger the actions.*/}
                    {/*</Typography>*/}
                </div>
            )}

            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B'}}
                type="source"
                position={Position.Right}
                id="cond-s"
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'/>
        </Card>
    );
};