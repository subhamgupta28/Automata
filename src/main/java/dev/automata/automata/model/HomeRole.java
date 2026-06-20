package dev.automata.automata.model;

public enum HomeRole {
    OWNER,   // full control, can delete home, transfer ownership
    ADMIN,   // manage devices + automations, invite/remove members
    MEMBER   // read + control devices, no management
}
