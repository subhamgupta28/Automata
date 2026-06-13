import React, {useEffect, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme} from "./Theme.jsx";
import {BrowserRouter} from "react-router-dom";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import {AuthProvider, useAuth} from "./components/auth/AuthContext.jsx";
import {DeviceDataProvider, useDeviceLiveData} from "./services/DeviceDataProvider.jsx";
import './utils/Glow.css'
import StarfieldBackground from "./components/integrations/StarfieldBackground.jsx";
import isEmpty from "./utils/Helper.jsx";

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

function getBorderColor(alertLevel) {
    switch (alertLevel) {
        case 'critical':
            return 'red';
        case 'warning':
            return 'yellow';
        case 'normal':
            return 'green';
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
            {/*{alertLevel && (*/}
            {/*    <Card variant='outlined' style={{*/}
            {/*        position: 'absolute',*/}
            {/*        left: '50%',*/}
            {/*        padding: '4px 16px',*/}
            {/*        borderRadius: '0px 0px 12px 12px',*/}
            {/*        borderColor: getBorderColor(alertLevel)*/}
            {/*    }}>*/}
            {/*        {alertMessages.message}*/}
            {/*    </Card>*/}
            {/*)}*/}

            <SideDrawer/>
        </main>
    );
}

function AuthenticatedApp() {
    const {user, loading} = useAuth();

    if (loading) return null;

    if (isEmpty(user)) {
        return (
            <main style={{position: 'relative'}}>
                <StarfieldBackground/>
                <SideDrawer/>
            </main>
        );
    }

    return (
        <DeviceDataProvider>
            <StarfieldBackground/>
            <AppContent/>
        </DeviceDataProvider>
    );
}

function App() {
    return (
        <ThemeProvider theme={darkTheme}>
            <BrowserRouter>
                <AuthProvider>
                    <AuthenticatedApp/>
                </AuthProvider>
            </BrowserRouter>
        </ThemeProvider>
    );
}

export default App
