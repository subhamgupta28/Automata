package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationState {
    private boolean wasTriggeredPreviously;
    private Date lastUpdated;
    private String triggerId;
    private String automationId;

}
