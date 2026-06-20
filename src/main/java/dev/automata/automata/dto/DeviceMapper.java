package dev.automata.automata.dto;

import dev.automata.automata.model.Device;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;


@Component
public class DeviceMapper implements Function<Device, DeviceDto> {

    @Override
    public DeviceDto apply(Device device) {
        Objects.requireNonNull(device, "Device must not be null");

        return DeviceDto.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .category(device.getCategory())
                .homeId(device.getHomeId())
                .host(device.getHost())
                .accessUrl(device.getAccessUrl())
                .macAddr(device.getMacAddr())
                .updateInterval(device.getUpdateInterval())
//                .sleep(device.getSleep())
//                .reboot(device.getReboot())
//                .status(device.getStatus())
//                .lastRegistered(device.getLastRegistered())
//                .attributes(device.getAttributes())
                // deviceSecretHash intentionally excluded — never send the hash to the client
                .build();
    }
}