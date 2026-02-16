package dev.automata.automata.service;

import dev.automata.automata.dto.*;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.SystemMetrics;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final AttributeTypeRepository attributeTypeRepository;
    private final WiFiDetailsRepository wiFiDetailsRepository;
    private final AutomationRepository automationRepository;
    private final MasterOptionRepository masterOptionRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;


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

    public AttributeType createAttributeType(AttributeType attributeType) {
        return attributeTypeRepository.save(attributeType);
    }


    public DeviceDto registerDevice(RegisterDevice registerDevice) {
        DeviceMapper deviceMapper = new DeviceMapper();
        var timestamp = System.currentTimeMillis();

        System.err.println(registerDevice);

        var device = Device.builder()
                .name(registerDevice.getName())
                .updateInterval(registerDevice.getUpdateInterval())
                .sleep(registerDevice.getSleep())
                .host(registerDevice.getHost())
                .type(registerDevice.getType())
                .macAddr(registerDevice.getMacAddr())
                .accessUrl(registerDevice.getAccessUrl())
                .reboot(registerDevice.getReboot())
                .attributes(registerDevice.getAttributes())
//                .lastOnline(ZonedDateTime.now())
                .lastRegistered(new Date())
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
            System.err.println("Already registered device: " + deviceMapper.apply(device));
            notificationService.sendNotification("Device: " + device.getName() + "  is back online", "low");
            return deviceMapper.apply(device);
        } else {
            notificationService.sendNotification("New device registered: " + device.getName(), "low");
        }


        var savedDevice = deviceRepository.save(device);

        var dashboard = deviceDashboardRepository.findByDeviceId(device.getId());
        if (dashboard.isEmpty()) {
            System.err.println("Device is in dashboard" + device.getId());
            var dash = Dashboard.builder()
                    .deviceId(device.getId())
                    .analytics(false)
                    .x(50)
                    .y(50)
                    .showCharts(false)
                    .showInDashboard(true)
                    .build();

            deviceDashboardRepository.save(dash);
        } else
            System.err.println("Device is not in dashboard" + device.getId());


        registerDevice.getAttributes().forEach(a -> {
            a.setDeviceId(savedDevice.getId());
            attributes.add(a);
        });
        attributeRepository.saveAll(attributes);
        device.setAttributes(attributes);

        deviceRepository.save(device);

        return deviceMapper.apply(device);
    }

    public void saveAttributes(List<Attribute> attributes) {
        attributeRepository.saveAll(attributes);
    }


    public String saveData(String deviceId, Map<String, Object> payload) {
//        TimeZone.setDefault(TimeZone.getTimeZone("GMT+5:30"));
        ZoneId userZone = ZoneId.of("Asia/Kolkata");
        var instant = Instant.now();
        ZonedDateTime dateTime =
                instant.atZone(userZone);


        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String formattedDate = dateTime.format(formatter);
        var data = Data.builder()
                .deviceId(deviceId)
                .dateTime(formattedDate)
                .data(payload)
                .updateDate(dateTime.toInstant())
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
        var chartAttr = dashboardChartsRepository.findAll();
        var dashboardDevice = deviceDashboardRepository.findAll();
        var dashboardMap = dashboardDevice.stream().collect(Collectors.toMap(Dashboard::getDeviceId, Function.identity()));

        var deviceList = new ArrayList<Device>();
        devices.forEach(device -> {
            var dashboard = dashboardMap.get(device.getId());
            if (dashboard != null) {
                device.setX(dashboard.getX());
                device.setY(dashboard.getY());
                device.setAnalytics(dashboard.isAnalytics());
                device.setShowCharts(dashboard.isShowCharts());
                device.setShowInDashboard(dashboard.isShowInDashboard());
            }
            getDeviceAttributes(chartAttr, deviceList, device);
        });
        deviceList.sort(Comparator.comparingDouble(Device::getY).thenComparingDouble(Device::getX));
        return deviceList;
    }

    private void getDeviceAttributes(List<DeviceCharts> chartAttr, ArrayList<Device> deviceList, Device device) {
        var newAttrs = new ArrayList<Attribute>();
        var attributes = device.getAttributes();
        attributes.forEach(a -> {
            var at = chartAttr.stream().filter(c -> c.getDeviceId().equals(device.getId()) && c.getAttributeKey().equals(a.getKey())).findFirst();
            a.setVisible(at.isPresent());
            newAttrs.add(a);
        });
        device.setAttributes(newAttrs);
//        var lastData = getLastData(device.getId());
//        device.setLastData(lastData);
        deviceList.add(device);
    }

    public List<Device> getDashboardDevices() {
        var dashboardDevice = deviceDashboardRepository.findByShowInDashboardTrue();
        var devices = deviceRepository.findByIdIn(dashboardDevice.stream().map(Dashboard::getDeviceId).toList());

        var dashboardMap = dashboardDevice.stream().collect(Collectors.toMap(Dashboard::getDeviceId, Function.identity()));

        var deviceList = new ArrayList<Device>();
        var chartAttr = dashboardChartsRepository.findByShowChartTrue();

        devices.forEach(device -> {
            var dashboard = dashboardMap.get(device.getId());
            if (dashboard != null) {
                device.setX(dashboard.getX());
                device.setY(dashboard.getY());
                device.setAnalytics(dashboard.isAnalytics());
                device.setShowCharts(dashboard.isShowCharts());
                device.setShowInDashboard(dashboard.isShowInDashboard());
            }

            getDeviceAttributes(chartAttr, deviceList, device);
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
        if (status == Status.OFFLINE)
            device.setLastOnline(new Date());
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
        notificationService.sendNotification("Attribute updated and now " + (isShow ? " visible in charts" : " not visible in charts"), "success");
        return "success";
    }

    public String showInDashboard(String deviceId, String isVisible) {
        var isShow = Boolean.parseBoolean(isVisible);
        var device = deviceDashboardRepository.findByDeviceId(deviceId).orElse(null);
        if (device != null) {
            device.setShowInDashboard(isShow);
            deviceDashboardRepository.save(device);
            notificationService.sendNotification("Device is " + (isShow ? " visible " : " not visible ") + "in dashboard", "success");
        } else {
            var dashboard = Dashboard.builder()
                    .showInDashboard(isShow)
                    .deviceId(deviceId)
                    .x(10)
                    .y(20)
                    .showCharts(false)
                    .build();
            deviceDashboardRepository.save(dashboard);
            notificationService.sendNotification("New device set", "success");
        }
        return "success";

    }

    public Map<String, Object> getMainNodePos() {
        var device = deviceDashboardRepository.findByDeviceId("main-node-1").orElse(null);
        if (device == null) {
            System.err.println("Device not found");
            return Map.of("x", 1360, "y", 20);
        }
        return Map.of("x", device.getX(), "y", device.getY());
    }

    public Device getDeviceByName(String name) {
        return deviceRepository.findByName(name);
    }

    public String showCharts(String deviceId, String isVisible) {
        var isShow = Boolean.parseBoolean(isVisible);
        var device = deviceDashboardRepository.findByDeviceId(deviceId).orElse(null);
        if (device != null) {
            device.setShowCharts(isShow);
            deviceDashboardRepository.save(device);
            notificationService.sendNotification("Device is " + (isShow ? " visible " : " not visible ") + "in charts", "success");
            return "success";
        }

        return "error";
    }

    //    @Scheduled(fixedDelay = 6000)
    public void testWifi() {
        var det = WiFiDetails.builder()
                .ssid("Ganda6969")
                .password("mohit@12345")
                .type("public").build();
        wiFiDetailsRepository.save(det);
    }

    public Map<String, String> getWiFiList() {
        Map<String, String> map = new HashMap<>();

        List<WiFiDetails> list = wiFiDetailsRepository.findAll();

        for (int i = 0; i < 3; i++) {
            if (i < list.size()) {
                WiFiDetails wifi = list.get(i);
                map.put("wn" + (i + 1), wifi.getSsid());
                map.put("wp" + (i + 1), wifi.getPassword());
            } else {
                map.put("wn" + (i + 1), "");
                map.put("wp" + (i + 1), "");
            }
        }

        return map;
    }

    public Object saveWiFiList(Map<String, String> body) {
        var list = new ArrayList<WiFiDetails>();
        for (int i = 1; i <= 3; i++) {
            String ssid = body.get("wn" + i);
            String password = body.get("wp" + i);

            list.add(WiFiDetails.builder()
                    .ssid(ssid)
                    .password(password)
                    .type("public")
                    .build());
            // save each to DB — upsert logic recommended
        }
        wiFiDetailsRepository.saveAll(list);
        return "success";
    }

    public String getShutdownStatus() {

        return "N";
    }

    public Object updateAttribute(String deviceId, String attribute, String isShow) {
        var dashboardOptional = deviceDashboardRepository.findByDeviceId(deviceId);
        var cond = Boolean.parseBoolean(isShow);
        if (dashboardOptional.isPresent()) {
            var dashboard = dashboardOptional.get();
            switch (attribute) {
                case "analytics" -> dashboard.setAnalytics(cond);
                case "showCharts" -> dashboard.setShowCharts(cond);
                case "showInDashboard" -> dashboard.setShowInDashboard(cond);
            }
            deviceDashboardRepository.save(dashboard);
        } else {
            var dashboard = Dashboard.builder()
                    .showInDashboard(cond)
                    .deviceId(deviceId)
                    .x(10)
                    .y(20)
                    .showCharts(false)
                    .build();
            switch (attribute) {
                case "analytics" -> dashboard.setAnalytics(cond);
                case "showCharts" -> dashboard.setShowCharts(cond);
                case "showInDashboard" -> dashboard.setShowInDashboard(cond);
            }
            deviceDashboardRepository.save(dashboard);
            notificationService.sendNotification("New device set", "success");
        }
        return "null";
    }

    public void saveDevice() {
        var device = getDevice("67571bf46f2d631aa77cc632");
        var attrs = new ArrayList<Attribute>(device.getAttributes());
        var att = Attribute.builder()
                .id("6882459c04a0a366870bec10")
                .key("toggle")
                .deviceId(device.getId())
                .displayName("Toggle")
                .visible(true)
                .units("")
                .extras(new HashMap<>())
                .type("ACTION|OUT")
                .build();

        attrs.add(
                att
        );
        device.setAttributes(attrs);
        System.err.println(device);
//        deviceRepository.save(device);
    }

    public String getAutomations() {
        return automationRepository.findByIsEnabledTrue()
                .stream()
                .map(a -> a.getName() + ":" + a.getId())
                .collect(Collectors.joining(","));
    }

    @Scheduled(fixedRate = 1000)
    public void executeInsert() {
        getMasterList();
    }

    public Object getMasterList() {
        var req = deviceRepository.findAll();
        var list = new ArrayList<>();
        for (var device : req) {
            for (var att : device.getAttributes()) {
                if (att.getType().equals("ACTION|SLIDER")) {
                    list.add(
                            Map.of(
                                    "id", device.getId(),
                                    "name", device.getName() + " " + att.getDisplayName(),
                                    "key", att.getKey()
                            )
                    );
                }
            }
        }
//        System.err.println(list);
//        var list = new ArrayList<>();
//        for (var i : req) {
//            list.add(
//                    Map.of(
//                            "id", i.getDeviceId(),
//                            "name", i.getName(),
//                            "key", i.getKey()
//                    )
//            );
//        }
        return list;
//        return null;
    }

    public Map<String, Object> getServerCreds() {
        var res = SystemMetrics.getNgrokDetails();
        if (res.containsKey("msg"))
            return Map.of("MQTT_HOST", "raspberry.local", "MQTT_PORT", "1883");
        else
            return res;
    }
}
