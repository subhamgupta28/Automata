package dev.automata.automata.dto;

import lombok.Data;

@Data
public class VirtualDeviceLockRequest {
    private String vid;
    private String password;
    private boolean permanentUnlock = false;   // if true, unlocking never expires until manually re-locked
    private int unlockDurationMinutes = 30;    // used when permanentUnlock = false
}
