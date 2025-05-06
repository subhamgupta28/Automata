import React, {useEffect, useRef, useState} from 'react'
// import './App.css'
import Nav from "./components/Nav.jsx";
import DeviceNodes from "./components/dashboard/DeviceNodes.jsx";
import ActionBoard from "./components/action/ActionBoard.jsx";
import {ThemeProvider} from "@mui/material/styles";
import {darkTheme, lightTheme} from "./Theme.jsx";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import Notifications from "./components/Notifications.jsx";
import Devices from "./components/Devices.jsx";
import {AppCacheProvider} from "./services/AppCacheContext.jsx";
import {SnackbarProvider} from "notistack";
import MobileView from "./components/dashboard/MobileView.jsx";
import AnalyticsView from "./components/dashboard/AnalyticsView.jsx";
import Exp from "./components/dashboard/Exp.jsx";
import {ConfigurationView} from "./components/dashboard/ConfigurationView.jsx";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import DOTS from 'vanta/dist/vanta.dots.min.js'

function App() {
    // const { messages, sendMessage } = useWebSocket('/topic/update');
    const [input, setInput] = useState('');

    const [vantaEffect, setVantaEffect] = useState(null)
    const myRef = useRef(null)
    useEffect(() => {
        // if (!vantaEffect) {
        //     setVantaEffect(DOTS({
        //         el: myRef.current,
        //         showLines: false
        //     }))
        // }
        return () => {
            if (vantaEffect) vantaEffect.destroy()
        }
    }, [vantaEffect])
    // const handleSend = () => {
    //     sendMessage('/app/send', input);
    //     setInput('');
    // };
    return (
        <>
            {/*<SideDrawer/>*/}
            <ThemeProvider theme={darkTheme}>
                <BrowserRouter>


                    <main >
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
                            <SnackbarProvider maxSnack={3} preventDuplicate>
                                <Notifications/>
                            </SnackbarProvider>
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
                </BrowserRouter>
            </ThemeProvider>
        </>
    )
}

export default App
