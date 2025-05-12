import React, {useEffect, useRef, useState} from 'react'

import {ThemeProvider} from "@mui/material/styles";
import {darkTheme, lightTheme} from "./Theme.jsx";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import SideDrawer from "./components/custom_drawer/SideDrawer.jsx";
import {AuthProvider} from "./components/auth/AuthContext.jsx";

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

                    <AuthProvider>
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
