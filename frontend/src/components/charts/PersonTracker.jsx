import React, {useEffect, useRef, useState} from "react";
import {Card, CardContent, Typography} from "@mui/material";

export default function PersonTracker({liveData, radarData, canvasWidth = 260, canvasHeight = 220}) {
    const canvasRef = useRef(null);
    const [position, setPosition] = useState({
        x: 0,
        y: 0,
        distance: 0,
        angle: 0,
    });

    // const canvasWidth = 240;
    // const canvasHeight = 200;
    const centerX = canvasWidth / 2;
    const centerY = canvasHeight;
    const maxRange = 1600;
    const scaleX = canvasWidth / (maxRange * 2);
    const scaleY = canvasHeight / maxRange;
    const scale = Math.min(scaleX, scaleY);

    useEffect(() => {
        if (liveData) {
            setPosition({
                x: liveData["x"],
                y: liveData["y"],
                distance: liveData["distance"],
                angle: liveData["angle"]
            })
        }

    }, [liveData])


    // Draw upper-half Cartesian view
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");

        ctx.clearRect(0, 0, canvasWidth, canvasHeight);

        // Draw axes
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 1;

        // X axis (horizontal)
        ctx.beginPath();
        ctx.moveTo(0, centerY);
        ctx.lineTo(canvasWidth, centerY);
        ctx.stroke();

        // Y axis (vertical)
        ctx.beginPath();
        ctx.moveTo(centerX, 0);
        ctx.lineTo(centerX, canvasHeight);
        ctx.stroke();

        // Draw horizontal grid lines (optional)
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 0.5;
        for (let y = centerY - 20; y > 0; y -= 20) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(canvasWidth, y);
            ctx.stroke();
        }

        // Draw vertical grid lines
        for (let x = centerX - 20; x > 0; x -= 20) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, centerY);
            ctx.stroke();
        }
        for (let x = centerX + 20; x < canvasWidth; x += 20) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, centerY);
            ctx.stroke();
        }
        ctx.strokeStyle = "#ff0000";
        [50, 100, 150].forEach((r) => {
            ctx.beginPath();
            ctx.arc(centerX, centerY, r, Math.PI, 0);
            ctx.stroke();
        });

        ctx.fillStyle = "#aaa";
        ctx.font = "10px sans-serif";
        ctx.fillText("+X", canvasWidth - 15, centerY - 5);
        ctx.fillText("-X", 5, centerY - 5);
        ctx.fillText("+Y", centerX + 5, 10);
        // Draw person dot (only if Y is in upper half)
        const dotX = Math.min(Math.max(centerX + position.x * scale, 0), canvasWidth);
        const dotY = Math.min(Math.max(centerY - position.y * scale, 0), centerY);

        if (dotY >= 0 && dotY <= centerY) {
            ctx.beginPath();
            ctx.arc(dotX, dotY, 5, 0, 2 * Math.PI);
            ctx.fillStyle = "red";
            ctx.fill();
        }
    }, [position]);

    return (
        <div style={{display:'flex', justifyContent:'center', alignItems:'center'}}>
            <canvas
                ref={canvasRef}
                width={canvasWidth}
                height={canvasHeight}
                style={{
                    borderStyle: 'dashed',
                    borderColor: '#606060',
                    borderRadius: "8px",
                    display: "block",
                }}
            />
        </div>
    );
};
