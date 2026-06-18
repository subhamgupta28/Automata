package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.HomeAccess;
import dev.automata.automata.model.HomeRole;
import dev.automata.automata.repository.HomeAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeAuthzService {

    private final HomeAccessRepository homeAccessRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper; // Inject ObjectMapper

    private String cacheKey(String homeId, String userId) {
        return "home_access:" + homeId + ":" + userId;
    }

    public HomeAccess requireAccess(String homeId, String userId) {
        String key = cacheKey(homeId, userId);

        // Check Redis first
        Object cachedObject = redisTemplate.opsForValue().get(key);
        if (cachedObject != null) {
            // Convert the map from Redis into the specific HomeAccess object
            return objectMapper.convertValue(cachedObject, HomeAccess.class);
        }

        // Miss — hit MongoDB
        HomeAccess access = homeAccessRepo.findByHomeIdAndUserId(homeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied or home not found"));

        // Cache for 10 minutes
        redisTemplate.opsForValue().set(key, access, Duration.ofMinutes(10));
        return access;
    }

    // Call this when access is revoked or role changes
    public void evictAccessCache(String homeId, String userId) {
        redisTemplate.delete(cacheKey(homeId, userId));
    }


    /**
     * Require one of the given roles. Throws 403 otherwise.
     */
    public HomeAccess requireRole(String homeId, String userId, HomeRole... permitted) {
        HomeAccess access = requireAccess(homeId, userId);
        boolean allowed = Arrays.asList(permitted).contains(access.getRole());
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Requires role: " + Arrays.toString(permitted));
        }
        return access;
    }

    /**
     * Returns true/false without throwing — useful for conditional UI data.
     */
    public boolean hasRole(String homeId, String userId, HomeRole... permitted) {
        try {
            HomeAccess access = requireAccess(homeId, userId);
            return Arrays.asList(permitted).contains(access.getRole());
        } catch (ResponseStatusException e) {
            return false;
        }
    }

    /**
     * Returns all homeIds the user is a member of — used to scope device queries.
     */
    public List<String> getUserHomeIds(String userId) {
        return homeAccessRepo.findAllByUserId(userId)
                .stream().map(HomeAccess::getHomeId).toList();
    }
}