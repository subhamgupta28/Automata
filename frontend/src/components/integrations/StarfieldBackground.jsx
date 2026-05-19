import React, {useEffect, useRef} from "react";

const StarfieldBackground = () => {
    const canvasRef = useRef(null);
    const animationRef = useRef(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        const ctx = canvas.getContext("2d");

        let stars = [];
        let shootingStars = [];
        const numStars = 360;

        const resizeCanvas = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            initStars();
        };

        const initStars = () => {
            stars = Array.from({length: numStars}, () => ({
                angle: Math.random() * Math.PI * 2,
                radius:
                    Math.random() *
                    Math.sqrt(canvas.width ** 2 + canvas.height ** 2),
                speed: Math.random() * 0.0003 + 0.00015,
                size: Math.random() * 1.2 + 0.5,
            }));
        };

        const spawnShootingStar = () => {
            if (shootingStars.length === 0 && Math.random() < 0.01) {
                shootingStars.push({
                    x: Math.random() * canvas.width * 0.5,
                    y: Math.random() * canvas.height * 0.5,
                    vx: 3 + Math.random() * 2,
                    vy: 1 + Math.random() * 1.5,
                    life: 80,
                    initialLife: 80,
                });
            }
        };

        const animate = () => {
            const centerX = canvas.width;
            const centerY = canvas.height;

            ctx.fillStyle = "#161616";
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            // Orbiting stars
            stars.forEach((star, i) => {
                star.angle += star.speed;

                const x = centerX + star.radius * Math.cos(star.angle);
                const y = centerY + star.radius * Math.sin(star.angle);

                const flicker =
                    0.4 + Math.abs(Math.sin(Date.now() * 0.0015 + i)) * 0.5;

                ctx.beginPath();
                ctx.fillStyle = `rgba(255,255,255,${flicker})`;
                ctx.arc(x, y, star.size, 0, Math.PI * 2);
                ctx.fill();
            });

            // Shooting stars
            spawnShootingStar();

            for (let i = shootingStars.length - 1; i >= 0; i--) {
                const s = shootingStars[i];
                const opacity = s.life / s.initialLife;

                const grad = ctx.createLinearGradient(
                    s.x,
                    s.y,
                    s.x - s.vx * 35,
                    s.y - s.vy * 35
                );

                grad.addColorStop(0, `rgba(255,255,255,${opacity})`);
                grad.addColorStop(1, `rgba(255, 255, 255, 0)`);

                ctx.strokeStyle = grad;
                ctx.lineWidth = 2;

                ctx.beginPath();
                ctx.moveTo(s.x, s.y);
                ctx.lineTo(s.x - s.vx * 18, s.y - s.vy * 18);
                ctx.stroke();

                s.x += s.vx;
                s.y += s.vy;
                s.life -= 1;

                if (s.life <= 0) {
                    shootingStars.splice(i, 1);
                }
            }

            animationRef.current = requestAnimationFrame(animate);
        };

        resizeCanvas();
        animate();

        window.addEventListener("resize", resizeCanvas);

        return () => {
            cancelAnimationFrame(animationRef.current);
            window.removeEventListener("resize", resizeCanvas);
        };
    }, []);

    return (
        <canvas
            ref={canvasRef}
            style={{
                position: "absolute",
                inset: 0,
                width: "100%",
                height: "100dvh",
                // zIndex: -1,
                pointerEvents: "none",
                // background: "black",
            }}
        />
    );
};

export default StarfieldBackground;