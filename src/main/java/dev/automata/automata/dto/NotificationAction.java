package dev.automata.automata.dto;

import dev.automata.automata.model.Users;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NotificationAction {
    private String action;
    private String homeId;
    private Users user;
    private Map<String, Object> payload;
}
