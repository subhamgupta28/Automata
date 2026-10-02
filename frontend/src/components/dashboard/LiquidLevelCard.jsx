import React, {useEffect, useRef} from "react";
import {Box, Card, Typography} from "@mui/material";

/**
 * LiquidLevelCard
 * MUI card (default 140x140, width/height adjustable) with a realistic, canvas-rendered liquid.
 *
 * Props:
 *  - width          number   card width in px, default 140
 *  - height         number   card height in px, default 140
 *  - level          (0-100)  liquid level, default 50
 *  - threshold      (0-100)  at or above this level the liquid turns red and pulses, default 80
 *  - title          string   overlay title inside the card
 *  - subtitleTop    string   small subtitle at the top of the card
 *  - subtitleBottom string   small subtitle at the bottom of the liquid area (above the footer)
 *  - footerLeft     string   first footer text
 *  - footerRight    string   second footer text
 *
 * Realism details:
 *  - three wave layers, each a sum of sine waves with different speed/phase
 *  - the surface "sloshes" (bigger waves) when the level changes, then settles
 *  - depth gradient, bright surface highlight, rising bubbles
 *  - critical state: red tint, faster/choppier waves, pulsing glow
 */

const SIZE = 140; // default for both width and height

const PALETTE = {
    normal: {top: [20, 60, 20], bottom: [15, 60, 15]},
    critical: {top: [255, 120, 110], bottom: [150, 15, 25]},
};

