import React, {useState, useEffect} from "react";
import {Box, IconButton} from "@mui/material";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

const Carousel = ({
                      slides = [],
                      autoPlay = false,
                      interval = 8000,
                      height = 200,
                      width
                  }) => {
    const [index, setIndex] = useState(0);

    const next = () =>
        setIndex((prev) => (prev === slides.length - 1 ? 0 : prev + 1));

    const prev = () =>
        setIndex((prev) => (prev === 0 ? slides.length - 1 : prev - 1));

    useEffect(() => {
        if (!autoPlay || slides.length <= 1) return;

        const timer = setInterval(next, interval);
        return () => clearInterval(timer);
    }, [index, autoPlay, interval, slides.length]);

    return (
        <Box
            sx={{
                position: "relative",
                overflow: "hidden",
                width,
                height,
                borderRadius: 2,
            }}
        >
            {/* Slides container */}
            <Box
                // onTransitionEnd={handleTransitionEnd}
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    height: "100%",
                    transform: `translateY(-${index * 100}%)`,
                    transition: "transform 0.3s ease",
                }}
            >
                {slides.map((slide, i) => {
                    const SlideComponent = slide.component;
                    return (
                        <Box key={i} sx={{ minHeight: "100%", height: "100%" }}>
                            <SlideComponent {...slide.props} />
                        </Box>
                    );
                })}
            </Box>

            {/* Controls */}
            {slides.length > 1 && (
                <>
                    <IconButton
                        onClick={prev}
                        sx={{
                            position: "absolute",
                            top: "35%",
                            right: 8,
                            transform: "translateY(-50%)",
                            backgroundColor: "rgba(0,0,0,0.4)",
                            color: "white",
                        }}
                    >
                        <ArrowUpwardIcon  fontSize="small"/>
                    </IconButton>

                    <IconButton
                        onClick={next}
                        sx={{
                            position: "absolute",
                            top: "65%",
                            right: 8,
                            transform: "translateY(-50%)",
                            backgroundColor: "rgba(0,0,0,0.4)",
                            color: "white",
                        }}
                    >
                        <ArrowDownwardIcon  fontSize="small"/>
                    </IconButton>
                </>
            )}
        </Box>
    );
};

export default Carousel;
