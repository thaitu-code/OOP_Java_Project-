package vn.id.fitnessai.entity;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NutritionMappingTest {
    @Test
    void mapsNutritionWithExistingUserAndParsesScopedAggregateQueriesWithoutDatabaseOrDdl() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .build();
        try {
            var sources = new MetadataSources(registry);
            try (var files = Files.list(Path.of("src/main/java/vn/id/fitnessai/entity"))) {
                for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var type = Class.forName("vn.id.fitnessai.entity." + file.getFileName().toString().replace(".java", ""));
                    if (type.isAnnotationPresent(Entity.class)) sources.addAnnotatedClass(type);
                }
            }
            var metadata = sources.buildMetadata();
            assertEquals("food_categories", metadata.getEntityBinding(FoodCategory.class.getName()).getTable().getName());
            assertEquals("foods", metadata.getEntityBinding(Food.class.getName()).getTable().getName());
            assertEquals("daily_meal_logs", metadata.getEntityBinding(NutritionLog.class.getName()).getTable().getName());
            var logBinding = metadata.getEntityBinding(NutritionLog.class.getName());
            assertTrue(logBinding.getTable().getUniqueKeys().values().stream().anyMatch(key ->
                    key.getColumns().stream().map(org.hibernate.mapping.Column::getName).toList()
                            .equals(java.util.List.of("user_id", "log_date"))));
            var detailBinding = metadata.getEntityBinding(LogDetail.class.getName());
            assertTrue(detailBinding.isVersioned());
            assertEquals(2, detailBinding.getProperty("calculatedCalories").getColumns().get(0).getScale());
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                assertNotNull(session.createSelectionQuery("select d.mealType, sum(d.calculatedCalories), "
                        + "sum(d.calculatedProtein), sum(d.calculatedCarbs), sum(d.calculatedFat) "
                        + "from LogDetail d where d.nutritionLog.user.id = :userId "
                        + "and d.nutritionLog.logDate = :date group by d.mealType", Object[].class));
                assertNotNull(session.createSelectionQuery("select f from Food f "
                        + "where f.active = true and f.category.active = true and f.foodNameKey like :name", Food.class));
                assertNotNull(session.createSelectionQuery("select count(l) from NutritionLog l where l.user.id = :userId "
                        + "and exists (select d.detailId from LogDetail d where d.nutritionLog = l)", Long.class));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