const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const rgb = (c, a = 0.5) =>
    `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;

export default function LiquidLevelCard({
                                            width = SIZE,
                                            height = SIZE,
                                            level = 50,
                                            threshold = 80,
                                            title = "",
                                            subtitleTop = "",
                                            subtitleBottom = "",
                                            footerLeft = "",
                                            footerRight = "",
                                            FooterComp
                                        }) {
    const value = Math.min(100, Math.max(0, Number(level) || 0));
    const critical = value <= threshold;

    const canvasRef = useRef(null);
    const propsRef = useRef({value, critical});
    propsRef.current = {value, critical};

    useEffect(() => {
        const canvas = canvasRef.current;
        const ctx = canvas.getContext("2d");
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        ctx.scale(dpr, dpr);

        const reduceMotion = window.matchMedia?.(
            "(prefers-reduced-motion: reduce)"
        ).matches;

        // simulation state
        const st = {
            level: propsRef.current.value, // animated level
            slosh: 0, // extra wave energy after level changes
            redMix: propsRef.current.critical ? 1 : 0, // smooth colour blend
            t: 0,
            bubbles: Array.from({length: 9}, () => ({
                x: Math.random() * width,
                y: Math.random() * height,
                r: 1 + Math.random() * 2.2,
                v: 6 + Math.random() * 12,
                ph: Math.random() * 6.28,
            })),
        };

        let raf;
        let last = performance.now();

        const surfaceY = (x, base, amp, t, layer, crit) => {
            const k = crit ? 1.35 : 1; // choppier when critical
            const ph = layer * 1.7;
            return (
                base +
                Math.sin(x * 0.034 * k + t * (1.1 + layer * 0.35) + ph) * amp +
                Math.sin(x * 0.071 * k - t * (1.7 + layer * 0.2) + ph * 2) * amp * 0.45 +
                Math.sin(x * 0.0135 + t * 0.6 + ph * 3) * amp * 0.6
            );
        };

        const frame = (now) => {
            const dt = Math.min(0.05, (now - last) / 1000);
            last = now;
            const {value: target, critical: crit} = propsRef.current;

            // ease level toward target; feed the difference into slosh energy
            const diff = target - st.level;
            const step = diff * Math.min(1, dt * 3);
            st.level += step;
            st.slosh = Math.min(6, st.slosh + Math.abs(step) * 0.5);
            st.slosh *= Math.pow(0.35, dt); // decay
            st.redMix += ((crit ? 1 : 0) - st.redMix) * Math.min(1, dt * 4);
            st.t += dt * (reduceMotion ? 0 : crit ? 1.6 : 1);

            const lvl = st.level;
            ctx.clearRect(0, 0, width, height);

            if (lvl > 0.2) {
                // less wave at the extremes so it never looks broken when nearly empty/full
                const edge = Math.min(1, lvl / 6, (100 - lvl) / 6 + 0.35);
                const rest = (crit ? 3.2 : 2.4) * edge;
                const amp = rest + st.slosh * edge;
                const base = height - (lvl / 100) * height;

                const top = mix(PALETTE.normal.top, PALETTE.critical.top, st.redMix);
                const bottom = mix(
                    PALETTE.normal.bottom,
                    PALETTE.critical.bottom,
                    st.redMix
                );
                const pulse = crit ? 0.5 + 0.5 * Math.sin(st.t * 4) : 0;

                const layers = [
                    {layer: 2, alpha: 0.35, shift: -3, a: 0.8},
                    {layer: 1, alpha: 0.55, shift: -1, a: 0.95},
                    {layer: 0, alpha: 1, shift: 0, a: 1},
                ];

                layers.forEach((L, idx) => {
                    const grad = ctx.createLinearGradient(0, base - 10, 0, height);
                    grad.addColorStop(0, rgb(top, L.alpha));
                    grad.addColorStop(1, rgb(bottom, L.alpha));

                    ctx.beginPath();
                    ctx.moveTo(0, height);
                    for (let x = 0; x <= width; x += 2) {
                        ctx.lineTo(
                            x,
                            surfaceY(x, base + L.shift, amp * L.a, st.t, L.layer, crit)
                        );
                    }
                    ctx.lineTo(width, height);
                    ctx.closePath();
                    ctx.fillStyle = grad;
                    ctx.fill();

                    // surface highlight on the front layer
                    if (idx === layers.length - 1) {
                        ctx.beginPath();
                        for (let x = 0; x <= width; x += 2) {
                            const y = surfaceY(x, base, amp, st.t, 0, crit);
                            x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
                        }
                        ctx.lineWidth = 1.6;
                        ctx.strokeStyle = `rgba(255,255,255,${0.55 + pulse * 0.2})`;
                        ctx.stroke();

                        // soft light band just under the surface
                        ctx.save();
                        ctx.clip(); // no-op safety if path empty
                        ctx.restore();
                    }
                });

                // underwater light shafts / depth shading
                const depth = ctx.createLinearGradient(0, base, 0, height);
                depth.addColorStop(0, "rgba(255,255,255,0.18)");
                depth.addColorStop(0.35, "rgba(255,255,255,0)");
                depth.addColorStop(1, "rgba(0,0,0,0.25)");
                ctx.save();
                ctx.beginPath();
                ctx.moveTo(0, height);
                for (let x = 0; x <= width; x += 2)
                    ctx.lineTo(x, surfaceY(x, base, amp, st.t, 0, crit));
                ctx.lineTo(width, height);
                ctx.closePath();
                ctx.clip();
                ctx.fillStyle = depth;
                ctx.fillRect(0, 0, width, height);

                // bubbles (clipped to the liquid)
                if (!reduceMotion) {
                    st.bubbles.forEach((b) => {
                        b.y -= b.v * dt * (crit ? 1.8 : 1);
                        b.x += Math.sin(st.t * 2 + b.ph) * 0.15;
                        const surf = surfaceY(b.x, base, amp, st.t, 0, crit);
                        if (b.y < surf + 2) {
                            b.y = height + Math.random() * 20;
                            b.x = Math.random() * width;
                        }
                        ctx.beginPath();
                        ctx.arc(b.x, b.y, b.r, 0, 6.283);
                        ctx.fillStyle = "rgba(255,255,255,0.22)";
                        ctx.fill();
                        ctx.lineWidth = 0.6;
                        ctx.strokeStyle = "rgba(255,255,255,0.5)";
                        ctx.stroke();
                    });
                }
                ctx.restore();

                // critical glow at the surface
                if (crit) {
                    ctx.save();
                    const g = ctx.createLinearGradient(0, base - 28, 0, base + 6);
                    g.addColorStop(0, "rgba(255,40,40,0)");
                    g.addColorStop(1, `rgba(255,40,40,${0.25 + pulse * 0.3})`);
                    ctx.fillStyle = g;
                    ctx.fillRect(0, base - 28, width, 34);
                    ctx.restore();
                }
            }

            raf = requestAnimationFrame(frame);
        };

        raf = requestAnimationFrame(frame);
        return () => cancelAnimationFrame(raf);
    }, [width, height]);

    return (
        <Card
            sx={{
                position: "relative",
                width,
                height,
                overflow: "hidden",
                borderRadius: 3,
                border: 1,
                borderColor: critical ? "rgb(255 0 0 / 0.3)" : "rgb(18 18 18 / 0.3)",
                transition: "border-color .4s",
                boxShadow: "rgb(30 30 30) 0px 0px 36px 6px inset",
            }}
            role="meter"
            variant="elevated"
            aria-label={title}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={value}
        >
            <canvas
                ref={canvasRef}
                style={{position: "absolute", inset: 0, width, height}}
            />

            {/* Threshold marker */}
            <Box
                sx={{
                    position: "absolute",
                    left: 0,
                    right: 0,
                    bottom: `${threshold}%`,
                    borderTop: "1px dashed",
                    borderColor: "error.main",
                    opacity: 0.6,
                    pointerEvents: "none",
                }}
            />

            {/* Top subtitle */}
            {subtitleTop && (
                <Typography
                    noWrap
                    sx={{
                        position: "absolute",
                        top: 6,
                        left: 8,
                        right: 8,
                        textAlign: "center",
                        fontSize: 11,
                        lineHeight: 1.3,
                        color: "#fff",
                        textShadow: "0 1px 3px rgba(0,0,0,.5)",
                        pointerEvents: "none",
                    }}
                >
                    {subtitleTop}
                </Typography>
            )}

            {/* Title overlay */}
            <Box
                sx={{
                    position: "absolute",
                    inset: "0 0 36px 0",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    pointerEvents: "none",
                }}
            >
                <Typography
                    variant="body2"
                    sx={{fontWeight: 600, color: "#fff", textShadow: "0 1px 3px rgba(0,0,0,.5)", textAlign: "center"}}
                >
                    {title}
                </Typography>
                <Typography
                    variant="h5"
                    sx={{fontWeight: 700, lineHeight: 1.1, color: "#fff", textShadow: "0 1px 4px rgba(0,0,0,.55)"}}
                >
                    {Math.round(value)}%
                </Typography>
            </Box>

            {/* Bottom subtitle (sits just above the footer) */}


            {/* Footer */}
            <Box
                sx={{
                    position: "absolute",
                    left: 0,
                    right: 0,
                    bottom: 0,
                    padding: "4px",
                    height: 50,
                    // py: 2.25,
                    borderRadius: "0 0 3 3",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: "rgba(0,0,0,.35)",
                    backdropFilter: "blur(2px)",
                }}
            >
                {subtitleBottom && (
                    <Typography
                        noWrap
                        sx={{
                            textAlign: "center",
                            fontSize: 11,
                            lineHeight: 1.3,
                            color: "#fff",
                            textShadow: "0 1px 3px rgba(0,0,0,.5)",
                            pointerEvents: "none",
                        }}
                    >
                        {subtitleBottom}
                    </Typography>
                )}
                <Box style={{display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center"}}>
                    {FooterComp}
                </Box>
            </Box>
        </Card>
    );
}

/* Usage (battery example):
 * <LiquidLevelCard
 *   level={72}
 *   threshold={20}
 *   title="Battery"
 *   subtitleTop="Discharging"
 *   subtitleBottom="Live 320 W"
 *   footerLeft="Today 1,240 Wh"
 *   footerRight="Updated 2 min ago"
 * />
 */