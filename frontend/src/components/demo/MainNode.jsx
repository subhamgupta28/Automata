import {Card} from "@mui/material";

export function MainNode({id, data, isConnectable, selected}){

    return(
        <Card variant="outlined" style={{padding:'12px', height: '850px', width:'1700px'}}>
            {data.value.name}
        </Card>
    )
}