import React from "react";
import {
    AppBar,
    Toolbar,
    Typography,
    Button,
    Box,
    Container,
    Grid,
    Card,
    CardContent,
    CardMedia,
} from "@mui/material";
import { SmartToy, Security, EnergySavingsLeaf } from "@mui/icons-material";
import {useNavigate} from "react-router-dom";

export default function Welcome() {

    const navigate = useNavigate();

    const handleButton = () => {
        navigate("/signin");
    };

    return (
        <Box>
            {/* Hero Section */}
            <Box
                sx={{
                    color: "white",
                    py: 6,
                    textAlign: "center",
                }}
            >
                <Container maxWidth="md">
                    <Typography variant="h3" gutterBottom>
                        Control Your Home from Anywhere
                    </Typography>
                    <Typography variant="h6" paragraph>
                        Automata lets you automate, monitor, and secure your home with a
                        touch of a button.
                    </Typography>
                    <Button variant="contained" color="secondary" size="large" onClick={handleButton}>
                        Get Started
                    </Button>
                </Container>
            </Box>

            {/* Features Section */}
            <Container sx={{ py: 2 }}>
                <Grid container spacing={4} justifyContent="center">
                    <FeatureCard
                        title="Automation"
                        icon={<SmartToy fontSize="large" />}
                        description="Set rules to automate your devices based on time, temperature, or motion."
                    />
                    <FeatureCard
                        title="Security"
                        icon={<Security fontSize="large" />}
                        description="Keep your home safe with smart locks, cameras, and alerts."
                    />
                    <FeatureCard
                        title="Energy Saving"
                        icon={<EnergySavingsLeaf fontSize="large" />}
                        description="Monitor usage and reduce power bills with intelligent scheduling."
                    />
                </Grid>
            </Container>
        </Box>
    );
}

function FeatureCard({ title, icon, description }) {
    return (
        <Grid item xs={12} sm={6} md={4}>
            <Card elevation={3} sx={{ textAlign: "center", p: 2 }}>
                <CardMedia>{icon}</CardMedia>
                <CardContent>
                    <Typography variant="h6" gutterBottom>
                        {title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {description}
                    </Typography>
                </CardContent>
            </Card>
        </Grid>
    );
}
