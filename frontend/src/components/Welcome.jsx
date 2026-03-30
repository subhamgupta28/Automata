import React from "react";

const archStyles = `
  .arch-diagram {
    --arch-bg: #0d1117;
    --arch-surface: #161b22;
    --arch-border: #30363d;
    --arch-text: #e6edf3;
    --arch-muted: #8b949e;
    --arch-blue: #58a6ff;
    --arch-green: #3fb950;
    --arch-orange: #d29922;
    --arch-red: #f85149;
    --arch-purple: #bc8cff;
    --arch-teal: #39d0d8;
    --arch-pink: #ff7b72;
    --arch-yellow: #e3b341;

    background: transparent;
    color: var(--arch-text);
    font-family: 'Segoe UI', system-ui, sans-serif;
    font-size: 13px;
    padding: 24px;
    width: 100%;
    height: 100%;
    overflow: auto;
  }
  .arch-diagram *, .arch-diagram *::before, .arch-diagram *::after {
    box-sizing: border-box;
  }
  .arch-diagram h1 {
    text-align: center;
    font-size: 22px;
    font-weight: 600;
    color: var(--arch-text);
    margin-bottom: 6px;
    letter-spacing: 0.5px;
  }
  .arch-diagram .a-subtitle {
    text-align: center;
    color: var(--arch-muted);
    font-size: 12px;
    margin-bottom: 32px;
  }
  .arch-diagram .a-wrap { max-width: 1300px; margin: 0 auto; }

  .arch-diagram .a-card {
    background: var(--arch-surface);
    border: 1px solid var(--arch-border);
    border-radius: 10px;
    padding: 14px 16px;
  }
  .arch-diagram .a-card-title {
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .arch-diagram .a-card-title .a-dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    display: inline-block;
    flex-shrink: 0;
  }
  .arch-diagram .a-item {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    padding: 3px 0;
    color: var(--arch-muted);
    font-size: 12px;
    line-height: 1.4;
  }
  .arch-diagram .a-tag {
    font-size: 10px;
    border-radius: 4px;
    padding: 1px 5px;
    font-weight: 600;
    white-space: nowrap;
    flex-shrink: 0;
    margin-top: 1px;
  }
  .arch-diagram .a-pill {
    display: inline-block;
    background: #21262d;
    color: var(--arch-muted);
    border-radius: 4px;
    font-size: 11px;
    padding: 2px 6px;
    margin: 2px 2px 0 0;
  }
  .arch-diagram .a-sub-card {
    background: #0d1117;
    border: 1px solid var(--arch-border);
    border-radius: 7px;
    padding: 10px 12px;
  }
  .arch-diagram .a-sub-title {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    margin-bottom: 7px;
    color: var(--arch-muted);
  }
  .arch-diagram .a-flow-row {
    display: flex;
    gap: 4px;
    align-items: center;
    margin-bottom: 4px;
    flex-wrap: wrap;
  }
  .arch-diagram .a-flow-step {
    background: #21262d;
    border: 1px solid var(--arch-border);
    border-radius: 5px;
    padding: 3px 8px;
    font-size: 11px;
    color: var(--arch-text);
    white-space: nowrap;
  }
  .arch-diagram .a-flow-arrow { color: var(--arch-muted); font-size: 12px; }

  /* themes */
  .arch-diagram .a-theme-blue   { border-color: #1f6feb; }
  .arch-diagram .a-theme-blue   .a-card-title { color: var(--arch-blue); }
  .arch-diagram .a-theme-blue   .a-dot { background: var(--arch-blue); }
  .arch-diagram .a-theme-green  { border-color: #238636; }
  .arch-diagram .a-theme-green  .a-card-title { color: var(--arch-green); }
  .arch-diagram .a-theme-green  .a-dot { background: var(--arch-green); }
  .arch-diagram .a-theme-orange { border-color: #9e6a03; }
  .arch-diagram .a-theme-orange .a-card-title { color: var(--arch-orange); }
  .arch-diagram .a-theme-orange .a-dot { background: var(--arch-orange); }
  .arch-diagram .a-theme-purple { border-color: #6e40c9; }
  .arch-diagram .a-theme-purple .a-card-title { color: var(--arch-purple); }
  .arch-diagram .a-theme-purple .a-dot { background: var(--arch-purple); }
  .arch-diagram .a-theme-teal   { border-color: #1b4b52; }
  .arch-diagram .a-theme-teal   .a-card-title { color: var(--arch-teal); }
  .arch-diagram .a-theme-teal   .a-dot { background: var(--arch-teal); }
  .arch-diagram .a-theme-red    { border-color: #b91c1c; }
  .arch-diagram .a-theme-red    .a-card-title { color: var(--arch-red); }
  .arch-diagram .a-theme-red    .a-dot { background: var(--arch-red); }
  .arch-diagram .a-theme-yellow { border-color: #9e6a03; }
  .arch-diagram .a-theme-yellow .a-card-title { color: var(--arch-yellow); }
  .arch-diagram .a-theme-yellow .a-dot { background: var(--arch-yellow); }

  /* tag colours */
  .arch-diagram .t-blue   { background: #1f3a5f; color: var(--arch-blue); }
  .arch-diagram .t-green  { background: #1a3a28; color: var(--arch-green); }
  .arch-diagram .t-orange { background: #3a2a10; color: var(--arch-orange); }
  .arch-diagram .t-purple { background: #2e1f5e; color: var(--arch-purple); }
  .arch-diagram .t-teal   { background: #0f2d30; color: var(--arch-teal); }
  .arch-diagram .t-red    { background: #3a1a1a; color: var(--arch-red); }
  .arch-diagram .t-gray   { background: #21262d; color: var(--arch-muted); }
  .arch-diagram .t-yellow { background: #3a2a10; color: var(--arch-yellow); }

  /* arrows */
  .arch-diagram .a-arrow-band {
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding: 5px 0;
  }
  .arch-diagram .a-arrow-col {
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .arch-diagram .a-vline { width: 2px; height: 18px; }
  .arch-diagram .a-arrowhead-down {
    width: 0; height: 0;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
  .arch-diagram .a-arrowhead-up {
    width: 0; height: 0;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
  .arch-diagram .a-arrow-label {
    font-size: 10px;
    color: var(--arch-muted);
    white-space: nowrap;
    background: var(--arch-bg);
    padding: 1px 7px;
    border-radius: 3px;
    border: 1px solid var(--arch-border);
    margin: 2px 0;
  }

  /* layout helpers */
  .arch-diagram .a-row  { display: flex; gap: 16px; align-items: stretch; }
  .arch-diagram .a-row-3 { display: grid; grid-template-columns: 1fr 2.4fr 1fr; gap: 16px; align-items: start; }
  .arch-diagram .a-fill { flex: 1; }
  .arch-diagram .a-divider { border-top: 1px solid var(--arch-border); margin: 9px 0; }
  .arch-diagram .a-backend-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; margin-top: 8px; }
  .arch-diagram .a-two-col   { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  .arch-diagram .a-three-col { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
  .arch-diagram .a-four-col  { display: grid; grid-template-columns: repeat(4,1fr); gap: 8px; }

  /* legend */
  .arch-diagram .a-legend {
    display: flex; gap: 16px; justify-content: center;
    flex-wrap: wrap; margin-top: 24px;
    padding-top: 16px; border-top: 1px solid var(--arch-border);
  }
  .arch-diagram .a-legend-item { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--arch-muted); }
  .arch-diagram .a-legend-dot  { width: 10px; height: 10px; border-radius: 50%; }

  /* new badge */
  .arch-diagram .a-new {
    font-size: 9px; font-weight: 700;
    background: #1a3a28; color: var(--arch-green);
    border: 1px solid #238636;
    border-radius: 3px; padding: 0 4px;
    vertical-align: middle; margin-left: 4px;
  }
`;

