package dev.automata.automata.firmware_builder;

import org.springframework.stereotype.Service;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@Service
@HttpExchange(url = "${app.firmware-builder.host}:${app.firmware-builder.port}/api")
public interface FirmwareService {

    @PostExchange("/devices")
    DeviceRegisterReq registerDevice(DeviceRegisterReq registerReq);

    @GetExchange("/devices")
    List<DeviceRegisterReq> getAllDevices();

    @GetExchange("/devices/{id}")
    List<DeviceRegisterReq> getDevice(String id);

    @GetExchange("/build/{id}")
    BuildRequest startBuild(String id);

    @GetExchange("/build/status/{id}")
    BuildRequest getBuildStatus(String id);
}
