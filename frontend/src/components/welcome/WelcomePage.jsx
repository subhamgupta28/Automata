// components/welcome/WelcomePage.jsx
// Portfolio-style overview of Automata. Shown to signed-out visitors at "/" and
// to everyone at "/welcome" (the "Overview" item in the sidebar).
// Uses the app's existing palette only: #161616 base, #f8e697 / #f0dc7a yellow,
// rgb(162 157 81) olive, white/grey text, translucent dark glass cards.

import React, {useState} from "react";
import {Box, Button, Chip, Container, IconButton, Stack, Tooltip, Typography} from "@mui/material";
import {motion, useReducedMotion} from "motion/react";
import {useNavigate} from "react-router-dom";
import GitHubIcon from "@mui/icons-material/GitHub";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import {useAuth} from "../auth/AuthContext.jsx";
import isEmpty from "../../utils/Helper.jsx";
import SystemFlow from "./SystemFlow.jsx";
import {FEATURES, GITHUB_URL, HERO, STACK, STEPS} from "./content.jsx";

const ACCENT = "#f0dc7a";
const OLIVE = "rgb(162 157 81)";
const glass = {
    background: "rgba(22,22,22,0.72)",
    border: "1px solid rgba(255,255,255,0.10)",
    backdropFilter: "blur(8px)",
};

// ── Small building blocks ───────────────────────────────────────────────────
function Reveal({children, delay = 0, y = 22, sx}) {
    const reduce = useReducedMotion();
    return (
        <Box
            component={motion.div}
            initial={reduce ? false : {opacity: 0, y}}
            whileInView={{opacity: 1, y: 0}}
            viewport={{once: true, margin: "0px 0px -60px 0px"}}
            transition={{duration: 0.5, delay, ease: "easeOut"}}
            sx={sx}
        >
            {children}
        </Box>
    );
}

function SectionHeader({eyebrow, title, subtitle, id}) {
    return (
        <Reveal sx={{mb: {xs: 3, md: 5}, maxWidth: 760}}>
            <Typography id={id} sx={{
                fontFamily: "monospace", fontSize: "0.75rem", letterSpacing: ".22em", textTransform: "uppercase",
                color: ACCENT, mb: 1, scrollMarginTop: 80,
            }}>
                {eyebrow}
            </Typography>
            <Typography component="h2" sx={{
                fontSize: {xs: "1.7rem", md: "2.3rem"}, fontWeight: 600, lineHeight: 1.15, color: "#fff",
            }}>
                {title}
            </Typography>
            {subtitle && (
                <Typography sx={{mt: 1.5, color: "text.secondary", fontSize: {xs: "0.95rem", md: "1.05rem"}, lineHeight: 1.65}}>
                    {subtitle}
                </Typography>
            )}
        </Reveal>
    );
}

function IconTile({icon: Icon, size = 44}) {
    return (
        <Box sx={{
            width: size, height: size, borderRadius: "12px", display: "grid", placeItems: "center", flexShrink: 0,
            color: ACCENT, background: "rgba(240,220,122,0.10)", border: "1px solid rgba(240,220,122,0.22)",
        }}>
            <Icon sx={{fontSize: size * 0.5}}/>
        </Box>
    );
}

// ── Top bar (signed-out only: signed-in users have the sidebar) ─────────────
function TopBar({onSignIn, onSignUp}) {
    const jump = (id) => document.getElementById(id)?.scrollIntoView({behavior: "smooth", block: "start"});
    return (
        <Box component="header" sx={{
            position: "sticky", top: 0, zIndex: 20,
            pt: "var(--safe-top)",
            background: "rgba(22,22,22,0.78)", backdropFilter: "blur(10px)",
            borderBottom: "1px solid rgba(255,255,255,0.08)",
        }}>
            <Container maxWidth="lg" sx={{display: "flex", alignItems: "center", height: 60, gap: 1}}>
                <Typography sx={{
                    fontFamily: "monospace", fontWeight: 700, letterSpacing: ".2rem", color: ACCENT, fontSize: "1.05rem",
                }}>
                    Automata
                </Typography>
                <Stack direction="row" spacing={0.5} sx={{ml: 3, display: {xs: "none", md: "flex"}}}>
                    {[["how-it-works", "How it works"], ["features", "Features"], ["stack", "Tech stack"]].map(([id, label]) => (
                        <Button key={id} color="inherit" size="small" onClick={() => jump(id)}
                                sx={{color: "text.secondary", textTransform: "none", "&:hover": {color: "#fff"}}}>
                            {label}
                        </Button>
                    ))}
                </Stack>
                <Box sx={{flexGrow: 1}}/>
                <Tooltip title="Source on GitHub">
                    <IconButton component="a" href={GITHUB_URL} target="_blank" rel="noopener noreferrer"
                                aria-label="Automata on GitHub" sx={{color: "text.secondary"}}>
                        <GitHubIcon/>
                    </IconButton>
                </Tooltip>
                <Button variant="outlined" size="small" onClick={onSignIn}>Sign in</Button>
                <Button variant="contained" size="small" onClick={onSignUp}
                        sx={{display: {xs: "none", sm: "inline-flex"}}}>
                    Sign up
                </Button>
            </Container>
        </Box>
    );
}

