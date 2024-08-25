package dev.automata.automata.service;

import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.Device;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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


    public Device registerDevice(RegisterDevice registerDevice){
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
        var isAlreadyRegistered = deviceRepository.findByIdIgnoreCase(registerDevice.getDeviceId()).orElse(null);
        if (isAlreadyRegistered!=null){
            return isAlreadyRegistered;
        }

        var savedDevice = deviceRepository.save(device);
        return savedDevice;
    }

    public void saveAttributes(List<Attribute> attributes){
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
}
