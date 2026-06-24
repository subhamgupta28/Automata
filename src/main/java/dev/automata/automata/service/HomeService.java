package dev.automata.automata.service;

import dev.automata.automata.dto.HomeDto;
import dev.automata.automata.dto.HomeMemberDto;
import dev.automata.automata.model.Home;
import dev.automata.automata.model.HomeAccess;
import dev.automata.automata.model.HomeRole;
import dev.automata.automata.model.Users;
import dev.automata.automata.repository.HomeAccessRepository;
import dev.automata.automata.repository.HomeRepository;
import dev.automata.automata.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeRepository homeRepo;
    private final HomeAccessRepository accessRepo;
    private final HomeAuthzService authzService;
    private final UsersRepository usersRepository;

    public List<HomeDto> getHomesForUser(String userId) {
        List<HomeAccess> accesses = accessRepo.findAllByUserId(userId);

        return accesses.stream().map(access -> {
            Home home = homeRepo.findById(access.getHomeId())
                    .orElseThrow(); // shouldn't happen if data is consistent

            return HomeDto.builder()
                    .id(home.getId())
                    .name(home.getName())
                    .timezone(home.getTimezone())
                    .ownerId(home.getOwnerId())
                    .myRole(access.getRole())
                    .build();
        }).toList();
    }

    public Home createHome(Home home, String userId) {
        Home savedHome = homeRepo.save(home);

        HomeAccess access = new HomeAccess();
        access.setHomeId(savedHome.getId());
        access.setUserId(userId);
        access.setRole(HomeRole.OWNER);
        access.setGrantedAt(Instant.now());
        access.setGrantedByUserId(userId);
        accessRepo.save(access);

        return savedHome;
    }

// In HomeService.java — replace getHomeMembers entirely

    public List<HomeMemberDto> getHomeMembers(String homeId, String requestingUserId) {
        authzService.requireAccess(homeId, requestingUserId);
        List<HomeAccess> accesses = accessRepo.findAllByHomeId(homeId);

        return accesses.stream().map(access -> {
            Users user = usersRepository.findById(access.getUserId())
                    .orElse(null);

            return HomeMemberDto.builder()
                    .userId(access.getUserId())
                    .name(user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown")
                    .email(user != null ? user.getEmail() : null)
                    .role(access.getRole())
                    .grantedAt(access.getGrantedAt())
                    .grantedByUserId(access.getGrantedByUserId())
                    .build();
        }).toList();
    }

    @Transactional
    public void changeMemberRole(String homeId, String requestingUserId, String targetUserId, HomeRole newRole) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER);

        if (newRole == HomeRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign OWNER role. Use transfer ownership instead.");
        }

        HomeAccess targetAccess = accessRepo.findByHomeIdAndUserId(homeId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not have access to this home"));

        targetAccess.setRole(newRole);
        accessRepo.save(targetAccess);
        authzService.evictAccessCache(homeId, targetUserId);
    }

    @Transactional
    public void deleteHome(String homeId, String requestingUserId) {
        authzService.requireRole(homeId, requestingUserId, HomeRole.OWNER);
        // TODO: Add more checks here, e.g., ensure all devices are deleted first
        accessRepo.deleteAllByHomeId(homeId);
        homeRepo.deleteById(homeId);
    }
}