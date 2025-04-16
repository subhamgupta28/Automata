import {LineChart, lineElementClasses } from "@mui/x-charts";
import {useEffect, useState} from "react";
import {getDetailChartData} from "../../services/apis.jsx";


export default function ChartDetail ({ deviceId })  {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);

    useEffect(()=>{
        const fetchChartData = async () => {
            const d = await getDetailChartData(deviceId)
            setData(d.data)
            setAttributes(d.attributes)
        }
        fetchChartData();
    }, [])


    const xLabels = data.map((item) => item.dateDay);

    // Dynamically build series
    const series = attributes.map((attr) => ({
        label: attr.charAt(0).toUpperCase() + attr.slice(1), // Capitalize label
        data: data.map((item) => item[attr]), showMark: false,
    }));

    return (
        <div >
            <LineChart
                // width={1400}
                height={400}
                series={series}
                xAxis={[{ scaleType: 'point', data: xLabels }]}

            />
        </div>
    );
};