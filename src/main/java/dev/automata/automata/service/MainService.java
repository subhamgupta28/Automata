package dev.automata.automata.service;

import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.RootDto;
import dev.automata.automata.dto.ValueDto;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
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
            var dev = isMacAddrPresent.get(0);
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

    public List<Device> getAllDevice() {
        var devices = deviceRepository.findAll();
        var deviceList = new ArrayList<Device>();
        devices.forEach(device -> {
            var lastData = getLastData(device.getId());
            device.setLastData(lastData);
            deviceList.add(device);
        });
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
}
