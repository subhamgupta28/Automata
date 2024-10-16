package dev.automata.automata.service;


import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ScheduleTasks {

    private final MainService mainService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 20000)
    public void refreshDevices() {
        System.err.println("Refreshing devices...");
//        messagingTemplate.convertAndSend("/topic/update", "{}");
    }


//    @Scheduled(fixedRate = 30000)
    public void getSystemInfo() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();

        // Get processor information
        CentralProcessor processor = hal.getProcessor();
        System.err.println("Current Frequency: " + Arrays.toString(processor.getCurrentFreq()));
        System.err.println("Processor: " + processor.getProcessorIdentifier().getName());
        System.err.println("Logical Processors: " + processor.getLogicalProcessorCount());
        System.err.println("Physical Processors: " + processor.getPhysicalProcessorCount());

        // Get memory information
        GlobalMemory memory = hal.getMemory();
        System.err.println("Total Memory: " + memory.getTotal() / (1024 * 1024) + " MB");
        System.err.println("Available Memory: " + memory.getAvailable() / (1024 * 1024) + " MB");
    }

}
