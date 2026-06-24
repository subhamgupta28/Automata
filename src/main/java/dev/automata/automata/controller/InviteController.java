package dev.automata.automata.controller;


import dev.automata.automata.dto.InviteDto;
import dev.automata.automata.model.Invite;
import dev.automata.automata.model.Users;
import dev.automata.automata.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping
    public ResponseEntity<Invite> createInvite(@RequestBody InviteDto inviteDto, @AuthenticationPrincipal Users user) {
        Invite invite = inviteService.createInvite(
                inviteDto.getHomeId(),
                user.getId(),
                inviteDto.getEmail(),
                inviteDto.getRoleToGrant()
        );
        return ResponseEntity.ok(invite);
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> acceptInvite(@RequestParam String token, @AuthenticationPrincipal Users user) {
        inviteService.acceptInvite(token, user.getId(), user.getEmail());
        return ResponseEntity.ok().build();
    }

    // In HomeController.java — add these two endpoints

    @PostMapping("/{homeId}/invites")
    public ResponseEntity<Invite> createInvite(
            @PathVariable String homeId,
            @RequestBody InviteDto inviteDto,
            @AuthenticationPrincipal Users user) {
        Invite invite = inviteService.createInvite(
                homeId,
                user.getId(),
                inviteDto.getEmail(),
                inviteDto.getRoleToGrant()
        );
        return ResponseEntity.ok(invite);
    }

    @GetMapping("/{homeId}/invites")
    public ResponseEntity<List<Invite>> getHomeInvites(
            @PathVariable String homeId,
            @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(inviteService.getHomeInvites(homeId, user.getId()));
    }
}