import React, {useEffect, useRef, useState} from "react";
import axios from "axios";
import {Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,} from "recharts";

const TrafficChart = () => {
    const [data, setData] = useState([]);
    const [totalRequests, setTotalRequests] = useState(0);
    const abortRef = useRef(null);

    const fetchMetrics = async () => {
        // Cancel any in-flight request before starting a new one
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        try {
            const base = "http://localhost:8010/actuator/metrics/http.server.requests";
            const response = await axios.get(base, {signal: controller.signal});
            const uriTag = response.data.availableTags?.find(tag => tag.tag === "uri");

            if (!uriTag || !uriTag.values.length) {
                console.warn("No URI data.");
                setData([]);
                return;
            }

            const metrics = await Promise.all(
                uriTag.values.map(async (uri) => {
                    const encodedUri = encodeURIComponent(uri);
                    const res = await axios.get(`${base}?tag=uri:${encodedUri}`, {signal: controller.signal});
                    const count = res.data.measurements.find(m => m.statistic === "COUNT")?.value || 0;
                    const latency = res.data.measurements.find(m => m.statistic === "MAX")?.value || 0;
                    return {uri, count, latency};
                })
            );

            setTotalRequests(metrics.reduce((sum, m) => sum + m.count, 0));
            setData(metrics);
        } catch (err) {
            if (!axios.isCancel(err) && err.name !== 'CanceledError') {
                console.error("Failed to load metrics", err);
            }
        }
    };

    useEffect(() => {
        fetchMetrics();
        const interval = setInterval(fetchMetrics, 10000);
        return () => {
            clearInterval(interval);
            abortRef.current?.abort();
        };
    }, []);

    return (
        <div className="p-4">
            <h2 className="text-2xl font-semibold mb-2">HTTP Traffic Dashboard</h2>
            <p className="mb-4 text-gray-600">Total Requests: <strong>{totalRequests}</strong></p>
            <ResponsiveContainer width="100%" height={300}>
                <ComposedChart data={data}>
                    <CartesianGrid strokeDasharray="3 3"/>
                    <XAxis dataKey="uri" tick={{fontSize: 11}} interval={0} angle={-30} textAnchor="end" height={60}/>
                    <YAxis yAxisId="count" orientation="left"
                           label={{value: 'Request Count', angle: -90, position: 'insideLeft'}}/>
                    <YAxis yAxisId="latency" orientation="right"
                           label={{value: 'Latency (s)', angle: 90, position: 'insideRight'}}/>
                    <Tooltip/>
                    <Legend/>
                    <Bar yAxisId="count" dataKey="count" name="Request Count" fill="rgba(75,192,192,0.6)"/>
                    <Line yAxisId="latency" type="monotone" dataKey="latency" name="Latency (s)"
                          stroke="rgba(255,99,132,1)" dot={false}/>
                </ComposedChart>
            </ResponsiveContainer>
        </div>
    );
};

export default TrafficChart;
