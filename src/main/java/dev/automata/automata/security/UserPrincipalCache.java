package dev.automata.automata.security;

import dev.automata.automata.model.Users;
import dev.automata.automata.utils.TtlCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Removes the per-request {@code findByEmail} from the JWT filter.
 *
 * <p>Every authenticated request used to load the user from MongoDB just to rebuild the principal. This keeps a short-lived
 * snapshot instead. Three decisions make that safe:
 * <ul>
 *   <li><b>Copy on read.</b> The cache stores its own private copy and every request receives a <em>fresh</em> copy, so
 *       code that mutates the {@code @AuthenticationPrincipal Users} can never leak state into another request
 *       or corrupt the cache. ({@code Users} holds only strings, an {@code Instant} and an enum, so a field-by-field copy
 *       is a complete copy.)</li>
 *   <li><b>Filter only.</b> Login still goes through the real {@code UserDetailsService}, so a changed password is never
 *       checked against a stale hash.</li>
 *   <li><b>Single-flight loading.</b> When an entry expires, one request reloads it while concurrent requests for the
 *       same user wait, instead of all hitting MongoDB at once. Failed loads (unknown user) are never cached.</li>
 * </ul>
 *
 * Trade-off: a change to a user (role, name, deletion) can take up to the TTL to reach requests that carry an already-issued
 * token. Tokens are not revocable before they expire anyway. Call {@link #invalidate} wherever a user is modified for
 * immediate effect on this node.
 *
 * <p>Properties: {@code automata.auth.user-cache-ttl-seconds=60} (0 disables), {@code automata.auth.user-cache-max-size=10000}.
 */
@Component
public class UserPrincipalCache {

    private final UserDetailsService delegate;
    private final TtlCache<String, Users> cache;   // null when disabled

    public UserPrincipalCache(UserDetailsService userDetailsService,
                              @Value("${automata.auth.user-cache-ttl-seconds:60}") long ttlSeconds,
                              @Value("${automata.auth.user-cache-max-size:10000}") int maxSize) {
        this.delegate = userDetailsService;
        this.cache = ttlSeconds > 0
                ? new TtlCache<>(TimeUnit.SECONDS.toNanos(ttlSeconds), Math.max(1, maxSize))
                : null;
    }

    /** @throws org.springframework.security.core.userdetails.UsernameNotFoundException if the user does not exist */
    public UserDetails load(String email) {
        if (cache == null) return delegate.loadUserByUsername(email);

        Users snapshot = cache.get(email, () -> {
            UserDetails loaded = delegate.loadUserByUsername(email);
            return loaded instanceof Users u ? copyOf(u) : null;   // anything unexpected is simply not cached
        });
        return snapshot != null ? copyOf(snapshot) : delegate.loadUserByUsername(email);
    }

    public void invalidate(String email) {
        if (cache != null) cache.invalidate(email);
    }

    static Users copyOf(Users u) {
        return Users.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .password(u.getPassword())
                .email(u.getEmail())
                .timestamp(u.getTimestamp())
                .role(u.getRole())
                .timezone(u.getTimezone())
                .updateDate(u.getUpdateDate())
                .build();
    }
}
