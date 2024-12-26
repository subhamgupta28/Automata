import * as React from 'react';
import {PieChart, pieArcLabelClasses} from '@mui/x-charts/PieChart';

export default function CustomPieChart({data, dataKey, unit}) {
    const dat = data.map((d) => {
        return {
            value: d[dataKey],
            color: 'orange',
            label: d._id
        }
    });

    return (
        <PieChart
            series={[
                {
                    arcLabel: (item) => `${item.value} ${unit}`,
                    arcLabelMinAngle: 25,
                    // arcLabelRadius: '60%',
                    innerRadius: 60,
                    outerRadius: 130,
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
            width={600}
            height={300}
        />
    );
}



