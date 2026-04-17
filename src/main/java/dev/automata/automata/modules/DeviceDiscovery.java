package dev.automata.automata.modules;

import dev.automata.automata.service.MainService;
import dev.automata.automata.service.NotificationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

@RequiredArgsConstructor
@Service
public class DeviceDiscovery {

    private JmDNS jmdns;
    private final MainService mainService;
    private final NotificationService notificationService;

    @PostConstruct
    public void startDiscovery() {
        try {
            jmdns = JmDNS.create(InetAddress.getLocalHost());

            // ESP32 devices
            jmdns.addServiceListener("_esp32._tcp.local.",
                    new Esp32Listener(mainService, notificationService));

            // WLED devices
            jmdns.addServiceListener("_wled._tcp.local.",
                    new WledListener(mainService, notificationService));

            System.out.println("✅ Device discovery started (ESP32 + WLED)");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void stopDiscovery() {
        try {
            if (jmdns != null) {
                jmdns.close();
                System.out.println("🛑 Discovery stopped.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================
    // ESP32 LISTENER
    // ========================
    private record Esp32Listener(
            MainService mainService,
            NotificationService notificationService
    ) implements ServiceListener {

        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("📡 ESP32 added: " + event.getName());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("❌ ESP32 removed: " + event.getName());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();

            String ip = info.getHostAddresses()[0];
            int port = info.getPort();
            String deviceId = info.getPropertyString("deviceId");

            System.out.println("🔍 ESP32 resolved: " + deviceId);
            System.out.println("IP: " + ip + ":" + port);

            if (deviceId == null || deviceId.isEmpty()) {
                deviceId = ip; // fallback
            }

            var existing = mainService.getDevice(deviceId);
            if (existing == null) {
                System.out.println("✨ New ESP32 discovered");

//                mainService.registerDevice(
//                        deviceId,
//                        ip,
//                        "ESP32"
//                );

                notificationService.sendNotification("New ESP32 device found", "high");
            }
        }
    }

    // ========================
    // WLED LISTENER
    // ========================
    private record WledListener(
            MainService mainService,
            NotificationService notificationService
    ) implements ServiceListener {

        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("💡 WLED added: " + event.getName());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("❌ WLED removed: " + event.getName());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();

            String ip = info.getHostAddresses()[0];
            int port = info.getPort();
            String name = event.getName();

            System.out.println("💡 WLED resolved: " + name);
            System.out.println("IP: " + ip + ":" + port);

            try {
                // Fetch metadata from WLED
                String json = fetchWledJson(ip);

                String deviceName = extractValue(json, "\"name\":\"", "\"");
                String deviceId = extractValue(json, "\"mac\":\"", "\"");

                if (deviceId == null || deviceId.isEmpty()) {
                    deviceId = ip; // fallback
                }

                var existing = mainService.getDevice(deviceId);
                if (existing == null) {
                    System.out.println("✨ New WLED discovered: " + deviceName);

//                    mainService.registerDevice(
//                            deviceId,
//                            ip,
//                            "WLED"
//                    );

                    notificationService.sendNotification("New WLED light found", "high");
                }

            } catch (Exception e) {
                System.err.println("⚠️ Failed to fetch WLED metadata, using fallback");

                String deviceId = ip;

                var existing = mainService.getDevice(deviceId);
                if (existing == null) {
//                    mainService.registerDevice(deviceId, ip, "WLED");
                    notificationService.sendNotification("New WLED light found", "high");
                }
            }
        }

        // ========================
        // Fetch /json from WLED
        // ========================
        private String fetchWledJson(String ip) throws IOException {
            URL url = new URL("http://" + ip + "/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            reader.close();
            return result.toString();
        }

        // ========================
        // VERY simple JSON parser
        // (replace with Jackson if you want clean code)
        // ========================
        private String extractValue(String json, String key, String endChar) {
            try {
                int start = json.indexOf(key);
                if (start == -1) return null;

                start += key.length();
                int end = json.indexOf(endChar, start);

                return json.substring(start, end);
            } catch (Exception e) {
                return null;
            }
        }
    }
}