import RemoveTwoToneIcon from "@mui/icons-material/RemoveTwoTone";
import IconButton from "@mui/material/IconButton";
import React, {useState} from "react";
import AddTwoToneIcon from "@mui/icons-material/AddTwoTone";
import Typography from "@mui/material/Typography";

export default function ActionRange({value, deviceId, name}) {
    const [num, setNum] = useState(value)
    const handleBtn = (type) => {
        if (type === "inc")
            setNum(s => s += 1)
        if (type==="dec")
            setNum(s => s -= 1)
    }
    return (
        <div>
            <IconButton
                size="small"
                onClick={() => handleBtn("dec")}
            >
                <RemoveTwoToneIcon/>
            </IconButton>
            <Typography>
                {num}
            </Typography>
            <IconButton
                size="small"
                onClick={() => handleBtn("inc")}
            >
                <AddTwoToneIcon/>
            </IconButton>
        </div>
    )
}