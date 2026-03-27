package dev.automata.automata.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.automata.automata.model.Automation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AutomationCache {
    private String id;

    @JsonProperty("automation")
    private Automation automation;
    private String triggerDeviceId;
    private String triggerDeviceType;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date lastUpdate;
    private Date previousExecutionTime;
    private boolean triggeredPreviously;
    private Boolean isActive;
    private boolean enabled;
    private Map<String, Object> previousState; // deviceId -> state
    private boolean active; // already exists but use properly

    private Date lastExecutionTime;
    private Date lastStateChangeTime;

    private Long conditionFirstTrueAt;
}
