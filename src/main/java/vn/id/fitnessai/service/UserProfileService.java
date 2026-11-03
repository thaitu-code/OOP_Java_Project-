package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.ProfileUpdateRequest;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.UserProfile;
import vn.id.fitnessai.repository.UserProfileRepository;
import vn.id.fitnessai.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final BodyMetricsCalculator bodyMetricsCalculator;

    @Transactional
    public UserProfileResponse updateUserProfile(String username, ProfileUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + username));

        int age = bodyMetricsCalculator.calculateAge(request.getBirthDate());
        if (age < 15 || age > 65) {
            throw new IllegalArgumentException("Hệ thống chỉ hỗ trợ độ tuổi từ 15 đến 65 tuổi. Tuổi hiện tại: " + age);
        }
        BigDecimal bmi = bodyMetricsCalculator.calculateBmi(request.getWeightKg(), request.getHeightCm());

        BigDecimal bmr = bodyMetricsCalculator.calculateBmr(
                request.getWeightKg(),
                request.getHeightCm(),
                age,
                request.getGender()
        );
        BigDecimal tdee = bodyMetricsCalculator.calculateTdee(bmr, request.getActivityLevel());

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            user.setProfile(profile);
        }

        profile.setDisplayName(request.getDisplayName().trim());
        profile.setGender(request.getGender());
        profile.setBirthDate(request.getBirthDate());
        profile.setHeightCm(request.getHeightCm());
        profile.setWeightKg(request.getWeightKg());
        profile.setActivityLevel(request.getActivityLevel());
        profile.setGoal(request.getGoal());
        profile.setCalculatedBmi(bmi);
        profile.setCalculatedBmr(bmr);
        profile.setCalculatedTdee(tdee);
        profile.setProfileCalculatedAt(LocalDateTime.now(BodyMetricsCalculator.ZONE_VN));

        userProfileRepository.save(profile);

        return mapToProfileResponse(user.getUsername(), profile, age);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + username));

        UserProfile profile = user.getProfile();
        if (profile == null) {
            return UserProfileResponse.builder()
                    .username(user.getUsername())
                    .build();
        }

        Integer age = profile.getBirthDate() != null
                ? bodyMetricsCalculator.calculateAge(profile.getBirthDate())
                : null;

        return mapToProfileResponse(user.getUsername(), profile, age);
    }

    private UserProfileResponse mapToProfileResponse(String username, UserProfile profile, Integer age) {
        return UserProfileResponse.builder()
                .username(username)
                .displayName(profile.getDisplayName())
                .gender(profile.getGender())
                .birthDate(profile.getBirthDate())
                .age(age)
                .heightCm(profile.getHeightCm())
                .weightKg(profile.getWeightKg())
                .activityLevel(profile.getActivityLevel())
                .goal(profile.getGoal())
                .calculatedBmi(profile.getCalculatedBmi())
                .calculatedBmr(profile.getCalculatedBmr())
                .calculatedTdee(profile.getCalculatedTdee())
                .profileCalculatedAt(profile.getProfileCalculatedAt())
                .build();
    }
}