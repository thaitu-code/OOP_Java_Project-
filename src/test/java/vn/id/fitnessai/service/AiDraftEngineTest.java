package vn.id.fitnessai.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDraftEngineTest {
    private final AiAccess access=mock(AiAccess.class);
    private final UserProfileRepository profiles=mock(UserProfileRepository.class);
    private final UserProfileService profileService=mock(UserProfileService.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final UserProfile p=new UserProfile();
    private final jakarta.validation.ValidatorFactory factory=Validation.buildDefaultValidatorFactory();
    private final AiDraftEngine engine=new AiDraftEngine(access,profiles,profileService,mock(AiAssistantService.class),mock(AiPlanApplyService.class),
            mock(AiScheduleService.class),mock(NutritionService.class),mock(FoodRepository.class),mock(ExerciseRepository.class),
            mock(AiRecommendationRepository.class),mock(AiMealPlanItemRepository.class),mapper,factory.getValidator());
    @BeforeEach void setup(){
        var u=new User();u.setId(7L);when(access.require("tester")).thenReturn(u);when(profiles.findByUserId(7L)).thenReturn(Optional.of(p));
        p.setUser(u);p.setDisplayName("Người dùng");p.setBirthDate(LocalDate.now().minusYears(25));p.setGender(Gender.MALE);
        p.setHeightCm(new BigDecimal("170"));p.setWeightKg(new BigDecimal("68"));p.setGoal(FitnessGoal.LOSE_WEIGHT);p.setActivityLevel(ActivityLevel.MODERATE);
    }
    @AfterEach void close(){factory.close();}
    @Test void preparingCurrentWeightDoesNotChangeTheProfile(){
        var result=engine.prepare("tester",mapper.readTree("{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":65}}}"));
        assertTrue(result.preview().contains("Cân nặng hiện tại (kg): 68 → 65"));assertEquals(new BigDecimal("68"),p.getWeightKg());verifyNoInteractions(profileService);
    }
    @Test void targetWeightPreviewIsSeparateFromCurrentWeight(){
        var result=engine.prepare("tester",mapper.readTree("{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"targetWeightKg\":60}}}"));
        assertTrue(result.preview().contains("Cân nặng đích (kg)"));assertFalse(result.preview().contains("Cân nặng hiện tại"));assertNull(p.getTargetWeightKg());assertEquals(new BigDecimal("68"),p.getWeightKg());
    }
    @Test void accountFieldsAreNotWritableByTheModel(){
        assertThrows(IllegalArgumentException.class,()->engine.prepare("tester",mapper.readTree("{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"password\":\"anything\"}}}")));
        verifyNoInteractions(profileService);
    }
    @Test void profileVersionAndBusinessDateArePartOfTheFingerprint(){
        var payload=mapper.readTree("{\"changes\":{\"weightKg\":65}}");String before=engine.fingerprint("tester","PROFILE_UPDATE",payload);
        p.setVersion(2);assertNotEquals(before,engine.fingerprint("tester","PROFILE_UPDATE",payload));
    }
    @Test void previewRejectsInvalidWeightAndUnsupportedAge(){
        assertThrows(IllegalArgumentException.class,()->engine.prepare("tester",mapper.readTree("{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":1}}}")));
        p.setBirthDate(LocalDate.now().minusYears(14));assertThrows(IllegalArgumentException.class,()->engine.prepare("tester",mapper.readTree("{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":65}}}")));
    }
}
