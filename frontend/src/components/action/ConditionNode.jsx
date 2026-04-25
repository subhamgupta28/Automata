import {Handle, Position, useNodeConnections, useNodes, useReactFlow} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import dayjs from "dayjs";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Chip,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import Typography from "@mui/material/Typography";
import {DesktopTimePicker, LocalizationProvider, TimePicker} from "@mui/x-date-pickers";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";
import customParseFormat from "dayjs/plugin/customParseFormat";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";

dayjs.extend(customParseFormat);

const conditionStyle = {
    borderRadius: '10px',
    width: '320px',
    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
    border: '2px solid #FFEB3B',
    background: 'transparent',
    backdropFilter: 'blur(6px)',
    backgroundColor: 'rgb(0 0 0 / 28%)',
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
        intervalMinutes: 0,
        durationMinutes: 0,
        deviceId: null,    // NEW: null = use primary trigger device
        nodeId: id
    };

    const [scheduleType, setScheduleType] = useState(conditionData.scheduleType);
    const [fromTime, setFromTime] = useState(
        conditionData.fromTime ? dayjs(conditionData.fromTime, "hh:mm:ss A") : dayjs()
    );
    const [toTime, setToTime] = useState(
        conditionData.toTime ? dayjs(conditionData.toTime, "hh:mm:ss A") : dayjs()
    );
    const [days, setDays] = useState(conditionData.days);
    const {updateNodeData, setNodes, setEdges} = useReactFlow();
    const [triggerData, setTriggerData] = useState({});
    const [triggerKey, setTriggerKey] = useState(conditionData.triggerKey);
    const [condition, setCondition] = useState(conditionData.condition);
    const [above, setAbove] = useState(conditionData.above);
    const [below, setBelow] = useState(conditionData.below);
    const [isRange, setIsRange] = useState(conditionData.isExact);
    const [conditionValue, setConditionValue] = useState(conditionData.value);
    const [solarType, setSolarType] = useState(conditionData.solarType);
    const [intervalMinutes, setIntervalMinutes] = useState(conditionData.intervalMinutes);
    const [offsetMinutes, setOffsetMinutes] = useState(conditionData.offsetMinutes);
    const [durationMinutes, setDurationMinutes] = useState(conditionData.durationMinutes);

    // NEW: which device this condition reads from
    // null / '' = primary trigger device (default, backward compatible)
    const [conditionDeviceId, setConditionDeviceId] = useState(conditionData.deviceId || '');

    const {devices} = useCachedDevices();
    const allDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

    const handleDaysChange = (event) => {
        const value = event.target.value;
        setDays(value.includes('Everyday') ? allDays : value);
    };

    const [time, setTime] = useState(
        conditionData.time ? dayjs(conditionData.time, "hh:mm:ss A") : dayjs()
    );
    const [type, setType] = useState(conditionData.type);

    const connections = useNodeConnections({handleType: 'target'});
    const nodes = useNodes();
    const conditionNodes = nodes.filter(node => node.type === 'trigger');

    const deleteNode = (nodeId) => {
        setNodes(nodes => nodes.filter(node => node.id !== nodeId));
        setEdges(eds => eds.filter(edge => edge.source !== nodeId && edge.target !== nodeId));
    };

    // ── Derive available attributes for the triggerKey dropdown ──────────
    // If condition has its own deviceId → show that device's attributes
    // Otherwise → show primary trigger device's attributes (existing behaviour)
    const availableAttributes = React.useMemo(() => {
        if (conditionDeviceId && devices) {
            const dev = devices.find(d => d.id === conditionDeviceId);
            return dev?.attributes || [];
        }
        // Fall back to primary trigger device attributes
        const triggerDev = triggerData?.deviceId && devices
            ? devices.find(d => d.id === triggerData.deviceId)
            : null;
        return triggerDev?.attributes || [];
    }, [conditionDeviceId, triggerData?.deviceId, devices]);

    // ── Secondary devices available in the trigger node ───────────────────
    const secondaryDevices = React.useMemo(() => {
        const sources = triggerData?.sources || [];
        return sources.filter(s => s.role === 'secondary');
    }, [triggerData?.sources]);

    const hasSecondaryDevices = secondaryDevices.length > 0;

    useEffect(() => {
        let cd = data.conditionData;
        if (!cd) return;
        if (cd.condition !== condition) setCondition(cd.condition);
        if (cd.above !== above) setAbove(cd.above);
        if (cd.below !== below) setBelow(cd.below);
        if (cd.triggerKey !== triggerKey) setTriggerKey(cd.triggerKey);
        if (cd.isExact !== isRange) setIsRange(cd.isExact);
        if (cd.value !== conditionValue) setConditionValue(cd.value);
        if (cd.type !== type) setType(cd.type);
        if ((cd.deviceId || '') !== conditionDeviceId) setConditionDeviceId(cd.deviceId || '');
        const newTime = dayjs(cd.time, "hh:mm:ss A");
        if (!newTime.isSame(time)) setTime(newTime);
        if (cd.scheduleType !== scheduleType) setScheduleType(cd.scheduleType || 'at');
        const newFrom = cd.fromTime ? dayjs(cd.fromTime, "hh:mm:ss A") : null;
        if (newFrom && !newFrom.isSame(fromTime)) setFromTime(newFrom);
        const newTo = cd.toTime ? dayjs(cd.toTime, "hh:mm:ss A") : null;
        if (newTo && !newTo.isSame(toTime)) setToTime(newTo);
        if (JSON.stringify(cd.days || []) !== JSON.stringify(days)) setDays(cd.days || []);
    }, [data.conditionData]);

    // Sync trigger key from trigger node
    useEffect(() => {
        const td = conditionNodes.length > 0 && conditionNodes[0].data.triggerData
            ? conditionNodes[0].data.triggerData
            : {keys: [], value: '', name: '', deviceId: '', type: '', sources: []};

        if (conditionDeviceId) {
            // Secondary device selected — find the key the user picked for this
            // device in the trigger node's sources list and auto-populate it.
            const source = (td.sources || []).find(s => s.deviceId === conditionDeviceId);
            const presetKey = source?.keys?.[0] || '';
            // Only set if different — avoids overwriting manual user edits
            if (presetKey && presetKey !== triggerKey) {
                setTriggerKey(presetKey);
            }
        } else {
            // Primary device — read from the keys[] array keyed by conditionId
            const matched = td?.keys?.find(f => f.conditionId === id);
            setTriggerKey(matched?.key || '');
        }

        setTriggerData(td);
        setType(td.type);
    }, [conditionNodes, id, conditionDeviceId]);

    // When condition device changes, reset triggerKey
    const handleConditionDeviceChange = (deviceId) => {
        setConditionDeviceId(deviceId);
        setTriggerKey(''); // reset key — user picks from new device's attributes
    };

    useEffect(() => {
        const previousNodes = connections.map(conn => ({
            nodeId: conn.source,
            handle: conn.sourceHandle
        }));

        const newData = {
            nodeId: id,
            condition,
            triggerKey,
            valueType: 'int',
            below,
            above,
            type,
            value: conditionValue,
            isExact: isRange,
            time: time.format("hh:mm:ss A"),
            scheduleType: scheduleType !== undefined ? scheduleType : '',
            fromTime: fromTime.format("hh:mm:ss A"),
            toTime: toTime.format("hh:mm:ss A"),
            days,
            solarType,
            offsetMinutes,
            intervalMinutes,
            durationMinutes,
            enabled: connections.length > 0,
            previousNodeRef: previousNodes,
            // NEW: which device owns this condition's data
            // null/'' = primary trigger device (backend falls back automatically)
            deviceId: conditionDeviceId || null,
        };

        if (JSON.stringify(data.conditionData) !== JSON.stringify(newData)) {
            updateNodeData(id, {conditionData: newData});
        }
    }, [
        condition, conditionValue, below, above, isRange, time, type,
        triggerKey, scheduleType, fromTime, toTime, days, solarType,
        offsetMinutes, intervalMinutes, durationMinutes, connections,
        conditionDeviceId,  // NEW dependency
    ]);

    const handleChange = (e, select) => {
        let value = e?.target?.value ?? e;
        if (select === 'value') setConditionValue(value);
        else if (select === 'condition') {
            setIsRange(value === 'equal');
            setCondition(value);
        } else if (select === 'above') setAbove(value);
        else if (select === 'below') setBelow(value);
        else if (select === 'time') {
            if (e && e.isValid()) setTime(e);
        }
    };

    const handleTitle = () => {
        if (condition === 'scheduled') {
            if (scheduleType === 'at') return `At ${time.format("hh:mm:ss A")}`;
            if (scheduleType === 'range') return `${fromTime.format("hh:mm A")} – ${toTime.format("hh:mm A")}`;
            if (scheduleType === 'solar') return `${solarType} +${offsetMinutes}min`;
            if (scheduleType === 'interval') return `Every ${intervalMinutes} min`;
        }
        // Show device name prefix when using a secondary device
        const devicePrefix = conditionDeviceId && devices
            ? (devices.find(d => d.id === conditionDeviceId)?.name || '') + ' · '
            : '';
        if (condition === 'range') return `${devicePrefix}${triggerKey} ${above}–${below}`;
        if (condition === 'equal') return `${devicePrefix}${triggerKey} = ${conditionValue}`;
        if (condition === 'above') return `${devicePrefix}${triggerKey} > ${conditionValue}`;
        if (condition === 'below') return `${devicePrefix}${triggerKey} < ${conditionValue}`;
        return '';
    };

    const isScheduled = condition === 'scheduled';

    return (
        <>
            <Handle
                style={{width: '18px', height: '18px', background: '#FFEB3B', opacity: 0}}
                type="target"
                position={Position.Left}
                id={"in:condition:" + id}
                isConnectable={isConnectable}
            />
            <AddIcon style={{
                background: '#FFEB3B', top: '50%',
                left: 0,
                transform: 'translate(-50%, -50%)'
            }} className='react-flow__handle'/>

            <div style={{display: 'flex', justifyContent: 'center', gap: '6px', margin: '4px', alignItems: 'center'}}>
                <Chip size="small" label={"Duration: " + durationMinutes}/>
                <Chip size="small" label={"Day: " + (days[0] || '-')}/>
                <IconButton onClick={() => deleteNode(id)}>
                    <DeleteIcon/>
                </IconButton>
            </div>

            <Accordion style={{borderRadius: '12px', marginTop: '0px', ...conditionStyle}}>
                <AccordionSummary expandIcon={<ArrowDownwardIcon/>}>
                    <Typography>{handleTitle()}</Typography>
                </AccordionSummary>

                <AccordionDetails>
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
                            <FormControl fullWidth className='nodrag' sx={{mb: 2, mt: 2}}>
                                <InputLabel>Condition</InputLabel>
                                <Select
                                    value={condition}
                                    size='small'
                                    label="Condition"
                                    onChange={(e) => handleChange(e, 'condition')}
                                    variant='outlined'
                                >
                                    <MenuItem value={'equal'}>equal to</MenuItem>
                                    <MenuItem value={'range'}>between</MenuItem>
                                    <MenuItem value={'above'}>above</MenuItem>
                                    <MenuItem value={'below'}>below</MenuItem>
                                    <MenuItem value={'scheduled'}>Scheduled</MenuItem>
                                </Select>
                            </FormControl>

                            {isScheduled ? (
                                <div>
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

                                    <LocalizationProvider dateAdapter={AdapterDayjs}>
                                        {scheduleType === 'at' && (
                                            <TimePicker label="Time" value={time}
                                                        onChange={(e) => e?.isValid() && setTime(e)}/>
                                        )}
                                        {scheduleType === 'range' && (
                                            <div style={{display: 'flex', flexDirection: 'column', gap: '20px'}}>
                                                <TimePicker label="From" value={fromTime}
                                                            onChange={(e) => e?.isValid() && setFromTime(e)}/>
                                                <TimePicker label="To" value={toTime}
                                                            onChange={(e) => e?.isValid() && setToTime(e)}/>
                                            </div>
                                        )}
                                    </LocalizationProvider>

                                    {scheduleType === 'solar' && (
                                        <div>
                                            <FormControl className='nodrag' fullWidth size="small" sx={{mb: 2}}>
                                                <InputLabel>Event</InputLabel>
                                                <Select variant="outlined" value={solarType} label="Event"
                                                        onChange={(e) => setSolarType(e.target.value)}>
                                                    <MenuItem value="sunrise">Sunrise</MenuItem>
                                                    <MenuItem value="sunset">Sunset</MenuItem>
                                                </Select>
                                            </FormControl>
                                            <TextField size="small" label="Offset (minutes)" type="number"
                                                       value={offsetMinutes}
                                                       onChange={(e) => setOffsetMinutes(Number(e.target.value))}
                                                       helperText="Negative = before event"/>
                                        </div>
                                    )}

                                    {scheduleType === 'interval' && (
                                        <TextField size="small" label="Run every (minutes)" type="number"
                                                   value={intervalMinutes}
                                                   onChange={(e) => setIntervalMinutes(Number(e.target.value))}/>
                                    )}

                                    <TextField size="small" label="Run for (minutes)" type="number"
                                               value={durationMinutes}
                                               onChange={(e) => setDurationMinutes(Number(e.target.value))}
                                               sx={{mt: 2}}/>

                                    <FormControl className='nodrag' fullWidth size="small" sx={{mt: 2}}>
                                        <InputLabel>Days</InputLabel>
                                        <Select variant="outlined" multiple label="Days" value={days}
                                                onChange={handleDaysChange}
                                                renderValue={(selected) => selected.join(', ')}>
                                            {['Everyday', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(day => (
                                                <MenuItem key={day} value={day}>
                                                    <Checkbox checked={days.indexOf(day) > -1}/>
                                                    <ListItemText primary={day}/>
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </div>
                            ) : (
                                <div>
                                    {/* ── Device override picker ────────────────────────────
                                        Only shown when the trigger has secondary devices.
                                        Lets user say "read 'lux' from the light sensor,
                                        not from the primary knock device."
                                    ─────────────────────────────────────────────────────── */}
                                    {hasSecondaryDevices && (
                                        <FormControl fullWidth size="small" className='nodrag' sx={{mb: 2}}>
                                            <InputLabel>Read data from</InputLabel>
                                            <Select
                                                variant="outlined"
                                                value={conditionDeviceId}
                                                label="Read data from"
                                                onChange={(e) => handleConditionDeviceChange(e.target.value)}
                                            >
                                                <MenuItem value="">
                                                    <em>Primary device (default)</em>
                                                </MenuItem>
                                                {secondaryDevices.map(s => {
                                                    const dev = devices?.find(d => d.id === s.deviceId);
                                                    return dev ? (
                                                        <MenuItem key={dev.id} value={dev.id}>
                                                            {dev.name}
                                                        </MenuItem>
                                                    ) : null;
                                                })}
                                            </Select>
                                        </FormControl>
                                    )}

                                    {/* ── Trigger key ──────────────────────────────────────
                                        Shows attributes for whichever device is selected
                                        (primary or secondary). Falls back to primary if none.
                                    ─────────────────────────────────────────────────────── */}
                                    {/*<FormControl fullWidth size="small" className='nodrag' sx={{mb: 2}}>*/}
                                    {/*    <InputLabel>Key</InputLabel>*/}
                                    {/*    <Select*/}
                                    {/*        variant="outlined"*/}
                                    {/*        value={triggerKey}*/}
                                    {/*        label="Key"*/}
                                    {/*        onChange={(e) => setTriggerKey(e.target.value)}*/}
                                    {/*    >*/}
                                    {/*        {availableAttributes.length === 0 && (*/}
                                    {/*            <MenuItem value="" disabled>*/}
                                    {/*                <em>No attributes found</em>*/}
                                    {/*            </MenuItem>*/}
                                    {/*        )}*/}
                                    {/*        {availableAttributes.map(attr => (*/}
                                    {/*            <MenuItem key={attr.id} value={attr.key}>*/}
                                    {/*                {attr.displayName}*/}
                                    {/*            </MenuItem>*/}
                                    {/*        ))}*/}
                                    {/*    </Select>*/}
                                    {/*</FormControl>*/}

                                    {/* ── Value inputs ─────────────────────────────────── */}
                                    {(isRange || condition === 'above' || condition === 'below') ? (
                                        <TextField size='small' label="Value" fullWidth
                                                   value={conditionValue}
                                                   onChange={(e) => handleChange(e, 'value')}/>
                                    ) : (
                                        <div>
                                            <TextField size='small' label="Below" fullWidth value={below}
                                                       onChange={(e) => handleChange(e, 'below')}
                                                       sx={{mb: 2}}/>
                                            <TextField size='small' label="Above" fullWidth value={above}
                                                       onChange={(e) => handleChange(e, 'above')}/>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
                </AccordionDetails>
            </Accordion>

            <Handle
                style={{width: '26px', height: '26px', background: '#4caf50'}}
                type="source"
                position={Position.Right}
                id={"out:cond-positive:" + id}
                isConnectable={isConnectable}
            />
            <Handle
                style={{width: '26px', height: '26px', background: '#f44336', top: '90%'}}
                type="source"
                position={Position.Right}
                id={"out:cond-negative:" + id}
                isConnectable={isConnectable}
            />
        </>
    );
};