// Executes the real browser scripts with disposable DOM/HTTP fixtures. No server or user data is touched.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const settle=async()=>{for(let i=0;i<25;i++)await new Promise(resolve=>setImmediate(resolve));};
function environment(handler,{hash='',prefix='',storage=new Map()}={}){
  const nodes=new Map(),all=[],calls=[],events=[];
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
  const ctx={document,Date,Intl,Map,Set,URL,URLSearchParams,AbortController,clearTimeout,crypto:{randomUUID:()=>crypto.randomUUID()},location:{hash,search:''},
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
  await test('Editing uses a plain composer and shows context without the proposal UUID',async()=>{
    const e=environment(chatHandler({pending:[proposal]}));e.run('src/main/resources/static/js/ai.js');await settle();
    e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Chỉnh sửa').onclick();
    assert.equal(e.get('chat-text').value,'');assert.equal(e.get('chat-edit-context').hidden,false);
    assert.ok(e.get('chat-edit-label').textContent.includes('Đang chỉnh sửa'));assert.ok(!e.get('chat-edit-label').textContent.includes(proposal.id));
    assert.equal(e.calls.filter(c=>c.options.method==='POST').length,0);
  });
  await test('Editing the second of two proposals sends its exact ID but displays only the requested change',async()=>{
    const second={...proposal,id:crypto.randomUUID(),kind:'WORKOUT_PLAN'};let wire;
    const e=environment(chatHandler({pending:[proposal,second],handle:(path,options)=>{
      if(path==='/api/ai/chat'&&options.method==='POST'){wire=JSON.parse(options.body);return {status:'COMPLETE',userMessage:{...pair[0],content:wire.content},aiMessage:pair[1],proposals:[]};}
    }}));e.run('src/main/resources/static/js/ai.js');await settle();
    e.get('proposals').querySelectorAll('button').filter(b=>b.textContent==='Chỉnh sửa')[1].onclick();
    await submit(e,'Chuyển buổi thứ Hai sang thứ Ba');
    assert.equal(wire.content,`Chỉnh đề xuất ${second.id}: Chuyển buổi thứ Hai sang thứ Ba`);
    assert.equal(e.get('messages').children[0].children[0].textContent,'Chuyển buổi thứ Hai sang thứ Ba');
    assert.equal(e.get('chat-edit-context').hidden,true);assert.equal(e.calls.filter(c=>c.path.endsWith('/confirm')).length,0);
  });
  await test('Leaving edit context keeps the proposal pending and sends an ordinary question',async()=>{
    let wire;const e=environment(chatHandler({pending:[proposal],handle:(path,options)=>{
      if(path==='/api/ai/chat'&&options.method==='POST')wire=JSON.parse(options.body);
    }}));e.run('src/main/resources/static/js/ai.js');await settle();
    e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Chỉnh sửa').onclick();
    e.get('chat-edit-cancel').onclick();assert.equal(e.get('chat-edit-context').hidden,true);
    await submit(e,'TDEE là gì?');assert.equal(wire.content,'TDEE là gì?');
    assert.equal(e.calls.filter(c=>c.path.endsWith('/cancel')||c.path.endsWith('/confirm')).length,0);
  });
  await test('Failed edits restore plain text and retry with the same proposal and request IDs',async()=>{
    const sent=[];const e=environment(chatHandler({pending:[proposal],handle:(path,options)=>{
      if(path==='/api/ai/chat'&&options.method==='POST'){const wire=JSON.parse(options.body);sent.push(wire);return {status:'FAILED',userMessage:{...pair[0],content:wire.content,status:'FAILED'},proposals:[],error:'Timeout',retryable:true};}
    }}));e.run('src/main/resources/static/js/ai.js');await settle();
    e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Chỉnh sửa').onclick();
    await submit(e,'Đổi sang 4 ngày');assert.equal(e.get('chat-text').value,'Đổi sang 4 ngày');assert.equal(e.get('chat-edit-context').hidden,false);
    await submit(e,'Đổi sang 4 ngày');assert.equal(sent[0].requestId,sent[1].requestId);
    assert.equal(sent[0].content,`Chỉnh đề xuất ${proposal.id}: Đổi sang 4 ngày`);assert.equal(sent[0].content,sent[1].content);
    assert.equal(e.get('messages').children[0].children[0].textContent,'Đổi sang 4 ngày');
  });
  await test('Reload restores a failed edit without exposing its ID or automatically resending',async()=>{
    const requestId=crypto.randomUUID(),content=`Chỉnh đề xuất ${proposal.id}: Đổi sang 4 ngày`,failed={...pair[0],content,status:'FAILED'};
    const e=environment(chatHandler({pending:[proposal],handle:(path,options)=>{
      if(path==='/api/ai/chat/session')return {sessionId:'session123',activeTurn:{requestId,status:'FAILED',userMessage:failed,proposals:[],error:'Timeout',retryable:true}};
      if(path==='/api/ai/chat'&&!options.method)return {messages:[failed],nextCursor:null};
    }}));e.run('src/main/resources/static/js/ai.js');await settle();
    assert.equal(e.get('chat-text').value,'Đổi sang 4 ngày');assert.equal(e.get('chat-edit-context').hidden,false);
    assert.equal(e.get('messages').children[0].children[0].textContent,'Đổi sang 4 ngày');
    assert.equal(e.calls.filter(c=>c.options.method==='POST').length,0);
    await submit(e,'Đổi sang 4 ngày');const sent=JSON.parse(e.calls.find(c=>c.options.method==='POST').options.body);
    assert.equal(sent.requestId,requestId);assert.equal(sent.content,content);
  });
  await test('Explicit confirmation uses the persisted proposal ID',async()=>{const e=environment(chatHandler());e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Xác nhận').onclick();assert.equal(e.calls.filter(c=>c.path===`/api/ai/proposals/${proposal.id}/confirm`).length,1);assert.equal(e.get('proposals').querySelectorAll('button').length,0);});
  await test('Unknown network outcome retries the same request ID',async()=>{let first=true;const ids=[];const e=environment(chatHandler({handle:(path,options)=>{if(path==='/api/ai/chat'&&options.method==='POST'){ids.push(JSON.parse(options.body).requestId);if(first){first=false;throw Error('offline');}}}}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);await submit(e,pair[0].content);assert.equal(ids.length,2);assert.equal(ids[0],ids[1]);});
  await test('Known provider failure preserves input for a same-turn retry',async()=>{const e=environment(chatHandler({handle:(path,options)=>path==='/api/ai/chat'&&options.method==='POST'?{status:'FAILED',error:'Timeout',userMessage:{...pair[0],status:'FAILED'},proposals:[],retryable:true}:undefined}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.equal(e.get('chat-text').value,pair[0].content);assert.ok(e.storage.get('fitness-ai-pending').includes('requestId'));});
  await test('All API calls honor an application context prefix',async()=>{const e=environment(chatHandler(),{prefix:'/fitness'});e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.ok(e.calls.every(c=>c.path.startsWith('/fitness/api/ai/')));});
  await test('Model text is displayed as text, never executable HTML',async()=>{const evil='<img src=x onerror=alert(1)>';const e=environment(chatHandler({handle:(path,options)=>path==='/api/ai/chat'&&options.method==='POST'?{status:'COMPLETE',userMessage:pair[0],aiMessage:{...pair[1],content:evil},proposals:[]}:undefined}));e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,'Hello');assert.equal(e.get('messages').children[1].children[0].textContent,evil);assert.equal(e.all.filter(n=>n.tag==='img').length,0);});
  await test('User bubble and typing indicator appear before the chat request resolves',async()=>{
    let release;const gate=new Promise(r=>release=r);
    const e=environment(chatHandler({handle:async(path,options)=>{if(path==='/api/ai/chat'&&!options.method)return {messages:[],nextCursor:null};if(path==='/api/ai/chat'&&options.method==='POST'){await gate;}}}));
    e.run('src/main/resources/static/js/ai.js');await settle();assert.equal(e.get('chat-typing').hidden,true);
    const sent=submit(e,pair[0].content);await settle();
    assert.equal(e.get('messages').children.length,1);assert.equal(e.get('messages').children[0].children[0].textContent,pair[0].content);
    assert.equal(e.get('chat-text').value,'');assert.equal(e.get('chat-typing').hidden,false);assert.equal(e.get('chat-welcome').hidden,true);
    release();await sent;assert.equal(e.get('messages').children.length,2);assert.equal(e.get('chat-typing').hidden,true);
  });
  await test('Reload automatically loads every history page and deduplicates overlapping IDs',async()=>{
    const history=Array.from({length:74},(_,i)=>({...pair[i%2],id:i+1,content:'Tin '+(i+1)}));
    const e=environment(chatHandler({handle:(path,options)=>{if(path==='/api/ai/chat'&&!options.method)return {messages:history.slice(24),nextCursor:25};if(path==='/api/ai/chat?before=25')return {messages:history.slice(0,25),nextCursor:null};}}));
    e.run('src/main/resources/static/js/ai.js');await settle();assert.equal(e.get('messages').children.length,74);assert.equal(e.get('more').hidden,true);
    assert.equal(e.get('messages').children[0].children[0].textContent,'Tin 1');assert.equal(e.get('messages').children[73].children[0].textContent,'Tin 74');
  });
  await test('Reload reconnects to the pending server turn without another POST',async()=>{
    let release;const gate=new Promise(r=>release=r),requestId=crypto.randomUUID();
    const pending={requestId,status:'PENDING',userMessage:{...pair[0],status:'PENDING'},proposals:[]};
    const e=environment(chatHandler({handle:async(path,options)=>{
      if(path==='/api/ai/chat/session')return {sessionId:'session123',activeTurn:pending};
      if(path==='/api/ai/chat'&&!options.method)return {messages:[pending.userMessage],nextCursor:null};
      if(path==='/api/ai/chat/turns/'+requestId){await gate;return {...pending,status:'COMPLETE',userMessage:pair[0],aiMessage:pair[1]};}
    }}));
    e.run('src/main/resources/static/js/ai.js');await settle();assert.equal(e.get('messages').children.length,1);assert.equal(e.get('chat-typing').hidden,false);
    assert.equal(e.calls.filter(c=>c.options.method==='POST').length,0);
    release();await settle();assert.equal(e.get('messages').children.length,2);assert.equal(e.get('chat-typing').hidden,true);assert.equal(e.get('send').disabled,false);
  });
  await test('Lost response followed by reload restores the saved pair without resending',async()=>{
    const storage=new Map();let saved=false,requestId;
    const handler=chatHandler({handle:(path,options)=>{
      if(path==='/api/ai/chat'&&options.method==='POST'){requestId=JSON.parse(options.body).requestId;saved=true;throw Error('Response lost');}
      if(path==='/api/ai/chat'&&!options.method)return {messages:saved?pair:[],nextCursor:null};
      if(path.startsWith('/api/ai/chat/turns/'))return {requestId,status:'COMPLETE',userMessage:pair[0],aiMessage:pair[1],proposals:[]};
    }});
    const a=environment(handler,{storage});a.run('src/main/resources/static/js/ai.js');await settle();await submit(a,pair[0].content);
    const b=environment(handler,{storage});b.run('src/main/resources/static/js/ai.js');await settle();
    assert.equal(b.get('messages').children.length,2);assert.equal(b.calls.filter(c=>c.options.method==='POST').length,0);assert.equal(storage.has('fitness-ai-pending'),false);
  });
  await test('Retrying a failed persisted turn does not insert a second user bubble',async()=>{
    let attempt=0,release;const gate=new Promise(r=>release=r);
    const e=environment(chatHandler({handle:async(path,options)=>{
      if(path==='/api/ai/chat'&&!options.method)return {messages:[],nextCursor:null};
      if(path==='/api/ai/chat'&&options.method==='POST'){attempt++;if(attempt===1)return {status:'FAILED',userMessage:{...pair[0],status:'FAILED'},proposals:[],retryable:true,error:'Timeout'};await gate;}
    }}));
    e.run('src/main/resources/static/js/ai.js');await settle();await submit(e,pair[0].content);assert.equal(e.get('messages').children.length,1);
    const retry=submit(e,pair[0].content);await settle();assert.equal(e.get('messages').children.length,1);release();await retry;assert.equal(e.get('messages').children.length,2);
  });
  await test('A different login discards the old outbox and does not poll the old request',async()=>{
    const oldRequest=crypto.randomUUID(),storage=new Map([['fitness-ai-session','previous123'],['fitness-ai-pending',JSON.stringify({sessionId:'previous123',requestId:oldRequest,content:'PRIVATE_PREVIOUS_LOGIN'})]]);
    const e=environment(chatHandler({handle:path=>path==='/api/ai/chat'?{messages:[],nextCursor:null}:undefined}),{storage});e.run('src/main/resources/static/js/ai.js');await settle();
    assert.equal(e.get('messages').children.length,0);assert.equal(e.get('chat-text').value,'');assert.ok(!e.calls.some(c=>c.path.includes(oldRequest)));assert.equal(storage.has('fitness-ai-pending'),false);
  });
  await test('Corrupt browser storage cannot prevent loading the server conversation',async()=>{
    const e=environment(chatHandler(),{storage:new Map([['fitness-ai-session','session123'],['fitness-ai-pending','{broken']])});e.run('src/main/resources/static/js/ai.js');await settle();
    assert.equal(e.get('messages').children.length,2);assert.equal(e.get('send').disabled,false);
  });
  await test('Failed history page can resume from its cursor without losing earlier messages',async()=>{
    let failed=true;const e=environment(chatHandler({handle:path=>{
      if(path==='/api/ai/chat')return {messages:[pair[1]],nextCursor:2};
      if(path==='/api/ai/chat?before=2'){if(failed){failed=false;throw Error('offline');}return {messages:[pair[0]],nextCursor:null};}
    }}));e.run('src/main/resources/static/js/ai.js');await settle();assert.equal(e.get('more').hidden,false);assert.equal(e.get('messages').children.length,1);
    await e.get('more').onclick();assert.equal(e.get('messages').children.length,2);assert.equal(e.get('more').hidden,true);
  });
  await test('Reload of a failed server turn restores a retryable message without calling the model',async()=>{
    const requestId=crypto.randomUUID();const e=environment(chatHandler({handle:path=>{
      if(path==='/api/ai/chat/session')return {sessionId:'session123',activeTurn:{requestId,status:'FAILED',userMessage:{...pair[0],status:'FAILED'},proposals:[],error:'Timeout',retryable:true}};
      if(path==='/api/ai/chat')return {messages:[{...pair[0],status:'FAILED'}],nextCursor:null};
    }}));e.run('src/main/resources/static/js/ai.js');await settle();assert.equal(e.get('messages').children.length,1);assert.equal(e.get('chat-text').value,pair[0].content);
    assert.equal(e.get('chat-typing').hidden,true);assert.equal(e.calls.filter(c=>c.options.method==='POST').length,0);assert.equal(e.get('send').disabled,false);
  });
  await test('Proposal confirmation does not show the model typing indicator',async()=>{
    let release;const gate=new Promise(r=>release=r);const e=environment(chatHandler({pending:[proposal],handle:async path=>{if(path.endsWith('/confirm'))await gate;}}));
    e.run('src/main/resources/static/js/ai.js');await settle();const confirmation=e.get('proposals').querySelectorAll('button').find(b=>b.textContent==='Xác nhận').onclick();await settle();
    assert.equal(e.get('chat-typing').hidden,true);release();await confirmation;
  });
  await test('Typing stops as soon as the reply arrives even when saved plans load slowly',async()=>{
    let sent=false,release;const gate=new Promise(r=>release=r);const e=environment(chatHandler({handle:async(path,options)=>{
      if(path==='/api/ai/chat'&&options.method==='POST')sent=true;
      if(path==='/api/ai/recommendations'&&sent){await gate;return [];}
    }}));e.run('src/main/resources/static/js/ai.js');await settle();const sending=submit(e,pair[0].content);await settle();
    assert.equal(e.get('messages').children.length,2);assert.equal(e.get('chat-typing').hidden,true);release();await sending;
  });
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
