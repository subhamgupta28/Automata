package dev.automata.automata.service;

import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.RootDto;
import dev.automata.automata.dto.ValueDto;
import dev.automata.automata.model.*;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MainService {

    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceRepository deviceRepository;
    private final DashboardRepository deviceDashboardRepository;
    private final DeviceChartsRepository dashboardChartsRepository;
    private final NotificationService notificationService;

    /*
     * Device: name = battery
     *         type = sensor
     *         updateInterval = 1000ms
     *         accessUrl = http://192.168.29.127
     *         id = 123
     * Attributes:
     *         deviceId = 123
     *         key = current
     *         value = 2
     *         id = 123_1
     *         uuid = random
     *         timestamp = 1234567890
     *
     *         deviceId = 123
     *         key = power
     *         value = 2
     *         id = 123_2
     *         uuid = random
     *         timestamp = 1234567890
     * */


    public Device registerDevice(RegisterDevice registerDevice) {
        var timestamp = System.currentTimeMillis();

        System.err.println(registerDevice);

        var device = Device.builder()
                .name(registerDevice.getName())
                .updateInterval(registerDevice.getUpdateInterval())
                .sleep(registerDevice.getSleep())
                .type(registerDevice.getType())
                .macAddr(registerDevice.getMacAddr())
                .accessUrl(registerDevice.getAccessUrl())
                .reboot(registerDevice.getReboot())
                .attributes(registerDevice.getAttributes())
                .status(registerDevice.getStatus()).build();


//        var isAlreadyRegistered = deviceRepository.findById(registerDevice.getDeviceId()).orElse(null);
        var attributes = new ArrayList<Attribute>();
        var isMacAddrPresent = deviceRepository.findByMacAddr(registerDevice.getMacAddr());

        if (!isMacAddrPresent.isEmpty()) {
            var dev = isMacAddrPresent.getFirst();
            device.setId(dev.getId());
            var attr = attributeRepository.findByDeviceId(dev.getId());
            if (!attr.isEmpty()) {
                System.err.print("Attributes: ");
                System.err.println(attr);
                attributeRepository.deleteByDeviceId(dev.getId());
            }

            registerDevice.getAttributes().forEach(a -> {
                a.setDeviceId(device.getId());
                attributes.add(a);
            });
            var atr = attributeRepository.saveAll(attributes);
            device.setAttributes(atr);
            dev = deviceRepository.save(device);
            System.err.println("Already registered device: " + device);
            return dev;
        }


        var savedDevice = deviceRepository.save(device);

        registerDevice.getAttributes().forEach(a -> {
            a.setDeviceId(savedDevice.getId());
            attributes.add(a);
        });
        attributeRepository.saveAll(attributes);
        device.setAttributes(attributes);

        deviceRepository.save(device);

        return savedDevice;
    }

    public void saveAttributes(List<Attribute> attributes) {
        attributeRepository.saveAll(attributes);
    }


    public String saveData(String deviceId, Map<String, Object> payload) {
//        TimeZone.setDefault(TimeZone.getTimeZone("GMT+5:30"));

        ZonedDateTime dateTime = ZonedDateTime.now();

        Date date = new Date();
        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.systemDefault());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        String formattedDate = sdf.format(date);
        var data = Data.builder()
                .deviceId(deviceId)
                .dateTime(formattedDate)
                .data(payload)
                .updateDate(date)
                .timestamp(dateTime.toInstant().getEpochSecond())
                .build();
        dataRepository.save(data);
        return "Saved";
    }

    public Device getDevice(String deviceId) {
        return deviceRepository.findById(deviceId).orElseThrow();
    }

    public DataDto getData(String deviceId) {

        var device = getDevice(deviceId);
        var attributes = attributeRepository.findAllByDeviceId(deviceId);
        var attributeMap = attributes.stream()
                .collect(Collectors.toMap(Attribute::getKey, Function.identity()));
        System.err.println(attributeMap);
        System.err.println(device);
        System.err.println(attributes.size());
        var rootDto = new ArrayList<RootDto>();
        dataRepository.findAllByDeviceId(deviceId).forEach(d -> {
            var values = new ArrayList<ValueDto>();
            d.getData().forEach((k, v) -> {
                System.out.println(k);
                var attribute = attributeMap.get(k);
                if (attribute != null) {
                    var valueDto = ValueDto.builder()
                            .key(k)
                            .value(String.valueOf(v))
                            .displayName(attribute.getDisplayName())
                            .units(attribute.getUnits())
                            .build();
                    values.add(valueDto);
                }

            });
            Instant instant = Instant.ofEpochMilli(d.getTimestamp());

            // Define a formatter for the date
            DateTimeFormatter formatter = DateTimeFormatter
                    .ofPattern("yyyy-MMM-dd") // Specify your desired format
                    .withZone(ZoneId.systemDefault());
            rootDto.add(RootDto.builder()
                    .values(values)
                    .date(formatter.format(instant))
                    .timestamp(d.getTimestamp())
                    .build());
        });
//        System.err.println(rootDto);

        return DataDto.builder()
                .pageSize(rootDto.size())
                .values(rootDto)
                .deviceId(deviceId)
                .pageNo(1)
                .build();

    }

