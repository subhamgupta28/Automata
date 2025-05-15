import React, {useEffect, useRef, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme, lightTheme} from "./Theme.jsx";
import {BrowserRouter, Route, Routes} from "react-router-dom";
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
    const {messages, sendMessage} = useWebSocket('/topic/notification');
    const [alertLevel, setAlertLevel] = useState('');

    useEffect(() => {
        if (messages.severity) {
            setAlertLevel(messages.severity);
        }
    }, [messages]);

    useEffect(() => {
        if (alertLevel && alertLevel !== 'normal') {
            const timer = setTimeout(() => {
                setAlertLevel('');
            }, 20000);

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
                    <main className={getBreathingClass(alertLevel)} style={{background:'#303030'}}>
                        <header>
                            {/*<Nav/>*/}
                        </header>
                        <section >
                            {/*<DndTest/>*/}
                            {/*<ActionBoard/>*/}
                            {/*<div ref={myRef} style={{height:'200px', width:'100px'}}>*/}

                            {/*</div>*/}
                            <SideDrawer/>
                            {/*<SignIn/>*/}
                            {/*<SignUp/>*/}

                            {/*<AppCacheProvider>*/}
                            {/*    <Routes>*/}
                            {/*        <Route path="/" element={<DeviceNodes/>}/>*/}
                            {/*        <Route path="actions" element={<ActionBoard/>}/>*/}
                            {/*        <Route path="devices" element={<Devices/>}/>*/}
                            {/*        <Route path="mob" element={<MobileView/>}/>*/}
                            {/*        <Route path="analytics" element={<AnalyticsView/>}/>*/}
                            {/*        <Route path="exp" element={<SideDrawer/>}/>*/}
                            {/*        <Route path="configure" element={<ConfigurationView/>}/>*/}
                            {/*    </Routes>*/}
                            {/*</AppCacheProvider>*/}




                        </section>

                    </main>
                    </AuthProvider>
                </BrowserRouter>
            </ThemeProvider>
        </>
    )
}

export default App
