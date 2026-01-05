import * as React from "react";
import {
    Card,
    Box,
    Typography,
    IconButton
} from "@mui/material";
import LightbulbIcon from "@mui/icons-material/Lightbulb";

export default function LightBulbCard({value, name}) {
    const [isOn, setIsOn] = React.useState(value || false);

    return (
        <Card
            elevation={0}
            sx={{
                margin: '6px',
                width: 220,
                borderRadius: "16px",
                padding: "12px 16px",
                boxShadow: "0px 1px 4px rgba(0,0,0,0.12)"
            }}
        >
            <Box display="flex" alignItems="center" gap={2}>
                {/* Icon Button */}
                <IconButton
                    onClick={() => setIsOn(!isOn)}
                    sx={{
                        width: 48,
                        height: 48,
                        borderRadius: "50%",
                        backgroundColor: isOn ? "#FFC107" : "#E0E0E0",
                        "&:hover": {
                            backgroundColor: isOn ? "#FFB300" : "#D5D5D5"
                        }
                    }}
                >
                    <LightbulbIcon
                        sx={{
                            color: isOn ? "#FFFFFF" : "#9E9E9E"
                        }}
                    />
                </IconButton>

                {/* Text */}
                <Box>
                    <Typography fontWeight={600} fontSize="16px">
                        {name}
                    </Typography>
                    <Typography
                        fontSize="14px"
                        color={isOn ? "text.primary" : "text.secondary"}
                    >
                        {isOn ? "On" : "Off"}
                    </Typography>
                </Box>
            </Box>
        </Card>
    );
}
