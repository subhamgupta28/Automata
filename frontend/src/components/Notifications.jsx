import useWebSocket from "../services/useWebSocket.jsx";
import React, {useEffect} from "react";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import CloseIcon from '@mui/icons-material/Close';
import {notificationAction} from "../services/apis.jsx";
import {useSnackbar} from 'notistack';

export default function Notifications() {
    const {messages, sendMessage} = useWebSocket('/topic/notification');
    const [open, setOpen] = React.useState(false);
    const {enqueueSnackbar} = useSnackbar();

    const handleClick = () => {
        setOpen(true);
    };

    const handleAutomation = async () => {
        await notificationAction("stop_automation", {"action": "snooze for 1 hr"});
    };

    const handleClose = (event, reason) => {
        if (reason === 'clickaway') {
            return;
        }

        setOpen(false);
    };

    const action = (
        <React.Fragment>
            {messages.severity === "automation" && (
                <div>
                    <Button color="secondary" size="small" onClick={handleAutomation}>
                        Stop Automation
                    </Button>
                    <IconButton
                        size="small"
                        aria-label="close"
                        color="inherit"
                        onClick={handleClose}
                    >
                        <CloseIcon fontSize="small"/>
                    </IconButton>
                </div>
            )}

        </React.Fragment>
    );

    useEffect(() => {
        if (messages.message) {
            console.log(messages)
            setOpen(true);
            enqueueSnackbar(messages.message);
        }
    }, [messages])

    return (
        <div style={{position: 'absolute'}}>

            {/*<Button onClick={handleClick}>Open Snackbar</Button>*/}
            {/*{messages.message && (*/}
            {/*    <Snackbar*/}
            {/*        open={open}*/}
            {/*        TransitionComponent={Slide}*/}
            {/*        autoHideDuration={3000}*/}
            {/*        onClose={handleClose}*/}
            {/*        message={messages.message}*/}
            {/*        action={action}*/}
            {/*    />*/}
            {/*)}*/}

        </div>
    )
}