package vn.id.fitnessai.service;

import jakarta.persistence.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/** Disposable database only. Exercises real transactions, locking, and snapshot aggregates. */
class UiDataIntegrationTest {
    @Configuration @EnableTransactionManagement @EnableJpaRepositories("vn.id.fitnessai.repository")
    @Import({AiAccess.class,WorkoutService.class,WorkoutSelectionService.class,DashboardService.class,
            NutritionService.class,AiNotificationService.class,AiScheduleService.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:ui_data;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("vn.id.fitnessai.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.hbm2ddl.halt_on_error",true,
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean jakarta.validation.Validator validator(){return Validation.buildDefaultValidatorFactory().getValidator();}
        @Bean Clock nutritionClock(){return Clock.fixed(Instant.parse("2026-11-13T17:00:00Z"),NutritionLog.VN_ZONE);}
    }
    static AnnotationConfigApplicationContext app;static TransactionTemplate tx;
    @BeforeAll static void start(){app=new AnnotationConfigApplicationContext(Config.class);tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));}
    @AfterAll static void stop(){if(app!=null)app.close();}
    <T>T inTx(Supplier<T> work){return tx.execute(s->work.get());}
    String user(){String name="ui-"+UUID.randomUUID();inTx(()->{var u=new User();u.setUsername(name);u.setEmail(name+"@example.test");u.setPassword("test-only");u.setEnabled(true);return app.getBean(UserRepository.class).saveAndFlush(u);});return name;}
    User owner(String name){return app.getBean(UserRepository.class).findByUsername(name).orElseThrow();}
    Exercise exercise(){return inTx(()->{var c=app.getBean(ExerciseCategoryRepository.class).saveAndFlush(new ExerciseCategory("Category "+UUID.randomUUID()));return app.getBean(ExerciseRepository.class).saveAndFlush(new Exercise("Exercise",new BigDecimal("4"),BigDecimal.ONE,c));});}
    WorkoutDetailRequest item(Exercise e){var i=new WorkoutDetailRequest();i.setExerciseId(e.getExerciseId());return i;}
    WorkoutSelectionService service(){return app.getBean(WorkoutSelectionService.class);}

