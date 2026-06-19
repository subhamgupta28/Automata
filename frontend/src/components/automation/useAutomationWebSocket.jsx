/**
 * useAutomationWebSocket.js
 *
 * Custom React hook that owns the full STOMP/SockJS lifecycle for the
 * Automation Live Inspector.  Auth is handled by injecting a Bearer token
 * into the STOMP CONNECT frame headers so Spring's ChannelInterceptor can
 * validate it on the server side — no cookie / session required.
 *
 * Usage
 * ─────
 *   const { connStatus, subscribe, unsubscribe } = useAutomationWebSocket({
 *     getToken,          // () => string | Promise<string>  — called on every connect/reconnect
 *     onBroadcast,       // (event) => void  — receives /topic/automation.all events
 *     onReconnect,       // (client) => void — called after CONNECT so callers can re-subscribe
 *   });
 *
 *   // Subscribe to per-automation topics
 *   subscribe(`/topic/automation.${id}`,        handleEvalEvent,   "eval");
 *   subscribe(`/topic/automation.${id}.actions`, handleActionEvent, "action");
 *
 *   // Tear down a topic
 *   unsubscribe("eval");
 *   unsubscribe("action");
 */

import {useCallback, useEffect, useRef, useState} from "react";
import {Client} from "@stomp/stompjs";
import SockJS from "sockjs-client";

// ─── WS endpoint ──────────────────────────────────────────────────────────────
const WS_URL =
    typeof __API_MODE__ !== "undefined" && __API_MODE__ === "serve"
        ? "http://localhost:8010/ws"
        : `${window.location.protocol}//${window.location.host}/ws`;

const RECONNECT_DELAY_MS = 5_000;

/**
 * Resolve the current token — supports both sync and async getToken fns.
 * Returns an empty string if the call throws so the connection still attempts
 * and the server can reject it cleanly.
 */
async function resolveToken(getToken) {
    try {
        const token = (await Promise.resolve(getToken())) ?? "";
        if (!token) {
            console.warn("[ws] No auth token found — WebSocket connection aborted.");
            return null;
        }
        return token;
    } catch (err) {
        console.warn("[ws] getToken() threw — WebSocket connection aborted.", err);
        return null;
    }
}

// ─── Hook ─────────────────────────────────────────────────────────────────────
/**
 * @param {object} opts
 * @param {() => string | Promise<string>} opts.getToken
 *   Called on every (re)connect to fetch a fresh JWT.
 *   Receives the raw token string — the hook adds the "Bearer " prefix.
 * @param {(event: object) => void} [opts.onBroadcast]
 *   Handler for /topic/automation.all (the global live-summary feed).
 * @param {(client: Client) => void} [opts.onReconnect]
 *   Called right after STOMP CONNECT so the parent can restore subscriptions
 *   that were wiped by a disconnect.
 */
export function useAutomationWebSocket({getToken, onBroadcast, onReconnect} = {}) {
    const [connStatus, setConnStatus] = useState("disconnected");

    // Stable refs so callbacks never need to be listed as useEffect deps
    const stompRef = useRef(null);
    const subsRef = useRef({});          // { [key: string]: StompSubscription }
    const onBroadcastRef = useRef(onBroadcast);
    const onReconnectRef = useRef(onReconnect);
    const getTokenRef = useRef(getToken);

    // Keep refs in sync with latest props each render
    useEffect(() => {
        onBroadcastRef.current = onBroadcast;
    }, [onBroadcast]);
    useEffect(() => {
        onReconnectRef.current = onReconnect;
    }, [onReconnect]);
    useEffect(() => {
        getTokenRef.current = getToken;
    }, [getToken]);

    // ── Lifecycle ────────────────────────────────────────────────────────────
    useEffect(() => {
        let isMounted = true;

        const client = new Client({
            reconnectDelay: RECONNECT_DELAY_MS,

            // webSocketFactory is called on every (re)connect attempt
            webSocketFactory: () => new SockJS(WS_URL, null, {withCredentials: false}),

            // connectHeaders are evaluated *once per connect attempt* by @stomp/stompjs.
            // We use beforeConnect (async) instead so we can await a fresh token.
            beforeConnect: async () => {
                const token = await resolveToken(getTokenRef.current ?? (() => ""));
                if (token === null) {
                    // No token — deactivate immediately rather than connecting unauthenticated.
                    // The client will not send a CONNECT frame.
                    client.deactivate();
                    return;
                }
                // Mutate connectHeaders directly — the library reads them right after
                // beforeConnect resolves, before sending the CONNECT frame.
                client.connectHeaders = {Authorization: `Bearer ${token}`};
            },

            onConnect: () => {
                if (!isMounted) return;
                setConnStatus("connected");

                // Broadcast feed ─ one global subscription, recreated after reconnect
                client.subscribe("/topic/automation.all", (msg) => {
                    try {
                        const event = JSON.parse(msg.body);
                        if (event?.automationId) onBroadcastRef.current?.(event);
                    } catch (e) {
                        console.warn("[ws] Failed to parse automation.all event", e);
                    }
                });

                // Notify parent so it can restore per-automation subscriptions
                onReconnectRef.current?.(client);
            },

            onDisconnect: () => {
                if (isMounted) setConnStatus("disconnected");
                // Clear sub refs so subscribe() re-creates them after reconnect
                subsRef.current = {};
            },

            onStompError: (frame) => {
                console.error("[ws] STOMP error", frame);
                if (isMounted) setConnStatus("disconnected");
            },

            onWebSocketError: (event) => {
                console.error("[ws] WebSocket error", event);
                if (isMounted) setConnStatus("disconnected");
            },
        });

        client.activate();
        stompRef.current = client;

        return () => {
            isMounted = false;
            client.deactivate();
            stompRef.current = null;
            subsRef.current = {};
        };
    }, []); // intentionally empty — hook owns the client for its full lifetime

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Subscribe to a STOMP topic.
     *
     * @param {string}   destination  e.g. "/topic/automation.abc123"
     * @param {function} handler      (parsedBody: object) => void
     * @param {string}   key          Stable string key — used to deduplicate/replace subs.
     *                                E.g. "eval" or "action".
     */
    const subscribe = useCallback((destination, handler, key) => {
        const client = stompRef.current;
        if (!client?.connected) return;

        // Drop existing sub under the same key
        try {
            subsRef.current[key]?.unsubscribe();
        } catch (_) {
        }

        subsRef.current[key] = client.subscribe(destination, (msg) => {
            try {
                handler(JSON.parse(msg.body));
            } catch (e) {
                console.warn(`[ws] Failed to parse message on ${destination}`, e);
            }
        });
    }, []);

    /**
     * Unsubscribe by key.
     *
     * @param {string} key  Same key used in subscribe()
     */
    const unsubscribe = useCallback((key) => {
        try {
            subsRef.current[key]?.unsubscribe();
        } catch (_) {
        }
        delete subsRef.current[key];
    }, []);

    /** Raw STOMP client — only needed for advanced / escape-hatch use cases. */
    const getClient = useCallback(() => stompRef.current, []);

    return {connStatus, subscribe, unsubscribe, getClient};
}