package org.example.authservice.config;

import org.example.authservice.model.Role;
import org.example.authservice.model.User;
import org.example.authservice.repository.RoleRepository;
import org.example.authservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(RoleRepository roleRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            if (roleRepository.count() == 0) {
                Role userRole = roleRepository.save(new Role("ROLE_USER"));
                Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"));

                User admin = new User("admin", "admin@example.com",
                        passwordEncoder.encode("admin123"));
                admin.setRoles(Set.of(adminRole));
                userRepository.save(admin);

                System.out.println(">>> Created default admin: username=admin, password=admin123");
            }
        };
    }
}