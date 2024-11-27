import useWebSocket from "../services/useWebSocket.jsx";
import {useEffect} from "react";
import {Snackbar} from "@mui/material";
import Button from "@mui/material/Button";


export default function Notifications() {
    const {messages, sendMessage} = useWebSocket('/topic/notifications');
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

    }, [messages])

    return(
        <div>
            <Button onClick={handleClick}>Open Snackbar</Button>
            <Snackbar
                open={open}
                autoHideDuration={6000}
                onClose={handleClose}
                message="Note archived"
                action={action}
            />
        </div>
    )
}