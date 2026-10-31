import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.WorkoutRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;
import vn.id.fitnessai.service.WorkoutService;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.ExerciseRepository;

/** Opt-in read-path measurement with rollback. Prints counts/timings, never account data. */
public class ProfileWorkoutReads {
    public static void main(String[] args) throws Exception {
        var loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("ROOT").setLevel(ch.qos.logback.classic.Level.OFF);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) { properties.load(reader); }
        Properties credentials = new Properties();
        credentials.setProperty("user", DiagnoseWorkoutDatabase.setting(properties, "username"));
        credentials.setProperty("password", DiagnoseWorkoutDatabase.setting(properties, "password"));
        credentials.setProperty("connectTimeout", "8"); credentials.setProperty("socketTimeout", "8");
        String url = DiagnoseWorkoutDatabase.setting(properties, "url");
        var readOnly = new java.util.concurrent.atomic.AtomicBoolean(true);
        ConnectionProvider provider = new ConnectionProvider() {
            public Connection getConnection() throws SQLException { Connection c = DriverManager.getConnection(url, credentials); c.setReadOnly(readOnly.get()); return c; }
            public void closeConnection(Connection c) throws SQLException { c.close(); }
            public boolean supportsAggressiveRelease() { return false; }
            public boolean isUnwrappableAs(Class<?> type) { return false; }
            public <T> T unwrap(Class<T> type) { throw new UnsupportedOperationException(); }
        };
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.provider_class", provider)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.generate_statistics", "true")
                .applySetting("hibernate.show_sql", "false").build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            try (var files = Files.list(Path.of("src/main/java/vn/id/fitnessai/entity"))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Class<?> type = Class.forName("vn.id.fitnessai.entity." + file.getFileName().toString().replace(".java", ""));
                    if (type.isAnnotationPresent(jakarta.persistence.Entity.class)) sources.addAnnotatedClass(type);
                }
            }
            try (var factory = sources.buildMetadata().buildSessionFactory()) {
                for (int run = 1; run <= 2; run++) {
                    try (Session session = factory.openSession()) {
                        session.setDefaultReadOnly(true); var tx = session.beginTransaction();
                        try {
                            if (Arrays.asList(args).contains("--exercise-only")) {
                                var ids = session.createSelectionQuery("select e.exerciseId from Exercise e order by e.exerciseId", Integer.class)
                                        .setMaxResults(1).getResultList();
                                if (ids.isEmpty()) throw new IllegalStateException("No exercise available for read-only measurement");
                                session.clear(); factory.getStatistics().clear(); long begin = System.nanoTime();
                                session.find(Exercise.class, ids.get(0)).getCategory().getCategoryName();
                                print("exercise-detail-lazy-category", run, factory.getStatistics().getPrepareStatementCount(), begin);
                                session.clear(); factory.getStatistics().clear(); begin = System.nanoTime();
                                new JpaRepositoryFactory(session).getRepository(ExerciseRepository.class)
                                        .findById(ids.get(0)).orElseThrow().getCategory().getCategoryName();
                                long count = factory.getStatistics().getPrepareStatementCount();
                                print("exercise-detail-fetch-category", run, count, begin);
                                if (count != 1) throw new IllegalStateException("Exercise detail must load its category with one SQL");
                                continue;
                            }
                            String username = session.createSelectionQuery("select u.username from User u order by u.id", String.class).setMaxResults(1).getSingleResult();
                            factory.getStatistics().clear(); long begin = System.nanoTime();
                            User full = session.createSelectionQuery("from User u where u.username = :name", User.class).setParameter("name", username).getSingleResult();
                            full.getId(); full.isEnabled(); full.isLocked();
                            print("full-user", run, factory.getStatistics().getPrepareStatementCount(), begin);
                            session.clear(); factory.getStatistics().clear(); begin = System.nanoTime();
                            var users = new JpaRepositoryFactory(session).getRepository(UserRepository.class);
                            var access = users.findWorkoutAccessByUsername(username).orElseThrow();
                            access.getId(); access.getEnabled(); access.getLocked();
                            print("workout-access", run, factory.getStatistics().getPrepareStatementCount(), begin);
                            session.clear(); factory.getStatistics().clear(); begin = System.nanoTime();
                            for (Exercise exercise : session.createSelectionQuery("from Exercise", Exercise.class).getResultList()) exercise.getCategory().getCategoryName();
                            print("library-lazy-categories", run, factory.getStatistics().getPrepareStatementCount(), begin);
                            session.clear(); factory.getStatistics().clear(); begin = System.nanoTime();
                            for (Exercise exercise : new JpaRepositoryFactory(session).getRepository(ExerciseRepository.class).findAll()) exercise.getCategory().getCategoryName();
                            print("library-fetch-categories", run, factory.getStatistics().getPrepareStatementCount(), begin);
                            session.clear();
                            var activeUsers = session.createSelectionQuery("select w.user.username from Workout w where w.status = :status order by w.workoutId desc", String.class)
                                    .setParameter("status", WorkoutStatus.IN_PROGRESS).setMaxResults(1).getResultList();
                            if (!activeUsers.isEmpty()) {
                                String activeName = activeUsers.get(0);
                                factory.getStatistics().clear(); begin = System.nanoTime();
                                for (int read = 0; read < 3; read++) {
                                    session.clear();
                                    User account = session.createSelectionQuery("from User u where u.username = :name", User.class).setParameter("name", activeName).getSingleResult();
                                    Workout workout = session.createSelectionQuery("from Workout w where w.user.id = :id and w.status = :status", Workout.class)
                                            .setParameter("id", account.getId()).setParameter("status", WorkoutStatus.IN_PROGRESS).getSingleResult();
                                    var rows = session.createSelectionQuery("from WorkoutDetail d where d.workout.workoutId = :id order by d.detailId", WorkoutDetail.class)
                                            .setParameter("id", workout.getWorkoutId()).getResultList();
                                    rows.forEach(d -> d.getWorkout().getWorkoutId());
                                }
                                print("legacy-three-read-shape", run, factory.getStatistics().getPrepareStatementCount(), begin);
                                session.clear(); factory.getStatistics().clear(); begin = System.nanoTime();
                                var repos = new JpaRepositoryFactory(session);
                                try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                                    var service = new WorkoutService(repos.getRepository(WorkoutRepository.class), repos.getRepository(UserRepository.class),
                                            repos.getRepository(ExerciseRepository.class), repos.getRepository(WorkoutDetailRepository.class), validation.getValidator());
                                    service.getCurrentWorkoutState(activeName).orElseThrow();
                                }
                                print("combined-session-snapshot", run, factory.getStatistics().getPrepareStatementCount(), begin);
                            }
                        } finally { tx.rollback(); }
                    }
                }
                if (Arrays.asList(args).contains("--exercise-only")) return;
                // PostgreSQL cấm FOR UPDATE trong READ ONLY. Dùng một khóa ngẫu nhiên không tồn tại:
                // chỉ kiểm tra cú pháp/mapping repository, không khóa hoặc sửa tài khoản thực tế.
                readOnly.set(false);
                try (Session session = factory.openSession()) {
                    var tx = session.beginTransaction();
                    try {
                        var users = new JpaRepositoryFactory(session).getRepository(UserRepository.class);
                        if (users.findWorkoutAccessForUpdate("__perf_absent_" + UUID.randomUUID()).isPresent()) throw new IllegalStateException("Unexpected account collision");
                        System.out.println("native-account-lock-empty-key: PASS; no matching row; rolled back");
                    } finally { tx.rollback(); }
                }
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
    private static void print(String operation, int run, long count, long begin) {
        System.out.printf(Locale.ROOT, "%s run=%d SQL=%d elapsed=%.1fms%n", operation, run, count, (System.nanoTime() - begin) / 1_000_000.0);
    }
}
