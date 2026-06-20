package dev.automata.automata.controller;

import dev.automata.automata.dto.HomeDto;
import dev.automata.automata.model.Home;
import dev.automata.automata.model.HomeRole;
import dev.automata.automata.model.Users;
import dev.automata.automata.service.HomeService;
import dev.automata.automata.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/homes")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final InviteService inviteService;

    @GetMapping("/mine")
    public ResponseEntity<List<HomeDto>> getMyHomes(@AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(homeService.getHomesForUser(user.getId()));
    }

    @PostMapping("/create")
    public ResponseEntity<Home> createHome(@RequestBody HomeDto homeDto, @AuthenticationPrincipal Users user) {
        Home home = new Home();
        home.setName(homeDto.getName());
        home.setTimezone(homeDto.getTimezone());
        home.setOwnerId(user.getId());
        home.setCreatedAt(Instant.now());
        return ResponseEntity.ok(homeService.createHome(home, user.getId()));
    }

    @GetMapping("/{homeId}/members")
    public ResponseEntity<List<HomeDto>> getHomeMembers(@PathVariable String homeId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(homeService.getHomeMembers(homeId, user.getId()));
    }

    @PatchMapping("/{homeId}/members/{userId}")
    public ResponseEntity<Void> changeMemberRole(@PathVariable String homeId, @PathVariable String userId, @RequestBody Map<String, HomeRole> payload, @AuthenticationPrincipal Users user) {
        homeService.changeMemberRole(homeId, user.getId(), userId, payload.get("role"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{homeId}/members/{userId}")
    public ResponseEntity<Void> revokeAccess(@PathVariable String homeId, @PathVariable String userId, @AuthenticationPrincipal Users user) {
        inviteService.revokeAccess(homeId, user.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{homeId}")
    public ResponseEntity<Void> deleteHome(@PathVariable String homeId, @AuthenticationPrincipal Users user) {
        homeService.deleteHome(homeId, user.getId());
        return ResponseEntity.ok().build();
    }
}