import {useState} from 'react'
import './App.css'
import Nav from "./components/Nav.jsx";
import useWebSocket from './services/useWebSocket';
import Data from "./components/Data.jsx";
import Devices from "./components/Devices.jsx";
import TreeNode from "./components/TreeNode.jsx";
import FlowDemo from "./components/FlowDemo.jsx";
import DeviceNodes from "./components/DeviceNodes.jsx";

function App() {
    const { messages, sendMessage } = useWebSocket();
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
                    {/*<div>*/}
                    {/*    <input*/}
                    {/*        type="text"*/}
                    {/*        value={input}*/}
                    {/*        onChange={(e) => setInput(e.target.value)}*/}
                    {/*    />*/}
                    {/*    <button onClick={handleSend}>Send Message</button>*/}
                    {/*</div>*/}
                    {/*<div>*/}
                    {/*    <h2>Messages:</h2>*/}
                    {/*    <ul>*/}
                    {/*        {messages.map((msg, index) => (*/}
                    {/*            <li key={index}>{msg}</li>*/}
                    {/*        ))}*/}
                    {/*    </ul>*/}
                    {/*</div>*/}


                </section>
            </main>

        </>
    )
}

export default App
