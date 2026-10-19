package vn.id.fitnessai.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.entity.Role;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.RoleRepository;
import vn.id.fitnessai.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class RoleInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createRoleIfNotExists("ROLE_USER");
        createRoleIfNotExists("ROLE_ADMIN");
        // Tự động sinh tài khoản Admin nếu trong DB chưa có
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@fitnessai.vn");
            admin.setPassword(passwordEncoder.encode("123456")); // Pass mặc định
            admin.setEnabled(true);
            // Lấy role ADMIN từ DB và gán cho tài khoản này
            Role adminRole = roleRepository.findByName("ROLE_ADMIN").get();
            admin.getRoles().add(adminRole);

            userRepository.save(admin);
        }
    }

    private void createRoleIfNotExists(String name) {
        if (roleRepository.findByName(name).isEmpty()) {
            Role role = new Role();
            role.setName(name);
            roleRepository.save(role);
        }
    }
}
