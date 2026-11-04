package vn.id.fitnessai.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.id.fitnessai.enums.MealType;
import vn.id.fitnessai.enums.NutritionEntrySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NutritionDomainTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-04T10:00:00+07:00");

    private FoodCategory category() { return new FoodCategory("Thịt và cá", null, now); }

    private Food food(FoodCategory category) {
        return new Food(category, "Thực phẩm minh họa", null, "Số liệu giả kiểm thử",
                "https://cdn.example.test/food.jpg", bd("165"), bd("31"), bd("0"), bd("3.6"), now);
    }

    private NutritionLog log() { return new NutritionLog(new User(), now); }

    private LogDetail detail(Food food, String grams) {
        return new LogDetail(log(), food, MealType.LUNCH, grams == null ? null : bd(grams), null, NutritionEntrySource.USER, now);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    @Test
    void calculatesFourValuesAndPreservesCreationTimeAndSourceOnUpdate() {
        Food food = food(category());
        LogDetail detail = detail(food, "200");
        assertEquals(bd("330.00"), detail.getCalculatedCalories());
        assertEquals(bd("62.00"), detail.getCalculatedProtein());
        assertEquals(bd("0.00"), detail.getCalculatedCarbs());
        assertEquals(bd("7.20"), detail.getCalculatedFat());
        detail.update(food, MealType.DINNER, bd("150"), " Ghi qua chat ", NutritionEntrySource.AI, now.plusMinutes(1));
        assertEquals(bd("247.50"), detail.getCalculatedCalories());
        assertEquals(bd("46.50"), detail.getCalculatedProtein());
        assertEquals(bd("5.40"), detail.getCalculatedFat());
        assertEquals("Ghi qua chat", detail.getNote());
        assertEquals(now, detail.getLoggedAt());
        assertEquals(now.plusMinutes(1), detail.getUpdatedAt());
        assertEquals(NutritionEntrySource.USER, detail.getCreatedSource());
        assertEquals(NutritionEntrySource.AI, detail.getLastModifiedSource());
    }

    @Test
    void masterChangesAndDeactivationDoNotChangeSnapshotsOrPortionEdits() {
        FoodCategory category = category();
        Food food = food(category);
        LogDetail detail = detail(food, "200");
        category.update("Nhóm đã đổi tên", null, now.plusSeconds(1));
        food.update(category, "Tên mới", null, null, "https://cdn.example.test/new.png",
                bd("999"), bd("9"), bd("8"), bd("7"), now.plusSeconds(2));
        food.setActive(false, now.plusSeconds(3));
        category.setActive(false, now.plusSeconds(3));
        detail.update(food, MealType.BREAKFAST, bd("100"), "Sửa gram món cũ", NutritionEntrySource.USER, now.plusMinutes(1));
        assertEquals("Thực phẩm minh họa", detail.getFoodNameSnapshot());
        assertEquals("Thịt và cá", detail.getCategoryNameSnapshot());
        assertEquals("https://cdn.example.test/food.jpg", detail.getImageUrlSnapshot());
        assertEquals(bd("165.00"), detail.getCalculatedCalories());
        assertEquals(bd("31.00"), detail.getCalculatedProtein());
        assertEquals(bd("0.00"), detail.getCalculatedCarbs());
        assertEquals(bd("3.60"), detail.getCalculatedFat());
    }

    @Test
    void replacingFoodUsesAllNewSnapshotValues() {
        LogDetail detail = detail(food(category()), "100");
        Food replacement = new Food(new FoodCategory("Rau củ", null, now), "Món khác", null, null, null,
                bd("20"), bd("2"), bd("3"), bd("0.5"), now);
        detail.update(replacement, MealType.AFTERNOON, bd("50"), null, NutritionEntrySource.USER, now.plusSeconds(1));
        assertEquals("Món khác", detail.getFoodNameSnapshot());
        assertEquals("Rau củ", detail.getCategoryNameSnapshot());
        assertNull(detail.getImageUrlSnapshot());
        assertEquals(bd("10.00"), detail.getCalculatedCalories());
        assertEquals(bd("1.00"), detail.getCalculatedProtein());
        assertEquals(bd("1.50"), detail.getCalculatedCarbs());
        assertEquals(bd("0.25"), detail.getCalculatedFat());
    }

    @Test
    void anotherEntityInstanceOfSameFoodDoesNotRefreshSnapshot() {
        Food original = food(category());
        ReflectionTestUtils.setField(original, "foodId", 4);
        LogDetail detail = detail(original, "100");
        Food reloaded = new Food(category(), "Thông số mới", null, null, null,
                bd("900"), bd("80"), bd("70"), bd("60"), now);
        ReflectionTestUtils.setField(reloaded, "foodId", 4);
        reloaded.setActive(false, now);
        detail.update(reloaded, MealType.LUNCH, bd("200"), null, NutritionEntrySource.AI, now);
        assertEquals(bd("330.00"), detail.getCalculatedCalories());
        assertEquals("Thực phẩm minh họa", detail.getFoodNameSnapshot());
    }

    @ParameterizedTest
    @CsvSource({"0.50,1,0.01", "0.49,1,0.00", "165,150.25,247.91", "99999.99,99999.99,99999980.00"})
    void usesHalfUpAndSupportsFractionalAndMaximumRepresentableInputs(String calories, String grams, String expected) {
        Food food = new Food(category(), "Test", null, null, null,
                bd(calories), bd("0"), bd("0"), bd("0"), now);
        assertEquals(bd(expected), detail(food, grams).getCalculatedCalories());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100000", "1.234"})
    void rejectsInvalidGramsWithoutChangingExistingEntry(String grams) {
        Food food = food(category());
        LogDetail detail = detail(food, "100");
        assertThrows(IllegalArgumentException.class, () -> detail.update(food, MealType.DINNER, bd(grams),
                "Không được lưu", NutritionEntrySource.AI, now.plusSeconds(1)));
        assertEquals(MealType.LUNCH, detail.getMealType());
        assertEquals(bd("100.00"), detail.getWeightGrams());
        assertNull(detail.getNote());
        assertEquals(now, detail.getUpdatedAt());
        assertEquals(NutritionEntrySource.USER, detail.getLastModifiedSource());
    }

    @Test
    void normalizesNamesWithoutDroppingVietnameseAccents() {
        FoodCategory category = new FoodCategory("  RAU   CỦ  ", "  Mô tả  ", now);
        assertEquals("RAU CỦ", category.getCategoryName());
        assertEquals("rau củ", category.getCategoryNameKey());
        Food food = new Food(category, " CƠM  chín ", null, null, null,
                bd("0"), bd("0"), bd("0"), bd("0"), now);
        assertEquals("cơm chín", food.getFoodNameKey());
        assertNotEquals("com chin", food.getFoodNameKey());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\u00a0", "\u2003"})
    void rejectsMissingNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> new FoodCategory(name, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Food(category(), name, null, null, null,
                bd("0"), bd("0"), bd("0"), bd("0"), now));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "100000", "0.001"})
    void rejectsInvalidCaloriesWithoutPartiallyChangingFood(String value) {
        FoodCategory category = category();
        Food food = food(category);
        assertThrows(IllegalArgumentException.class, () -> food.update(category, "Không được đổi", null, null, null,
                bd(value), bd("1"), bd("1"), bd("1"), now.plusMinutes(1)));
        assertEquals("Thực phẩm minh họa", food.getFoodName());
        assertEquals(bd("165.00"), food.getCaloriesPer100g());
        assertEquals(now, food.getUpdatedAt());
    }

    @Test
    void rejectsMissingFieldsMacroOverflowAndOversizeTexts() {
        Food food = food(category());
        assertThrows(IllegalArgumentException.class, () -> detail(food, null));
        assertThrows(IllegalArgumentException.class, () -> new NutritionLog(null, now));
        assertThrows(IllegalArgumentException.class, () -> new FoodCategory("a".repeat(101), null, now));
        assertThrows(IllegalArgumentException.class, () -> food.update(category(), "Test", null, null, null,
                bd("1"), bd("10000"), bd("0"), bd("0"), now));
        LogDetail detail = detail(food, "100");
        assertThrows(IllegalArgumentException.class, () -> detail.update(food, null, bd("100"), null, NutritionEntrySource.USER, now));
        assertThrows(IllegalArgumentException.class, () -> detail.update(food, MealType.LUNCH, bd("100"), null, null, now));
        assertThrows(IllegalArgumentException.class, () -> detail.update(food, MealType.LUNCH, bd("100"), "a".repeat(501), NutritionEntrySource.USER, now));
        assertEquals(bd("165.00"), detail.getCalculatedCalories());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:image/png;base64,abc", "file:///photo.png", "//cdn.example.test/photo.png",
            "https://name:secret@example.test/photo.png", "https://cdn.example.test/image.svg", "https://cdn.example.test/movie.mp4"})
    void rejectsUnsupportedImageReferencesWithoutMakingNetworkCalls(String image) {
        assertThrows(IllegalArgumentException.class, () -> new Food(category(), "Test", null, null, image,
                bd("1"), bd("1"), bd("1"), bd("1"), now));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.test/FOOD.JPG?v=1#preview", "http://cdn.example.test/food.webp",
            "https://cdn.example.test/food.gif", "https://cdn.example.test/food.png"})
    void acceptsImageUrlsWithoutFetchingTheImage(String image) {
        Food food = new Food(category(), "Test", null, null, " " + image + " ",
                bd("1"), bd("1"), bd("1"), bd("1"), now);
        assertEquals(image, food.getImageUrl());
    }

    @Test
    void categoryDeactivationBlocksNewEntriesWithoutChangingIndividualFoodStatus() {
        FoodCategory category = category();
        Food food = food(category);
        category.setActive(false, now);
        assertTrue(food.isActive());
        for (NutritionEntrySource source : NutritionEntrySource.values()) {
            assertThrows(IllegalStateException.class, () -> new LogDetail(log(), food, MealType.LUNCH,
                    bd("100"), null, source, now));
        }
        assertThrows(IllegalStateException.class, () -> food(category));
        category.setActive(true, now);
        assertTrue(food.isAvailableForLogging());
        food.setActive(false, now);
        category.setActive(false, now);
        category.setActive(true, now);
        assertFalse(food.isAvailableForLogging());
    }

    @Test
    void rejectedReplacementDoesNotOverwriteThePreviousSnapshot() {
        Food original = food(category());
        LogDetail detail = detail(original, "100");
        Food unavailable = food(category());
        unavailable.setActive(false, now);
        assertThrows(IllegalStateException.class, () -> detail.update(unavailable, MealType.DINNER,
                bd("50"), "Sai", NutritionEntrySource.AI, now.plusMinutes(1)));
        assertSame(original, detail.getFood());
        assertEquals(bd("165.00"), detail.getCalculatedCalories());
        assertEquals(MealType.LUNCH, detail.getMealType());
    }

    @Test
    void derivesVietnameseDateFromInstantAndLocksAtMidnightForBothSources() {
        OffsetDateTime utc = OffsetDateTime.parse("2026-11-03T17:30:00Z");
        NutritionLog log = new NutritionLog(new User(), utc);
        assertEquals(java.time.LocalDate.of(2026, 11, 4), log.getLogDate());
        log.requireEditableAt(OffsetDateTime.parse("2026-11-04T16:59:59.999999Z"));
        OffsetDateTime midnight = OffsetDateTime.parse("2026-11-04T17:00:00Z");
        assertThrows(IllegalStateException.class, () -> log.requireEditableAt(midnight));
        for (NutritionEntrySource source : NutritionEntrySource.values()) {
            LogDetail detail = new LogDetail(log, food(category()), MealType.LUNCH, bd("100"), null, source, now);
            assertThrows(IllegalStateException.class, () -> detail.update(detail.getFood(), MealType.DINNER,
                    bd("50"), null, source, midnight));
            assertEquals(bd("100.00"), detail.getWeightGrams());
        }
    }

    @Test
    void rejectsMissingOrBackwardsServerTime() {
        Food food = food(category());
        LogDetail detail = detail(food, "100");
        assertThrows(IllegalArgumentException.class, () -> new NutritionLog(new User(), null));
        assertThrows(IllegalArgumentException.class, () -> detail.update(food, MealType.LUNCH,
                bd("50"), null, NutritionEntrySource.USER, now.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> food.setActive(false, now.minusSeconds(1)));
        assertTrue(food.isActive());
    }

    @Test
    void repeatedFoodIsASeparateEntryAndNewEntryUsesCurrentMasterValues() {
        FoodCategory category = category();
        Food food = food(category);
        LogDetail first = detail(food, "100");
        food.update(category, "Tên mới", null, null, null, bd("200"), bd("20"), bd("10"), bd("5"), now);
        LogDetail second = detail(food, "100");
        assertNotSame(first, second);
        assertEquals(bd("165.00"), first.getCalculatedCalories());
        assertEquals(bd("200.00"), second.getCalculatedCalories());
        assertEquals(bd("365.00"), first.getCalculatedCalories().add(second.getCalculatedCalories()));
    }
}
