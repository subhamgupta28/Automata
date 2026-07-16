package dev.automata.automata.firmware_builder;

import lombok.Data;

@Data
public class DeviceRegisterReq {
    private String id;
    private String name;
    private String githubRepoUrl;
    private String accessUrl;
    private String macAddr;
    private String homeId;
}
