import useWebSocket from "../services/useWebSocket.jsx";
import {useEffect} from "react";
import {notificationAction} from "../services/apis.jsx";
import {useSnackbar} from "notistack";

export default function Notifications() {
    const {messages} = useWebSocket();
    const {enqueueSnackbar} = useSnackbar();

    const handleStop = () => notificationAction("stop_automation", {action: "stop"});
    const handleSnooze = () => notificationAction("stop_automation", {action: "snooze for 1 hr"});

    useEffect(() => {
        if (!messages?.message) return;
        console.log("notify", messages)
        const severity = messages.severity ?? "info";

        enqueueSnackbar(messages.message, {
            variant: severity,
            // persist: severity === "automation",   // automation stays until dismissed
            autoHideDuration: 5000,
            // custom props forwarded to AutomataSnackbar
            header: messages.header ?? null,
            onStop: handleStop,
            onSnooze: handleSnooze,
        });
    }, [messages]);

    return null;
}