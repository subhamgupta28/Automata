import LockIcon from '@mui/icons-material/Lock';
import {Button, CircularProgress, TextField, Typography} from "@mui/material";
import {useState} from "react";
import {C} from "./WeatherCardV2.jsx";

export default function ProtectedView({onUnlock, error, loading}) {
    const [pin, setPin] = useState('');

    const handleChange = (e) => {
        setPin(e.target.value);
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (pin.length === 6 && onUnlock) onUnlock(pin);
    };

    return (
        <div
            onMouseDown={(e) => e.stopPropagation()} // keep React Flow from dragging/selecting the node while typing
            style={{
                height: '100%',
                width: '100%',
                boxShadow: 'rgb(30 30 30) 0px 0px 86px 10px inset',
                border: `2px solid ${C.border}`,
                borderRadius: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'absolute',
                backdropFilter: 'blur(8px)',
                zIndex: 1
            }}>
            <form onSubmit={handleSubmit}
                  style={{display: 'flex', alignItems: 'center', flexDirection: 'column', gap: '8px'}}>
                <LockIcon/>
                <Typography fontWeight={600}>Locked</Typography>
                <TextField
                    size="small"
                    value={pin}
                    onChange={handleChange}
                    placeholder="6-digit PIN"
                    
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    error={!!error}
                    helperText={error}
                    disabled={loading}
                />
                <Button type="submit" size="small" variant="outlined" disabled={pin.length !== 6 || loading}>
                    {loading ? <CircularProgress size={16}/> : 'Unlock'}
                </Button>
            </form>
        </div>
    )
}