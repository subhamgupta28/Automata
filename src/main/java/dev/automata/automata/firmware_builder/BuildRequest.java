package dev.automata.automata.firmware_builder;

import lombok.Data;

@Data
public class BuildRequest {
    String buildLog;
    String deviceId;
    String errorMessage;
    String finishedAt;
    String id;
    String startedAt;
    String status;
    String version;
}
