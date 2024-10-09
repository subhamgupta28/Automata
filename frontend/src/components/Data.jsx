import {useEffect, useState} from "react";
import {getDevices, getDataByDeviceId} from "../services/apis.jsx";

import { Pie } from "react-chartjs-2";
const data = {
    labels: ['Red', 'Orange', 'Blue'],
    // datasets is an array of objects where each object represents a set of data to display corresponding to the labels above. for brevity, we'll keep it at one object
    datasets: [
        {
            label: 'Popularity of colours',
            data: [55, 23, 96],
            // you can set indiviual colors for each bar
            backgroundColor: [
                'rgba(255, 255, 255, 0.6)',
                'rgba(255, 255, 255, 0.6)',
                'rgba(255, 255, 255, 0.6)'
            ],
            borderWidth: 1,
        }
    ]
}
function PieChart({ chartData }) {
    return (
        <div className="chart-container">
            <h2 style={{ textAlign: "center" }}>Pie Chart</h2>
            <Pie
                data={chartData}
                options={{
                    plugins: {
                        title: {
                            display: true,
                            text: "Users Gained between 2016-2020"
                        }
                    }
                }}
            />
        </div>
    );
}

export default function Data() {

    const [data, setData] = useState([]);

    useEffect(() => {
        getDataByDeviceId("66d36ec988b2900e1ee74c76").then((data) => {
            console.log(data.values);
            setData(data.values);
        })
    }, [])

    return (
        <>
            <div className="container mt-4">
                <div className="row card">
                    <PieChart chartData={chartData} />
                </div>
            </div>
        </>


    )
}