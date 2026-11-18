// components/welcome/content.jsx
// All copy for the Overview / landing page lives here so the page components
// stay purely presentational. Every claim below was checked against the repo
// (pom.xml, package.json, MqttConfig.java, route table in SideDrawer.jsx).

import SensorsIcon from "@mui/icons-material/Sensors";
import HubIcon from "@mui/icons-material/Hub";
import DnsIcon from "@mui/icons-material/Dns";
import StorageIcon from "@mui/icons-material/Storage";
import BoltIcon from "@mui/icons-material/Bolt";
import SpeedIcon from "@mui/icons-material/Speed";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import DeveloperBoardIcon from "@mui/icons-material/DeveloperBoard";
import DashboardIcon from "@mui/icons-material/Dashboard";
import InsightsIcon from "@mui/icons-material/Insights";
import MapIcon from "@mui/icons-material/Map";
import TuneIcon from "@mui/icons-material/Tune";
import LightbulbIcon from "@mui/icons-material/Lightbulb";
import SecurityIcon from "@mui/icons-material/Security";
import TroubleshootIcon from "@mui/icons-material/Troubleshoot";
import WebIcon from "@mui/icons-material/Web";
import SyncAltIcon from "@mui/icons-material/SyncAlt";

export const GITHUB_URL = "https://github.com/subhamgupta28/Automata";

// ── Hero ────────────────────────────────────────────────────────────────────
export const HERO = {
    eyebrow: "IoT automation & device orchestration",
    title: "Automata",
    tagline: "Connect your devices. Build automations visually. Watch everything happen live.",
    chips: ["MQTT", "WebSocket · STOMP", "Spring Boot", "React 19", "MongoDB", "Redis"],
};

// ── System diagram ──────────────────────────────────────────────────────────
// Node details shown in the info panel when a node is selected.
export const FLOW_NODES = {
    devices: {
        title: "IoT Devices",
        sub: "ESP32 · Pi · WLED",
        icon: SensorsIcon,
        description:
            "Microcontrollers and single-board computers publish telemetry and status over MQTT, and listen for action commands. WLED lighting controllers are driven through their own MQTT topics.",
        tags: ["ESP32", "Raspberry Pi", "WLED", "MQTT client"],
    },
    mqtt: {
        title: "MQTT Broker",
        sub: "Mosquitto · HiveMQ",
        icon: HubIcon,
        description:
            "The message bus between hardware and software. Devices publish to sendData, sendLiveData and status; the backend publishes commands to action and receives acknowledgements on ackAction.",
        tags: ["sendData", "sendLiveData", "status", "action", "ackAction"],
    },
    backend: {
        title: "Spring Boot API",
        sub: "Java 21 · Spring Boot 4",
        icon: DnsIcon,
        description:
            "The brain of the system. A Spring Integration MQTT adapter ingests device messages, REST controllers serve the UI, and Spring Security with JWT guards every request. Virtual threads are enabled.",
        tags: ["Spring Integration", "Spring Security", "JWT", "REST", "Virtual threads"],
    },
    automation: {
        title: "Automation Engine",
        sub: "Triggers → actions",
        icon: AutoAwesomeIcon,
        description:
            "Rules built on the visual canvas are evaluated against incoming data. When a trigger fires and its conditions hold, the engine executes its actions, which are published back to devices over MQTT.",
        tags: ["Rule evaluation", "Validation", "Scheduling", "Action dispatch"],
    },
    mongo: {
        title: "MongoDB",
        sub: "Documents & time series",
        icon: StorageIcon,
        description:
            "Durable storage for devices, telemetry history, automations, dashboards, notifications and users.",
        tags: ["Devices", "Telemetry history", "Automations", "Users"],
    },
    redis: {
        title: "Redis",
        sub: "Cache",
        icon: BoltIcon,
        description:
            "Spring Cache on Redis serves frequently read data quickly, and feature-flag changes clear the cache instantly.",
        tags: ["Spring Cache", "Feature flags"],
    },
    ws: {
        title: "WebSocket",
        sub: "STOMP · /topic/data",
        icon: SyncAltIcon,
        description:
            "Live device data is broadcast to every connected browser over a STOMP broker, so dashboards update the moment a device reports, with no polling.",
        tags: ["SockJS", "STOMP", "/topic/data"],
    },
    ui: {
        title: "React Dashboard",
        sub: "React 19 · MUI",
        icon: WebIcon,
        description:
            "The single-page app you are looking at: live dashboards, analytics, maps, the visual automation builder and device management, all talking to the API with Axios and JWT.",
        tags: ["React 19", "Material UI", "React Flow", "Axios"],
    },
};

// Positions per layout. "desktop" is a left-to-right pipeline; "mobile" is a
// two-column vertical snake so the diagram stays legible on a phone.
export const FLOW_LAYOUTS = {
    desktop: {
        height: 470,
        nodeWidth: 212,
        positions: {
            devices: {x: 0, y: 110},
            mqtt: {x: 310, y: 110},
            backend: {x: 620, y: 110},
            automation: {x: 620, y: 300},
            mongo: {x: 930, y: 10},
            redis: {x: 930, y: 140},
            ws: {x: 930, y: 270},
            ui: {x: 930, y: 400},
        },
    },
    mobile: {
        height: 640,
        nodeWidth: 140,
        compact: true,          // smaller node chrome, no subtitle, labels only on active edges
        positions: {
            devices: {x: 0, y: 0},
            mqtt: {x: 0, y: 120},
            automation: {x: 216, y: 180},
            backend: {x: 0, y: 240},
            mongo: {x: 216, y: 300},
            redis: {x: 216, y: 400},
            ws: {x: 0, y: 380},
            ui: {x: 0, y: 520},
        },
    },
};

