package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class DeviceLoginRequest {
    private String macAddr;
    private String deviceSecret;
}
