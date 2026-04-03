import React, {useEffect, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme} from "./Theme.jsx";
import {BrowserRouter} from "react-router-dom";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import {AuthProvider} from "./components/auth/AuthContext.jsx";
import useWebSocket from "./services/useWebSocket.jsx";
import SystemStateMonitor, {useSystemStateMonitor} from "./components/integrations/Usesystemstatemonitor.jsx";

// import Silk from "./components/dashboard/Silk.jsx";

// import './components/dashboard/Exp.css'


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
    const onStateChange = (state) => console.log("State →", state)

    const {state, lastChanged} = useSystemStateMonitor({
        idleTimeout: 60000,
        reportInterval: 280000,
        headers: {},
        onStateChange
    });
    useEffect(() => {
        if (messages.severity) {
            // console.log("msg", messages.timestamp)
            const alertLevel = messages.severity;
            setAlertLevel(messages.severity);
            if (alertLevel && alertLevel !== 'normal') {
                const timer = setTimeout(() => {
                    setAlertLevel('');
                }, 20000);
                // console.log("msg", timer)
                return () => clearTimeout(timer);
            }
        }
    }, [messages]);

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

                        <main className={getBreathingClass(alertLevel)}
                              style={{
                                  // background: `url(${backgroundImage})`,
                                  // backgroundSize: 'cover',
                                  backgroundColor: '#161616',
                                  // backgroundRepeat: 'no-repeat',
                                  // backgroundBlendMode:'darken',
                                  // background: "radial-gradient(circle at 50% 50%, rgba(0, 128, 255, 0.6),rgba(10, 10, 30, 0.9) 30%,#000000 100%)"
                                  // background: "linear-gradient(135deg, rgb(211 244 122 / 15%), rgb(255 255 255 / 20%), rgb(211 244 122 / 15%))",

                              }}
                        >
                            <SystemStateMonitor
                                idleTimeout={60000}       // 60s before marking idle
                                reportInterval={280000}    // keep-alive ping every 30s
                                onStateChange={(state) => console.log("State →", state)}
                                debug={true}
                            />
                            {/*<header>*/}
                            {/*<Nav/>*/}
                            {/*</header>*/}
                            {/*<section*/}
                            {/*    style={{backgroundColor: '#1b1b1b'}}*/}
                            {/*>*/}
                            {/*    <Silk*/}
                            {/*        speed={5}*/}
                            {/*        scale={1}*/}
                            {/*        color="#c0a000"*/}
                            {/*        noiseIntensity={10}*/}
                            {/*        rotation={0}*/}
                            {/*    />*/}
                            <SideDrawer/>
                            {/*</section>*/}
                            {/*<HealthCheck/>*/}
                        </main>
                    </AuthProvider>
                </BrowserRouter>
            </ThemeProvider>
        </>
    )
}

export default App
