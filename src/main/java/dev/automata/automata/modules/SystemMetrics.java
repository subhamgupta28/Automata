package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Status;
import dev.automata.automata.service.HomeRoutingService;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemMetrics {

    private final MainService mainService;
    private final NodeExporterClient nodeExporterClient;
    private final MessageChannel mqttOutboundChannel;
    private final HomeRoutingService homeRoutingService;
    private final ObjectMapper objectMapper;

    private static String deviceId = "";

    @Value("${application.env}")
    private String env;

    private void registerSystemMetrics() {
        InetAddress localhost = null;
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        String hostName = "";
        String hostAddr = "";
        if (localhost != null) {
            hostName = localhost.getHostName();
            hostAddr = localhost.getHostAddress();
        }
        log.info("HostName: {}, HostAddr: {}", hostName, hostAddr);

        var device = RegisterDevice.builder()
                .name(hostName)
                .sleep(false)
                .reboot(false)
                .host(hostName)
                .macAddr(env)
                .accessUrl("http://" + hostAddr + ":8010")
                .type("System")
                .status(Status.ONLINE)
                .updateInterval(190000L)
                .attributes(
                        List.of(
                                Attribute.builder()
                                        .key("totalMemory")
                                        .displayName("Memory")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("state")
                                        .displayName("State")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("cpuFreq")
                                        .displayName("CPU Freq")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("memoryUsagePercent")
                                        .displayName("Used Memory")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("availableMemory")
                                        .displayName("Free Memory")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("host")
                                        .displayName("Host")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("cpu_temp")
                                        .displayName("Cpu Temp")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("diskUsagePercent")
                                        .displayName("Disk Usage")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("diskTotal")
                                        .displayName("Disk Total")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("diskFree")
                                        .displayName("Disk Free")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("gpu_temp")
                                        .displayName("GPU Temp")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("uptime")
                                        .displayName("Uptime")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("app_notify")
                                        .displayName("App Notification")
                                        .type("ACTION|IN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("alert").displayName("Alert").type("ACTION|IN")
                                        .units("").extras(new HashMap<>()).visible(true).build(),
                                Attribute.builder()
                                        .key("nvme_temp").displayName("NVMe Temp")
                                        .type("DATA|AUX").units("").extras(new HashMap<>()).visible(true).build(),
                                Attribute.builder()
                                        .key("nvme_wear").displayName("NVMe Wear")
                                        .type("DATA|AUX").units("").extras(new HashMap<>()).visible(true).build(),
                                Attribute.builder()
                                        .key("nvme_warning").displayName("NVMe Status")
                                        .type("DATA|MAIN").units("").extras(new HashMap<>()).visible(true).build(),
                                Attribute.builder()
                                        .key("nvme_unsafe_shutdowns").displayName("Unsafe Shutdowns")
                                        .type("DATA|AUX").units("").extras(new HashMap<>()).visible(true).build(),
                                Attribute.builder()
                                        .key("nvme_media_errors").displayName("Media Errors")
                                        .type("DATA|AUX").units("").extras(new HashMap<>()).visible(true).build()
                        )
                )
                .build();

        mainService.registerDevice(device);
    }

    public static Map<String, Object> getNgrokDetails() {
        try {
            var map = new HashMap<String, Object>();
            String response = new RestTemplate().getForObject("http://host.docker.internal:4040/api/tunnels", String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            log.info(response);
            for (JsonNode tunnel : root.get("tunnels")) {
                String publicUrl = tunnel.get("public_url").asText();
                log.info("Ngrok Public URL: {}", publicUrl);
                URI uri = new URI(publicUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                map.put("MQTT_HOST", host);
                map.put("MQTT_PORT", port);
            }
            return map;
        } catch (Exception e) {
            log.error("SystemMetrics: getNgrokDetails", e);
        }
        return Map.of("msg", "error");
    }

    @Scheduled(fixedRate = 360000)
    public void save() {
        var data = getData();
        if (data != null) {
            mainService.saveData(deviceId, data);
        }
        var res = mainService.getShutdownStatus();
        if (res.equals("Y")) {
            shutdownSystem();
        }
    }

    private void shutdownSystem() {
        try {
            // implement if needed
        } catch (Exception e) {
            log.error("SystemMetrics: shutdownSystem", e);
        }
    }

    private HashMap<String, Object> getData() {
        try {
            var data = new HashMap<String, Object>(nodeExporterClient.collectMetrics());

            data.put("device_id", deviceId);
            data.put("last_seen", new Date());
            data.put("host", InetAddress.getLocalHost().getHostName());

            return data;
        } catch (Exception e) {
            log.error("SystemMetrics: getData failed", e);
        }
        return null;
    }

    @Scheduled(fixedRate = 4000)
    public void getInfo() {
//        if (!env.equals("dev")) {
        var data = getData();
        if (data != null) {
            var map = new HashMap<String, Object>();
            map.put("deviceId", deviceId);
            data.put("device_id", deviceId);
            map.put("data", data);
            homeRoutingService.routeToHome(deviceId, "data", map);
            try {
                String json = objectMapper.writeValueAsString(data);
                mqttOutboundChannel.send(MessageBuilder.withPayload(json)
                        .setHeader("mqtt_topic", "sendLiveData").build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
//        }
    }

    @EventListener
    public void handleApplicationReadyEvent(ApplicationReadyEvent event) {
        log.info("ready...");
        registerSystemMetrics();
        var device = mainService.getDeviceByEnv(env);
        if (device == null) {
            registerSystemMetrics();
        } else {
            deviceId = device.getId();
        }
    }
}