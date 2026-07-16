package dev.automata.automata.firmware_builder;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange(url = "${app.firmware-builder.host}:${app.firmware-builder.port}/api", accept = "application/json")
public interface FirmwareService {

    @PostExchange("/devices")
    DeviceRegisterReq registerDevice(@RequestBody DeviceRegisterReq registerReq);

    @GetExchange("/devices")
    List<DeviceRegisterReq> getAllDevices();

    @GetExchange("/devices/{id}")
    DeviceRegisterReq getDevice(@PathVariable String id);

    @GetExchange("/build/{id}")
    BuildRequest startBuild(@PathVariable String id);

    @GetExchange("/build/status/{id}")
    BuildRequest getBuildStatus(@PathVariable String id);
}
