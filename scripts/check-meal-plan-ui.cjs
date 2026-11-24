// Exact meal-calendar.js, disposable DOM/API data, no application server/database/provider.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const settle=async()=>{for(let i=0;i<20;i++)await new Promise(r=>setImmediate(r));};
function env({storage=new Map(),owner='one',empty=false,prefix='/fitness',confirm=true,handle}={}){
  const clock={now:Date.parse('2026-11-13T10:00:00Z')},nodes=new Map(),all=[],calls=[],events=[],intervals=[],gates=[];
  class TestDate extends Date{constructor(...args){super(...(args.length?args:[clock.now]));}static now(){return clock.now;}}
  class Element{
    constructor(tag='div',text=''){this.tag=tag;this.textContent=text;this.children=[];this.handlers={};this.dataset={};this.style={};this.attrs={};this.value='';this.disabled=false;this.hidden=false;all.push(this);}
    append(...children){this.children.push(...children);}
    replaceChildren(...children){this.children=[];this.append(...children);}
    add(child){this.append(child);if(this.children.length===1)this.value=String(child.value);}
    addEventListener(k,f){this.handlers[k]=f;}
    setAttribute(k,v){this.attrs[k]=v;}
  }
  const get=id=>{if(!nodes.has(id))nodes.set(id,new Element());return nodes.get(id);};get('nutrition-app').dataset.owner=owner;
  const rec=id=>({id,type:'MEAL_PLAN',title:'Plan '+id,status:'PROPOSED',targetCalories:2200,targetProtein:130,targetCarbs:250,targetFat:65});
  const item=(id,date,mealType,foodName,grams,calories)=>({id,date,mealType,foodName,grams,calories,logged:false,protein:10,carbs:20,fat:5});
  const details={1:{recommendation:rec(1),confirmed:true,meals:[item(11,'2026-11-13','BREAKFAST','Yến mạch',60,230),item(12,'2026-11-13','BREAKFAST','Chuối',100,90),item(13,'2026-11-12','LUNCH','Past',150,180),item(14,'2026-11-14','LUNCH','Future',150,180)]},2:{recommendation:rec(2),confirmed:false,meals:[item(21,'2026-11-13','LUNCH','Plan 2 food',150,200)]}};
  const data={details,plans:empty?[]:[rec(1),rec(2)]};
  const ctx={Date:TestDate,Intl,Map,Set,URL,Option:function(text,value){this.textContent=text;this.value=String(value);},AbortController,clearTimeout,
    document:{getElementById:get,createElement:t=>new Element(t),querySelector:s=>s.includes('app-base')?{content:prefix+'/'}:s.includes('_csrf_header')?{content:'X-CSRF-TOKEN'}:s.includes('_csrf')?{content:'csrf'}:new Element()},
    window:{dispatchEvent:e=>events.push(e)},CustomEvent:function(type){this.type=type;},location:{hash:'#meal-plan'},
    sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
    setTimeout:(f,ms)=>{const t=setTimeout(f,ms);t.unref();return t;},setInterval:f=>intervals.push(f),
    confirm:()=>typeof confirm==='function'?confirm(clock):confirm,
    fetch:async(url,options={})=>{
      assert.ok(url.startsWith(prefix+'/'));const path=url.slice(prefix.length),body=options.body?JSON.parse(options.body):null;calls.push({path,...options,body});
      if(options.method)assert.equal(options.headers['X-CSRF-TOKEN'],'csrf');
      const gate=gates.find(g=>!g.used&&g.match(path,options));if(gate){gate.used=true;await gate.promise;}
      let result=handle&&await handle({path,options,body,data,calls});
      if(result===undefined){
        if(path==='/api/ai/recommendations')result=data.plans;
        else if(/^\/api\/ai\/recommendations\/\d+$/.test(path))result=data.details[Number(path.split('/').at(-1))];
        else if(path.endsWith('/log-meals')){const detail=data.details[Number(path.split('/').at(-2))];for(const s of body.items){const item=detail.meals.find(i=>i.id===s.id);item.logged=true;item.loggedGrams=s.grams;item.loggedCalories=item.calories*s.grams/item.grams;}result={count:body.items.length,message:'Đã ghi món'};}
        else throw Error('Unexpected '+path);
      }
      const status=result?._status||200;return {status,ok:status>=200&&status<300,headers:{get:()=> 'application/json'},json:async()=>JSON.parse(JSON.stringify(result))};
    }};
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/meal-calendar.js','utf8'),ctx);
  const walk=(el,tag)=>el.children.flatMap(c=>c&&typeof c==='object'?[...(c.tag===tag?[c]:[]),...walk(c,tag)]:[]);
  const checks=()=>walk(get('meal-ai-calendar'),'input').filter(i=>i.type==='checkbox');
  const grams=()=>walk(get('meal-ai-calendar'),'input').filter(i=>i.type==='number');
  return {get,all,calls,events,data,storage,clock,checks,grams,
    hold(match){let release;const promise=new Promise(r=>release=r);gates.push({match,promise,release});return release;},
    pick(index=0,value){const c=checks()[index],g=grams()[index];c.checked=true;if(value!==undefined){g.value=String(value);g.handlers.input();}c.handlers.change();},
    async day(day){get('meal-week-days').children.find(b=>b.children[1].textContent===String(day)).handlers.click();await settle();},
    async plan(id){get('meal-ai-select').value=String(id);await get('meal-ai-select').handlers.change();await settle();},
    async log(){await get('meal-ai-log-today').handlers.click();await settle();},
    async refresh(){await get('meal-ai-refresh').handlers.click();await settle();},
    async midnight(){clock.now+=86400000;intervals.forEach(f=>f());await settle();},
    text(id){function text(e){return String(e.textContent||'')+e.children.map(c=>typeof c==='object'?text(c):String(c)).join(' ');}return text(get(id));}
  };
}
const posts=e=>e.calls.filter(c=>c.method==='POST');let passed=0;async function test(name,f){await f();console.log('PASS '+name);passed++;}
(async()=>{
  await test('Initial reads show 7 days, 4 meals, real targets and no automatic writes',async()=>{const e=env();await settle();assert.equal(posts(e).length,0);assert.equal(e.get('meal-week-days').children.length,7);assert.equal(e.get('meal-ai-calendar').children.length,4);assert.ok(e.text('meal-ai-targets').includes('2.200'));assert.ok(e.get('meal-ai-log-today').disabled);});
  await test('Selected grams update total and only chosen items are submitted',async()=>{const e=env();await settle();e.pick(0,120);assert.equal(e.get('meal-selected-calories').textContent,'460 kcal');await e.log();assert.deepEqual(posts(e)[0].body.items,[{id:11,grams:120}]);assert.equal(e.data.details[1].meals[1].logged,false);assert.ok(e.text('meal-ai-calendar').includes('120 g'));assert.ok(e.text('meal-ai-calendar').includes('Đã ghi 460 kcal'));assert.ok(e.events.some(e=>e.type==='fitnessai:nutrition-updated'));});
  await test('Past and future days are read-only; returning today restores selection',async()=>{const e=env();await settle();e.pick(0,75);await e.day(12);assert.ok(e.checks().every(c=>c.disabled));await e.log();assert.equal(posts(e).length,0);await e.day(14);assert.ok(e.checks().every(c=>c.disabled));await e.day(13);assert.equal(e.checks()[0].checked,true);assert.equal(e.grams()[0].value,'75');assert.ok(e.text('meal-ai-calendar').includes('287,5 kcal'));});
  await test('Zero, empty, overflow and excess decimals never write',async()=>{for(const value of ['','0','5001','1.001']){const e=env();await settle();e.pick(0,value);assert.ok(e.get('meal-ai-log-today').disabled);await e.log();assert.equal(posts(e).length,0);}});
  await test('Native confirmation can cancel without changing data',async()=>{const e=env({confirm:false});await settle();e.pick();await e.log();assert.equal(posts(e).length,0);assert.equal(e.checks()[0].checked,true);});
  await test('Double click while saving sends one POST and locks controls',async()=>{const e=env();await settle();e.pick();const release=e.hold(p=>p.endsWith('/log-meals'));const a=e.log();await settle();const b=e.log();assert.equal(posts(e).length,1);assert.ok(e.grams().every(i=>i.disabled));assert.ok(e.get('meal-ai-select').disabled);release();await Promise.all([a,b]);});
  await test('Unknown result retries exact payload after reload',async()=>{const storage=new Map();let first=true;const handle=({path})=>{if(path.endsWith('/log-meals')&&first){first=false;throw new TypeError('offline');}};const a=env({storage,handle});await settle();a.pick(0,80);await a.log();assert.equal(storage.size,1);const b=env({storage,handle});await settle();assert.ok(b.grams().every(i=>i.disabled));await b.log();assert.deepEqual(posts(a)[0].body,posts(b)[0].body);assert.equal(storage.size,0);});
  await test('Refresh resolves a committed response lost in the network without a replay',async()=>{let once=true;const e=env({handle:({path,body,data})=>{if(path.endsWith('/log-meals')&&once){once=false;for(const s of body.items)data.details[1].meals.find(i=>i.id===s.id).logged=true;throw new TypeError('lost response');}}});await settle();e.pick();await e.log();assert.equal(e.storage.size,1);await e.refresh();assert.equal(posts(e).length,1);assert.equal(e.storage.size,0);assert.ok(e.text('meal-ai-calendar').includes('Đã ghi nhật ký'));});
  await test('Successful write followed by failed read never causes another POST',async()=>{let saved=false;const e=env({handle:({path})=>{if(path.endsWith('/log-meals'))saved=true;else if(saved&&path==='/api/ai/recommendations/1')throw new TypeError('read offline');}});await settle();e.pick();await e.log();await e.log();assert.equal(posts(e).length,1);assert.ok(e.get('meal-ai-log-today').disabled);});
  await test('Late detail response cannot replace a newly selected plan',async()=>{const e=env();await settle();const release=e.hold(p=>p==='/api/ai/recommendations/1');const a=e.plan(1);await settle();await e.plan(2);release();await a;assert.ok(e.text('meal-ai-calendar').includes('Plan 2 food'));assert.ok(!e.text('meal-ai-calendar').includes('Yến mạch'));});
  await test('Replacing a plan disables logging and never pretends it is confirmed',async()=>{const e=env();await settle();e.data.details[1].recommendation.status='REPLACED';await e.refresh();assert.ok(e.get('meal-ai-plan-status').textContent.includes('Đã thay thế'));assert.ok(e.checks().every(c=>c.disabled));await e.log();assert.equal(posts(e).length,0);});
  await test('Vietnam midnight disables old day and also rechecks after confirm',async()=>{const e=env();await settle();e.pick();await e.midnight();await e.log();assert.equal(posts(e).length,0);const b=env({confirm:clock=>{clock.now+=86400000;return true;}});await settle();b.pick();await b.log();assert.equal(posts(b).length,0);});
  await test('Markup food names and dangerous image URLs remain inert',async()=>{const e=env();await settle();e.data.details[1].meals[0].foodName='<img src=x onerror=alert(1)>';e.data.details[1].meals[0].imageUrl='javascript:alert(1)';await e.refresh();assert.ok(e.text('meal-ai-calendar').includes('<img'));assert.equal(e.all.filter(n=>n.tag==='img').length,0);});
  await test('Empty state and unknown targets do not show sample values',async()=>{const e=env({empty:true});await settle();assert.equal(e.get('meal-ai-empty').hidden,false);assert.ok(e.get('meal-ai-log-today').disabled);const b=env();await settle();b.data.details[1].recommendation.targetCalories=null;await b.refresh();assert.ok(b.text('meal-ai-targets').includes('Chưa đặt'));});
  await test('Pending requests are isolated by account',async()=>{const storage=new Map(),a=env({storage,handle:({path})=>{if(path.endsWith('/log-meals'))throw new TypeError('offline');}});await settle();a.pick();await a.log();const b=env({storage,owner:'two'});await settle();assert.ok(b.checks().every(c=>!c.checked&&!c.disabled));assert.equal(posts(b).length,0);});
  await test('Definitive rejection unlocks editing and clears pending receipt',async()=>{const e=env({handle:({path})=>path.endsWith('/log-meals')?{_status:400,error:'Food unavailable'}:undefined});await settle();e.pick();await e.log();assert.equal(e.storage.size,0);assert.ok(e.grams().every(i=>!i.disabled));assert.ok(e.get('meal-ai-notice').textContent.includes('Food unavailable'));});
  console.log(`Meal-plan UI: ${passed} scenarios passed (DOM/API fixtures only).`);
})().catch(e=>{console.error(e);process.exitCode=1;});
