package dev.automata.automata.service;

import dev.automata.automata.dto.*;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.SystemMetrics;
import dev.automata.automata.repository.*;
import dev.automata.automata.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HomeAuthzService authzService;
    private final DeviceMapper deviceMapper;
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

    // Device path -- open
    public DeviceDto registerDevice(RegisterDevice registerDevice) {
        log.info("Registering device: {}", registerDevice);

        Device device = buildDevice(registerDevice);

        return deviceRepository.findByMacAddr(registerDevice.getMacAddr())
                .stream()
                .findFirst()
                .map(existing -> reRegisterDevice(device, existing, registerDevice))
                .orElseGet(() -> registerNewDevice(device, registerDevice));
    }

    // ── Re-registration (MAC already known) ──────────────────────────────────

    private DeviceDto reRegisterDevice(Device incoming, Device existing,
                                       RegisterDevice registerDevice) {
        incoming.setId(existing.getId());
        incoming.setHomeId(existing.getHomeId());
        incoming.setStatus(existing.getStatus());
        incoming.setCreatedBy(existing.getCreatedBy());

        // Merge — preserves attribute IDs and user overrides
        List<Attribute> merged = mergeAttributes(
                existing.getId(), registerDevice.getAttributes());
        incoming.setAttributes(merged);

        Device savedDevice = deviceRepository.save(incoming);
        log.info("Re-registered device {} | attributes: existing={}, incoming={}, merged={}",
                savedDevice.getId(),
                existing.getAttributes().size(),
                registerDevice.getAttributes().size(),
                merged.size());

        notificationService.sendNotification(
                "Device: " + incoming.getName() + " is back online", "low");

        return deviceMapper.apply(savedDevice);
    }

    // ── First-time registration ───────────────────────────────────────────────
    private DeviceDto registerNewDevice(Device device, RegisterDevice registerDevice) {
        Device savedDevice = deviceRepository.save(device);

        // Init — fresh insert, no existing attrs to merge
        List<Attribute> attributes = initAttributes(
                savedDevice.getId(), registerDevice.getAttributes());

        ensureDashboardExists(savedDevice.getId());

        savedDevice.setAttributes(attributes);
        deviceRepository.save(savedDevice);

        notificationService.sendNotification(
                "New device registered: " + device.getName(), "low");

        return deviceMapper.apply(savedDevice);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Device buildDevice(RegisterDevice req) {
        String rawSecret = UUID.randomUUID().toString();

        return Device.builder()
                .name(req.getName())
                .updateInterval(req.getUpdateInterval())
                .sleep(req.getSleep())
                .host(req.getHost())
                .type(req.getType())
                .category(req.getCategory())
                .macAddr(req.getMacAddr())
                .accessUrl(req.getAccessUrl())
                .reboot(req.getReboot())
                .attributes(req.getAttributes())
                .lastRegistered(new Date())
                .status(req.getStatus())
                .status(Status.UNCLAIMED)   // always start unclaimed
                .homeId(null)               // no home until a user claims it
                .deviceSecretHash(passwordEncoder.encode(rawSecret))
                .build();
    }

    /**
     * Merges incoming attributes from the device with what's stored.
     * - Keys that exist → update metadata (displayName, units, type, visible)
     * but preserve any user-set overrides you want to protect (e.g. displayName)
     * - New keys → insert
     * - Keys no longer reported → delete
     * - System attribute (last_seen) is always ensured
     */
    public List<Attribute> mergeAttributes(String deviceId, List<Attribute> incoming) {
        Map<String, Attribute> existing = attributeRepository
                .findByDeviceId(deviceId)
                .stream()
                .collect(Collectors.toMap(Attribute::getKey, a -> a));

        Map<String, Attribute> incomingMap = incoming.stream()
                .collect(Collectors.toMap(Attribute::getKey, a -> a));

        List<Attribute> toSave = new ArrayList<>();
        Set<String> toDelete = new HashSet<>();

        // Update or insert
        incomingMap.forEach((key, incomingAttr) -> {
            incomingAttr.setDeviceId(deviceId);

            if (existing.containsKey(key)) {
                Attribute existingAttr = existing.get(key);
                // Update only hardware-reported metadata, preserve the DB id
                existingAttr.setUnits(incomingAttr.getUnits());
                existingAttr.setType(incomingAttr.getType());
                existingAttr.setVisible(incomingAttr.getVisible());
                // Only overwrite displayName if device explicitly changed it
                if (incomingAttr.getDisplayName() != null) {
                    existingAttr.setDisplayName(incomingAttr.getDisplayName());
                }
                toSave.add(existingAttr);
            } else {
                log.info("New attribute '{}' added for device {}", key, deviceId);
                toSave.add(incomingAttr);
            }
        });

        // Remove keys no longer reported (skip last_seen — it's system-managed)
        existing.forEach((key, attr) -> {
            if (!incomingMap.containsKey(key) && !key.equals("last_seen")) {
                log.info("Attribute '{}' removed for device {}", key, deviceId);
                toDelete.add(attr.getId());
            }
        });

        if (!toDelete.isEmpty()) {
            attributeRepository.deleteAllById(toDelete);
        }

        // Always ensure last_seen exists
        if (!existing.containsKey("last_seen")) {
            toSave.add(buildLastSeen(deviceId));
        }

        return attributeRepository.saveAll(toSave);
    }

    public List<Attribute> initAttributes(String deviceId, List<Attribute> incoming) {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(buildLastSeen(deviceId));

        incoming.forEach(a -> {
            a.setDeviceId(deviceId);
            attributes.add(a);
        });

        return attributeRepository.saveAll(attributes);
    }

    private Attribute buildLastSeen(String deviceId) {
        return Attribute.builder()
                .deviceId(deviceId)
                .key("last_seen")
                .displayName("Last Seen")
                .units("time")
                .type("DATA|AUX")
                .visible(true)
                .build();
    }


    private void ensureDashboardExists(String deviceId) {
        if (deviceDashboardRepository.findByDeviceId(deviceId).isEmpty()) {
            Dashboard dash = Dashboard.builder()
                    .deviceId(deviceId)
                    .analytics(false)
                    .x(50).y(50)
                    .showCharts(false)
                    .showInDashboard(true)
                    .build();
            deviceDashboardRepository.save(dash);
            log.debug("Created dashboard for device {}", deviceId);
        }
    }

    public void saveAttributes(List<Attribute> attributes) {
        attributeRepository.saveAll(attributes);
    }

    // Device path -- open
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

    public List<Device> getAllDeviceUi(String homeId, Users user) {
        authzService.requireAccess(homeId, user.getId());
        var devices = deviceRepository.findAllByHomeId(homeId);
        var chartAttr = dashboardChartsRepository.findAll();
        var dashboardDevice = deviceDashboardRepository.findAllByHomeId(homeId);
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

    public List<Device> getAllDevice() {
        return deviceRepository.findAll();
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

    public List<Device> getDashboardDevices(String homeId, Users user) {
        var dashboardDevice = deviceDashboardRepository.findByShowInDashboardTrueAndHomeId(homeId);
        var deviceIds = dashboardDevice.stream().map(Dashboard::getDeviceId).toList();
        var devices = deviceRepository.findByIdIn(deviceIds);

        var dashboardMap = dashboardDevice.stream().collect(Collectors.toMap(Dashboard::getDeviceId, Function.identity()));

        var deviceList = new ArrayList<Device>();
        var chartAttr = dashboardChartsRepository.findByShowChartTrueAndDeviceIdIn(deviceIds);

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

    public Map<String, Object> getLastData(String deviceId, String homeId, Users user) {
        authzService.requireAccess(homeId, user.getId());
        var data = dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(deviceId).orElse(new Data());
        return data.getData();
    }

    public Data getLastFullData(String deviceId) {
        return dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(deviceId).orElse(new Data());
    }

    public String updateDevicePosition(String deviceId, String x, String y, String homeId, Users user) {
        var device = deviceDashboardRepository.findByDeviceIdAndHomeId(deviceId, homeId).orElse(null);
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

    public String updateAttrCharts(String deviceId, String attribute, String isVisible, String homeId, Users user) {
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

    public String showInDashboard(String deviceId, String isVisible, String homeId, Users user) {
        var isShow = Boolean.parseBoolean(isVisible);
        var device = deviceDashboardRepository.findByDeviceIdAndHomeId(deviceId, homeId).orElse(null);
        if (device != null) {
            device.setShowInDashboard(isShow);
            deviceDashboardRepository.save(device);
            notificationService.sendNotification("Device is " + (isShow ? " visible " : " not visible ") + "in dashboard", "success", homeId);
        } else {
            var dashboard = Dashboard.builder()
                    .showInDashboard(isShow)
                    .deviceId(deviceId)
                    .x(10)
                    .y(20)
                    .showCharts(false)
                    .build();
            deviceDashboardRepository.save(dashboard);
            notificationService.sendNotification("New device set", "success", homeId);
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

    public Device getDeviceByCategory(String category) {
        return deviceRepository.findByCategory(category);
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

    public void saveDevice(Device device) {
        System.err.println("Device saved: " + deviceRepository.save(device));
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

//    @Scheduled(fixedRate = 1000)
//    public void executeInsert() {
//        getMasterList();
//    }

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

    public String setStatusOfDeviceByMacAddress(String address, Status status) {
        var devices = deviceRepository.findAllByMacAddr(address);
        if (devices == null)
            return "error";
        devices.forEach(d -> setStatus(d.getId(), status));
        return "success";
    }

    public String updateWledDevice(List<WledPresets> devices) {
        for (var device : devices) {
            var res = deviceRepository.findByCategory(device.getName());
            var attributes = new ArrayList<Attribute>();
            if (res.getType().equals("WLED")) {
                var presets = new HashMap<String, Object>();
                for (var preset : device.getPresets()) {
                    presets.put(preset.getName(), Integer.parseInt(preset.getId()));
                }
                var presetAttr = res.getAttributes().stream().filter(d -> d.getType().equals("ACTION|PRESET")).toList();
                presetAttr.getFirst().setExtras(presets);

                var attr = attributeRepository.findByDeviceId(res.getId());
                if (!attr.isEmpty()) {
                    System.err.print("Attributes: ");
                    System.err.println(attr);
                    attributeRepository.deleteByDeviceId(res.getId());
                }

                res.getAttributes().forEach(a -> {
                    a.setDeviceId(res.getId());
                    attributes.add(a);
                });
                var atr = attributeRepository.saveAll(attributes);
                res.setAttributes(atr);

                System.err.println("Saved Preset for: " + res.getName() + " preset: " + attributes);
                saveDevice(res);
            }
        }
        return null;
    }

    public void setRecentDeviceData(String id, Map<String, Object> payload) {

    }

    public DeviceAuthResponse deviceLogin(DeviceLoginRequest req) {
        List<Device> device = deviceRepository
                .findByMacAddr(req.getMacAddr());

        if (device == null)
            throw new BadCredentialsException("Invalid device");

        if (device.isEmpty())
            throw new BadCredentialsException("Invalid device");
        if (!passwordEncoder.matches(
                req.getDeviceSecret(),
                device.getFirst().getDeviceSecretHash())) {

            throw new BadCredentialsException("Invalid device");
        }

        String token =
                jwtService.generateDeviceToken(device.getFirst());

        return new DeviceAuthResponse(token);
    }

    public List<Device> getUnclaimedDevices(String homeId, String requestingUserId) {
        authzService.requireAccess(homeId, requestingUserId);
        return deviceRepository.findAllByHomeIdIsNull();
    }

    // DeviceService
    public void claimDevice(String homeId, String deviceId, String requestingUserId) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER, HomeRole.ADMIN);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (device.getHomeId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device is already claimed");
        }
        device.setHomeId(homeId);
        deviceRepository.save(device);
    }
}
