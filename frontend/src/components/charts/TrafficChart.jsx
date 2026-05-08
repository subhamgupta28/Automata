import React, { useEffect, useState } from "react";
import axios from "axios";
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, LineElement, Title, Tooltip, Legend, PointElement } from "chart.js";
import { Chart } from "react-chartjs-2";

ChartJS.register(CategoryScale, LinearScale, BarElement, LineElement, PointElement, Title, Tooltip, Legend);

const TrafficChart = () => {
    const [data, setData] = useState([]);
    const [totalRequests, setTotalRequests] = useState(0);
    const [chartData, setChartData] = useState({ labels: [], datasets: [] });

    const fetchMetrics = async () => {
        try {
            const base = "http://localhost:8010/actuator/metrics/http.server.requests";
            const response = await axios.get(base);
            const uriTag = response.data.availableTags?.find(tag => tag.tag === "uri");

            if (!uriTag || !uriTag.values.length) {
                console.warn("No URI data.");
                setChartData({ labels: [], datasets: [] });
                return;
            }

            const metrics = await Promise.all(
                uriTag.values.map(async (uri) => {
                    const encodedUri = encodeURIComponent(uri);
                    const res = await axios.get(`${base}?tag=uri:${encodedUri}`);
                    const count = res.data.measurements.find(m => m.statistic === "COUNT")?.value || 0;
                    const latency = res.data.measurements.find(m => m.statistic === "MAX")?.value || 0;
                    return { uri, count, latency };
                })
            );

            const total = metrics.reduce((sum, m) => sum + m.count, 0);
            setTotalRequests(total);

            setChartData({
                labels: metrics.map(m => m.uri),
                datasets: [
                    {
                        type: "bar",
                        label: "Request Count",
                        data: metrics.map(m => m.count),
                        backgroundColor: "rgba(75,192,192,0.6)"
                    },
                    {
                        type: "line",
                        label: "Latency (s)",
                        data: metrics.map(m => m.latency),
                        borderColor: "rgba(255,99,132,1)",
                        backgroundColor: "rgba(255,99,132,0.2)",
                        yAxisID: 'latency'
                    }
                ]
            });
        } catch (err) {
            console.error("Failed to load metrics", err);
        }
    };

    useEffect(() => {
        fetchMetrics();
        const interval = setInterval(fetchMetrics, 10000); // refresh every 10s
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="p-4">
            <h2 className="text-2xl font-semibold mb-2">HTTP Traffic Dashboard</h2>
            <p className="mb-4 text-gray-600">Total Requests: <strong>{totalRequests}</strong></p>
            <Chart
                type="bar"
                data={chartData}
                options={{
                    responsive: true,
                    interaction: {
                        mode: 'index',
                        intersect: false
                    },
                    stacked: false,
                    plugins: {
                        title: {
                            display: true,
                            text: "Request Count and Latency per URI"
                        },
                        legend: {
                            position: "top"
                        }
                    },
                    scales: {
                        y: {
                            type: 'linear',
                            display: true,
                            position: 'left',
                            title: { display: true, text: 'Request Count' }
                        },
                        latency: {
                            type: 'linear',
                            display: true,
                            position: 'right',
                            title: { display: true, text: 'Latency (ms)' },
                            grid: { drawOnChartArea: false }
                        }
                    }
                }}
            />
        </div>
    );
};

export default TrafficChart;