// ── Hero ────────────────────────────────────────────────────────────────────
function Hero({signedIn, onPrimary, onSignIn, guestBusy, guestError}) {
    return (
        <Box sx={{position: "relative", overflow: "hidden", pt: {xs: 7, md: 12}, pb: {xs: 6, md: 10}}}>
            {/* Ambient background: soft glows + faint dot grid, all theme colours */}
            <Box aria-hidden sx={{
                position: "absolute", inset: 0, pointerEvents: "none",
                background: `
                    radial-gradient(600px 320px at 15% 10%, rgba(248,230,151,0.10), transparent 70%),
                    radial-gradient(520px 300px at 90% 30%, rgba(162,157,81,0.12), transparent 70%)`,
            }}/>
            <Box aria-hidden sx={{
                position: "absolute", inset: 0, pointerEvents: "none", opacity: 0.5,
                backgroundImage: "radial-gradient(rgba(255,255,255,0.10) 1px, transparent 1px)",
                backgroundSize: "28px 28px",
                maskImage: "radial-gradient(ellipse at 50% 30%, #000 20%, transparent 75%)",
                WebkitMaskImage: "radial-gradient(ellipse at 50% 30%, #000 20%, transparent 75%)",
            }}/>

            <Container maxWidth="lg" sx={{position: "relative"}}>
                <Reveal y={16}>
                    <Chip label={HERO.eyebrow} size="small" variant="outlined" sx={{
                        mb: 3, fontFamily: "monospace", letterSpacing: ".06em", fontSize: "0.72rem",
                        borderColor: "rgba(240,220,122,0.4)", color: ACCENT, background: "rgba(240,220,122,0.06)",
                    }}/>
                </Reveal>

                <Reveal delay={0.05}>
                    <Box component="h1" sx={{
                        m: 0, fontFamily: "monospace", fontWeight: 700, lineHeight: 1,
                        fontSize: "clamp(2.6rem, 10vw, 5.8rem)",
                        letterSpacing: {xs: ".08em", md: ".16em"},
                        background: `linear-gradient(100deg, #f8e697 20%, ${OLIVE} 95%)`,
                        WebkitBackgroundClip: "text", backgroundClip: "text",
                        WebkitTextFillColor: "transparent", color: "transparent",
                    }}>
                        {HERO.title}
                    </Box>
                </Reveal>

                <Reveal delay={0.12}>
                    <Typography sx={{
                        mt: 3, maxWidth: 680, color: "#d8d8d8", lineHeight: 1.55,
                        fontSize: {xs: "1.1rem", md: "1.4rem"},
                    }}>
                        {HERO.tagline}
                    </Typography>
                </Reveal>

                <Reveal delay={0.18}>
                    <Stack direction={{xs: "column", sm: "row"}} spacing={1.5} sx={{mt: 4}} alignItems={{sm: "center"}}>
                        <Button
                            variant="contained" size="large" onClick={onPrimary} disabled={guestBusy}
                            startIcon={signedIn ? <ArrowForwardIcon/> : <PlayArrowIcon/>}
                            sx={{px: 3, py: 1.2, fontWeight: 600}}
                        >
                            {signedIn ? "Open dashboard" : guestBusy ? "Starting demo…" : "Try the live demo"}
                        </Button>
                        {!signedIn && (
                            <Button variant="outlined" size="large" onClick={onSignIn} sx={{px: 3, py: 1.2}}>
                                Sign in
                            </Button>
                        )}
                        <Button color="inherit" size="large" component="a" href={GITHUB_URL} target="_blank"
                                rel="noopener noreferrer" startIcon={<GitHubIcon/>}
                                sx={{color: "text.secondary", textTransform: "none", "&:hover": {color: "#fff"}}}>
                            View source
                        </Button>
                    </Stack>
                    {!signedIn && (
                        <Typography variant="caption" sx={{display: "block", mt: 1.5, color: "text.secondary"}}>
                            The demo is a read-only guest session, so no account is needed.
                        </Typography>
                    )}
                    {guestError && (
                        <Typography variant="caption" role="alert" sx={{display: "block", mt: 1, color: "#ff8a80"}}>
                            {guestError}
                        </Typography>
                    )}
                </Reveal>

                <Reveal delay={0.24}>
                    <Stack direction="row" useFlexGap flexWrap="wrap" sx={{mt: 5, gap: 1}}>
                        {HERO.chips.map((c) => (
                            <Chip key={c} label={c} size="small" sx={{
                                fontFamily: "monospace", fontSize: "0.72rem", color: "#e0e0e0",
                                background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.10)",
                            }}/>
                        ))}
                    </Stack>
                </Reveal>
            </Container>
        </Box>
    );
}

