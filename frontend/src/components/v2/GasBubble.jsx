import Typography from "@mui/material/Typography";
import {Box, Card, LinearProgress} from "@mui/material";

const LEVEL_COLORS = {
    success: "#4caf50",
    warning: "#ff9800",
    error: "#f44336",
};
const solidColors = [
    '#42a5f5',
    '#2ca02c',
    '#ff7f0e',
    '#d62728',
    '#bcbd22',
];

const getLevelColor = (value, type) => {
    switch (type) {
        case "co2":
            if (value < 800) return "success";
            if (value < 1200) return "warning";
            return "error";

        case "ch20":
            if (value < 300) return "success";
            if (value < 600) return "warning";
            return "error";

        case "tvoc":
            if (value < 0.08) return "success";
            if (value < 0.1) return "warning";
            return "error";
        case "pm25":
            if (value < 25) return "success";
            if (value < 50) return "warning";
            return "error";
        default:
            return "success";
    }
};
export const GasBubble = ({
                              value,
                              max,
                              type,
                              size,
                              top,
                              left,
                              color
                          }) => {
    const level = getLevelColor(value, type);
    const percent = Math.min((value / max) * 100, 100);
    // const bgColor = LEVEL_COLORS[level];

    return (
        <Box
            sx={{
                position: "absolute",
                top,
                left,
                width: size,
                height: size,
                borderRadius: "50%",
                backgroundColor: color,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 10px 30px rgba(0,0,0,0.15)",
            }}
        >
            <Typography
                sx={{
                    fontWeight: 700,
                    fontSize: size / 4,
                    color: "#fff",
                }}
            >
                {Math.round(percent)}%
            </Typography>
        </Box>
    );
};
export const GasLegend = ({items}) => {
    return (
        <Box display="flex" justifyContent="center" gap={1} mt={3}>
            {items.map(({label, value, type, color, units}, index) => {
                const level = getLevelColor(value, type);
                // const color = LEVEL_COLORS[level];

                return (
                    <Card variant="outlined" style={{padding:"10px", borderRadius:"10px", backgroundColor:"#000"}} key={index}
                          display="flex" alignItems="center" justifyContent="center"  flexDirection="column"
                    >
                        <div style={{display:"flex", gap: "10px", alignItems:"center"}}>
                            <Box
                                sx={{
                                    width: 12,
                                    height: 12,
                                    borderRadius: "50%",
                                    backgroundColor: color,
                                }}
                            />
                            <Typography variant="caption" fontWeight={600}>
                                {label}
                            </Typography>
                        </div>
                        <Typography variant="caption" fontWeight={600}>
                            {value}{" "}{units}
                        </Typography>
                    </Card>
                );
            })}
        </Box>
    );
};