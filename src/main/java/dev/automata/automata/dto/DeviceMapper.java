package dev.automata.automata.dto;

import dev.automata.automata.model.Device;
import lombok.RequiredArgsConstructor;
import java.util.function.Function;

import java.lang.reflect.Method;


public class DeviceMapper implements Function<Device, DeviceDto> {

    @Override
    public DeviceDto apply(Device device) {
        return DeviceDto.builder()
                .id(device.getId())
                .updateInterval(device.getUpdateInterval())
                .type(device.getType())
                .name(device.getName())
                .host(device.getHost())
                .accessUrl(device.getAccessUrl())
                .macAddr(device.getMacAddr())
                .build();
    }
}
