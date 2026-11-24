// Local QA only: real Thymeleaf-rendered pages, disposable RAM APIs, no database/provider/proxy.
// Render first: mvnw.cmd -Dtest=UiPageRenderingTest,WorkoutPageControllerTest,NutritionPageControllerTest test
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto');
const root=path.resolve(__dirname,'..'),workout=require('./workout-ui-fixture.cjs').fixture(()=>Date.now(),{automation:true}),nutrition=require('./nutrition-ui-fixture.cjs').fixture();
const receipts=new Map(),messages=[],proposals=new Map(),chatReplies=new Map();let nextMessage=1;
const chatDelay=Math.max(0,Math.min(20000,Number(process.env.AI_UI_PREVIEW_DELAY_MS)||0));
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
for(const [foodId,mealType,weightGrams] of [[1,'BREAKFAST',150],[2,'LUNCH',120],[4,'LUNCH',200]])nutrition.route('POST','/nutrition-logs/today/details',{foodId,mealType,weightGrams});
const notifications=[{id:1,content:'Bản kiểm thử giao diện sử dụng dữ liệu mô phỏng trong bộ nhớ.',link:'/home',applicable:true,read:false}];
// Only this QA server supplies sample plans/images; product code always reads the real APIs.
const qaPlan={id:201,type:'MEAL_PLAN',title:'Tăng cơ · 3 bữa chính, 1 bữa phụ',status:'PROPOSED',createdAt:new Date().toISOString(),targetCalories:2200,targetProtein:130,targetCarbs:250,targetFat:65};
const qaFoods=[[7,'Yến mạch',60,383.33,'BREAKFAST','oats'],[5,'Chuối',100,90,'BREAKFAST','banana'],[1,'Cơm trắng',150,130,'LUNCH','rice'],[2,'Ức gà',120,165,'LUNCH','chicken'],[6,'Sữa chua',100,66,'AFTERNOON','yogurt'],[8,'Hạnh nhân',20,580,'AFTERNOON','almonds'],[9,'Cá hồi',120,208.33,'DINNER','salmon'],[4,'Rau xanh',200,35,'DINNER','greens']];
for(const [id,name,,kcal] of qaFoods){const food=nutrition.foods.find(f=>f.foodId===id);food.foodName=name;food.caloriesPer100g=kcal;}
const qaMeals=[];const qaMonday=new Date(today()+'T12:00:00Z');qaMonday.setUTCDate(qaMonday.getUTCDate()-((qaMonday.getUTCDay()+6)%7));
for(let day=0;day<7;day++){const date=new Date(qaMonday);date.setUTCDate(date.getUTCDate()+day);for(const [foodId,foodName,grams,calories,mealType,photo] of qaFoods)qaMeals.push({id:1000+qaMeals.length,foodId,foodName,date:date.toISOString().slice(0,10),mealType,grams,calories:Math.round(calories*grams)/100,protein:10,carbs:20,fat:5,logged:false,imageUrl:fs.existsSync(path.join(root,'target/meal-preview-assets','preview-food-'+photo+'.jpg'))?'/images/preview-food-'+photo+'.jpg':null});}
async function api(method,p,body){
  if(p.startsWith('/api/ai/notifications')){if(method==='PATCH')notifications[0].read=true;return {status:200,data:notifications};}
  if(p==='/api/dashboard/summary'){
    const date=today(),totals=nutrition.route('GET','/nutrition-logs').data.totals;
    return {status:200,data:{today:date,days:Array.from({length:7},(_,i)=>{const d=new Date(date+'T12:00:00+07:00');d.setUTCDate(d.getUTCDate()-6+i);return {date:d.toISOString().slice(0,10),nutrition:i===6?totals:{calories:800+i*110,protein:40+i*5,carbs:130+i*5,fat:25+i*2},burnedCalories:80+i*25};}),targets:{planId:1,calories:2000,protein:100,carbs:250,fat:60},currentWorkout:workout.route('GET','/workouts/current').data,nextWorkout:null}};
  }
  if(p==='/api/ai/chat/session'){const latest=[...chatReplies.values()].at(-1);return {status:200,data:{sessionId:'ui-preview-session',activeTurn:latest?.status==='PENDING'?latest:null}};}
  if(p.startsWith('/api/ai/proposals?'))return {status:200,data:[...proposals.values()].filter(p=>p.status==='PENDING')};
  if(p==='/api/ai/recommendations')return {status:200,data:[qaPlan]};
  if(p==='/api/ai/recommendations/201')return {status:200,data:{recommendation:qaPlan,confirmed:true,meals:qaMeals,exercises:[]}};
  if(p==='/api/ai/recommendations/201/log-meals'){
    const items=body.items?.map(s=>({selection:s,item:qaMeals.find(i=>i.id===s.id)}));
    if(!items?.length||items.some(({selection:s,item:i})=>!i||i.date!==today()||!(s.grams>0&&s.grams<=5000)))return {status:400,data:{error:'Chọn các món hợp lệ của hôm nay'}};
    let count=0;for(const {selection:s,item:i} of items){if(i.logged)continue;const result=nutrition.route('POST','/nutrition-logs/today/details',{foodId:i.foodId,mealType:i.mealType,weightGrams:s.grams});if(result.status>=400)return result;i.logged=true;i.loggedGrams=s.grams;i.loggedCalories=Math.round(i.calories*s.grams/i.grams*100)/100;count++;}
    return {status:200,data:{message:`Đã ghi ${count} món vào nhật ký mô phỏng.`,count}};
  }
  if(p.startsWith('/api/ai/chat/turns/')&&method==='GET')return chatReplies.has(p.split('/').at(-1))?{status:200,data:chatReplies.get(p.split('/').at(-1))}:{status:404,data:{error:'Không có lượt chat'}};
  if(p.startsWith('/api/ai/chat')&&method==='GET'){const before=Number(new URL(p,'http://localhost').searchParams.get('before'))||Infinity,rows=messages.filter(m=>m.id<before).slice(-51);return {status:200,data:{messages:rows.slice(-50),nextCursor:rows.length>50?rows.at(-50).id:null}};}
  if(p==='/api/ai/chat'&&method==='POST'){
    if(chatReplies.has(body.requestId))return {status:200,data:chatReplies.get(body.requestId)};
    const userMessage={id:nextMessage++,sender:'USER',content:body.content,createdAt:new Date().toISOString(),status:'COMPLETE',requestId:body.requestId},aiMessage={id:nextMessage++,sender:'AI',content:'**Bản kiểm thử giao diện**\nTDEE là năng lượng tiêu hao ước tính trong một ngày.\n- Đây là câu trả lời mô phỏng.\n- Chưa có thông tin nào được thay đổi.',createdAt:new Date().toISOString(),status:'COMPLETE'};
    const p={id:randomUUID(),status:'PENDING',kind:'PROFILE_UPDATE',preview:'Đề xuất mô phỏng: cân nặng hiện tại 68 → 65 kg.\nChưa có dữ liệu thật nào được thay đổi.'};
    if(/65/.test(body.content)){proposals.set(p.id,p);aiMessage.content='Đây là bản xem trước mô phỏng. Hãy kiểm tra trước khi xác nhận.';}
    userMessage.sessionId='ui-preview-session';userMessage.status='PENDING';messages.push(userMessage);
    chatReplies.set(body.requestId,{requestId:body.requestId,status:'PENDING',userMessage,proposals:[]});
    if(chatDelay)await new Promise(resolve=>setTimeout(resolve,chatDelay));
    userMessage.status='COMPLETE';messages.push(aiMessage);
    const reply={requestId:body.requestId,status:'COMPLETE',userMessage,aiMessage,proposals:[...proposals.values()]};chatReplies.set(body.requestId,reply);return {status:200,data:reply};
  }
  if(p.startsWith('/api/ai/proposals/')&&method==='POST'){const draft=proposals.get(p.split('/')[4]);draft.status=p.endsWith('/confirm')?'CONFIRMED':'CANCELLED';draft.result='Đã xử lý bản mô phỏng. Không thay đổi database.';return {status:200,data:{proposal:draft,messages:[]}};}
  if(p.startsWith('/api/ai/chat/')&&method==='DELETE'){const index=messages.findIndex(m=>m.id===Number(p.split('/').at(-1)));if(index>=0)messages.splice(index,1);return {status:204,data:null};}
  if(p==='/api/ai/schedule')return {status:200,data:{}};
  if(p==='/workouts/from-selection'&&method==='POST'){
    const prev=receipts.get(body.requestId);if(prev)return {status:200,data:prev};
    const created=workout.route('POST','/workouts');if(created.status>=400)return created;
    for(const item of body.items){const result=workout.route('POST',`/workouts/${created.data.workoutId}/details`,item);if(result.status>=400)return result;}
    const result=workout.route('GET','/workouts/current');receipts.set(body.requestId,result.data);return result;
  }
  if(p.startsWith('/workouts')||p.startsWith('/exercises')||p.startsWith('/exercise-categories')||p.startsWith('/check-ins')||p.startsWith('/admin/exercise'))return workout.route(method,p,body);
  return nutrition.route(method,p,body);
}
const pages={'/':'ui-preview/home','/home':'ui-preview/home','/ai':'ui-preview/ai','/profile':'ui-preview/profile','/login':'ui-preview/login','/register':'ui-preview/register','/forgot-password':'ui-preview/forgot','/reset-password':'ui-preview/reset','/admin/dashboard':'ui-preview/admin','/admin/badges/page':'ui-preview/badges','/workout':'workout-preview/user','/admin/workout-library':'workout-preview/admin','/nutrition':'nutrition-preview/user','/admin/nutrition-library':'nutrition-preview/admin'};
http.createServer(async(req,res)=>{
  try{
    const u=new URL(req.url,'http://localhost');let file;
    if(req.method==='GET'&&pages[u.pathname])file=path.join(root,'target',pages[u.pathname]+'.html');
    else if(req.method==='GET'&&/^\/images\/preview-food-[a-z]+\.jpg$/.test(u.pathname))file=path.join(root,'target/meal-preview-assets',path.basename(u.pathname));
    else if(req.method==='GET'&&/^\/(css|js|images)\/[\w.-]+$/.test(u.pathname))file=path.join(root,'src/main/resources/static',u.pathname);
    if(file){let data=fs.readFileSync(file);if(file.endsWith('.html'))data=data.toString('utf8').replace(/(<body[^>]*>)/,'$1<div style="position:fixed;top:0;left:0;right:0;z-index:100;background:#fff3cf;color:#735819;padding:5px;text-align:center;font:11px Segoe UI">BẢN KIỂM THỬ · Dữ liệu mô phỏng · Không kết nối database hoặc Gemini</div>');const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.jpg':'image/jpeg','.png':'image/png','.svg':'image/svg+xml'};res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'});res.end(data);return;}
    let text='';for await(const chunk of req)text+=chunk;const result=await api(req.method,req.url,text?JSON.parse(text):{});res.writeHead(result.status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(result.data==null?'':JSON.stringify(result.data));
  }catch(e){res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({message:e.message}));}
}).listen(8773,'127.0.0.1',()=>console.log('UI QA only: http://127.0.0.1:8773/home'));
