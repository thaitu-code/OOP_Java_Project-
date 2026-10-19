package vn.id.fitnessai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import vn.id.fitnessai.service.CustomUserDetailsService;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    // Mã hóa mật khẩu Bcrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cấu hình AuthenticationProvider để kết nối đến db và Bcrypt
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // Cấu hình luồng truy cập và trang đăng nhập
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Ai cũng vào được trang đăng ký và file tĩnh
                        .requestMatchers(
                                "/api/auth/**",
                                "/register",
                                "/login",
                                "/forgot-password",
                                "/reset-password",
                                "/verify"
                        ).permitAll()
                        // Chỉ những tài khoản có ROLE_ADMIN mới vào được các link bắt đầu bằng /admin/
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Mọi trang khác bắt buộc phải đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login") // Đường dẫn (URL) trỏ tới trang login HTML của bạn
                        .loginProcessingUrl("/perform_login") // URL này Spring tự bắt khi form submit (không cần bạn viết Controller)
                        .defaultSuccessUrl("/home", true) // Nếu đăng nhập thành công, chuyển hướng tới /home
                        .failureUrl("/login?error=true") // Nếu sai mật khẩu, quay lại trang login kèm lỗi
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/perform_logout") // URL gọi khi muốn đăng xuất
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .authenticationProvider(authenticationProvider());

        return http.build();
    }
}
