import ThermostatCard from "./ThermostatCard.jsx";

export default function ({devices, messages}){

    return(
        <>
            {devices.map(device=>(
                <div key={device.id}>
                    <ThermostatCard  device={device} messages={messages}/>
                </div>

            ))}
        </>
    )
}