package vn.id.fitnessai.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor public class AiAssistantService {
 private final UserRepository users; private final UserProfileRepository profiles;
 private final AiChatMessageRepository chats; private final AiRecommendationRepository recommendations;
 private final AiMealPlanItemRepository mealItems; private final AiRecommendedExerciseRepository exerciseItems;
 private final FoodRepository foods; private final ExerciseRepository exercises; private final GeminiAiClient gemini;
 private static final int MAX_TEXT=3000;
 private User own(String username){User u=users.findByUsername(username).orElseThrow(()->new IllegalArgumentException("Tài khoản không tồn tại"));if(!u.isEnabled()||u.isLocked())throw new SecurityException("Tài khoản không khả dụng");return u;}
 private UserProfile profile(User u){return profiles.findByUserId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cần cập nhật hồ sơ trước khi tạo khuyến nghị"));}
 public record Message(Long id,String sessionId,UUID turnId,String sender,String content,String status,OffsetDateTime createdAt) {}
 private Message map(AiChatMessage m){return new Message(m.getId(),m.getSessionId(),m.getTurnId(),m.getSender(),m.getContent(),m.getStatus(),m.getCreatedAt());}
 public record ChatPage(List<Message> messages,Long nextCursor) {}
 @Transactional(readOnly=true) public ChatPage history(String username,Long before){User u=own(username);long cursor=before==null?Long.MAX_VALUE:before;List<AiChatMessage> list=chats.findByUser_IdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(u.getId(),cursor,PageRequest.of(0,11));boolean more=list.size()>10;List<AiChatMessage> slice=list.subList(0,Math.min(10,list.size()));Long next=more?slice.get(slice.size()-1).getId():null;List<Message> out=new ArrayList<>(slice.stream().map(this::map).toList());Collections.reverse(out);return new ChatPage(out,next);}
 @Transactional public void delete(String username,Long id){User u=own(username);AiChatMessage m=chats.findById(id).orElseThrow(()->new NoSuchElementException("Không tìm thấy tin"));if(!m.getUser().getId().equals(u.getId()))throw new SecurityException("Không được xóa tin người khác");m.setDeletedAt(OffsetDateTime.now());}
 @Transactional public Message begin(String username,String session,String text,UUID turnId){User u=own(username);if(session==null||!session.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Phiên chat không hợp lệ");if(text==null||text.isBlank()||text.length()>MAX_TEXT)throw new IllegalArgumentException("Tin nhắn phải có 1-3000 ký tự");AiChatMessage m=new AiChatMessage();m.setUser(u);m.setSessionId(session);m.setTurnId(turnId);m.setSender("USER");m.setContent(text.trim());m.setStatus("PENDING");return map(chats.save(m));}
 public record ChatResult(Message userMessage,Message aiMessage,String error) {}
 public ChatResult send(String username,String session,String text){UUID turn=UUID.randomUUID();Message user=begin(username,session,text,turn);try{
  User u=own(username);String system=systemContext(u);
  List<AiChatMessage> recent=chats.findByUser_IdAndSessionIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(u.getId(),session,user.id(),PageRequest.of(0,200));
  Collections.reverse(recent);List<Map<String,Object>> prompt=new ArrayList<>();
  // Preserve up to 10 completed user/AI turns, never include the current message twice.
  Map<UUID,List<AiChatMessage>> turns=new LinkedHashMap<>();for(AiChatMessage m:recent)if("COMPLETE".equals(m.getStatus()))turns.computeIfAbsent(m.getTurnId(),x->new ArrayList<>()).add(m);
  List<List<AiChatMessage>> pairs=new ArrayList<>();for(var value:turns.values())if(value.size()==2&&value.get(0).getSender().equals("USER")&&value.get(1).getSender().equals("AI"))pairs.add(value);
  for(var pair:pairs.subList(Math.max(0,pairs.size()-10),pairs.size()))for(var m:pair)prompt.add(Map.of("role",m.getSender().equals("USER")?"user":"model","text",m.getContent()));
  prompt.add(Map.of("role","user","text",text.trim()));String answer=gemini.generate(system,prompt);
  return new ChatResult(mark(username,user.id(),"COMPLETE"),saveReply(username,session,turn,answer),null);
 }catch(Exception e){mark(username,user.id(),"FAILED");return new ChatResult(new Message(user.id(),user.sessionId(),user.turnId(),user.sender(),user.content(),"FAILED",user.createdAt()),null,e instanceof IllegalStateException ? e.getMessage() : "Không nhận được phản hồi Gemini: " + e.getClass().getSimpleName());}}
 @Transactional public Message mark(String username,Long id,String status){User u=own(username);AiChatMessage m=chats.findById(id).orElseThrow();if(!m.getUser().getId().equals(u.getId()))throw new SecurityException("Không có quyền");m.setStatus(status);return map(chats.save(m));}
 @Transactional public Message saveReply(String username,String session,UUID turn,String answer){User u=own(username);AiChatMessage m=new AiChatMessage();m.setUser(u);m.setSessionId(session);m.setTurnId(turn);m.setSender("AI");m.setContent(answer);return map(chats.save(m));}
 @Transactional(readOnly=true) public String systemContext(User u){StringBuilder s=new StringBuilder("Bạn là trợ lý FitnessAI, trả lời tiếng Việt. Không được tự nhận đã cập nhật lịch tập hoặc nhật ký ăn. Các gợi ý chat chỉ là tham khảo, phải được xác nhận và lưu qua API kế hoạch. Không tự bịa dữ liệu, không chẩn đoán bệnh. ");profiles.findByUserId(u.getId()).ifPresent(x->{s.append("Hồ sơ: giới tính ").append(x.getGender()).append(", chiều cao cm ").append(x.getHeightCm()).append(", cân nặng kg ").append(x.getWeightKg()).append(", vận động ").append(x.getActivityLevel()).append(", mục tiêu ").append(x.getGoal()).append(", TDEE ").append(x.getCalculatedTdee()).append(" kcal. Cân nặng đích chưa được lưu ở hồ sơ này; nếu cần hãy hỏi user. ");});return s.toString();}
 public record RecommendationView(Long id,String type,String title,String content,String status,boolean read,OffsetDateTime createdAt){}
 private RecommendationView view(AiRecommendation x){return new RecommendationView(x.getId(),x.getType(),x.getTitle(),x.getContent(),x.getStatus(),x.getReadAt()!=null,x.getCreatedAt());}
 @Transactional(readOnly=true) public List<RecommendationView> recommendations(String username){return recommendations.findTop100ByProfile_User_IdOrderByIdDesc(own(username).getId()).stream().map(this::view).toList();}
 private static final Set<String> TYPES=Set.of("MEAL_PLAN","WORKOUT_PLAN","CALORIE_ALERT","INACTIVITY_ALERT","STREAK_MILESTONE");
 public record MealItem(Integer foodId,LocalDate date,vn.id.fitnessai.enums.MealType mealType,BigDecimal grams){}
 public record ExerciseItem(Integer exerciseId,Integer dayOfWeek,Integer sets,Integer reps){}
 public record PlanRequest(String type,String title,String content,Long chatId,Integer mealLogId,BigDecimal targetCalories,List<MealItem> meals,List<ExerciseItem> exercises){}
 public record PlanResult(RecommendationView recommendation,BigDecimal calories,BigDecimal protein,BigDecimal carbs,BigDecimal fat,BigDecimal targetCalories){}
 private BigDecimal calc(BigDecimal per100,BigDecimal grams){return per100.multiply(grams).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);}
 @Transactional public PlanResult create(String username,PlanRequest r){User u=own(username);UserProfile p=profile(u);
 if(r==null||!TYPES.contains(r.type()))throw new IllegalArgumentException("Loại khuyến nghị không hợp lệ");if(r.title()==null||r.title().isBlank()||r.title().length()>180||r.content()==null||r.content().isBlank()||r.content().length()>10000)throw new IllegalArgumentException("Tiêu đề/nội dung không hợp lệ");
 if(!"MEAL_PLAN".equals(r.type())&&r.meals()!=null&&!r.meals().isEmpty())throw new IllegalArgumentException("Danh sách món chỉ dành cho MEAL_PLAN");if(!"WORKOUT_PLAN".equals(r.type())&&r.exercises()!=null&&!r.exercises().isEmpty())throw new IllegalArgumentException("Danh sách bài chỉ dành cho WORKOUT_PLAN");
 if(r.mealLogId()!=null)throw new IllegalArgumentException("Không cho gắn meal_log_id khi tạo kế hoạch dự kiến; nhật ký thực tế được quản lý riêng");
 AiRecommendation a=new AiRecommendation();a.setProfile(p);a.setType(r.type());a.setTitle(r.title());a.setContent(r.content());
 if(r.chatId()!=null){AiChatMessage m=chats.findById(r.chatId()).orElseThrow(()->new IllegalArgumentException("Chat không tồn tại"));if(!m.getUser().getId().equals(u.getId())||m.getDeletedAt()!=null)throw new SecurityException("Chat không thuộc user");a.setChat(m);}
 a=recommendations.save(a);BigDecimal k=BigDecimal.ZERO,pr=k,ca=k,fa=k;
 if("MEAL_PLAN".equals(r.type())){if(r.meals()==null||r.meals().isEmpty()||r.meals().size()>60)throw new IllegalArgumentException("Thực đơn cần 1-60 món");if(r.targetCalories()==null||r.targetCalories().signum()<=0)throw new IllegalArgumentException("Thiếu mục tiêu calo");for(MealItem item:r.meals()){
 if(item==null||item.foodId()==null||item.date()==null||item.mealType()==null||item.grams()==null||item.grams().signum()<=0||item.grams().compareTo(new BigDecimal("5000"))>0||item.date().isBefore(LocalDate.now(NutritionLog.VN_ZONE)))throw new IllegalArgumentException("Ngày, bữa hoặc số gram không hợp lệ");
 Food food=foods.findById(item.foodId()).orElseThrow(()->new IllegalArgumentException("Food ID không tồn tại"));if(!food.isAvailableForLogging())throw new IllegalArgumentException("Food ID không hoạt động");AiMealPlanItem i=new AiMealPlanItem();i.setRecommendation(a);i.setFood(food);i.setPlanDate(item.date());i.setMealType(item.mealType());i.setGrams(item.grams());i.setCalories(calc(food.getCaloriesPer100g(),item.grams()));i.setProtein(calc(food.getProteinPer100g(),item.grams()));i.setCarbs(calc(food.getCarbsPer100g(),item.grams()));i.setFat(calc(food.getFatPer100g(),item.grams()));mealItems.save(i);k=k.add(i.getCalories());pr=pr.add(i.getProtein());ca=ca.add(i.getCarbs());fa=fa.add(i.getFat());}}
 if("WORKOUT_PLAN".equals(r.type())){if(r.exercises()==null||r.exercises().isEmpty()||r.exercises().size()>80)throw new IllegalArgumentException("Cần 1-80 bài tập");int pos=0;for(ExerciseItem item:r.exercises()){
 if(item==null||item.exerciseId()==null||item.dayOfWeek()==null||item.dayOfWeek()<1||item.dayOfWeek()>7||item.sets()==null||item.sets()<1||item.sets()>30||item.reps()==null||item.reps()<1||item.reps()>200)throw new IllegalArgumentException("Bài tập/ngày/sets/reps không hợp lệ");Exercise ex=exercises.findById(item.exerciseId()).orElseThrow(()->new IllegalArgumentException("Exercise ID không tồn tại"));if(!ex.isActive())throw new IllegalArgumentException("Bài tập đã ngưng sử dụng");AiRecommendedExercise i=new AiRecommendedExercise();i.setRecommendation(a);i.setExercise(ex);i.setDayOfWeek(item.dayOfWeek());i.setPlannedSets(item.sets());i.setPlannedReps(item.reps());i.setPosition(pos++);exerciseItems.save(i);}}
 return new PlanResult(view(a),k,pr,ca,fa,r.targetCalories());}
 @Transactional public RecommendationView read(String username,Long id){User u=own(username);AiRecommendation r=recommendations.findById(id).orElseThrow();if(!r.getProfile().getUser().getId().equals(u.getId()))throw new SecurityException("Không có quyền");r.setReadAt(OffsetDateTime.now());return view(r);}
 @Transactional(readOnly=true) public Map<String,Object> detail(String username,Long id){User u=own(username);AiRecommendation r=recommendations.findById(id).orElseThrow();if(!r.getProfile().getUser().getId().equals(u.getId()))throw new SecurityException("Không có quyền");return Map.of("recommendation",view(r),"meals",mealItems.findByRecommendation_IdOrderByPlanDateAscIdAsc(id).stream().map(i->Map.of("foodId",i.getFood().getFoodId(),"foodName",i.getFood().getFoodName(),"date",i.getPlanDate().toString(),"mealType",i.getMealType().name(),"grams",i.getGrams(),"calories",i.getCalories(),"protein",i.getProtein(),"carbs",i.getCarbs(),"fat",i.getFat())).toList(),"exercises",exerciseItems.findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(id).stream().map(i->Map.of("exerciseId",i.getExercise().getExerciseId(),"exerciseName",i.getExercise().getExerciseName(),"dayOfWeek",i.getDayOfWeek(),"sets",i.getPlannedSets(),"reps",i.getPlannedReps())).toList());}
}
