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
import java.io.IOException;
import java.net.InetAddress;

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
            jmdns.addServiceListener("_esp32._tcp.local.", new Esp32Listener(mainService, notificationService));
            System.out.println("✅ ESP32 discovery started...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void stopDiscovery() {
        try {
            if (jmdns != null) {
                jmdns.close();
                System.out.println("🛑 ESP32 discovery stopped.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private record Esp32Listener(
            MainService mainService,
            NotificationService notificationService
    ) implements ServiceListener {
            @Override
            public void serviceAdded(ServiceEvent event) {
                System.out.println("📡 Service added: " + event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                System.out.println("❌ Service removed: " + event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                System.out.println("🔍 Service resolved: " + info);
                System.out.println("IP: " + info.getHostAddresses()[0] + ":" + info.getPort());
                var deviceId = info.getPropertyString("deviceId");
                System.err.println(deviceId);
                var checkForDevice = mainService.getDevice(deviceId);
                if (checkForDevice == null) {
                    System.err.println("New device discovered");
                    notificationService.sendNotification("New device found", "high");
                }
                System.err.println(info.getPropertyString("ip"));
            }
        }
}
