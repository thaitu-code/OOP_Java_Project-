package vn.id.fitnessai.entity;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkoutAutomationMappingTest {
    @Test void buildsHibernateModelAndParsesTimeoutQueryWithoutDatabaseConnectionOrDdl() throws Exception {
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
            assertEquals("exercise_levels", metadata.getEntityBinding(ExerciseLevel.class.getName()).getTable().getName());
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                assertNotNull(factory.getMetamodel().entity(Exercise.class).getAttribute("levels"));
                var query = session.createSelectionQuery("select d.workout.workoutId from WorkoutDetail d where d.estimatedSecondsSnapshot is not null and d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.RESTING", Integer.class);
                assertNotNull(query, "Parse JPQL only; do not execute or connect to a database");
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
