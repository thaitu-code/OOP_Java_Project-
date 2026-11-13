// Executes the real browser scripts with disposable DOM/HTTP fixtures. No server or user data is touched.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const settle=async()=>{for(let i=0;i<25;i++)await new Promise(resolve=>setImmediate(resolve));};
function environment(handler,{hash='',prefix=''}={}){
  const nodes=new Map(),all=[],calls=[],events=[],storage=new Map();
  class Element{
    constructor(tag='div'){this.tag=tag;this.children=[];this.handlers={};this.dataset={};this.disabled=false;this.hidden=false;this.value='';this.style={};this.textContent='';all.push(this);}
    append(...children){this.children.push(...children);for(const c of children)if(c&&typeof c==='object')c.parent=this;}
    prepend(child){this.children.unshift(child);child.parent=this;}
    replaceChildren(...children){this.children=[];this.append(...children);}
    add(child){this.append(child);if(this.children.length===1)this.value=String(child.value);}
    addEventListener(name,fn){this.handlers[name]=fn;}
    setAttribute(k,v){this[k]=v;}
    querySelectorAll(selector){return this.children.flatMap(c=>typeof c==='object'?[...(selector==='button'&&c.tag==='button'?[c]:[]),...(c.querySelectorAll?.(selector)||[])]:[]);}
    focus(){}
    remove(){if(this.parent)this.parent.children=this.parent.children.filter(c=>c!==this);}
    checkValidity(){return Number(this.value)>0&&Number(this.value)<=5000;}
  }
  const get=id=>{if(!nodes.has(id))nodes.set(id,new Element());return nodes.get(id);};
  const document={hidden:false,getElementById:get,createElement:tag=>new Element(tag),querySelector:selector=>{
    if(selector.includes('app-base'))return {content:prefix+'/'};
    if(selector.includes('_csrf_header'))return {content:'X-CSRF-TOKEN'};
    if(selector.includes('_csrf'))return {content:'csrf-fixture'};
    return new Element();
  }};
  const ctx={document,Date,Intl,Map,Set,URLSearchParams,crypto:{randomUUID:()=>crypto.randomUUID()},location:{hash,search:''},
    sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
    setInterval:()=>0,setTimeout:fn=>setImmediate(fn),confirm:()=>true,
    Option:function(text,value){this.textContent=text;this.value=String(value);},
    CustomEvent:function(type,init={}){this.type=type;this.detail=init.detail;},
    window:{dispatchEvent:e=>events.push(e),addEventListener(){}},
    fetch:async(path,options={})=>{calls.push({path,options});const result=await handler(path.slice(prefix.length),options);return {ok:true,status:200,redirected:false,headers:{get:()=> 'application/json'},json:async()=>result};}
  };
  return {ctx,get,all,calls,events,storage,run:file=>vm.runInNewContext(fs.readFileSync(file,'utf8'),ctx)};
}
const stamp=new Date().toISOString();
const pair=[{id:1,sender:'USER',content:'Hôm nay tôi vừa cân được 65 kg',status:'COMPLETE',createdAt:stamp},{id:2,sender:'AI',content:'Xem trước thay đổi',status:'COMPLETE',createdAt:stamp}];
const proposal={id:crypto.randomUUID(),kind:'PROFILE_UPDATE',preview:'Cân nặng hiện tại: 68 → 65 kg',status:'PENDING'};
function chatHandler(extra={}){let submitted=false;return async(path,options)=>{
  if(extra.handle){const value=await extra.handle(path,options);if(value!==undefined)return value;}
  if(path==='/api/ai/chat/session')return {sessionId:'session123'};
  if(path.startsWith('/api/ai/proposals?'))return extra.pending||(submitted?[proposal]:[]);
  if(path==='/api/ai/recommendations'||path==='/api/ai/notifications')return [];
  if(path==='/api/ai/chat'&&options.method==='POST'){submitted=true;return {status:'COMPLETE',userMessage:pair[0],aiMessage:pair[1],proposals:[proposal]};}
  if(path==='/api/ai/chat')return {messages:pair,nextCursor:null};
  if(path.endsWith('/confirm'))return {proposal:{...proposal,status:'CONFIRMED',result:'Đã cập nhật'},messages:[]};
  throw Error('Unexpected request '+path);
};}
async function submit(e,text){e.get('chat-text').value=text;await e.get('chat-form').onsubmit({preventDefault(){}});await settle();}
let passed=0;async function test(name,work){await work();passed++;console.log('PASS '+name);}
(async()=>{
  await test('One send control; no execution mode',async()=>{const html=fs.readFileSync('src/main/resources/templates/ai.html','utf8');assert.ok(html.includes('id="send"'));assert.ok(!html.includes('ai-execute'));});
  await test('Sending prepares a preview without confirming or writing',async()=>{const e=environment(chatHandler());e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.equal(e.calls.filter(c=>c.options.method==='POST').length,1);assert.equal(e.get('proposals').children.length,1);assert.ok(e.get('proposals').querySelectorAll('button').some(b=>b.textContent==='Xác nhận'));});
  await test('Loading history after a sent turn does not duplicate messages',async()=>{const e=environment(chatHandler());e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await e.get('more').onclick();assert.equal(e.get('messages').children.length,2);});
  await test('Editing stays in the same composer without confirming',async()=>{const e=environment(chatHandler());e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Chỉnh sửa').onclick();assert.ok(e.get('chat-text').value.includes(proposal.id));assert.equal(e.calls.filter(c=>c.path.endsWith('/confirm')).length,0);});
  await test('Explicit confirmation uses the persisted proposal ID',async()=>{const e=environment(chatHandler());e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Xác nhận').onclick();assert.equal(e.calls.filter(c=>c.path===`/api/ai/proposals/${proposal.id}/confirm`).length,1);assert.equal(e.get('proposals').querySelectorAll('button').length,0);});
  await test('Unknown network outcome retries the same request ID',async()=>{let first=true;const ids=[];const e=environment(chatHandler({handle:(path,options)=>{if(path==='/api/ai/chat'&&options.method==='POST'){ids.push(JSON.parse(options.body).requestId);if(first){first=false;throw Error('offline');}}}}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await submit(e,pair[0].content);assert.equal(ids.length,2);assert.equal(ids[0],ids[1]);});
  await test('Known provider failure preserves input for a same-turn retry',async()=>{const e=environment(chatHandler({handle:(path,options)=>path==='/api/ai/chat'&&options.method==='POST'?{status:'FAILED',error:'Timeout',userMessage:{...pair[0],status:'FAILED'},proposals:[],retryable:true}:undefined}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.equal(e.get('chat-text').value,pair[0].content);assert.ok(e.storage.get('fitness-ai-pending').includes('requestId'));});
  await test('All API calls honor an application context prefix',async()=>{const e=environment(chatHandler(),{prefix:'/fitness'});e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.ok(e.calls.every(c=>c.path.startsWith('/fitness/api/ai/')));});
  await test('Model text is displayed as text, never executable HTML',async()=>{const evil='<img src=x onerror=alert(1)>';const e=environment(chatHandler({handle:(path,options)=>path==='/api/ai/chat'&&options.method==='POST'?{status:'COMPLETE',userMessage:pair[0],aiMessage:{...pair[1],content:evil},proposals:[]}:undefined}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,'Hello');assert.equal(e.get('messages').children[1].children[0].textContent,evil);assert.equal(e.all.filter(n=>n.tag==='img').length,0);});
  await test('Plan approval does not automatically add or start a workout',async()=>{
    const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const v=k=>parts.find(p=>p.type===k).value;const day=`${v('year')}-${v('month')}-${v('day')}`;let applied=false;
    const plan={id:1,title:'Fixture',content:'test',type:'WORKOUT_PLAN',status:'PROPOSED',workoutStartDate:day,restDays:''};const exercises=[{dayOfWeek:new Date(day+'T12:00:00Z').getUTCDay()||7,exerciseName:'Bài fixture',sets:3,reps:10}];
    const e=environment(async(path)=>{if(path==='/api/ai/recommendations')return [plan];if(path==='/api/ai/schedule')return applied?{recommendation:plan,exercises}:{};if(path.endsWith('/apply-workout')){applied=true;plan.status='APPLIED';return {message:'Đã chấp nhận'};}if(path.endsWith('/start-today-workout'))return {message:'Đã thêm',count:1};return {recommendation:plan,exercises};});
    e.run('src/main/resources/static/js/weekly-ai.js');await settle();await e.get('weekly-apply').handlers.click();assert.equal(e.calls.filter(c=>c.path.endsWith('/start-today-workout')).length,0);await e.get('weekly-start-today').handlers.click();assert.equal(e.get('weekly-start-today').disabled,false);
  });
  await test('Meal logging requires selection and sends actual grams for selected items only',async()=>{
    const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const value=k=>parts.find(p=>p.type===k).value;const today=`${value('year')}-${value('month')}-${value('day')}`;
    const meals=[1,2].map(id=>({id,foodName:'Món '+id,date:today,mealType:'LUNCH',grams:150,calories:150,protein:3,carbs:30,fat:1,logged:false}));
    const detail={recommendation:{id:1,title:'Thực đơn',status:'PROPOSED',targetCalories:2000,targetProtein:100,targetCarbs:250,targetFat:60},meals};let payload=null;
    const e=environment(async(path,options)=>{if(path==='/api/ai/recommendations')return [{id:1,title:'Thực đơn',type:'MEAL_PLAN',createdAt:stamp}];if(path.endsWith('/log-meals')){payload=JSON.parse(options.body);for(const item of payload.items)meals.find(m=>m.id===item.id).logged=true;return {message:'Đã ghi',count:payload.items.length};}return detail;},{hash:'#meal-plan'});
    e.run('src/main/resources/static/js/meal-calendar.js');await settle();await e.get('meal-ai-log-today').handlers.click();assert.equal(payload,null);
    const checks=e.all.filter(n=>n.type==='checkbox'),grams=e.all.filter(n=>n.type==='number');checks[0].checked=true;grams[0].value='120';
    await e.get('meal-ai-log-today').handlers.click();assert.equal(payload.items.length,1);assert.equal(payload.items[0].id,1);assert.equal(payload.items[0].grams,120);assert.equal(meals[1].logged,false);
    assert.ok(e.events.some(event=>event.type==='fitnessai:nutrition-updated'));
  });
  console.log(`${passed} frontend checks passed (fixtures only).`);
})().catch(e=>{console.error(e);process.exitCode=1;});
