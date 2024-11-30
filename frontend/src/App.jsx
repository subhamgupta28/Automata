import {useState} from 'react'
import './App.css'
import Nav from "./components/Nav.jsx";
import useWebSocket from './services/useWebSocket';
import DeviceNodes from "./components/dashboard/DeviceNodes.jsx";
import ActionBoard from "./components/action/ActionBoard.jsx";
import {ThemeProvider} from "@mui/material/styles";
import {darkTheme} from "./Theme.jsx";
import SignIn from "./components/auth/SignIn.jsx";
import SignUp from "./components/auth/SignUp.jsx";


function App() {
    // const { messages, sendMessage } = useWebSocket('/topic/update');
    const [input, setInput] = useState('');


    // const handleSend = () => {
    //     sendMessage('/app/send', input);
    //     setInput('');
    // };
    return (
        <>
            <ThemeProvider theme={darkTheme}>
            <main>
                <header>
                    <Nav/>
                </header>

                <section className={"content"}>
                    {/*<DndTest/>*/}
                    {/*<ActionBoard/>*/}
                    <DeviceNodes/>
                    {/*<SignIn/>*/}
                    {/*<SignUp/>*/}


                </section>

            </main>
            </ThemeProvider>
        </>
    )
}

export default App
