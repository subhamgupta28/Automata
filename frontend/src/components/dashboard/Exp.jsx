import React from "react";
import { BarChart } from '@mui/x-charts/BarChart';
import { Grid, Paper } from '@mui/material';
import AccessAlarms from "@mui/icons-material/AccessAlarms";
import CottageOutlinedIcon from "@mui/icons-material/CottageOutlined";


const Exp = () => {
    return (
        <div>
            <CustomGrid/>
        </div>
    );
};

export default Exp;



const CustomGrid = () => {
    const items = [
        "Item 1", "Item 2", "Item 3", "Item 4", "Item 5", "Item 6", "Item 7","Item 1", "Item 2", "Item 3", "Item 4", "Item 5", "Item 6", "Item 7"
    ];

    const oddItems = items.filter((_, index) => index % 2 === 0); // Items 1, 3, 5, ...
    const evenItems = items.filter((_, index) => index % 2 !== 0); // Items 2, 4, 6, ...

    return (
        <Grid container spacing={2} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, 1fr)' }}>
            {/* First Row (Odd-indexed items) */}
            {oddItems.map((item, index) => (
                <Grid item xs={6} key={index}>
                    <Paper style={{ padding: 20 }}>{item}</Paper>
                </Grid>
            ))}

            {/* Second Row (Even-indexed items) */}
            {/*{evenItems.map((item, index) => (*/}
            {/*    <Grid item xs={6} key={index}>*/}
            {/*        <Paper style={{ padding: 20 }}>{item}</Paper>*/}
            {/*    </Grid>*/}
            {/*))}*/}
        </Grid>
    );
};