// ── Sections ────────────────────────────────────────────────────────────────
function Section({children, sx}) {
    return (
        <Box component="section" sx={{py: {xs: 5, md: 8}, ...sx}}>
            <Container maxWidth="lg">{children}</Container>
        </Box>
    );
}

function HowItWorks() {
    return (
        <Section>
            <SectionHeader
                id="how-it-works"
                eyebrow="How it works"
                title="From a sensor reading to a switched light, in real time"
                subtitle="Click any block to see what it does. Highlighted lines show the traffic flowing in and out of it."
            />
            <Reveal><SystemFlow/></Reveal>

            <Box sx={{mt: {xs: 5, md: 7}, position: "relative", maxWidth: 860}}>
                {/* timeline rail */}
                <Box aria-hidden sx={{
                    position: "absolute", left: 19, top: 8, bottom: 8, width: 2,
                    background: `linear-gradient(${ACCENT}, rgba(240,220,122,0.05))`,
                }}/>
                <Stack spacing={{xs: 3, md: 3.5}}>
                    {STEPS.map((step, i) => (
                        <Reveal key={step.title} delay={0.04}>
                            <Stack direction="row" spacing={2.5} alignItems="flex-start">
                                <Box sx={{
                                    position: "relative", zIndex: 1, width: 40, height: 40, borderRadius: "50%", flexShrink: 0,
                                    display: "grid", placeItems: "center", fontFamily: "monospace", fontWeight: 700,
                                    color: "#161616", background: ACCENT, boxShadow: "0 0 0 6px #161616",
                                }}>
                                    {i + 1}
                                </Box>
                                <Box sx={{minWidth: 0, pt: 0.25}}>
                                    <Typography sx={{fontWeight: 600, fontSize: "1.1rem", color: "#fff"}}>{step.title}</Typography>
                                    <Typography sx={{mt: 0.5, color: "text.secondary", lineHeight: 1.65}}>{step.body}</Typography>
                                    <Box component="code" sx={{
                                        display: "inline-block", mt: 1, px: 1, py: 0.25, borderRadius: "6px", maxWidth: "100%",
                                        fontFamily: "monospace", fontSize: "0.75rem", color: ACCENT,
                                        background: "rgba(240,220,122,0.07)", border: "1px solid rgba(240,220,122,0.18)",
                                        overflowWrap: "anywhere",
                                    }}>
                                        {step.code}
                                    </Box>
                                </Box>
                            </Stack>
                        </Reveal>
                    ))}
                </Stack>
            </Box>
        </Section>
    );
}

function Features() {
    return (
        <Section>
            <SectionHeader
                id="features"
                eyebrow="Features"
                title="Everything between the device and the dashboard"
                subtitle="Built for a real smart home: it ingests, stores, automates and visualises, all from one app."
            />
            <Box sx={{
                display: "grid", gap: {xs: 1.5, md: 2.5},
                gridTemplateColumns: {xs: "1fr", sm: "repeat(2, 1fr)", lg: "repeat(3, 1fr)"},
            }}>
                {FEATURES.map((f, i) => (
                    <Reveal key={f.title} delay={(i % 3) * 0.06} sx={{display: "flex"}}>
                        <Box sx={{
                            ...glass, flex: 1, p: {xs: 2.25, md: 3}, borderRadius: "16px",
                            transition: "transform .2s, border-color .2s, box-shadow .2s",
                            "&:hover": {
                                transform: "translateY(-4px)", borderColor: "rgba(240,220,122,0.45)",
                                boxShadow: "0 14px 40px rgba(0,0,0,0.45), 0 0 0 1px rgba(240,220,122,0.08)",
                            },
                        }}>
                            <IconTile icon={f.icon}/>
                            <Typography sx={{mt: 2, fontWeight: 600, fontSize: "1.05rem", color: "#fff"}}>{f.title}</Typography>
                            <Typography sx={{mt: 0.75, color: "text.secondary", lineHeight: 1.65, fontSize: "0.92rem"}}>
                                {f.body}
                            </Typography>
                        </Box>
                    </Reveal>
                ))}
            </Box>
        </Section>
    );
}

