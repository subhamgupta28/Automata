package dev.automata.automata.service;

import dev.automata.automata.model.HomeAccess;
import dev.automata.automata.model.HomeRole;
import dev.automata.automata.model.Invite;
import dev.automata.automata.repository.HomeAccessRepository;
import dev.automata.automata.repository.InviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final HomeAccessRepository homeAccessRepository;
    private final HomeAuthzService authzService;

    @Transactional
    public Invite createInvite(String homeId, String requestingUserId, String email, HomeRole roleToGrant) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER, HomeRole.ADMIN);

        if (roleToGrant == HomeRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot invite users as OWNER");
        }

        Invite invite = new Invite();
        invite.setToken(UUID.randomUUID().toString());
        invite.setHomeId(homeId);
        invite.setInvitedByUserId(requestingUserId);
        invite.setInvitedEmail(email);
        invite.setRoleToGrant(roleToGrant);
        invite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invite.setTtl(invite.getExpiresAt()); // For MongoDB's TTL index

        // TODO: Send email to invite.getInvitedEmail() with the invite link

        return inviteRepository.save(invite);
    }

    public List<Invite> getHomeInvites(String homeId, String requestingUserId) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER, HomeRole.ADMIN);
        return inviteRepository.findAllByHomeIdAndUsedIsFalseAndExpiresAtAfter(
                homeId, Instant.now());
    }

    @Transactional
    public void acceptInvite(String token, String userId, String userEmail) {
        Invite invite = inviteRepository.findByTokenAndUsedIsFalse(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found or already used"));

        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite has expired");
        }

        if (invite.getInvitedEmail() != null && !invite.getInvitedEmail().equalsIgnoreCase(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invite is for a different user");
        }

        // Check if user already has access
        if (homeAccessRepository.findByHomeIdAndUserId(invite.getHomeId(), userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already has access to this home");
        }

        HomeAccess access = new HomeAccess();
        access.setHomeId(invite.getHomeId());
        access.setUserId(userId);
        access.setRole(invite.getRoleToGrant());
        access.setGrantedAt(Instant.now());
        access.setGrantedByUserId(invite.getInvitedByUserId());
        homeAccessRepository.save(access);

        invite.setUsed(true);
        inviteRepository.save(invite);
    }

    public void revokeAccess(String homeId, String requestingUserId, String targetUserId) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER, HomeRole.ADMIN);

        HomeAccess targetAccess = homeAccessRepository.findByHomeIdAndUserId(homeId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not have access to this home"));

        if (targetAccess.getRole() == HomeRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot revoke access from the owner");
        }

        if (targetAccess.getRole() == HomeRole.ADMIN && authzService.requireRole(homeId, requestingUserId).getRole() != HomeRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins cannot revoke access from other admins");
        }

        homeAccessRepository.delete(targetAccess);
        authzService.evictAccessCache(homeId, targetUserId);
    }
}