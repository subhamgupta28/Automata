package dev.automata.automata.modules;

import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Status;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SystemMetrics {

    private final MainService mainService;
    private static String deviceId = "";
    private final SimpMessagingTemplate messagingTemplate;
    private RestTemplate restTemplate;
    private void registerSystemMetrics() {
        var device = RegisterDevice.builder()
                .name("System")
                .sleep(false)
                .reboot(false)
                .host("raspberry.local")
                .macAddr("")
                .accessUrl("http://raspberry.local:8010")
                .type("System")
                .status(Status.ONLINE)
                .updateInterval(190000L)
                .attributes(
                        List.of(
                                Attribute.builder()
                                        .key("totalMemory")
                                        .displayName("Memory")
                                        .type("DATA|MAIN")
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
                                        .type("DATA|MAIN")
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
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("fan_speed")
                                        .displayName("Fan Speed")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("battery_percent")
                                        .displayName("SOC")
                                        .type("DATA|AUX")
                                        .units("%")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("power")
                                        .displayName("Power")
                                        .type("DATA|AUX")
                                        .units("mW")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("time_remaining")
                                        .displayName("Time Left")
                                        .type("DATA|AUX")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("uptime")
                                        .displayName("Uptime")
                                        .type("DATA|AUX")
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
                                        .key("alert")
                                        .displayName("Alert")
                                        .type("ACTION|IN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build()
                        )
                )
                .build();

        mainService.registerDevice(device);
    }

    @Scheduled(fixedRate = 360000)
    public void save() {
        var data = getData();
        if (data!=null){
            mainService.saveData(deviceId, data);
        }
        var res = mainService.getShutdownStatus();
        if (res.equals("Y")){
            shutdownSystem();
        }
    }
    private void shutdownSystem(){
        try {


        }catch (Exception e){
            System.err.println(e);
        }

    }
    private final SystemInfo systemInfo = new SystemInfo();


    public long getTotalMemory() {
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        return memory.getTotal();
    }

    public long getAvailableMemory() {
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        return memory.getAvailable();
    }

    public String getMemoryUsagePercent() {
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        double percent = 100.0 * (memory.getTotal() - memory.getAvailable()) / memory.getTotal();
        return formatPercent(percent);
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public String formatPercent(double value) {
        return String.format("%.2f%%", value);
    }
    public String getUptimeHuman() {
        long uptimeSec = systemInfo.getOperatingSystem().getSystemUptime();
        long hours = uptimeSec / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
    public String getCpuUsagePercent() {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpu = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        return formatPercent(cpu);
    }
    private HashMap<String, Object> getData() {
        try {

            var cpuFreq = getCpuUsagePercent();

            var data = new HashMap<String, Object>();
            data.put("totalMemory", formatBytes(getTotalMemory()));
            data.put("memoryUsagePercent", getMemoryUsagePercent());
            data.put("uptime", getUptimeHuman());
            data.put("availableMemory", formatBytes(getAvailableMemory()));
            data.put("cpuFreq", cpuFreq);
            data.put("device_id", deviceId);
            data.put("host", systemInfo.getOperatingSystem().getNetworkParams().getHostName());
            data.put("cpu_temp", systemInfo.getHardware().getSensors().getCpuTemperature());

            var fans = systemInfo.getHardware().getSensors().getFanSpeeds();
            if (fans != null && fans.length > 0) {
                data.put("fan_speed", fans[0]);
            }

            System.out.println(Arrays.toString(systemInfo.getHardware().getSensors().getFanSpeeds()));
            var power = systemInfo.getHardware().getPowerSources();
            if (power!=null && !power.isEmpty()) {
                data.put("battery_percent", power.getFirst().getRemainingCapacityPercent());
                data.put("power", power.getFirst().getPowerUsageRate());
                data.put("time_remaining", power.getFirst().getTimeRemainingEstimated());
            }

            System.err.println(systemInfo.getHardware().getPowerSources());
            //[Name: System Battery, Device Name: Primary,
            // RemainingCapacityPercent: 100.0%, Time Remaining: Unknown, Time Remaining Instant: Unknown,
            // Power Usage Rate: 0.0mW, Voltage: 13.16V, Amperage: 0.0mA,
            // Power OnLine: true, Charging: false, Discharging: false,
            // Capacity Units: MWH, Current Capacity: 82194, Max Capacity: 82194, Design Capacity: 83028,
            // Cycle Count: 4, Chemistry: LION, Manufacture Date: unknown, Manufacturer: HP,
            // SerialNumber: SerialNumber, Temperature: unknown]
            System.err.println(data);
            return data;
        } catch (Exception e) {
//            System.err.println("System Metrics Exception: "+e);
        }
        return null;
    }

//    @Scheduled(fixedRate = 1000 * 30)// every 5 mins
//    private void healthCheck() {
//        try{
//            restTemplate = new RestTemplate();
//            var res = restTemplate.getForObject("http://raspberry.local:8010/api/v1/main/healthCheck", String.class);
////            System.err.println("Health Check: "+res);
//        }catch (Exception e){
//            System.err.println(e);
//        }
//    }


    @Scheduled(fixedRate = 10000)
    public void getInfo() {
        var data = getData();
        if (data != null){
            var map = new HashMap<String, Object>();
            map.put("deviceId", deviceId);
            map.put("data", data);
            messagingTemplate.convertAndSend("/topic/data", map);
        }

    }


    @EventListener
    public void handleApplicationReadyEvent(ApplicationReadyEvent event) {
        System.err.println("ready...");
        registerSystemMetrics();
        var device = mainService.getDeviceByName("System");
        if (device == null) {
            registerSystemMetrics();
        } else {
            deviceId = device.getId();
        }

    }
}
