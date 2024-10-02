import {useState} from 'react'
import './App.css'
import Nav from "./components/Nav.jsx";

function App() {
    const [count, setCount] = useState(0)

    return (
        <>
            <main data-bs-theme="dark" >
                <header>
                    <Nav/>
                </header>
                <section className={"content"}>
                    <div className="alert alert-primary" role="alert">
                        A simple primary alert—check it out!
                    </div>
                </section>
            </main>

        </>
    )
}

export default App
