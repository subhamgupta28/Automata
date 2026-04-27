package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * A Scene is a named group of automations that fire together in order.
 * <p>
 * When a scene is triggered, each member automation's positive actions
 * are executed in member priority order with an optional delay between
 * members. The individual automation state machines are NOT advanced —
 * scenes are always stateless fire-and-forget.
 * <p>
 * Use cases:
 * - "Good morning" → dim lights, turn on fan, play music notification
 * - "Movie mode"   → set lighting preset, lower blinds, mute notifications
 * - "Leave home"   → turn off all lights, set thermostat, lock alert
 */
@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_scenes")
public class AutomationScene {

    @Id
    private String id;

    private String name;
    private String description;
    private String icon;              // optional icon key for UI

    @Builder.Default
    private Boolean isEnabled = true;

    private Date updateDate;
    private Date lastTriggeredAt;

    /**
     * Ordered list of automations in this scene.
     */
    private List<SceneMember> members;

    // ── Member ────────────────────────────────────────────────────────────────

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneMember {
        private String automationId;
        private String automationName;   // denormalised for display

        /**
         * Execution order within the scene. Lower = earlier.
         * Members with equal order run in parallel.
         */
        private int order;

        /**
         * Delay in seconds AFTER this member's actions complete before
         * the next member fires. Allows staggered scene transitions.
         */
        private int delayAfterSeconds;
    }
}
