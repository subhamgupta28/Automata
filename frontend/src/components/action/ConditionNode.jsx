import {Handle, Position, useNodeConnections, useNodes, useNodesData, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import dayjs from "dayjs";
import {Card, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";
import {DesktopTimePicker, LocalizationProvider, TimePicker} from "@mui/x-date-pickers";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";
import customParseFormat from "dayjs/plugin/customParseFormat";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";

dayjs.extend(customParseFormat);

const conditionStyle = {
    padding: '10px',
    borderRadius: '10px',
    width: '260px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
    background: 'transparent',
    backdropFilter: 'blur(6px)',
    backgroundColor: 'rgb(255 255 255 / 8%)',
    overflow: 'visible'
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
        isExact: true,
        type: 'state',
        scheduleType: 'at',
        fromTime: '2:20:05 AM',
        toTime: '2:20:05 AM',
        days: ['Everyday'],
        solarType: 'sunset',
        offsetMinutes: 0,
        intervalMinutes: 30,
        durationMinutes: 5
    };
    const [scheduleType, setScheduleType] = useState(conditionData.scheduleType); // 'at' | 'range'
    const [fromTime, setFromTime] = useState(
        conditionData.fromTime ? dayjs(conditionData.fromTime, "hh:mm:ss A") : dayjs()
    );
    const [toTime, setToTime] = useState(
        conditionData.toTime ? dayjs(conditionData.toTime, "hh:mm:ss A") : dayjs()
    );
    const [days, setDays] = useState(conditionData.days); // ['Mon', 'Tue']
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [triggerData, setTriggerData] = useState({})
    const [triggerKey, setTriggerKey] = useState(conditionData.triggerKey)
    const [condition, setCondition] = useState(conditionData.condition)
    const [above, setAbove] = useState(conditionData.above)
    const [below, setBelow] = useState(conditionData.below)
    const [isRange, setIsRange] = useState(conditionData.isExact)
    const [conditionValue, setConditionValue] = useState(conditionData.value)
    const [solarType, setSolarType] = useState(conditionData.solarType); // 'sunrise' | 'sunset'
    const [intervalMinutes, setIntervalMinutes] = useState(conditionData.intervalMinutes);
    const [offsetMinutes, setOffsetMinutes] = useState(conditionData.offsetMinutes);
    const [durationMinutes, setDurationMinutes] = useState(conditionData.durationMinutes);


    const allDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

    const handleDaysChange = (event) => {
        const value = event.target.value;

        if (value.includes('Everyday')) {
            setDays(allDays);
        } else {
            setDays(value);
        }
    };

    const [time, setTime] = useState(
        conditionData.time ? dayjs(conditionData.time, "hh:mm:ss A") : dayjs()
    );
    const [type, setType] = useState(conditionData.type);
    const connections = useNodeConnections({
        handleType: 'target'
    });
    // console.log("condition", data, connections)
    const nodesData = useNodesData(
        connections.map((connection) => connection.source),
    );

    const nodes = useNodes();

    const conditionNodes = nodes.filter(node => node.type === 'trigger');


    const deleteNode = (nodeId) => {
        setNodes((nodes) => nodes.filter((node) => node.id !== nodeId)); // Remove the node
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
    }
    const prevConditionDataRef = React.useRef(null);
    useEffect(() => {
        let cd = data.conditionData;
        if (!cd) return;

        // ✅ Only update if actually different
        if (cd.condition !== condition) setCondition(cd.condition);
        if (cd.above !== above) setAbove(cd.above);
        if (cd.below !== below) setBelow(cd.below);
        if (cd.triggerKey !== triggerKey) setTriggerKey(cd.triggerKey);
        if (cd.isExact !== isRange) setIsRange(cd.isExact);
        if (cd.value !== conditionValue) setConditionValue(cd.value);
        if (cd.type !== type) setType(cd.type);

        // ⚠️ time needs special handling
        const newTime = dayjs(cd.time, "hh:mm:ss A");
        if (!newTime.isSame(time)) setTime(newTime);

        // ✅ NEW fields
        if (cd.scheduleType !== scheduleType) setScheduleType(cd.scheduleType || 'at');

        const newFrom = cd.fromTime ? dayjs(cd.fromTime, "hh:mm:ss A") : null;
        if (newFrom && !newFrom.isSame(fromTime)) setFromTime(newFrom);

        const newTo = cd.toTime ? dayjs(cd.toTime, "hh:mm:ss A") : null;
        if (newTo && !newTo.isSame(toTime)) setToTime(newTo);

        if (JSON.stringify(cd.days || []) !== JSON.stringify(days)) {
            setDays(cd.days || []);
        }

    }, [data.conditionData]);
    useEffect(() => {
        const triggerData = conditionNodes.length > 0 && conditionNodes[0].data.triggerData
            ? conditionNodes[0].data.triggerData
            : {
                keys: [],
                value: '',
                name: '',
                deviceId: '',
                type: ''
            };

        const matched = triggerData?.keys?.find(f => f.conditionId === id);

        setTriggerKey(matched?.key || '');
        setTriggerData(triggerData);
        setType(triggerData.type);

    }, [conditionNodes, id]);

    useEffect(() => {
        const previousNodeRef = connections.length > 0
            ? connections[connections.length - 1].sourceHandle
            : '';
        const newData = {
            nodeId: scheduleType !== undefined ? scheduleType : '',
            condition,
            triggerKey,
            valueType: 'int',
            below,
            above,
            type,
            value: conditionValue,
            isExact: isRange,
            time: time.format("hh:mm:ss A"),
            scheduleType,
            fromTime: fromTime.format("hh:mm:ss A"),
            toTime: toTime.format("hh:mm:ss A"),
            days,
            solarType,
            offsetMinutes,
            intervalMinutes,
            durationMinutes,
            enabled: connections.length > 0,
            previousNodeRef
        };

        if (JSON.stringify(data.conditionData) !== JSON.stringify(newData)) {
            updateNodeData(id, {conditionData: newData});
        }

    }, [
        condition,
        conditionValue,
        below,
        above,
        isRange,
        time,
        type,
        triggerKey,
        scheduleType,   // ✅ missing
        fromTime,       // ✅ missing
        toTime,         // ✅ missing
        days,           // ✅ missing
        solarType,
        offsetMinutes,
        intervalMinutes,
        durationMinutes,
        connections
    ]);

    const handleChange = (e, select) => {
        let value = e?.target?.value ?? e; // handles both normal events and dayjs objects
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
            if (e && e.isValid()) {
                setTime(e);
                // console.log("time", e.format("hh:mm:ss A"));
            } else {
                console.warn("Invalid time value:", e);
            }
        }
    }

    useEffect(() => {
        if (scheduleType === 'range' && fromTime.isAfter(toTime)) {
            console.warn("Invalid time range");
        }
    }, [fromTime, toTime]);
    return (
        <Card style={{...conditionStyle, padding: '10px'}}>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id={"cond-node-type-" + scheduleType}
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
                        <DesktopTimePicker format="hh:mm:ss A" value={time}
                                           onChange={(e) => handleChange(e, 'time')}/>
                    </LocalizationProvider>

                </div>

            ) : (
                <div style={{marginBottom: '18px'}}>
                    <Typography variant="body1" fontWeight="bold" sx={{marginLeft: 1}}>
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
                            <MenuItem value={'scheduled'}> Scheduled </MenuItem>
                        </Select>
                    </FormControl>

                    {condition === 'scheduled' ? (
                            <div>
                                <Typography variant="body2" sx={{mb: 2}}>
                                    Schedule automation
                                </Typography>

                                {/* Schedule Type */}
                                <FormControl className='nodrag' fullWidth size="small" sx={{mb: 2}}>
                                    <InputLabel>Schedule Type</InputLabel>
                                    <Select
                                        variant="outlined"
                                        value={scheduleType}
                                        label="Schedule Type"
                                        onChange={(e) => setScheduleType(e.target.value)}
                                    >
                                        <MenuItem value="at">At specific time</MenuItem>
                                        <MenuItem value="range">Between time range</MenuItem>
                                        <MenuItem value="solar">Sun-based</MenuItem>
                                        <MenuItem value="interval">Repeat every</MenuItem>
                                    </Select>
                                </FormControl>

                                {/* Time Pickers */}
                                <LocalizationProvider dateAdapter={AdapterDayjs}>
                                    {scheduleType === 'at' && (
                                        <TimePicker
                                            label="Time"
                                            value={time}
                                            onChange={(e) => e?.isValid() && setTime(e)}
                                        />
                                    )}
                                    {scheduleType === 'range' && (
                                        <div style={{display: 'flex', flexDirection: 'column', gap: '20px'}}>
                                            <TimePicker
                                                label="From"
                                                value={fromTime}
                                                onChange={(e) => e?.isValid() && setFromTime(e)}
                                            />
                                            <TimePicker
                                                label="To"
                                                value={toTime}
                                                onChange={(e) => e?.isValid() && setToTime(e)}
                                            />
                                        </div>
                                    )}

                                </LocalizationProvider>

                                {scheduleType === 'solar' && (
                                    <div>
                                        <FormControl className='nodrag' fullWidth size="small" sx={{mb: 2}}>
                                            <InputLabel>Event</InputLabel>
                                            <Select
                                                variant="outlined"
                                                value={solarType}
                                                label="Event"
                                                onChange={(e) => setSolarType(e.target.value)}
                                            >
                                                <MenuItem value="sunrise">Sunrise</MenuItem>
                                                <MenuItem value="sunset">Sunset</MenuItem>
                                            </Select>
                                        </FormControl>

                                        <TextField
                                            size="small"
                                            label="Offset (minutes)"
                                            type="number"
                                            value={offsetMinutes}
                                            onChange={(e) => setOffsetMinutes(Number(e.target.value))}
                                            helperText="Use negative for before (-60 = 1hr before)"
                                        />
                                    </div>
                                )}
                                {scheduleType === 'interval' && (
                                    <TextField
                                        size="small"
                                        label="Run every (minutes)"
                                        type="number"
                                        value={intervalMinutes}
                                        onChange={(e) => setIntervalMinutes(Number(e.target.value))}
                                    />
                                )}
                                <TextField
                                    size="small"
                                    label="Run for (minutes)"
                                    type="number"
                                    value={durationMinutes}
                                    onChange={(e) => setDurationMinutes(Number(e.target.value))}
                                    sx={{mt: 2}}
                                />
                                {/* Days Selector */}
                                <FormControl className='nodrag' fullWidth size="small" sx={{mt: 2}}>
                                    <InputLabel>Days</InputLabel>
                                    <Select
                                        variant="outlined"
                                        multiple
                                        label="Days"
                                        value={days}
                                        onChange={handleDaysChange}
                                        renderValue={(selected) => selected.join(', ')}
                                    >
                                        {['Everyday', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(day => (
                                            <MenuItem key={day} value={day}>
                                                <Checkbox checked={days.indexOf(day) > -1}/>
                                                <ListItemText primary={day}/>
                                            </MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                            </div>
                        ) :
                        (isRange || condition === 'above' || condition === 'below' ? (
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
                        ))
                    }
                    {/*<Typography variant="body1" style={{margin: '18px'}}>*/}
                    {/*    trigger the actions.*/}
                    {/*</Typography>*/}
                </div>
            )}

            {/*<Handle*/}
            {/*    style={{width: '18px', height: '18px', background: '#FFEB3B'}}*/}
            {/*    type="source"*/}
            {/*    position={Position.Right}*/}
            {/*    id="cond-s"*/}
            {/*    isConnectable={isConnectable}*/}
            {/*/>*/}

            <Handle
                style={{width: '26px', height: '26px', background: '#4caf50', top: '25%'}}
                type="source"
                position={Position.Right}
                id={"cond-positive-" + scheduleType}
                isConnectable={isConnectable}
            />

            <Handle
                style={{width: '26px', height: '26px', background: '#f44336', top: '75%'}}
                type="source"
                position={Position.Right}
                id={"cond-negative-" + scheduleType}
                isConnectable={isConnectable}
            />


            <div style={{
                background: 'transparent', top: '25%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'>
                Positive
            </div>
            <div style={{
                background: 'transparent',
                top: '75%',
                right: 0,
                transform: 'translate(50%, -50%)'
            }} className='react-flow__handle'>
                Negative
            </div>
        </Card>
    );
};