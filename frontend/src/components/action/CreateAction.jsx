import React, { useState } from 'react';
import {
    TextField,
    Button,
    Box,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Checkbox,
    FormControlLabel,
    Grid,
    DialogTitle, DialogContent, Dialog
} from '@mui/material';

export default function CreateAction({ isOpen, onClose, automations }) {
    const [data, setData] = useState({
        id: "6759f552e4c261194473ef04",
        name: "When motion is detected turn on the lights",
        trigger: {
            deviceId: "670ec3bc166ab22722fbf4ea",
            type: "state",
            value: "0",
            key: "button"
        },
        actions: [
            { key: "pwm", deviceId: "6713fd6118af335020f90f73", data: "255" },
            { key: "onOff", deviceId: "67571bf46f2d631aa77cc632", data: "1" }
        ],
        conditions: [
            { condition: "numeric", valueType: "int", above: "200", below: "300", value: "250" }
        ]
    });

    const handleChange = (e, section, index = null) => {
        const { name, value } = e.target;
        const updatedData = { ...data };

        if (section === 'trigger') {
            updatedData.trigger[name] = value;
        } else if (section === 'actions') {
            updatedData.actions[index][name] = value;
        } else if (section === 'conditions') {
            updatedData.conditions[index][name] = value;
        } else {
            updatedData[name] = value;
        }

        setData(updatedData);
    };

    const handleAddAction = () => {
        const updatedData = { ...data };
        updatedData.actions.push({ key: "", deviceId: "", data: "" });
        setData(updatedData);
    };

    const handleAddCondition = () => {
        const updatedData = { ...data };
        updatedData.conditions.push({ condition: "numeric", valueType: "int", above: "", below: "", value: "" });
        setData(updatedData);
    };

    return (
        <Dialog fullWidth maxWidth="md" onClose={onClose} open={isOpen}>
            <DialogTitle>Create Action</DialogTitle>
            <DialogContent style={{overflow: 'auto'}}>
                <div style={{padding: '10px'}}>
                    <Box sx={{padding: 2}} style={{display: 'flex', flexDirection: 'row'}}>
                        <div>
                            <TextField
                                size='small'
                                label="Name"
                                fullWidth
                                value={data.name}
                                onChange={(e) => handleChange(e, '')}
                                name="name"
                                sx={{marginBottom: 2}}
                            />
                            <FormControl fullWidth sx={{marginBottom: 2}}>
                                <InputLabel>Trigger Type</InputLabel>
                                <Select
                                    size='small'
                                    value={data.trigger.type}
                                    label="Trigger Type"
                                    onChange={(e) => handleChange(e, 'trigger')}
                                    name="type"
                                >
                                    <MenuItem value="state">State</MenuItem>
                                    <MenuItem value="event">Event</MenuItem>
                                </Select>
                            </FormControl>
                            <TextField
                                size='small'
                                label="Trigger Value"
                                fullWidth
                                value={data.trigger.value}
                                onChange={(e) => handleChange(e, 'trigger')}
                                name="value"
                                sx={{marginBottom: 2}}
                            />
                            <TextField
                                size='small'
                                label="Trigger Key"
                                fullWidth
                                value={data.trigger.key}
                                onChange={(e) => handleChange(e, 'trigger')}
                                name="key"
                                sx={{marginBottom: 2}}
                            />
                        </div>
                        <Grid container spacing={2} sx={{marginBottom: 2, marginLeft: 2}}>
                            {data.actions.map((action, index) => (
                                <Grid item xs={12} key={index}>
                                    <TextField
                                        size='small'
                                        label="Action Device ID"
                                        fullWidth
                                        value={action.deviceId}
                                        onChange={(e) => handleChange(e, 'actions', index)}
                                        name="deviceId"
                                        sx={{marginBottom: 1}}
                                    />
                                    <TextField
                                        size='small'
                                        label="Action Key"
                                        fullWidth
                                        value={action.key}
                                        onChange={(e) => handleChange(e, 'actions', index)}
                                        name="key"
                                        sx={{marginBottom: 1}}
                                    />
                                    <TextField
                                        size='small'
                                        label="Action Data"
                                        fullWidth
                                        value={action.data}
                                        onChange={(e) => handleChange(e, 'actions', index)}
                                        name="data"
                                        sx={{marginBottom: 1}}
                                    />
                                </Grid>
                            ))}
                            <Grid item xs={12}>
                                <Button size='small' variant="outlined" onClick={handleAddAction}>
                                    Add Action
                                </Button>
                            </Grid>
                        </Grid>
                        <Grid container spacing={2} sx={{marginBottom: 2, marginLeft: 2}}>
                            {data.conditions.map((condition, index) => (
                                <Grid item xs={12} key={index}>
                                    <FormControl fullWidth sx={{marginBottom: 1}}>
                                        <InputLabel>Condition Type</InputLabel>
                                        <Select
                                            size='small'
                                            label='Condition Type'
                                            value={condition.condition}
                                            onChange={(e) => handleChange(e, 'conditions', index)}
                                            name="condition"
                                        >
                                            <MenuItem value="numeric">Numeric</MenuItem>
                                            <MenuItem value="string">String</MenuItem>
                                        </Select>
                                    </FormControl>
                                    <TextField
                                        size='small'
                                        label="Above"
                                        fullWidth
                                        value={condition.above}
                                        onChange={(e) => handleChange(e, 'conditions', index)}
                                        name="above"
                                        sx={{marginBottom: 1}}
                                    />
                                    <TextField
                                        size='small'
                                        label="Below"
                                        fullWidth
                                        value={condition.below}
                                        onChange={(e) => handleChange(e, 'conditions', index)}
                                        name="below"
                                        sx={{marginBottom: 1}}
                                    />
                                    <TextField
                                        size='small'
                                        label="Condition Value"
                                        fullWidth
                                        value={condition.value}
                                        onChange={(e) => handleChange(e, 'conditions', index)}
                                        name="value"
                                        sx={{marginBottom: 1}}
                                    />
                                </Grid>
                            ))}
                            <Grid item xs={12}>
                                <Button size='small' variant="outlined" onClick={handleAddCondition}>
                                    Add Condition
                                </Button>
                            </Grid>
                        </Grid>
                    </Box>
                    <Button size='small' variant="contained" onClick={() => console.log(data)} sx={{margin: 2}}>
                        Save
                    </Button>
                </div>
            </DialogContent>
        </Dialog>
    );
}
