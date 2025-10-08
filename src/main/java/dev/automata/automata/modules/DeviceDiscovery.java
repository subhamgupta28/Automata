package dev.automata.automata.modules;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;

@Service
public class DeviceDiscovery {
    private JmDNS jmdns;

    @PostConstruct
    public void startDiscovery() {
        try {
            jmdns = JmDNS.create(InetAddress.getLocalHost());
            jmdns.addServiceListener("_esp32._tcp.local.", new Esp32Listener());
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

    private static class Esp32Listener implements ServiceListener {
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
            System.err.println(info.getPropertyString("deviceId"));
            System.err.println(info.getPropertyString("ip"));
        }
    }
}
