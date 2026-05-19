import React, {useEffect, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme} from "./Theme.jsx";
import {BrowserRouter} from "react-router-dom";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import {AuthProvider} from "./components/auth/AuthContext.jsx";
import {DeviceDataProvider, useDeviceLiveData} from "./services/DeviceDataProvider.jsx";
import './utils/Glow.css'
import StarfieldBackground from "./components/integrations/StarfieldBackground.jsx";

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

// Inner component so it can consume the WebSocket context provided above it
function AppContent() {
    const {alertMessages, sendMessage} = useDeviceLiveData();
    const [alertLevel, setAlertLevel] = useState('');
    const onStateChange = (state) => console.log("State →", state);

    // const {state, lastChanged} = useSystemStateMonitor({
    //     idleTimeout: 60000,
    //     reportInterval: 280000,
    //     headers: {},
    //     onStateChange
    // });

    useEffect(() => {
        if (alertMessages.severity) {
            const level = alertMessages.severity;
            setAlertLevel(level);
            if (level && level !== 'normal') {
                const timer = setTimeout(() => {
                    setAlertLevel('');
                }, 20000);
                return () => clearTimeout(timer);
            }
        }
    }, [alertMessages]);

    return (
        <main className={getBreathingClass(alertLevel)} style={{position: 'relative'}}>
            <SideDrawer/>
        </main>
    );
}

function App() {
    return (
        <ThemeProvider theme={darkTheme}>
            <BrowserRouter>
                <AuthProvider>
                    <DeviceDataProvider>
                        <StarfieldBackground/>
                        <AppContent/>
                    </DeviceDataProvider>
                </AuthProvider>
            </BrowserRouter>
        </ThemeProvider>
    );
}

export default App