function TechStack() {
    return (
        <Section>
            <SectionHeader
                id="stack"
                eyebrow="Tech stack"
                title="Modern, boring-where-it-counts technology"
                subtitle="A Java 21 / Spring Boot backend, a React 19 single-page app, MQTT for hardware and MongoDB plus Redis for data."
            />
            <Box sx={{
                display: "grid", gap: {xs: 1.5, md: 2.5},
                gridTemplateColumns: {xs: "1fr", md: "repeat(2, 1fr)"},
            }}>
                {STACK.map((g, i) => (
                    <Reveal key={g.group} delay={(i % 2) * 0.06} sx={{display: "flex"}}>
                        <Box sx={{...glass, flex: 1, p: {xs: 2.25, md: 3}, borderRadius: "16px"}}>
                            <Stack direction="row" spacing={1.5} alignItems="center" sx={{mb: 2}}>
                                <IconTile icon={g.icon} size={38}/>
                                <Typography sx={{fontWeight: 600, fontSize: "1.05rem", color: "#fff"}}>{g.group}</Typography>
                            </Stack>
                            <Stack direction="row" useFlexGap flexWrap="wrap" sx={{gap: 0.9}}>
                                {g.items.map((t) => (
                                    <Chip key={t} label={t} size="small" variant="outlined" sx={{
                                        fontFamily: "monospace", fontSize: "0.74rem", color: "#e6e6e6",
                                        borderColor: "rgba(255,255,255,0.16)",
                                        transition: "all .2s",
                                        "&:hover": {borderColor: ACCENT, color: ACCENT},
                                    }}/>
                                ))}
                            </Stack>
                        </Box>
                    </Reveal>
                ))}
            </Box>
        </Section>
    );
}

function ClosingCta({signedIn, onPrimary, guestBusy}) {
    return (
        <Section sx={{pb: {xs: 8, md: 12}}}>
            <Reveal>
                <Box sx={{
                    ...glass, position: "relative", overflow: "hidden", borderRadius: "24px",
                    p: {xs: 3, md: 6}, textAlign: "center",
                    border: "1px solid rgba(240,220,122,0.25)",
                }}>
                    <Box aria-hidden sx={{
                        position: "absolute", inset: 0, pointerEvents: "none",
                        background: "radial-gradient(500px 220px at 50% 0%, rgba(240,220,122,0.14), transparent 70%)",
                    }}/>
                    <Typography component="h2" sx={{
                        position: "relative", fontSize: {xs: "1.6rem", md: "2.2rem"}, fontWeight: 600, color: "#fff",
                    }}>
                        {signedIn ? "Back to your home" : "See it running"}
                    </Typography>
                    <Typography sx={{position: "relative", mt: 1.5, color: "text.secondary", maxWidth: 520, mx: "auto"}}>
                        {signedIn
                            ? "Your devices, automations and live data are one click away."
                            : "Explore the dashboards, automations and analytics in a read-only guest session, or read the code on GitHub."}
                    </Typography>
                    <Stack direction={{xs: "column", sm: "row"}} spacing={1.5} justifyContent="center"
                           sx={{position: "relative", mt: 3.5}}>
                        <Button variant="contained" size="large" onClick={onPrimary} disabled={guestBusy}
                                sx={{px: 3.5, fontWeight: 600}}>
                            {signedIn ? "Open dashboard" : guestBusy ? "Starting demo…" : "Try the live demo"}
                        </Button>
                        <Button variant="outlined" size="large" component="a" href={GITHUB_URL} target="_blank"
                                rel="noopener noreferrer" startIcon={<GitHubIcon/>} sx={{px: 3.5}}>
                            GitHub
                        </Button>
                    </Stack>
                </Box>
            </Reveal>
        </Section>
    );
}

// ── Page ────────────────────────────────────────────────────────────────────
export default function WelcomePage() {
    const {user, loginAsGuest} = useAuth();
    const navigate = useNavigate();
    const signedIn = !isEmpty(user);
    const [guestBusy, setGuestBusy] = useState(false);
    const [guestError, setGuestError] = useState("");

    const startDemo = async () => {
        if (signedIn) {
            navigate("/");
            return;
        }
        try {
            setGuestBusy(true);
            setGuestError("");
            await loginAsGuest();
            navigate("/");
        } catch {
            setGuestError("The demo is unavailable right now. Please sign in or create an account instead.");
        } finally {
            setGuestBusy(false);
        }
    };

    return (
        <Box sx={{position: "relative", minHeight: "100%", pb: "var(--safe-bottom)"}}>
            {!signedIn && <TopBar onSignIn={() => navigate("/signin")} onSignUp={() => navigate("/signup")}/>}
            <Hero
                signedIn={signedIn}
                onPrimary={startDemo}
                onSignIn={() => navigate("/signin")}
                guestBusy={guestBusy}
                guestError={guestError}
            />
            <HowItWorks/>
            <Features/>
            <TechStack/>
            <ClosingCta signedIn={signedIn} onPrimary={startDemo} guestBusy={guestBusy}/>
        </Box>
    );
}