// Handle ids: s/t (source/target) + side (r,l,t,b) + optional slot (a = 30%, b = 70%).
// e.g. "sr" = source on the right edge, centred; "tla" = target on the left edge, 30% down.
export const FLOW_EDGES = [
    {
        id: "devices-mqtt", source: "devices", target: "mqtt", label: "sendData",
        desktop: {sh: "sra", th: "tla"}, mobile: {sh: "sbb", th: "ttb"},
    },
    {
        id: "mqtt-devices", source: "mqtt", target: "devices", label: "action",
        desktop: {sh: "slb", th: "trb"}, mobile: {sh: "sta", th: "tba"},
    },
    {
        id: "mqtt-backend", source: "mqtt", target: "backend", label: "subscribe",
        desktop: {sh: "sr", th: "tl"}, mobile: {sh: "sbb", th: "ttb"},
    },
    {
        id: "backend-automation", source: "backend", target: "automation", label: "evaluate",
        desktop: {sh: "sb", th: "tt"}, mobile: {sh: "sr", th: "tl"},
    },
    {
        id: "automation-mqtt", source: "automation", target: "mqtt", label: "publish",
        desktop: {sh: "sl", th: "tb"}, mobile: {sh: "stb", th: "trb"},
    },
    {
        id: "backend-mongo", source: "backend", target: "mongo", label: "persist",
        desktop: {sh: "sra", th: "tl"}, mobile: {sh: "sr", th: "tl"},
    },
    {
        id: "backend-redis", source: "backend", target: "redis", label: "cache",
        desktop: {sh: "sr", th: "tl"}, mobile: {sh: "srb", th: "tl"},
    },
    {
        id: "backend-ws", source: "backend", target: "ws", label: "broadcast",
        desktop: {sh: "srb", th: "tl"}, mobile: {sh: "sb", th: "tt"},
    },
    {
        id: "ws-ui", source: "ws", target: "ui", label: "live",
        desktop: {sh: "sb", th: "tt"}, mobile: {sh: "sb", th: "tt"},
    },
];

// ── How it works ────────────────────────────────────────────────────────────
export const STEPS = [
    {
        title: "Devices report in",
        body: "ESP32, Raspberry Pi and WLED devices publish telemetry and status to the MQTT broker.",
        code: "sendData · sendLiveData · status",
    },
    {
        title: "The backend ingests",
        body: "Spring Boot subscribes through a Spring Integration adapter, stores history in MongoDB and caches in Redis.",
        code: "MQTT → Spring Boot → MongoDB / Redis",
    },
    {
        title: "Rules are evaluated",
        body: "The automation engine checks each trigger and its conditions against the fresh data.",
        code: "trigger → condition → action",
    },
    {
        title: "Actions go back out",
        body: "Matching rules publish commands to the devices, which acknowledge on ackAction.",
        code: "action → device → ackAction",
    },
    {
        title: "The UI stays live",
        body: "Every update is pushed to open dashboards over STOMP, so the screen always matches the room.",
        code: "STOMP /topic/data → React",
    },
];

// ── Features ────────────────────────────────────────────────────────────────
export const FEATURES = [
    {
        icon: DeveloperBoardIcon,
        title: "Device management",
        body: "Register, search and monitor every device with live online/offline status, details and remote reboot.",
    },
    {
        icon: AutoAwesomeIcon,
        title: "Visual automation builder",
        body: "Drag triggers, conditions and actions onto a React Flow canvas, validate the rule, then save it.",
    },
    {
        icon: TroubleshootIcon,
        title: "Live inspector",
        body: "Follow automations as they evaluate and fire, so you can see why something did or did not happen.",
    },
    {
        icon: DashboardIcon,
        title: "Real-time dashboards",
        body: "Telemetry streams in over WebSocket onto a home dashboard, with in-app alerts when things go wrong.",
    },
    {
        icon: InsightsIcon,
        title: "Analytics",
        body: "Time-series history, energy statistics and trend charts built with MUI X Charts and Recharts.",
    },
    {
        icon: MapIcon,
        title: "Device map",
        body: "See where your devices are on an interactive Leaflet map.",
    },
    {
        icon: TuneIcon,
        title: "Virtual devices",
        body: "Define your own devices and attributes, choose what shows on the dashboard and what feeds analytics.",
    },
    {
        icon: LightbulbIcon,
        title: "WLED & audio-reactive",
        body: "Discover WLED controllers on your network and drive lighting effects from microphone audio.",
    },
    {
        icon: SecurityIcon,
        title: "Secure, multi-home",
        body: "JWT authentication, role-based access, separate homes and a read-only guest mode.",
    },
];

// ── Tech stack ──────────────────────────────────────────────────────────────
export const STACK = [
    {
        group: "Frontend",
        icon: WebIcon,
        items: ["React 19", "Vite", "Material UI 7", "React Flow", "Motion", "MUI X Charts", "Recharts", "Leaflet", "React Router 7", "Axios", "SockJS + STOMP"],
    },
    {
        group: "Backend",
        icon: DnsIcon,
        items: ["Java 21", "Spring Boot 4", "Spring Security", "JWT (JJWT)", "Spring Integration MQTT", "Spring WebSocket", "Spring Data", "Spring Cache", "Actuator"],
    },
    {
        group: "Data & messaging",
        icon: StorageIcon,
        items: ["MongoDB", "Redis", "MQTT", "Mosquitto", "HiveMQ"],
    },
    {
        group: "Infrastructure",
        icon: SpeedIcon,
        items: ["Docker", "Kubernetes", "Jenkins", "Maven"],
    },
];
