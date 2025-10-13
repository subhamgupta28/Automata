package dev.automata.automata.service;


import dev.automata.automata.model.Data;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Parameter;
import dev.automata.automata.model.Status;
import dev.automata.automata.repository.DataHistRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleTasks {

    private final MainService mainService;
    private final DataRepository dataRepository;
    private final DeviceRepository deviceRepository;
    private final DataHistRepository dataHistRepository;
    private final ParameterRepository parameterRepository;
    private final SimpMessagingTemplate messagingTemplate;

    //1 hour interval
//    @Scheduled(fixedRate = 10000)
    public void refreshDevices() {
        var startTime = new Date();
        System.err.println("Starting consolidation...");
        System.err.println("start time: " + startTime);
        var devices = mainService.getAllDevice();


        for (var device : devices) {
            var lastParameter = parameterRepository.findByDeviceId(device.getId());
            LocalDateTime now = LocalDateTime.now();
            // Start of the current hour
            LocalDateTime startOfHour = now.truncatedTo(ChronoUnit.HOURS);
            var startTimestamp = startOfHour.atZone(ZoneId.systemDefault()).toInstant();
            // End of the current hour
            LocalDateTime endOfHour = startOfHour.plusMinutes(30).minusSeconds(1);
            var endTimestamp = endOfHour.atZone(ZoneId.systemDefault()).toInstant();

            System.out.println("Start of hour timestamp: " + startTimestamp);
            System.out.println("End of hour timestamp: " + endTimestamp);
            if (lastParameter != null) {
                lastParameter.setTransactionFrom(startTimestamp.getEpochSecond());
                lastParameter.setTransactionTo(endTimestamp.getEpochSecond());
//                parameterRepository.save(lastParameter);
//                System.err.println("Last parameter updated: " + lastParameter);

                var data = dataRepository.findByDeviceIdAndUpdateDateBetween(device.getId(), Date.from(startTimestamp), Date.from(endTimestamp));
                if (data != null) {
                    System.err.println(device.getName());
                    System.err.println(data.size());
                }

            }else {
                System.err.println("Parameter not found: " + device.getId());
                var para = Parameter.builder()
                        .deviceId(device.getId())
                        .transactionFrom(startTimestamp.getEpochSecond())
                        .transactionTo(endTimestamp.getEpochSecond()).build();
//                parameterRepository.save(para);
                System.err.println("New parameter added: ");

            }



        }


    }
    @Scheduled(fixedRate = 180000) // runs every 60 seconds
    public void checkAndUpdateStatus() {
        var devices = deviceRepository.findAll();

        Instant now = Instant.now();
        System.err.println("Starting consolidation...");
        for (var device : devices) {
            var entity = dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(device.getId()).orElse(new Data());
            if (entity.getTimestamp() != null) {
//                System.err.println(entity);
                Duration diff = Duration.between(entity.getUpdateDate().toInstant(), now);
                var newStatus = diff.toMinutes() <= 10 ? Status.ONLINE : Status.OFFLINE;
//                System.err.println(diff.toMinutes());
                mainService.setStatus(device.getId(), newStatus);
//                System.err.println("ID: " + entity.getId() + ", Status: " + newStatus);
            }
        }
        System.err.println("Consolidation done.");
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
