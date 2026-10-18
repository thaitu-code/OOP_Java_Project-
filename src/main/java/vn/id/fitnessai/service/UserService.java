package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.RegisterRequest;
import vn.id.fitnessai.entity.Role;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.RoleRepository;
import vn.id.fitnessai.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(RegisterRequest registerRequest){
        if(userRepository.existsByUsername(registerRequest.getUsername())){
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
        }
        if(userRepository.existsByEmail(registerRequest.getEmail())){
            throw new RuntimeException("Email đã được đăng ký");
        }
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        // Băm mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // Gán role mặc định là User
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow(()
                -> new RuntimeException("Không tìm thấy Role"));
        roles.add(userRole);
        user.setRoles(roles);
        return userRepository.save(user);
    }
}
