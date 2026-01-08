import ThermostatCard from "./ThermostatCard.jsx";

export default function ({devices, messages}){

    return(
        <>
            {devices.map(device=>(
                <ThermostatCard device={device} messages={messages}/>
            ))}
        </>
    )
}