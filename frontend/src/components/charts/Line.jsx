import { LineChart } from '@mui/x-charts/LineChart';

export default function Line (){
    const dataset = [
        { x: 1, y: 2 },
        { x: 2, y: 5.5 },
        { x: 3, y: 2 },
        { x: 5, y: 8.5 },
        { x: 8, y: 1.5 },
        { x: 10, y: 50 },
    ];

    return(
        <>
            <LineChart
                dataset={dataset}
                xAxis={[{ dataKey: 'x' }]}
                series={[{ dataKey: 'y' }]}
                height={200}
                width={300}
            />
        </>
    )
}