//    @KafkaListener(topics = "${kafka.topic}", groupId = "group_id")
//    public void consume(String message) {
//        System.out.println("Message consumed from Kafka: " + message);
//    }

    @Async
    public void triggerBackgroundTask() {
        // Your logic for the background task
        System.out.println("Background task started...");
        try {
            // Simulating some time-consuming task
            Thread.sleep(15000); // 5 seconds delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Background task completed!");
    }

    public List<Device> getAllDevice() {
        var devices = deviceRepository.findAll();
//        triggerBackgroundTask();
        var deviceList = new ArrayList<Device>();
        var chartAttr = dashboardChartsRepository.findByShowChartTrue();

        devices.forEach(device -> {
            var dashboard = deviceDashboardRepository.findByDeviceId(device.getId()).orElse(null);
            if (dashboard != null) {
                device.setX(dashboard.getX());
                device.setY(dashboard.getY());
                device.setShowCharts(dashboard.isShowCharts());
                device.setShowInDashboard(dashboard.isShowInDashboard());
            }

            var newAttrs = new ArrayList<Attribute>();
            var attributes = device.getAttributes();
            attributes.forEach(a -> {
                var at = chartAttr.stream().filter(c -> c.getAttributeKey().equals(a.getKey())).findFirst();
                a.setVisible(at.isPresent());
                newAttrs.add(a);
            });
            device.setAttributes(newAttrs);
            var lastData = getLastData(device.getId());
            device.setLastData(lastData);
            deviceList.add(device);
        });
        deviceList.sort(Comparator.comparingDouble(Device::getY).thenComparingDouble(Device::getX));

        return deviceList;
    }

    public Map<String, Object> setStatus(String deviceId, Status status) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            System.err.println("Device not found");
            return new HashMap<>();
        }
        device.setStatus(status);
        System.err.println("Set status to " + status + " for device " + deviceId);
        var lastData = dataRepository.getFirstByDeviceIdOrderByTimestampDesc(deviceId);
        device = deviceRepository.save(device);
        var map = new HashMap<String, Object>();
        map.put("deviceId", device.getId());
        map.put("deviceConfig", device);
        map.put("data", lastData);
        return map;
    }

    public Map<String, Object> getLastData(String deviceId) {
        var data = dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(deviceId).orElse(new Data());
        return data.getData();
    }

    public String updateDevicePosition(String deviceId, String x, String y) {
        var device = deviceDashboardRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            System.err.println("Device not found");
            device = new Dashboard();
        }
        device.setDeviceId(deviceId);
        device.setX(Math.floor(Double.parseDouble(x)));
        device.setY(Math.floor(Double.parseDouble(y)));
//        device.setShowCharts(false);

        deviceDashboardRepository.save(device);
        notificationService.sendNotification("Devices positions updated", "success");
        return "success";
    }

    public String updateAttrCharts(String deviceId, String attribute, String isVisible) {
        var isShow = Boolean.parseBoolean(isVisible);
        var attr = attributeRepository.findByKeyAndDeviceId(attribute, deviceId);
        System.err.println(attr);
        var deviceChart = dashboardChartsRepository.findByDeviceIdAndAttributeKey(deviceId, attribute);
        if (attr != null && deviceChart == null) {
            System.err.println("Device chart not found");
            var dc = DeviceCharts.builder()
                    .attributeKey(attribute)
                    .showChart(isShow)
                    .deviceId(deviceId).build();
            dashboardChartsRepository.save(dc);
        } else {
            deviceChart.setShowChart(isShow);
            dashboardChartsRepository.save(deviceChart);
        }

//        if (attr == null) {
//            System.err.println("Attribute not found");
//            return "Attribute not found";
//        }
//        System.err.println(attr);
//        attr.setVisible(!Boolean.parseBoolean(isVisible));
//        attributeRepository.save(attr);
        notificationService.sendNotification("Attribute updated and now "+ (isShow?" visible in charts":" not visible in charts"), "success");
        return "success";
    }

    public String showInDashboard(String deviceId, String isVisible) {
        var isShow = Boolean.parseBoolean(isVisible);
        var device = deviceDashboardRepository.findByDeviceId(deviceId).orElse(null);
        if (device != null) {
            device.setShowInDashboard(isShow);
            deviceDashboardRepository.save(device);
            notificationService.sendNotification("Device is "+(isShow?" visible ":" not visible ")+"in dashboard", "success");
            return "success";
        }
        notificationService.sendNotification("Something went wrong", "error");
        return "error";
    }
}
