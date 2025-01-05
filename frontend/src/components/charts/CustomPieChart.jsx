import * as React from 'react';
import {PieChart, pieArcLabelClasses} from '@mui/x-charts/PieChart';

export default function CustomPieChart({chartData}) {

    let data = chartData.data[0];
    let attributes = chartData.attributes;
    let labels = chartData.timestamps;
    // console.log(data);
    const dat = attributes.map((d, index) => {
        return {
            value: data[d],
            color: '#b9b9b9',
            label: labels[index]
        }
    });
    // console.log(dat)

    return (
        <PieChart
            series={[
                {
                    arcLabel: (item) => `${item.value}`,
                    arcLabelMinAngle: 25,
                    // arcLabelRadius: '60%',
                    innerRadius: 50,
                    outerRadius: 110,
                    paddingAngle: 2,
                    cornerRadius: 5,
                    data: dat
                },
            ]}
            sx={{
                [`& .${pieArcLabelClasses.root}`]: {
                    fontWeight: 'bold',
                },
            }}
            width={500}
            height={300}
        />
    );
}



