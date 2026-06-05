package dev.automata.automata.config;

import dev.automata.automata.model.Users;
import dev.automata.automata.model.Role;
import dev.automata.automata.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * DataLoader - Initializes the guest user account on application startup
 * Guest user is used for read-only access to allowed routes
 */
@Component
@RequiredArgsConstructor
public class GuestUserDataLoader implements CommandLineRunner {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        createGuestUserIfNotExists();
    }

    private void createGuestUserIfNotExists() {
        Optional<Users> existingGuest = usersRepository.findByEmail("guest@automata.local");
        
        if (existingGuest.isPresent()) {
            System.out.println("Guest user already exists");
            return;
        }

        Users guestUser = Users.builder()
                .id("guest")
                .email("guest@automata.local")
                .password(passwordEncoder.encode("guest"))
                .firstName("Guest")
                .lastName("User")
                .role(Role.GUEST)
                .timezone("Asia/Kolkata")
                .timestamp(Instant.now())
                .build();

        usersRepository.save(guestUser);
        System.out.println("Guest user created successfully with email: guest@automata.local");
    }
}
