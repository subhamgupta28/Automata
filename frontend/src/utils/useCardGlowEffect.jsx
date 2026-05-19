import {useEffect} from "react";

export const useCardGlowEffect = (cardRef, autoAnimate = false) => {
    useEffect(() => {
        const card = cardRef.current;
        if (!card) return;
        let animationFrame;

        // AUTO ANIMATION
        if (autoAnimate) {
            let angle = 0;
            const animate = () => {
                angle = (angle + 1) % 360;

                card.style.setProperty('--start', angle);

                animationFrame = requestAnimationFrame(animate);
            };
            animate();
            return () => cancelAnimationFrame(animationFrame);
        }
        // MOUSE CONTROLLED
        const handleMouseMove = (e) => {
            const rect = card.getBoundingClientRect();
            const mouseX = e.clientX - rect.left - rect.width / 2;
            const mouseY = e.clientY - rect.top - rect.height / 2;
            let angle = Math.atan2(mouseY, mouseX) * (180 / Math.PI);
            angle = (angle + 360) % 360;
            card.style.setProperty('--start', angle + 60);
        };
        card.addEventListener('mousemove', handleMouseMove);
        return () => {
            card.removeEventListener('mousemove', handleMouseMove);
        };
    }, [cardRef, autoAnimate]);
};