package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.UserRepository;

@Component @RequiredArgsConstructor
public class AiAccess {
    private final UserRepository users;
    public User require(String username) {
        return check(users.findByUsername(username).orElseThrow(() -> new SecurityException("Không tìm thấy tài khoản")));
    }
    public User lock(String username) {
        return check(users.findByUsernameForUpdate(username).orElseThrow(() -> new SecurityException("Không tìm thấy tài khoản")));
    }
    private User check(User user) {
        if (!user.isEnabled() || user.isLocked()) throw new SecurityException("Tài khoản chưa xác minh hoặc đã bị khóa");
        return user;
    }
}