    @Test void concurrentAndLaterReplaysReturnOneSessionWithNoStartedExercises(){
        String name=user();var e=exercise();var request=new WorkoutSelectionRequest(UUID.randomUUID(),List.of(item(e)));
        var a=CompletableFuture.supplyAsync(()->service().create(name,request));var b=CompletableFuture.supplyAsync(()->service().create(name,request));
        var created=a.join();assertEquals(created.getWorkoutId(),b.join().getWorkoutId());assertEquals(created.getWorkoutId(),service().create(name,request).getWorkoutId());
        assertEquals(1,app.getBean(WorkoutRepository.class).countByUser_IdAndStatus(owner(name).getId(),WorkoutStatus.IN_PROGRESS));
        var details=inTx(()->app.getBean(WorkoutDetailRepository.class).findAllByWorkout_WorkoutIdOrderByDetailIdAsc(created.getWorkoutId()));
        assertEquals(1,details.size());assertEquals(WorkoutDetailStatus.NOT_STARTED,details.get(0).getStatus());assertNull(details.get(0).getStartedAt());
    }
    @Test void secondItemFailureRollsBackSessionFirstDetailAndReceipt(){
        String name=user();var first=item(exercise());var invalid=item(exercise());invalid.setLevelId(Integer.MAX_VALUE);
        UUID id=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->service().create(name,new WorkoutSelectionRequest(id,List.of(first,invalid))));
        assertEquals(0,app.getBean(WorkoutRepository.class).countByUser_IdAndStatus(owner(name).getId(),WorkoutStatus.IN_PROGRESS));
        assertTrue(app.getBean(WorkoutSelectionRepository.class).findByUser_IdAndRequestId(owner(name).getId(),id).isEmpty());
    }
    @Test void requestKeysAreBoundToContentAndAccount(){
        String one=user(),two=user();UUID id=UUID.randomUUID();var e=exercise();var request=new WorkoutSelectionRequest(id,List.of(item(e)));var a=service().create(one,request);var b=service().create(two,request);
        assertNotEquals(a.getWorkoutId(),b.getWorkoutId());
        assertThrows(IllegalStateException.class,()->service().create(one,new WorkoutSelectionRequest(id,List.of(item(exercise())))));
        inTx(()->{var u=owner(one);u.setLocked(true);return app.getBean(UserRepository.class).save(u);});
        assertThrows(SecurityException.class,()->service().create(one,request));assertThrows(SecurityException.class,()->app.getBean(DashboardService.class).summary(one));
    }
    @Test void duplicateAndUnpairedPlannedTargetsCannotCreateAnything(){
        String name=user();var i=item(exercise());
        assertThrows(IllegalArgumentException.class,()->service().create(name,new WorkoutSelectionRequest(UUID.randomUUID(),List.of(i,i))));
        i.setPlannedSets(3);assertThrows(IllegalArgumentException.class,()->service().create(name,new WorkoutSelectionRequest(UUID.randomUUID(),List.of(i))));
        assertFalse(app.getBean(WorkoutRepository.class).existsByUser_IdAndStatus(owner(name).getId(),WorkoutStatus.IN_PROGRESS));
    }
    @Test void selectedLevelAndManualTargetsArePreserved(){
        String name=user();var e=exercise();var level=inTx(()->app.getBean(ExerciseLevelRepository.class).saveAndFlush(new ExerciseLevel(app.getBean(ExerciseRepository.class).getReferenceById(e.getExerciseId()),"Easy",3,10,30,20,130)));
        var i=item(e);i.setLevelId(level.getId());i.setPlannedSets(3);i.setPlannedReps(10);
        var result=service().create(name,new WorkoutSelectionRequest(UUID.randomUUID(),List.of(i)));
        var d=app.getBean(WorkoutService.class).getWorkoutDetails(name,result.getWorkoutId()).orElseThrow().get(0);
        assertEquals("Easy",d.getAutomation().levelName());assertEquals(3,d.getPlannedSets());assertEquals(10,d.getPlannedReps());
    }
    @Test void dashboardUsesVietnamDaysAndRealSnapshotsWithoutCreatingLogs(){
        String name=user(),other=user();var e=exercise();LocalDate today=LocalDate.of(2026,11,14);
        inTx(()->{for(var entry:List.of(Map.entry(name,today.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime()),Map.entry(name,today.atStartOfDay(NutritionLog.VN_ZONE).minusNanos(1000).toOffsetDateTime()),Map.entry(other,today.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime()))){
            var w=app.getBean(WorkoutRepository.class).saveAndFlush(new Workout(owner(entry.getKey()),entry.getValue().minusMinutes(5)));
            w.setStatus(WorkoutStatus.COMPLETED);w.setEndTime(entry.getValue());
            var d=new WorkoutDetail(w,app.getBean(ExerciseRepository.class).getReferenceById(e.getExerciseId()));setField(d,"status",WorkoutDetailStatus.COMPLETED);setField(d,"completedAt",entry.getValue());setField(d,"caloriesBurned",new BigDecimal("25"));setField(d,"durationMinutes",new BigDecimal("5"));app.getBean(WorkoutDetailRepository.class).saveAndFlush(d);
        }
            var now=today.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime();
            var category=app.getBean(FoodCategoryRepository.class).saveAndFlush(new FoodCategory("Food "+UUID.randomUUID(),null,now));
            var food=app.getBean(FoodRepository.class).saveAndFlush(new Food(category,"Snapshot food",null,null,null,new BigDecimal("100"),new BigDecimal("10"),new BigDecimal("20"),new BigDecimal("5"),now));
            var log=app.getBean(NutritionLogRepository.class).saveAndFlush(new NutritionLog(owner(name),now));
            app.getBean(LogDetailRepository.class).saveAndFlush(new LogDetail(log,food,MealType.LUNCH,new BigDecimal("150"),null,NutritionEntrySource.USER,now));
            food.update(category,"Changed food",null,null,null,new BigDecimal("200"),new BigDecimal("20"),new BigDecimal("30"),new BigDecimal("10"),now.plusSeconds(1));
            return true;
        });
        long before=app.getBean(NutritionLogRepository.class).count();var summary=app.getBean(DashboardService.class).summary(name);
        assertEquals(today,summary.today());assertEquals(7,summary.days().size());assertEquals(0,new BigDecimal("25").compareTo(summary.days().get(6).burnedCalories()));assertEquals(0,new BigDecimal("25").compareTo(summary.days().get(5).burnedCalories()));
        assertNull(summary.targets());assertNull(summary.nextWorkout());assertEquals(before,app.getBean(NutritionLogRepository.class).count());
        assertEquals(0,new BigDecimal("150").compareTo(summary.days().get(6).nutrition().getCalories()));
        assertEquals(0,new BigDecimal("15").compareTo(summary.days().get(6).nutrition().getProtein()));
    }
    @Test void futureAndUnconfirmedPlansDoNotReplaceTodaysTargetsOrSchedule(){
        String name=user();LocalDate today=LocalDate.of(2026,11,14);var e=exercise();
        Long accepted=inTx(()->{
            var p=new UserProfile();p.setUser(owner(name));p.setDisplayName("Test profile");p.setGender(Gender.MALE);p=app.getBean(UserProfileRepository.class).saveAndFlush(p);
            var now=today.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime();
            var c=app.getBean(FoodCategoryRepository.class).saveAndFlush(new FoodCategory("Plan "+UUID.randomUUID(),null,now));
            var food=app.getBean(FoodRepository.class).saveAndFlush(new Food(c,"Planned food",null,null,null,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,now));
            Long id=null;
            for(int i=0;i<3;i++){
                var plan=new AiRecommendation();plan.setProfile(p);plan.setType("MEAL_PLAN");plan.setTitle("Plan "+i);plan.setContent("Test only");plan.setTargetCalories(BigDecimal.valueOf(1800+i*100));if(i!=2)plan.setSourceDraftId(UUID.randomUUID());plan=app.getBean(AiRecommendationRepository.class).saveAndFlush(plan);if(i==0)id=plan.getId();
                var item=new AiMealPlanItem();item.setRecommendation(plan);item.setFood(food);item.setPlanDate(i==1?today.plusDays(1):today);item.setMealType(MealType.LUNCH);item.setGrams(BigDecimal.TEN);item.setCalories(BigDecimal.ONE);item.setProtein(BigDecimal.ONE);item.setCarbs(BigDecimal.ONE);item.setFat(BigDecimal.ONE);app.getBean(AiMealPlanItemRepository.class).saveAndFlush(item);
            }
            var future=new AiRecommendation();future.setProfile(p);future.setType("WORKOUT_PLAN");future.setTitle("Tomorrow");future.setContent("Test only");future.setStatus("SCHEDULED");future.setWorkoutStartDate(today.plusDays(1));future=app.getBean(AiRecommendationRepository.class).saveAndFlush(future);
            var entry=new AiRecommendedExercise();entry.setRecommendation(future);entry.setExercise(app.getBean(ExerciseRepository.class).getReferenceById(e.getExerciseId()));entry.setDayOfWeek(today.plusDays(1).getDayOfWeek().getValue());entry.setPosition(1);app.getBean(AiRecommendedExerciseRepository.class).saveAndFlush(entry);return id;
        });
        var result=app.getBean(DashboardService.class).summary(name);assertEquals(accepted,result.targets().planId());assertEquals(0,new BigDecimal("1800").compareTo(result.targets().calories()));assertEquals(today.plusDays(1),result.nextWorkout().date());
        assertTrue(app.getBean(AiScheduleService.class).effective(owner(name).getId(),today).isEmpty());assertNull(result.currentWorkout());
    }
}
