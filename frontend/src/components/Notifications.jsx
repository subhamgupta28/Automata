import useWebSocket from "../services/useWebSocket.jsx";
import React, {useEffect} from "react";
import {Alert, Snackbar} from "@mui/material";
import Button from "@mui/material/Button";
import Slide from '@mui/material/Slide';

export default function Notifications() {
    const {messages, sendMessage} = useWebSocket('/topic/notification');
    const [open, setOpen] = React.useState(false);

    const handleClick = () => {
        setOpen(true);
    };

    const handleClose = (event, reason) => {
        if (reason === 'clickaway') {
            return;
        }

        setOpen(false);
    };

    useEffect(() => {
        console.log(messages)
        setOpen(true);
    }, [messages])

    return(
        <div style={{position:'absolute'}}>
            {/*<Button onClick={handleClick}>Open Snackbar</Button>*/}
            {messages.message && (
                <Snackbar
                    open={open}
                    TransitionComponent={Slide}
                    autoHideDuration={3000}
                    onClose={handleClose}
                >
                    <Alert
                        onClose={handleClose}
                        severity={messages.severity}
                        icon={false}
                        variant="filled"
                        sx={{ width: '100%' }}
                    >
                        {messages.message}
                    </Alert>
                </Snackbar>
            )}

        </div>
    )
}