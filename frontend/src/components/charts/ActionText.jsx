
//type: ACTION|MENU|TEXT
import {TextField} from "@mui/material";

export default function ActionText({value, deviceId, name}){
    
    return(
        <div>
            <TextField
                label="Size"
                id="outlined-size-small"
                defaultValue="Small"
                size="small"
            />
        </div>
    )
}