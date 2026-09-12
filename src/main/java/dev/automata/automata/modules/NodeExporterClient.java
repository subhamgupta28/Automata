package dev.automata.automata.modules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads system metrics from a locally running Prometheus Node Exporter instance.
 * Node Exporter must be running on port 9100 of the host machine.
 * <p>
 * Thermal zones on Radxa Cubie A7S (Allwinner A733):
 * zone 0 = cpul_thermal_zone  (little cores)
 * zone 1 = cpub_thermal_zone  (big cores)
 * zone 4 = gpu_thermal_zone
 * zone 6 = ddr_thermal_zone
 * zone 7 = skin_zone
 */
@Slf4j
@Component
public class NodeExporterClient {

    // In Docker: use host.docker.internal
    // On host directly: use localhost
    @Value("${node.exporter.url:http://localhost:9100/metrics}")
    private String nodeExporterUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> collectMetrics() {
        var result = new HashMap<String, Object>();
        try {
            String raw = restTemplate.getForObject(nodeExporterUrl, String.class);
            if (raw == null) {
                log.warn("NodeExporterClient: empty response from {}", nodeExporterUrl);
                return result;
            }

            // Accumulators for averaging CPU freq across big cores (0-5)
            double cpuFreqSumBig = 0;
            int cpuFreqCountBig = 0;

            // Disk totals for root filesystem
            long diskTotalBytes = 0;
            long diskAvailBytes = 0;
            long diskFreeBytes = 0;

            // Memory accumulators
            long memTotalBytes = 0;
            long memAvailableBytes = 0;

            // CPU usage accumulators
            double cpuIdleTotal = 0;
            double cpuTotalTotal = 0;

            for (String line : raw.split("\n")) {
                if (line.startsWith("#")) continue;

                // ── CPU Temperature ──────────────────────────────────────────
                // Big cores (cpub) — most relevant for thermal throttling
                if (line.startsWith("node_thermal_zone_temp{type=\"cpub_thermal_zone\"")) {
                    double temp = parseValue(line);
                    result.put("cpu_temp_big", String.format("%.1f°C", temp));
                }
                // Little cores (cpul)
                if (line.startsWith("node_thermal_zone_temp{type=\"cpul_thermal_zone\"")) {
                    double temp = parseValue(line);
                    result.put("cpu_temp_little", String.format("%.1f°C", temp));
                }
                // Use big core temp as the primary cpu_temp (matches OSHI behavior)
                if (line.startsWith("node_hwmon_temp_celsius{chip=\"thermal_thermal_zone1\"") && line.contains("sensor=\"temp0\"")) {
                    double temp = parseValue(line);
                    result.put("cpu_temp", String.format("%.1f°C", temp));
                }
                // GPU temp
                if (line.startsWith("node_thermal_zone_temp{type=\"gpu_thermal_zone\"")) {
                    result.put("gpu_temp", String.format("%.1f°C", parseValue(line)));
                }
                // DDR temp
                if (line.startsWith("node_thermal_zone_temp{type=\"ddr_thermal_zone\"")) {
                    result.put("ddr_temp", String.format("%.1f°C", parseValue(line)));
                }
                // Skin/board temp
                if (line.startsWith("node_thermal_zone_temp{type=\"skin_zone\"")) {
                    result.put("board_temp", String.format("%.1f°C", parseValue(line)));
                }
                // NVMe SMART metrics from textfile collector
                if (line.startsWith("nvme_temperature_celsius ")) {
                    result.put("nvme_temp", String.format("%.0f°C", parseValue(line)));
                }
                if (line.startsWith("nvme_available_spare_percent ")) {
                    result.put("nvme_spare", parseValue(line) + "%");
                }
                if (line.startsWith("nvme_percentage_used ")) {
                    result.put("nvme_wear", parseValue(line) + "%");
                }
                if (line.startsWith("nvme_critical_warning ")) {
                    result.put("nvme_warning", parseValue(line) == 0 ? "OK" : "WARNING");
                }
                if (line.startsWith("nvme_unsafe_shutdowns_total ")) {
                    result.put("nvme_unsafe_shutdowns", (long) parseValue(line));
                }
                if (line.startsWith("nvme_media_errors_total ")) {
                    result.put("nvme_media_errors", (long) parseValue(line));
                }
                // ── CPU Frequency ────────────────────────────────────────────
                // Big cores are cpu0-cpu5 (1.794 GHz max), little are cpu6-cpu7 (416 MHz)
                if (line.startsWith("node_cpu_scaling_frequency_hertz{")) {
                    String cpuId = extractLabel(line, "cpu");
                    if (cpuId != null) {
                        int cpuNum = Integer.parseInt(cpuId);
                        double hz = parseValue(line);
                        if (cpuNum <= 5) { // big cores
                            cpuFreqSumBig += hz;
                            cpuFreqCountBig++;
                        }
                        // Store individual core freq
                        result.put("cpu" + cpuNum + "_freq_mhz", String.format("%.0f MHz", hz / 1_000_000));
                    }
                }

                // ── Memory ───────────────────────────────────────────────────
                if (line.startsWith("node_memory_MemTotal_bytes ")) {
                    memTotalBytes = (long) parseValue(line);
                    result.put("totalMemory", formatBytes(memTotalBytes));
                }
                if (line.startsWith("node_memory_MemAvailable_bytes ")) {
                    memAvailableBytes = (long) parseValue(line);
                    result.put("availableMemory", formatBytes(memAvailableBytes));
                }
                if (line.startsWith("node_memory_MemFree_bytes ")) {
                    result.put("freeMemory", formatBytes((long) parseValue(line)));
                }
                if (line.startsWith("node_memory_Buffers_bytes ")) {
                    result.put("buffersMemory", formatBytes((long) parseValue(line)));
                }
                if (line.startsWith("node_memory_Cached_bytes ")) {
                    result.put("cachedMemory", formatBytes((long) parseValue(line)));
                }

                // ── Disk (root filesystem = NVMe nvme0n1p3) ──────────────────
                if (line.startsWith("node_filesystem_size_bytes{") && line.contains("mountpoint=\"/\"")) {
                    diskTotalBytes = (long) parseValue(line);
                    result.put("diskTotal", formatBytes(diskTotalBytes));
                }
                if (line.startsWith("node_filesystem_avail_bytes{") && line.contains("mountpoint=\"/\"")) {
                    diskAvailBytes = (long) parseValue(line);
                    result.put("diskFree", formatBytes(diskAvailBytes));
                }
                if (line.startsWith("node_filesystem_free_bytes{") && line.contains("mountpoint=\"/\"")) {
                    diskFreeBytes = (long) parseValue(line);
                }

                // ── System Uptime ─────────────────────────────────────────────
                if (line.startsWith("node_time_seconds ")) {
                    result.put("unixTime", (long) parseValue(line));
                }
                if (line.startsWith("node_boot_time_seconds ")) {
                    long bootTime = (long) parseValue(line);
                    long uptimeSec = System.currentTimeMillis() / 1000 - bootTime;
                    long hours = uptimeSec / 3600;
                    long minutes = (uptimeSec % 3600) / 60;
                    result.put("uptime", hours + "h " + minutes + "m");
                }

                // ── CPU Usage (idle time method) ──────────────────────────────
                if (line.startsWith("node_cpu_seconds_total{")) {
                    double val = parseValue(line);
                    cpuTotalTotal += val;
                    if (line.contains("mode=\"idle\"")) {
                        cpuIdleTotal += val;
                    }
                }

                // ── Network ───────────────────────────────────────────────────
                if (line.startsWith("node_network_receive_bytes_total{") && line.contains("device=\"eth0\"")) {
                    result.put("netRxBytes", formatBytes((long) parseValue(line)));
                }
                if (line.startsWith("node_network_transmit_bytes_total{") && line.contains("device=\"eth0\"")) {
                    result.put("netTxBytes", formatBytes((long) parseValue(line)));
                }
            }

            // ── Post-processing ───────────────────────────────────────────────

            // Average big core CPU frequency
            if (cpuFreqCountBig > 0) {
                double avgHz = cpuFreqSumBig / cpuFreqCountBig;
                result.put("cpuFreq", String.format("%.0f MHz", avgHz / 1_000_000));
            }

            // Memory usage percent
            if (memTotalBytes > 0 && memAvailableBytes > 0) {
                double usedPct = 100.0 * (memTotalBytes - memAvailableBytes) / memTotalBytes;
                result.put("memoryUsagePercent", String.format("%.2f%%", usedPct));
                result.put("usedMemory", formatBytes(memTotalBytes - memAvailableBytes));
            }

            // Disk usage percent (using avail, not free — avail excludes reserved blocks)
            if (diskTotalBytes > 0 && diskAvailBytes > 0) {
                long usedBytes = diskTotalBytes - diskFreeBytes;
                double usedPct = 100.0 * usedBytes / diskTotalBytes;
                result.put("diskUsagePercent", String.format("%.2f%%", usedPct));
                result.put("diskUsed", formatBytes(usedBytes));
            }

            // CPU usage percent (cumulative, best effort without delta tracking)
            if (cpuTotalTotal > 0) {
                double usagePct = 100.0 * (1.0 - cpuIdleTotal / cpuTotalTotal);
                result.put("cpuUsagePercent", String.format("%.2f%%", usagePct));
            }

            log.debug("NodeExporterClient: collected {} metrics", result.size());

        } catch (Exception e) {
            log.error("NodeExporterClient: failed to fetch metrics from {}", nodeExporterUrl, e);
        }
        return result;
    }

    /**
     * Extracts a label value from a Prometheus metric line.
     * e.g. extractLabel("node_cpu{cpu=\"3\",mode=\"idle\"} 1.0", "cpu") → "3"
     */
    private String extractLabel(String line, String labelName) {
        String search = labelName + "=\"";
        int start = line.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = line.indexOf("\"", start);
        if (end == -1) return null;
        return line.substring(start, end);
    }

    /**
     * Parses the numeric value at the end of a Prometheus metric line.
     * Format: "metric_name{labels} VALUE [timestamp]"
     */
    private double parseValue(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Double.parseDouble(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}