import React, {useEffect, useRef, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme, lightTheme} from "./Theme.jsx";
import {BrowserRouter} from "react-router-dom";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import {AuthProvider} from "./components/auth/AuthContext.jsx";
import useWebSocket from "./services/useWebSocket.jsx";

function getBreathingClass(alertLevel) {
    switch (alertLevel) {
        case 'critical':
            return 'breathing-red';
        case 'warning':
            return 'breathing-yellow';
        case 'normal':
            return 'breathing-green';
        default:
            return '';
    }
}
function App() {
    // const { messages, sendMessage } = useWebSocket('/topic/update');
    const {messages, sendMessage} = useWebSocket('/topic/alert');
    const [alertLevel, setAlertLevel] = useState('');

    useEffect(() => {
        if (messages.severity) {
            console.log("msg", messages.timestamp)
            setAlertLevel(messages.severity);
        }
    }, [messages]);

    useEffect(() => {
        if (alertLevel && alertLevel !== 'normal') {
            const timer = setTimeout(() => {
                setAlertLevel('');
            }, 20000);
            console.log("msg", timer)
            return () => clearTimeout(timer);
        }
    }, [alertLevel]);
    // const handleSend = () => {
    //     sendMessage('/app/send', input);
    //     setInput('');
    // };
    return (
        <>
            {/*<SideDrawer/>*/}
            <ThemeProvider theme={darkTheme}>
                <BrowserRouter>

                    <AuthProvider>
                    <main className={getBreathingClass(alertLevel)} style={{background:'#505050'}}>
                        <header>
                            {/*<Nav/>*/}
                        </header>
                        <section >
                            <SideDrawer/>
                        </section>
                    </main>
                    </AuthProvider>
                </BrowserRouter>
            </ThemeProvider>
        </>
    )
}

export default App