export default function Welcome() {
    return (
        <div className="arch-diagram">
            <style>{archStyles}</style>

            <h1>Automata — System Architecture</h1>
            <div className="a-subtitle">
                Spring Boot 3.3.1 · Java 21 · React 19.2.4 · Vite 8 · MongoDB 6 · Redis 7 · Spring Integration MQTT ·
                Kubernetes
            </div>

            <div className="a-wrap">

                {/* ══════════════════════ ROW A – Edge + CI/CD */}
                <div className="a-row">

                    {/* IoT Edge */}
                    <div className="a-card a-theme-teal a-fill">
                        <div className="a-card-title"><span className="a-dot"></span>IoT Edge Devices</div>
                        <div className="a-three-col">
                            <div>
                                <div className="a-item"><span className="a-tag t-teal">ESP32</span></div>
                                <div className="a-item"
                                     style={{paddingLeft: '2px', fontSize: '11px'}}>Sensors &amp; actuators<br/>WiFi-connected
                                </div>
                            </div>
                            <div>
                                <div className="a-item"><span className="a-tag t-teal">WLED</span></div>
                                <div className="a-item" style={{paddingLeft: '2px', fontSize: '11px'}}>LED
                                    controllers<br/>HTTP + MQTT
                                </div>
                            </div>
                            <div>
                                <div className="a-item"><span className="a-tag t-teal">RPi</span></div>
                                <div className="a-item" style={{paddingLeft: '2px', fontSize: '11px'}}>Raspberry Pi<br/>Gateway
                                    nodes
                                </div>
                            </div>
                        </div>
                        <div className="a-divider"></div>
                        <div className="a-item"><span
                            className="a-tag t-gray">Pub topics</span>sendData/# &nbsp;·&nbsp; sendLiveData/# &nbsp;·&nbsp; $SYS/broker/clients/123
                        </div>
                        <div className="a-item"><span
                            className="a-tag t-gray">Sub topics</span>action/# &nbsp;·&nbsp; automata-wled/# &nbsp;·&nbsp; automata-wled/all
                        </div>
                    </div>

                    {/* MQTT Broker */}
                    <div className="a-card a-theme-orange" style={{minWidth: '195px'}}>
                        <div className="a-card-title"><span className="a-dot"></span>MQTT Broker</div>
                        <div className="a-item"><span className="a-tag t-orange">Mosquitto 2.0</span> K8s
                            (eclipse-mosquitto)
                        </div>
                        <div className="a-item"><span className="a-tag t-orange">HiveMQ</span> broker.hivemq.com
                            (public)
                        </div>
                        <div className="a-divider"></div>
                        <div className="a-item"><span className="a-tag t-gray">Dev</span> tcp://192.168.1.54:1884</div>
                        <div className="a-item"><span className="a-tag t-gray">Prod</span> tcp://rabbitmq:1884 <span
                            style={{fontSize: '10px', color: 'var(--arch-muted)'}}>(svc name)</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Protocol</span> MQTT (not AMQP)</div>
                        <div className="a-item" style={{marginTop: '4px'}}>
                            <span className="a-tag t-orange">QoS 1</span>&nbsp;
                            <span className="a-tag t-gray">KeepAlive 60s</span>&nbsp;
                            <span className="a-tag t-gray">MaxInflight 10k</span>
                        </div>
                    </div>

                    {/* CI/CD */}
                    <div className="a-card a-theme-yellow" style={{minWidth: '220px'}}>
                        <div className="a-card-title"><span className="a-dot"></span>CI/CD Pipeline</div>
                        <div className="a-flow-row">
                            <div className="a-flow-step">Git Push</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">Jenkins</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">Maven</div>
                        </div>
                        <div className="a-flow-row">
                            <div className="a-flow-step">Docker Build</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">Registry :5000</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">K8s Deploy</div>
                        </div>
                        <div className="a-divider"></div>
                        <div className="a-item"><span className="a-tag t-yellow">Jenkinsfile</span> Webhook-triggered
                            stages
                        </div>
                        <div className="a-item"><span className="a-tag t-gray">Image</span> localhost:5000/myapp:local
                        </div>
                    </div>

                </div>
                {/* /row A */}

                {/* ── Arrow band A→B */}
                <div className="a-arrow-band">
                    <div className="a-arrow-col" style={{flex: 3}}>
                        <div className="a-arrowhead-up" style={{borderBottom: '7px solid #39d0d8'}}></div>
                        <div className="a-vline" style={{background: '#39d0d8'}}></div>
                        <div className="a-arrow-label" style={{color: 'var(--arch-teal)', borderColor: '#1b4b52'}}>
                            MQTT Publish / Subscribe · Spring Integration MQTT 6.2.1
                        </div>
                        <div className="a-vline" style={{background: '#39d0d8'}}></div>
                        <div className="a-arrowhead-down" style={{borderTop: '7px solid #39d0d8'}}></div>
                    </div>
                    <div className="a-arrow-col" style={{flex: 1.5}}>
                        <div className="a-vline" style={{background: '#e3b341', height: '36px'}}></div>
                        <div className="a-arrowhead-down" style={{borderTop: '7px solid #e3b341'}}></div>
                    </div>
                </div>

                {/* ══════════════════════ ROW B – Frontend / Backend / Infra */}
                <div className="a-row-3">

                    {/* ── Frontend */}
                    <div className="a-card a-theme-blue">
                        <div className="a-card-title"><span className="a-dot"></span>Frontend</div>

                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>Framework &amp; Build</div>
                        <div className="a-item"><span className="a-tag t-blue">React 19.2.4</span> JSX / Hooks</div>
                        <div className="a-item"><span className="a-tag t-blue">Vite 8.0.1</span> Bundles → static/</div>
                        <div className="a-item"><span className="a-tag t-blue">Router 7.5.2</span> React Router DOM
                        </div>

                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>UI &amp; Styling</div>
                        <div className="a-item"><span className="a-tag t-blue">MUI 7.3.4</span> Component library</div>
                        <div className="a-item"><span className="a-tag t-blue">MUI X 8.27.2</span> Charts + Date pickers
                        </div>
                        <div className="a-item"><span className="a-tag t-gray">Base-UI 1.3.0</span> <span
                            className="a-new">NEW</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Toolpad Core 0.16.0</span> <span
                            className="a-new">NEW</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Emotion 11.14.0</span> CSS-in-JS</div>
                        <div className="a-item"><span className="a-tag t-gray">Framer Motion 12.38</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Notistack 3.0.2</span> Toast alerts</div>

                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>Auth</div>
                        <div className="a-item"><span className="a-tag t-blue">JWT</span> AuthContext.jsx</div>
                        <div className="a-item"><span className="a-tag t-gray">Axios 1.8.4</span> Auto-refresh
                            interceptor
                        </div>
                        <div className="a-item"><span className="a-tag t-gray">Route</span> PrivateRoute guard</div>

                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>Real-time</div>
                        <div className="a-item"><span className="a-tag t-blue">STOMP</span> sockjs-client 1.6.1</div>
                        <div className="a-item"><span className="a-tag t-gray">Hook</span> useWebSocket.jsx</div>

                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>Visualization</div>
                        <div className="a-item"><span className="a-tag t-gray">Chart.js 4.4.9</span> Bar / Line / Pie
                        </div>
                        <div className="a-item"><span className="a-tag t-gray">Recharts 2.15.3</span> Analytics</div>
                        <div className="a-item"><span className="a-tag t-gray">Google Charts 5.2.1</span> <span
                            className="a-new">NEW</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Liquid Gauge 0.1.0</span> <span
                            className="a-new">NEW</span></div>
                        <div className="a-item"><span className="a-tag t-gray">Leaflet 1.9.4</span> Map view</div>
                        <div className="a-item"><span className="a-tag t-gray">XYFlow 12.9.0</span> Automation editor
                        </div>
                        <div className="a-item"><span className="a-tag t-gray">elkjs 0.11.1</span> Graph layout</div>

                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>Components (98 files)</div>
                        <div style={{marginTop: '2px', flexWrap: 'wrap', display: 'flex', gap: '3px'}}>
                            <span className="a-pill">action/</span>
                            <span className="a-pill">auth/</span>
                            <span className="a-pill">charts/</span>
                            <span className="a-pill">dashboard/</span>
                            <span className="a-pill">device_types/</span>
                            <span className="a-pill">integrations/</span>
                            <span className="a-pill">v2/</span>
                            <span className="a-pill">demo/</span>
                            <span className="a-pill">custom_drawer/</span>
                        </div>
                    </div>

                    {/* ── Backend */}
                    <div className="a-card a-theme-green">
                        <div className="a-card-title">
                            <span className="a-dot"></span>Backend — Spring Boot 3.3.1 · Java 21 · Virtual Threads ·
                            Async/Scheduled
                        </div>

                        <div className="a-backend-grid">

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-green)'}}>Controllers (REST
                                    /api/v1/)
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/main</span> Device data, charts
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/action</span> Automation
                                    CRUD &amp; exec
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/virtual</span> Virtual devices
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/auth</span> Login / register /
                                    refresh
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/dashboard</span> Dashboard CRUD
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">/utility</span> System utilities
                                </div>
                            </div>

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-green)'}}>Services</div>
                                <div className="a-item"><span className="a-tag t-green">Main</span> Device ops,
                                    attributes
                                </div>
                                <div className="a-item"><span className="a-tag t-green">Mqtt</span> @ServiceActivator
                                    handlers
                                </div>
                                <div className="a-item"><span className="a-tag t-green">Analytics</span> Time-series
                                    aggregation
                                </div>
                                <div className="a-item"><span className="a-tag t-green">Notify</span> WebSocket
                                    broadcasts
                                </div>
                                <div className="a-item"><span className="a-tag t-green">Redis</span> Cache + distributed
                                    locks
                                </div>
                                <div className="a-item"><span className="a-tag t-green">Schedule</span> @Scheduled
                                    background tasks
                                </div>
                                <div className="a-item"><span className="a-tag t-green">VirtualDevice</span> Virtual
                                    logic
                                </div>
                                <div className="a-item"><span className="a-tag t-green">VirtualDashboard</span></div>
                            </div>

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-green)'}}>Data Access (21
                                    Repos)
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">Device</span> DeviceRepository
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">Data</span> DataRepository</div>
                                <div className="a-item"><span className="a-tag t-gray">Hist</span> DataHistRepository
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">Auto</span> AutomationRepository
                                </div>
                                <div className="a-item"><span
                                    className="a-tag t-gray">Log</span> AutomationLogRepository
                                </div>
                                <div className="a-item"><span
                                    className="a-tag t-gray">Energy</span> EnergyStatRepository
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">User</span> UsersRepository</div>
                                <div className="a-item" style={{color: 'var(--arch-muted)', fontSize: '11px'}}>+ 14
                                    more…
                                </div>
                            </div>

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-orange)'}}>MQTT Integration
                                    (Spring)
                                </div>
                                <div className="a-item"><span className="a-tag t-orange">Config</span> MqttConfig.java
                                </div>
                                <div className="a-item"><span
                                    className="a-tag t-orange">Paho</span> MqttPahoMessageDriven
                                </div>
                                <div className="a-item"><span className="a-tag t-orange">Flow</span> IntegrationFlow
                                    routing
                                </div>
                                <div className="a-item"><span className="a-tag t-orange">Safe</span> SafeJsonTransformer
                                </div>
                                <div className="a-item"><span className="a-tag t-orange">Out</span> mqttOutboundChannel
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">Pool</span> 2–10 thread executor
                                </div>
                            </div>

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-blue)'}}>WebSocket (STOMP)</div>
                                <div className="a-item"><span className="a-tag t-blue">EP</span> /ws (SockJS)</div>
                                <div className="a-item"><span className="a-tag t-blue">Broker</span> /topic &nbsp;/queue
                                </div>
                                <div className="a-item"><span className="a-tag t-blue">App</span> /app &nbsp;/automata
                                </div>
                                <div className="a-item"><span className="a-tag t-blue">User</span> /user destinations
                                </div>
                                <div className="a-item"><span className="a-tag t-blue">API</span> SimpMessagingTemplate
                                </div>
                                <div className="a-item"><span
                                    className="a-tag t-gray">Sub</span> /topic/data &nbsp;/topic/alert
                                </div>
                                <div className="a-item"><span className="a-tag t-gray">Sub</span> /topic/notification
                                </div>
                            </div>

                            <div className="a-sub-card">
                                <div className="a-sub-title" style={{color: 'var(--arch-purple)'}}>System Modules</div>
                                <div className="a-item"><span className="a-tag t-purple">OSHI 6.6.5</span> CPU/RAM/Disk
                                    metrics
                                </div>
                                <div className="a-item"><span className="a-tag t-purple">jMDNS 3.5.8</span> mDNS
                                    discovery
                                </div>
                                <div className="a-item"><span className="a-tag t-purple">UDP</span> Broadcast discovery
                                </div>
                                <div className="a-item"><span className="a-tag t-purple">WLED</span> HTTP LED control
                                </div>
                                <div className="a-item"><span
                                    className="a-tag t-purple">Audio</span> AudioWebSocketHandler
                                </div>
                                <div className="a-item"><span className="a-tag t-purple">AudioWLED</span> Reactive
                                    effects
                                </div>
                            </div>

                        </div>

                        {/* Automation Engine */}
                        <div className="a-divider" style={{marginTop: '12px'}}></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-purple)', marginBottom: '8px'}}>
                            Automation Engine <span className="a-new">EXPANDED</span>
                        </div>
                        <div className="a-two-col" style={{gap: '12px'}}>
                            <div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">Trigger</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Condition Eval</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Action</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">MQTT out</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Log + Alert</div>
                                </div>
                                <div className="a-item" style={{marginTop: '4px'}}>
                                    <span className="a-tag t-purple">AutomationService</span> 805 lines · core executor
                                </div>
                                <div className="a-item">
                                    <span className="a-tag t-purple">ValidationService</span> 362 lines · rule
                                    validation
                                </div>
                            </div>
                            <div>
                                <div className="a-item"><span className="a-tag t-purple">SimulationService</span> 379
                                    lines · dry-run testing <span className="a-new">NEW</span></div>
                                <div className="a-item"><span className="a-tag t-purple">AnalyticsService</span> 293
                                    lines · execution metrics <span className="a-new">NEW</span></div>
                                <div className="a-item"><span className="a-tag t-purple">MultiTimezone</span> 256 lines
                                    · timezone-aware triggers <span className="a-new">NEW</span></div>
                                <div className="a-item"><span className="a-tag t-gray">AutomationController</span> 316+
                                    lines · expanded endpoints
                                </div>
                            </div>
                        </div>

                        {/* Security */}
                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-red)', marginBottom: '6px'}}>Security —
                            Spring Security + JWT
                        </div>
                        <div className="a-flow-row">
                            <div className="a-flow-step">Request</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">JwtAuthFilter</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">SecurityConfig</div>
                            <div className="a-flow-arrow">→</div>
                            <div className="a-flow-step">STATELESS</div>
                        </div>
                        <div className="a-item" style={{marginTop: '4px', flexWrap: 'wrap', gap: '4px'}}>
                            <span className="a-tag t-red">JJWT 0.11.5</span>
                            <span className="a-tag t-red">7-day tokens</span>
                            <span className="a-tag t-red">BCrypt 10r</span>
                            <span className="a-tag t-gray">IP local auth</span>
                            <span className="a-tag t-gray">CORS whitelist</span>
                        </div>
                        <div className="a-item" style={{
                            marginTop: '3px',
                            flexWrap: 'wrap',
                            gap: '4px',
                            color: 'var(--arch-muted)',
                            fontSize: '11px'
                        }}>
                            Public: /auth/** · /ws · /healthCheck · Allowed origins: localhost:5173,
                            raspberry.local:8010, automata.realsubhamgupta.in
                        </div>
                    </div>

                    {/* ── Right panel */}
                    <div style={{display: 'flex', flexDirection: 'column', gap: '16px'}}>

                        <div className="a-card a-theme-purple">
                            <div className="a-card-title"><span className="a-dot"></span>Kubernetes</div>
                            <div className="a-item"><span
                                className="a-tag t-purple">Deploy</span> automata-deployment.yaml
                            </div>
                            <div className="a-item"><span className="a-tag t-purple">Svc</span> LoadBalancer 6969→8010
                            </div>
                            <div className="a-item"><span className="a-tag t-purple">HPA</span> 1–2 replicas, CPU 80%
                            </div>
                            <div className="a-divider"></div>
                            <div className="a-item"><span
                                className="a-tag t-teal">mqtt.yaml</span> eclipse-mosquitto:2.0
                            </div>
                            <div className="a-item"
                                 style={{paddingLeft: '4px', fontSize: '11px', color: 'var(--arch-muted)'}}>Port 1883,
                                1Gi PVC, auth
                            </div>
                            <div className="a-item"><span className="a-tag t-green">mongodb.yaml</span> mongo:6.0</div>
                            <div className="a-item"
                                 style={{paddingLeft: '4px', fontSize: '11px', color: 'var(--arch-muted)'}}>Port 27017,
                                10Gi PVC
                            </div>
                            <div className="a-item"><span className="a-tag t-red">redis.yaml</span> redis:7 ClusterIP
                                6379
                            </div>
                            <div className="a-divider"></div>
                            <div className="a-item"><span className="a-tag t-gray">MetalLB</span> External IP</div>
                            <div className="a-item"><span className="a-tag t-gray">ConfigMap</span> Non-sensitive config
                            </div>
                            <div className="a-item"><span className="a-tag t-gray">Secret</span> JWT key, DB creds</div>
                        </div>

                        <div className="a-card" style={{borderColor: '#30363d'}}>
                            <div className="a-card-title" style={{color: 'var(--arch-muted)'}}>
                                <span className="a-dot" style={{background: 'var(--arch-muted)'}}></span>Docker
                            </div>
                            <div className="a-item"><span className="a-tag t-gray">Base</span> openjdk:21-jdk-slim</div>
                            <div className="a-item"><span className="a-tag t-gray">Port</span> EXPOSE 8010</div>
                            <div className="a-item"><span className="a-tag t-gray">JAR</span> Automata-0.0.1-SNAPSHOT
                            </div>
                            <div className="a-item"><span className="a-tag t-gray">Compose</span> MongoDB only (local
                                dev)
                            </div>
                        </div>

                        <div className="a-card a-theme-yellow">
                            <div className="a-card-title"><span className="a-dot"></span>Profiles</div>
                            <div className="a-item"><span className="a-tag t-yellow">dev</span> 192.168.1.54 services
                            </div>
                            <div className="a-item"><span className="a-tag t-yellow">prod</span> K8s service names</div>
                            <div className="a-divider"></div>
                            <div className="a-sub-title">CORS Origins</div>
                            <div className="a-item" style={{fontSize: '11px'}}>localhost:5173</div>
                            <div className="a-item" style={{fontSize: '11px'}}>raspberry.local:8010</div>
                            <div className="a-item" style={{fontSize: '11px'}}>automata.realsubhamgupta.in</div>
                        </div>

                    </div>

                </div>
                {/* /row-3 */}

                {/* ── Arrow band B→C */}
                <div className="a-arrow-band">
                    <div className="a-arrow-col" style={{flex: 1}}>
                        <div className="a-vline" style={{background: '#58a6ff', height: '14px'}}></div>
                        <div className="a-arrowhead-down" style={{borderTop: '7px solid #58a6ff'}}></div>
                    </div>
                    <div className="a-arrow-col" style={{flex: 2.4}}>
                        <div className="a-arrowhead-up" style={{borderBottom: '7px solid #3fb950'}}></div>
                        <div className="a-vline" style={{background: '#3fb950', height: '12px'}}></div>
                        <div className="a-arrow-label" style={{color: 'var(--arch-green)', borderColor: '#238636'}}>
                            Spring Data MongoDB · @Cacheable Redis · Spring Integration MQTT
                        </div>
                        <div className="a-vline" style={{background: '#3fb950', height: '12px'}}></div>
                        <div className="a-arrowhead-down" style={{borderTop: '7px solid #3fb950'}}></div>
                    </div>
                    <div className="a-arrow-col" style={{flex: 1}}>
                        <div className="a-vline" style={{background: '#bc8cff', height: '14px'}}></div>
                        <div className="a-arrowhead-down" style={{borderTop: '7px solid #bc8cff'}}></div>
                    </div>
                </div>

                {/* ══════════════════════ ROW C – Data Layer */}
                <div className="a-row">

                    {/* MongoDB */}
                    <div className="a-card a-theme-green a-fill">
                        <div className="a-card-title">
                            <span className="a-dot"></span>MongoDB 6.0 — Primary Data Store (25 @Document collections)
                        </div>
                        <div className="a-four-col">
                            <div>
                                <div className="a-sub-title">Devices</div>
                                <div className="a-item"><span className="a-tag t-green">device</span></div>
                                <div className="a-item"><span className="a-tag t-gray">attribute</span></div>
                                <div className="a-item"><span className="a-tag t-gray">attribute_type</span></div>
                                <div className="a-item"><span className="a-tag t-gray">device_action_state</span></div>
                            </div>
                            <div>
                                <div className="a-sub-title">Time-Series</div>
                                <div className="a-item"><span className="a-tag t-green">data</span> Real-time</div>
                                <div className="a-item"><span className="a-tag t-green">data_hist</span> Archive</div>
                                <div className="a-item"><span className="a-tag t-gray">energy_stat</span></div>
                                <div className="a-item"><span className="a-tag t-gray">device_charts</span></div>
                            </div>
                            <div>
                                <div className="a-sub-title">Automation</div>
                                <div className="a-item"><span className="a-tag t-purple">automations</span></div>
                                <div className="a-item"><span className="a-tag t-gray">automation_log</span></div>
                                <div className="a-item"><span className="a-tag t-gray">automation_detail</span></div>
                                <div className="a-item"><span className="a-tag t-gray">master_option</span></div>
                            </div>
                            <div>
                                <div className="a-sub-title">Users / UI</div>
                                <div className="a-item"><span className="a-tag t-blue">users</span></div>
                                <div className="a-item"><span className="a-tag t-gray">token</span></div>
                                <div className="a-item"><span className="a-tag t-gray">dashboard</span></div>
                                <div className="a-item"><span className="a-tag t-gray">virtual_device</span></div>
                                <div className="a-item"><span className="a-tag t-gray">virtual_dashboard</span></div>
                                <div className="a-item"><span className="a-tag t-gray">notification</span></div>
                            </div>
                        </div>
                        <div className="a-divider"></div>
                        <div className="a-item" style={{flexWrap: 'wrap', gap: '4px'}}>
                            <span className="a-tag t-green">Aggregation framework</span>
                            <span className="a-tag t-gray">21 Repositories</span>
                            <span className="a-tag t-gray">ZonedDateTime converters</span>
                            <span className="a-tag t-gray">10Gi PVC (K8s)</span>
                        </div>
                    </div>

                    {/* Redis */}
                    <div className="a-card a-theme-red" style={{minWidth: '220px'}}>
                        <div className="a-card-title">
                            <span className="a-dot"></span>Redis 7 — Cache &amp; Locking <span
                            className="a-new">EXPANDED</span>
                        </div>
                        <div className="a-item"><span className="a-tag t-red">@Cacheable</span> Spring Cache layer</div>
                        <div className="a-item"><span className="a-tag t-red">Sessions</span> Token &amp; session store
                        </div>
                        <div className="a-item"><span className="a-tag t-red">State</span> App live state</div>
                        <div className="a-divider"></div>
                        <div className="a-sub-title" style={{color: 'var(--arch-red)'}}>Distributed Locking (NEW)</div>
                        <div className="a-item"><span className="a-tag t-red">Lock</span> Atomic Lua scripts</div>
                        <div className="a-item"><span className="a-tag t-red">TTL</span> Key expiry management</div>
                        <div className="a-item"><span className="a-tag t-red">Auto</span> Automation cache</div>
                        <div className="a-item"><span className="a-tag t-red">Atomic</span> CAS operations</div>
                        <div className="a-divider"></div>
                        <div className="a-item"><span className="a-tag t-gray">ClusterIP :6379</span> (K8s)</div>
                        <div className="a-item"><span className="a-tag t-gray">RedisService</span> 299+ lines</div>
                    </div>

                </div>
                {/* /row C */}

                {/* ══════════════════════ DATA FLOWS */}
                <div style={{marginTop: '20px'}}>
                    <div className="a-card" style={{borderColor: '#30363d'}}>
                        <div className="a-card-title" style={{color: 'var(--arch-muted)'}}>
                            <span className="a-dot" style={{background: 'var(--arch-muted)'}}></span>Key Data Flows
                        </div>
                        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginTop: '4px'}}>

                            <div>
                                <div className="a-sub-title"
                                     style={{color: 'var(--arch-teal)', marginBottom: '6px'}}>IoT Telemetry
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">ESP32</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Mosquitto</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">MqttService</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">MainService.saveData()</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">MongoDB</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">SimpMessagingTemplate</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">/topic/data</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Browser</div>
                                </div>
                            </div>

                            <div>
                                <div className="a-sub-title" style={{color: 'var(--arch-purple)', marginBottom: '6px'}}>
                                    Automation Execution <span className="a-new">EXPANDED</span>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">Trigger (time/state)</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">ValidationService</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Condition Eval</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">MultiTimezone check</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Redis dist-lock</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Action Dispatch</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">MQTT publish</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Device</div>
                                    <div className="a-flow-arrow">+</div>
                                    <div className="a-flow-step">Log + WS Alert</div>
                                    <div className="a-flow-arrow">+</div>
                                    <div className="a-flow-step">Analytics</div>
                                </div>
                            </div>

                            <div>
                                <div className="a-sub-title"
                                     style={{color: 'var(--arch-blue)', marginBottom: '6px'}}>Auth Flow
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">POST /auth/login</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">BCrypt verify</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">JWT issued</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">Stored in Redis</div>
                                    <div className="a-flow-arrow">+</div>
                                    <div className="a-flow-step">MongoDB token</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">Axios Bearer header</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">JwtAuthFilter</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Protected route</div>
                                </div>
                            </div>

                            <div>
                                <div className="a-sub-title"
                                     style={{color: 'var(--arch-green)', marginBottom: '6px'}}>Analytics Pipeline
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">GET /chart/{'{deviceId}/{range}'}</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">AnalyticsService</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">MongoDB Aggregation</div>
                                </div>
                                <div className="a-flow-row">
                                    <div className="a-flow-step">ChartDataDto</div>
                                    <div className="a-flow-arrow">→</div>
                                    <div className="a-flow-step">Chart.js / Recharts / Google Charts</div>
                                </div>
                            </div>

                        </div>
                    </div>
                </div>

                {/* ══════════════════════ LEGEND */}
                <div className="a-legend">
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-teal)'}}></div>
                        IoT / MQTT
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-blue)'}}></div>
                        Frontend (React 19.2.4 · Vite 8)
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-green)'}}></div>
                        Backend / MongoDB 6
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-red)'}}></div>
                        Redis 7 / K8s
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-purple)'}}></div>
                        Automation Engine / Modules
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-orange)'}}></div>
                        MQTT Broker (Mosquitto 2.0)
                    </div>
                    <div className="a-legend-item">
                        <div className="a-legend-dot" style={{background: 'var(--arch-yellow)'}}></div>
                        CI/CD · Jenkins
                    </div>
                    <div className="a-legend-item"
                         style={{color: 'var(--arch-muted)', fontSize: '11px', paddingLeft: '8px'}}>
                        Spring Boot 3.3.1 · Java 21 · Spring Integration MQTT 6.2.1 · JJWT 0.11.5 · OSHI 6.6.5 ·
                        v0.0.1-SNAPSHOT (experimental)
                    </div>
                </div>

            </div>
            {/* /a-wrap */}
        </div>
    );
}
