import useWebSocket from "../services/useWebSocket.jsx";
import React, {useEffect} from "react";
import {Snackbar} from "@mui/material";
import Button from "@mui/material/Button";


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
                    autoHideDuration={6000}
                    onClose={handleClose}
                    message={messages.message}
                />
            )}

        </div>
    )
}