package dev.automata.automata.service;

import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.RootDto;
import dev.automata.automata.dto.ValueDto;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.Device;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
                .accessUrl(registerDevice.getAccessUrl())
                .reboot(registerDevice.getReboot())
                .attributes(registerDevice.getAttributes())
                .status(registerDevice.getStatus()).build();


        var isAlreadyRegistered = deviceRepository.findById(registerDevice.getDeviceId()).orElse(null);
        if (isAlreadyRegistered != null) {
            System.err.println("Already registered device: " + registerDevice.getDeviceId());
            return isAlreadyRegistered;
        }


        var savedDevice = deviceRepository.save(device);
        var attributes = new ArrayList<Attribute>();
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
//        var attributes = attributeRepository.findAllByDeviceId(deviceId);
        var data = Data.builder()
                .deviceId(deviceId)
                .data(payload)
                .timestamp(System.currentTimeMillis())
                .build();
        dataRepository.save(data);

//        System.out.println(attributes);
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
                    .ofPattern("yyyy-MMM-dd HH:mm:ss") // Specify your desired format
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
        return deviceRepository.findAll();
    }
}
