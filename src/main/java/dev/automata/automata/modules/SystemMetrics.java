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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemMetrics {

    private final MainService mainService;
    private static String deviceId = "";
    private final SimpMessagingTemplate messagingTemplate;

    private void registerSystemMetrics() {
        var device = RegisterDevice.builder()
                .name("System")
                .sleep(false)
                .reboot(false)
                .macAddr("")
                .accessUrl("http://raspberry.local:8010")
                .type("")
                .status(Status.ONLINE)
                .updateInterval(190000L)
                .attributes(
                        List.of(
                                Attribute.builder()
                                        .key("cpu_temp")
                                        .displayName("CPU Temp")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("cpu_freq")
                                        .displayName("CPU Freq")
                                        .type("DATA|MAIN")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("ram_usage")
                                        .displayName("Ram Usage")
                                        .type("DATA|MAIN")
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
                                        .build()
                        )
                )
                .build();

        mainService.registerDevice(device);
    }


    @Scheduled(fixedRate = 10000)
    public void getInfo() {
        try {
            // Get CPU frequency
            String cpuFreq = getCpuFrequency();
            System.out.println("CPU Frequency: " + cpuFreq);

            // Get RAM usage
            String ramUsage = getRamUsage();
            System.out.println("RAM Usage: " + ramUsage);

            // Get CPU temperature
            String cpuTemp = getCpuTemperature();
            System.out.println("CPU Temperature: " + cpuTemp);

            String uptime = getUptime();
            System.out.println("System Uptime: " + uptime);

            var data =  new HashMap<String, Object>();
            data.put("cpu_temp", cpuTemp);
            data.put("ram_usage", ramUsage);
            data.put("uptime", uptime);
            data.put("cpu_freq", cpuFreq);

            var map = new HashMap<String, Object>();
            map.put("deviceId", deviceId);
            map.put("data", data);

            messagingTemplate.convertAndSend("/topic/data", map);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getUptime() throws Exception {
        String command = "uptime -p"; // Get the uptime in a human-readable format
        return executeCommand(command).replace("up", "").replace("days","d").replace("hours","h").replace("minutes","m");
    }

    private static String getCpuFrequency() throws Exception {
        String filePath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line = reader.readLine();
        reader.close();

        if (line != null) {
            int currentFreqKHz = Integer.parseInt(line.trim());
            // Convert kHz to MHz and return the value
            return String.format("%.2f", currentFreqKHz / 1000.0); // MHz
        }
        return "N/A";
    }

    // Method to get RAM usage
    private static String getRamUsage() throws Exception {
        String command = "free -h | grep Mem"; // Get memory usage
        String result = executeCommand(command);

        // Parse the result and extract total and used memory
        if (result != null && !result.isEmpty()) {
            String[] parts = result.split("\\s+");
            return "T: " + parts[1] + ", U: " + parts[2]; // Extract total and used memory
        }
        return "N/A";
    }

    // Method to get CPU temperature
    private static String getCpuTemperature() throws Exception {
        String command = "cat /sys/class/thermal/thermal_zone0/temp"; // Read CPU temperature in millidegrees Celsius
        String result = executeCommand(command);
        if (result != null) {
            // Convert the millidegrees to degrees Celsius
            int tempInMilliCelsius = Integer.parseInt(result.trim());
            return (tempInMilliCelsius / 1000.0) + " °C";
        }
        return "N/A";
    }

    // Method to execute a command and return the output
    private static String executeCommand(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString().trim();
    }

    @EventListener
    public void handleApplicationReadyEvent(ApplicationReadyEvent event) {
        System.err.println("ready...");
        registerSystemMetrics();
        var device = mainService.getDeviceByName("System");
        if (device == null) {
            registerSystemMetrics();
        }else {
            deviceId = device.getId();
        }

    }
}
