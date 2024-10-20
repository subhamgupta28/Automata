import {useState} from 'react'
import './App.css'
import Nav from "./components/Nav.jsx";
import useWebSocket from './services/useWebSocket';
import DeviceNodes from "./components/dashboard/DeviceNodes.jsx";


function App() {
    const { messages, sendMessage } = useWebSocket('/topic/update');
    const [input, setInput] = useState('');

    const handleSend = () => {
        sendMessage('/app/send', input);
        setInput('');
    };
    return (
        <>
            <main data-bs-theme="dark" >
                <header>
                    <Nav/>
                </header>

                <section className={"content"}>
                    {/*<Devices/>*/}
                    {/*<TreeNode/>*/}
                    <DeviceNodes/>


                </section>

            </main>

        </>
    )
}

export default App
