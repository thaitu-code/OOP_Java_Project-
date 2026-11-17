package vn.id.fitnessai.service;

import jakarta.persistence.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real Spring/JPA transactions on disposable H2. Never loads application.properties or production services/jobs. */
class AiConversationIntegrationTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="vn.id.fitnessai.repository")
    @Import({AiAccess.class,AiAssistantService.class,AiScheduleService.class,AiPlanApplyService.class,AiNotificationService.class,
            NutritionService.class,WorkoutService.class,UserProfileService.class,BodyMetricsCalculator.class,CheckInStreakService.class,
            BadgeService.class,AiContextService.class,AiDraftEngine.class,AiConversationStore.class,AiDraftService.class,AiConversationService.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:ai_conversation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("vn.id.fitnessai.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.hbm2ddl.halt_on_error",true,
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean jakarta.validation.Validator validator(){return Validation.buildDefaultValidatorFactory().getValidator();}
        @Bean Clock nutritionClock(){return Clock.system(NutritionLog.VN_ZONE);}
        @Bean GeminiAiClient gemini(){return mock(GeminiAiClient.class);}
        @Bean JavaMailSender mail(){return mock(JavaMailSender.class);}
    }
    private static AnnotationConfigApplicationContext app;
    private static TransactionTemplate tx;
    @BeforeAll static void start(){app=new AnnotationConfigApplicationContext(Config.class);tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));}
    @AfterAll static void stop(){if(app!=null)app.close();}
    @BeforeEach void resetProvider(){reset(app.getBean(GeminiAiClient.class));}
    private <T> T inTx(Supplier<T> call){return tx.execute(s->call.get());}
    private String user(){
        String name="test-"+UUID.randomUUID();
        inTx(()->{var u=new User();u.setUsername(name);u.setEmail(name+"@example.test");u.setPassword("TEST_ONLY_DO_NOT_SEND");u.setEnabled(true);app.getBean(UserRepository.class).saveAndFlush(u);
            var p=new UserProfile();p.setUser(u);p.setDisplayName("PRIVATE_DISPLAY_NAME");p.setBirthDate(LocalDate.now().minusYears(25));p.setGender(Gender.MALE);
            p.setHeightCm(new BigDecimal("170"));p.setWeightKg(new BigDecimal("68"));p.setGoal(FitnessGoal.LOSE_WEIGHT);p.setActivityLevel(ActivityLevel.MODERATE);
            p.setCalculatedTdee(new BigDecimal("2200"));p.setTrainingPreferences("Mới bắt đầu, ở nhà có tạ, 30 phút, không có hạn chế được khai báo");
            u.setProfile(p);return app.getBean(UserProfileRepository.class).saveAndFlush(p);});return name;
    }
    private UserProfile profile(String name){return inTx(()->app.getBean(UserProfileRepository.class).findByUserUsername(name).orElseThrow());}
    private void answer(String raw){doAnswer(a->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Provider must not hold a DB transaction");return raw;}).when(app.getBean(GeminiAiClient.class)).generateJson(anyString(),anyList());}
    private AiConversationStore.Reply weight(String name, UUID request){
        answer("{\"type\":\"PROPOSE\",\"reply\":\"Xem cập nhật cân nặng\",\"actions\":[{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":65}}}]}");
        return app.getBean(AiConversationService.class).send(name,"session123","Hôm nay tôi vừa cân được 65 kg",request);
    }
    @Test void previewDoesNotWriteAndConcurrentConfirmationsCommitOnlyOnce(){
        String name=user();UUID request=UUID.randomUUID();var reply=weight(name,request);assertEquals("COMPLETE",reply.status(),reply.error());
        assertEquals(0,new BigDecimal("68").compareTo(profile(name).getWeightKg()));assertEquals(1,reply.proposals().size());UUID id=reply.proposals().get(0).id();
        var service=app.getBean(AiDraftService.class);
        var first=CompletableFuture.supplyAsync(()->service.decide(name,id,true));var second=CompletableFuture.supplyAsync(()->service.decide(name,id,true));
        assertEquals("CONFIRMED",first.join().proposal().status());assertEquals("CONFIRMED",second.join().proposal().status());
        assertEquals(0,new BigDecimal("65").compareTo(profile(name).getWeightKg()));assertEquals(1,profile(name).getVersion());
        var replay=app.getBean(AiConversationService.class).send(name,"session123","Hôm nay tôi vừa cân được 65 kg",request);
        assertEquals(id,replay.proposals().get(0).id());verify(app.getBean(GeminiAiClient.class),times(1)).generateJson(anyString(),anyList());
    }
    @Test void stalePreviewCannotOverwriteACommittedProfileChange(){
        String name=user();var reply=weight(name,UUID.randomUUID());
        inTx(()->{var p=app.getBean(UserProfileRepository.class).findByUserUsername(name).orElseThrow();p.setWeightKg(new BigDecimal("70"));return p;});
        assertEquals("STALE",app.getBean(AiDraftService.class).decide(name,reply.proposals().get(0).id(),true).proposal().status());
        assertEquals(0,new BigDecimal("70").compareTo(profile(name).getWeightKg()));
    }
    @Test void cancellationAndCrossUserRequestsDoNotChangeProfile(){
        String name=user(),other=user();var reply=weight(name,UUID.randomUUID());UUID id=reply.proposals().get(0).id();
        assertThrows(SecurityException.class,()->app.getBean(AiDraftService.class).decide(other,id,true));
        assertEquals("CANCELLED",app.getBean(AiDraftService.class).decide(name,id,false).proposal().status());
        assertEquals(0,new BigDecimal("68").compareTo(profile(name).getWeightKg()));
    }
    @Test void providerFailureCanRetryTheSameTurnWithoutDuplicatingMessages(){
        String name=user();UUID request=UUID.randomUUID();var service=app.getBean(AiConversationService.class);
        when(app.getBean(GeminiAiClient.class).generateJson(anyString(),anyList())).thenThrow(new IllegalStateException("Timeout"));
        assertEquals("FAILED",service.send(name,"session123","TDEE là gì?",request).status());
        answer("{\"type\":\"ANSWER\",\"reply\":\"TDEE là năng lượng ước tính.\",\"actions\":[]}");
        var reply=service.send(name,"session123","TDEE là gì?",request);assertEquals("COMPLETE",reply.status(),reply.error());
        long count=inTx(()->app.getBean(AiChatMessageRepository.class).findByUser_IdAndTurnIdOrderByIdAsc(profile(name).getUser().getId(),reply.userMessage().turnId()).size());assertEquals(2,count);
    }
    @Test void userMessageIsCommittedAndReloadableWhileTheProviderIsStillWorking() throws Exception {
        String name=user();UUID request=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(app.getBean(GeminiAiClient.class).generateJson(anyString(),anyList())).thenAnswer(a->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test timed out");
            return "{\"type\":\"ANSWER\",\"reply\":\"Đã nhận được câu hỏi.\",\"actions\":[]}";
        });
        var sent=CompletableFuture.supplyAsync(()->app.getBean(AiConversationService.class).send(name,"session123","TDEE là gì?",request));
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var saved=app.getBean(AiAssistantService.class).history(name,"session123",null);
            assertEquals(1,saved.messages().size());assertEquals("PENDING",saved.messages().get(0).status());
            assertEquals("TDEE là gì?",saved.messages().get(0).content());
            var pending=app.getBean(AiConversationStore.class).activeTurn(name,"session123");
            assertEquals(request,pending.requestId());assertEquals("PENDING",pending.status());
            assertNull(app.getBean(AiConversationStore.class).activeTurn(name,"other-session"));
        } finally { release.countDown(); }
        assertEquals("COMPLETE",sent.get(10,TimeUnit.SECONDS).status());
        assertEquals(2,app.getBean(AiAssistantService.class).history(name,"session123",null).messages().size());
        assertNull(app.getBean(AiConversationStore.class).activeTurn(name,"session123"));
    }
    @Test void sessionHistoryPaginatesWithoutMixingLoginsAndKeepsMoreThanTenTurnsOfContext() {
        String name=user(),other=user();
        inTx(()->{
            var owner=app.getBean(UserRepository.class).findByUsername(name).orElseThrow();
            var stranger=app.getBean(UserRepository.class).findByUsername(other).orElseThrow();
            var repository=app.getBean(AiChatMessageRepository.class);
            for(int i=0;i<35;i++) {
                UUID turn=UUID.randomUUID();
                for(String sender:List.of("USER","AI")) {
                    var m=new AiChatMessage();m.setUser(owner);m.setSessionId("session123");m.setTurnId(turn);m.setSender(sender);m.setContent(sender+" lượt "+i);repository.save(m);
                }
            }
            for(var u:List.of(owner,stranger)) {
                var m=new AiChatMessage();m.setUser(u);m.setSessionId(u==owner?"previous123":"session123");m.setTurnId(UUID.randomUUID());m.setSender("USER");m.setContent("PRIVATE_OTHER_LOGIN");repository.save(m);
            }
            var failed=new AiChatMessage();failed.setUser(owner);failed.setSessionId("session123");failed.setTurnId(UUID.randomUUID());failed.setSender("USER");failed.setContent("FAILED_QUESTION");failed.setStatus("FAILED");repository.save(failed);
            return null;
        });
        var service=app.getBean(AiAssistantService.class);
        var first=service.history(name,"session123",null);assertEquals(50,first.messages().size());assertNotNull(first.nextCursor());
        var older=service.history(name,"session123",first.nextCursor());assertEquals(21,older.messages().size());assertNull(older.nextCursor());
        assertEquals("USER lượt 0",older.messages().get(0).content());
        assertFalse(first.toString().contains("PRIVATE_OTHER_LOGIN"));assertFalse(older.toString().contains("PRIVATE_OTHER_LOGIN"));
        var context=app.getBean(AiConversationStore.class).history(name,"session123",UUID.randomUUID());
        assertEquals(70,context.size());assertEquals("USER lượt 0",context.get(0).get("text"));
        assertFalse(context.toString().contains("FAILED_QUESTION"));assertFalse(context.toString().contains("PRIVATE_OTHER_LOGIN"));
    }
    @Test void deletingAFailedMessageDoesNotResurrectItAsAnActiveTurnOnReload() {
        String name=user();when(app.getBean(GeminiAiClient.class).generateJson(anyString(),anyList())).thenThrow(new IllegalStateException("Timeout"));
        var failed=app.getBean(AiConversationService.class).send(name,"session123","TDEE là gì?",UUID.randomUUID());
        assertEquals("FAILED",failed.status());assertNotNull(app.getBean(AiConversationStore.class).activeTurn(name,"session123"));
        app.getBean(AiAssistantService.class).delete(name,failed.userMessage().id());
        assertNull(app.getBean(AiConversationStore.class).activeTurn(name,"session123"));
        assertTrue(app.getBean(AiAssistantService.class).history(name,"session123",null).messages().isEmpty());
    }
    @Test void contextDoesNotSendAccountIdentityOrSecretsToProvider(){
        String name=user();answer("{\"type\":\"ANSWER\",\"reply\":\"Bạn chưa ghi nhật ký hôm nay.\",\"actions\":[]}");
        var reply=app.getBean(AiConversationService.class).send(name,"session123","Hôm nay tôi đã ăn gì?",UUID.randomUUID());assertEquals("COMPLETE",reply.status(),reply.error());
        verify(app.getBean(GeminiAiClient.class)).generateJson(anyString(),argThat(list->{String prompt=list.toString();return !prompt.contains(name)&&!prompt.contains("PRIVATE_DISPLAY_NAME")&&!prompt.contains("TEST_ONLY_DO_NOT_SEND")&&!prompt.contains("timerToken");}));
    }
    @Test void aChangeWhileProviderRunsRejectsTheOldSnapshot(){
        String name=user();
        when(app.getBean(GeminiAiClient.class).generateJson(anyString(),anyList())).thenAnswer(a->{
            inTx(()->{var p=app.getBean(UserProfileRepository.class).findByUserUsername(name).orElseThrow();p.setWeightKg(new BigDecimal("71"));return p;});
            return "{\"type\":\"PROPOSE\",\"reply\":\"Xem thay đổi\",\"actions\":[{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":65}}}]}";
        });
        var reply=app.getBean(AiConversationService.class).send(name,"session123","Tôi vừa cân được 65 kg",UUID.randomUUID());assertEquals("FAILED",reply.status());assertTrue(reply.proposals().isEmpty());assertEquals(0,new BigDecimal("71").compareTo(profile(name).getWeightKg()));
    }

    @Test void acceptedWorkoutRepeatsAndDoesNotStartAWorkoutUntilRequested(){
        String name=user();LocalDate today=LocalDate.now(NutritionLog.VN_ZONE);int day=today.getDayOfWeek().getValue();
        Integer id=inTx(()->{var category=new ExerciseCategory("Fixture "+UUID.randomUUID());app.getBean(ExerciseCategoryRepository.class).save(category);
            var exercise=new Exercise("Đi bộ fixture",new BigDecimal("3"),new BigDecimal("5"),category);return app.getBean(ExerciseRepository.class).saveAndFlush(exercise).getExerciseId();});
        List<Integer> rest=new ArrayList<>();for(int i=1;i<=7;i++)if(i!=day)rest.add(i);
        String payload=app.getBean(ObjectMapper.class).writeValueAsString(Map.of("title","Lịch fixture","content","Theo điều kiện đã cung cấp","startDate",today.toString(),
                "exercises",List.of(Map.of("exerciseId",id,"dayOfWeek",day,"sets",3,"reps",10)),"restDays",rest));
        answer("{\"type\":\"PROPOSE\",\"reply\":\"Xem trước lịch\",\"actions\":[{\"kind\":\"WORKOUT_PLAN\",\"payload\":"+payload+"}]}");
        var reply=app.getBean(AiConversationService.class).send(name,"session123","Lập lịch tuần",UUID.randomUUID());assertEquals("COMPLETE",reply.status(),reply.error());
        var result=app.getBean(AiDraftService.class).decide(name,reply.proposals().get(0).id(),true);assertEquals("CONFIRMED",result.proposal().status());
        Long userId=profile(name).getUser().getId(),planId=result.proposal().recommendationId();
        assertEquals(planId,app.getBean(AiScheduleService.class).effective(userId,today.plusWeeks(3)).orElseThrow().getId());
        assertTrue(app.getBean(WorkoutService.class).getCurrentWorkout(name).isEmpty());
        var added=app.getBean(AiPlanApplyService.class).startTodayWorkout(name,planId);assertEquals(1,added.get("count"));
        var current=app.getBean(WorkoutService.class).getCurrentWorkout(name).orElseThrow();
        assertNull(app.getBean(WorkoutService.class).getWorkoutDetails(name,current.getWorkoutId()).orElseThrow().get(0).getStartedAt());
    }

    @Test void mealCorrectionUsesTheSameServiceAndResolvesTheExistingCalorieAlert(){
        String name=user();
        Integer detailId=inTx(()->{
            var user=app.getBean(UserRepository.class).findByUsername(name).orElseThrow();var now=OffsetDateTime.now(NutritionLog.VN_ZONE);
            var category=app.getBean(FoodCategoryRepository.class).save(new FoodCategory("Fixture "+UUID.randomUUID(),null,now));
            var food=app.getBean(FoodRepository.class).save(new Food(category,"Cơm "+UUID.randomUUID(),null,null,null,new BigDecimal("100"),new BigDecimal("3"),new BigDecimal("20"),new BigDecimal("1"),now));
            var log=app.getBean(NutritionLogRepository.class).save(new NutritionLog(user,now));
            return app.getBean(LogDetailRepository.class).saveAndFlush(new LogDetail(log,food,MealType.LUNCH,new BigDecimal("2300"),null,NutritionEntrySource.USER,now)).getDetailId();
        });
        var notify=app.getBean(AiNotificationService.class);var first=notify.refresh(name);assertEquals(1,first.stream().filter(n->n.type().equals("CALORIE_ALERT")).count());
        var before=app.getBean(NutritionService.class).getDailyLog(name,null).getMeals().stream().flatMap(m->m.getItems().stream()).filter(i->i.getDetailId().equals(detailId)).findFirst().orElseThrow();
        String payload=app.getBean(ObjectMapper.class).writeValueAsString(Map.of("detailId",detailId,"expectedVersion",before.getVersion(),"foodId",before.getFoodId(),"mealType","LUNCH","grams",100));
        answer("{\"type\":\"PROPOSE\",\"reply\":\"Xem sửa khối lượng\",\"actions\":[{\"kind\":\"MEAL_UPDATE\",\"payload\":"+payload+"}]}");
        var reply=app.getBean(AiConversationService.class).send(name,"session123","Tôi ăn 100 g, sửa món vừa ghi",UUID.randomUUID());assertEquals("COMPLETE",reply.status(),reply.error());
        assertEquals("CONFIRMED",app.getBean(AiDraftService.class).decide(name,reply.proposals().get(0).id(),true).proposal().status());
        var alerts=notify.refresh(name).stream().filter(n->n.type().equals("CALORIE_ALERT")).toList();assertEquals(1,alerts.size());assertFalse(alerts.get(0).applicable());assertFalse(alerts.get(0).read());
        assertEquals(first.get(0).id(),alerts.get(0).id());
        assertEquals(NutritionEntrySource.AI,app.getBean(NutritionService.class).getDailyLog(name,null).getMeals().stream().flatMap(m->m.getItems().stream()).findFirst().orElseThrow().getLastModifiedSource());
    }

    @Test void mealDisplayImagesDoNotEnterProviderContext(){
        String name=user(),other=user();String image="https://example.test/private-meal-image.jpg";
        Integer foodId=inTx(()->{
            var now=OffsetDateTime.now(NutritionLog.VN_ZONE);
            var category=app.getBean(FoodCategoryRepository.class).save(new FoodCategory("Image "+UUID.randomUUID(),null,now));
            return app.getBean(FoodRepository.class).saveAndFlush(new Food(category,"Image food",null,null,image,
                    new BigDecimal("100"),new BigDecimal("3"),new BigDecimal("20"),BigDecimal.ONE,now)).getFoodId();
        });
        var plans=app.getBean(AiAssistantService.class);
        var plan=plans.create(name,new AiAssistantService.PlanRequest("MEAL_PLAN","Display test","Test only",null,null,
                new BigDecimal("2200"),List.of(new AiAssistantService.MealItem(foodId,LocalDate.now(NutritionLog.VN_ZONE),MealType.LUNCH,new BigDecimal("150"))),null,null));
        Long id=plan.recommendation().id();
        inTx(()->{var rec=app.getBean(AiRecommendationRepository.class).findById(id).orElseThrow();rec.setSourceDraftId(UUID.randomUUID());return rec;});
        var display=plans.detailForDisplay(name,id);assertEquals(true,display.get("confirmed"));assertTrue(display.toString().contains(image));
        Long itemId=(Long)((Map<?,?>)((List<?>)display.get("meals")).get(0)).get("id");
        inTx(()->{
            var now=OffsetDateTime.now(NutritionLog.VN_ZONE);var log=app.getBean(NutritionLogRepository.class).save(new NutritionLog(app.getBean(UserRepository.class).findByUsername(name).orElseThrow(),now));
            var entry=new LogDetail(log,app.getBean(FoodRepository.class).getReferenceById(foodId),MealType.LUNCH,new BigDecimal("75"),null,NutritionEntrySource.AI,now);
            entry.linkAiMealPlanItem(itemId);return app.getBean(LogDetailRepository.class).saveAndFlush(entry);
        });
        var loggedRow=(Map<?,?>)((List<?>)plans.detailForDisplay(name,id).get("meals")).get(0);
        assertEquals(0,new BigDecimal("75").compareTo((BigDecimal)loggedRow.get("loggedGrams")));
        assertEquals(0,new BigDecimal("75").compareTo((BigDecimal)loggedRow.get("loggedCalories")));
        assertFalse(plans.detail(name,id).toString().contains("loggedGrams"));
        assertFalse(plans.detail(name,id).toString().contains(image));assertThrows(SecurityException.class,()->plans.detailForDisplay(other,id));
        answer("{\"type\":\"ANSWER\",\"reply\":\"Bạn có một thực đơn đã lưu.\",\"actions\":[]}");
        app.getBean(AiConversationService.class).send(name,"session123","Tôi có thực đơn nào?",UUID.randomUUID());
        verify(app.getBean(GeminiAiClient.class)).generateJson(anyString(),argThat(input->!input.toString().contains(image)));
    }

    @Test void acceptedRestDayPreservesStreakWithoutCreatingACheckIn(){
        String name=user();LocalDate today=LocalDate.now(NutritionLog.VN_ZONE),yesterday=today.minusDays(1);
        inTx(()->{
            var user=app.getBean(UserRepository.class).findByUsername(name).orElseThrow();var p=app.getBean(UserProfileRepository.class).findByUserUsername(name).orElseThrow();
            var plan=new AiRecommendation();plan.setProfile(p);plan.setType("WORKOUT_PLAN");plan.setTitle("Rest fixture");plan.setContent("Accepted rest fixture");plan.setStatus("APPLIED");plan.setWorkoutStartDate(yesterday);plan.setRestDays(String.valueOf(yesterday.getDayOfWeek().getValue()));app.getBean(AiRecommendationRepository.class).save(plan);
            var streak=new UserStreak(user);streak.setCurrentStreak(6);streak.setHighestStreak(6);streak.setLastFinalizedDate(yesterday.minusDays(1));app.getBean(UserStreakRepository.class).save(streak);return null;
        });
        var summary=app.getBean(CheckInStreakService.class).getSummary(name);assertEquals(6,summary.currentStreak());assertFalse(summary.checkedInToday());
        assertTrue(inTx(()->app.getBean(DailyFitnessRecordRepository.class).findByUser_IdAndActivityDate(profile(name).getUser().getId(),yesterday).orElseThrow().isRestDayValid()));
    }
    @Test void lockedAccountCannotConfirmAnEarlierPreview(){
        String name=user();var reply=weight(name,UUID.randomUUID());
        inTx(()->{var u=app.getBean(UserRepository.class).findByUsername(name).orElseThrow();u.setLocked(true);return null;});
        assertThrows(SecurityException.class,()->app.getBean(AiDraftService.class).decide(name,reply.proposals().get(0).id(),true));
        assertEquals(0,new BigDecimal("68").compareTo(profile(name).getWeightKg()));
    }
    @Test void requestIdCannotBeReusedForDifferentContent(){
        String name=user();UUID request=UUID.randomUUID();weight(name,request);
        assertThrows(IllegalArgumentException.class,()->app.getBean(AiConversationService.class).send(name,"session123","Nội dung khác",request));
        verify(app.getBean(GeminiAiClient.class),times(1)).generateJson(anyString(),anyList());
    }
}
