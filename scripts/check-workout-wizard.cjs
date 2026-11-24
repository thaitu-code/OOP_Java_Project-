// Runs the exact wizard script with disposable DOM/API state. No production calls.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const source=fs.readFileSync('src/main/resources/static/js/workout-wizard.js','utf8');
function env({storage=new Map(),owner='one',current=null,submit=async()=>({workoutId:1})}={}){
  class Element{
    constructor(tag='div',text=''){this.tagName=tag.toUpperCase();this.textContent=text;this.children=[];this.value='';this.dataset={};this.style={};this.hidden=false;this.disabled=false;this.handlers={};this.attrs={};this.classList={toggle(){}};}
    append(...children){this.children.push(...children);}
    replaceChildren(...children){this.children=[];this.append(...children);}
    get firstChild(){return this.children[0];}
    setAttribute(k,v){this.attrs[k]=v;}
    addEventListener(k,f){this.handlers[k]=f;}
    focus(){}showModal(){this.open=true;}close(){this.open=false;}
    querySelectorAll(tag){return this.children.flatMap(c=>[...(c.tagName===tag.toUpperCase()?[c]:[]),...c.querySelectorAll(tag)]);}
    reportValidity(){return this.querySelectorAll('input').concat(this.querySelectorAll('select')).every(i=>!i.required||i.value!==''&&(!i.min||Number(i.value)>=Number(i.min))&&(!i.max||Number(i.value)<=Number(i.max)));}
  }
  const nodes=new Map();for(const id of ['workout-wizard','wizard-notice','wizard-settings','wizard-counter','wizard-back','wizard-next','wizard-confirm','wizard-title','wizard-search','wizard-category','wizard-exercises','wizard-preview','wizard-close',...Array.from({length:3},(_,i)=>['wizard-step-'+(i+1),'wizard-marker-'+(i+1)]).flat()])nodes.set(id,new Element());
  nodes.get('workout-wizard').dataset.owner=owner;
  const exercises=[{exerciseId:1,exerciseName:'Squat',description:'Test',categoryId:1,categoryName:'Strength',active:true,minDurationMinutes:1,levels:[{id:1,name:'Easy',active:true,sets:3,reps:10,secondsPerSet:30,restSeconds:20,estimatedSeconds:130},{id:2,name:'Medium',active:true,sets:4,reps:12,secondsPerSet:35,restSeconds:30,estimatedSeconds:230}]},{exerciseId:2,exerciseName:'Walk',categoryId:2,categoryName:'Cardio',active:true,minDurationMinutes:10,levels:[]}];
  const calls=[],events=[];
  const api=async(path,options={})=>{calls.push({path,...options});if(path==='/exercises')return exercises;if(path==='/workouts/current/state'){if(current)return {workout:current};const e=Error('Not found');e.status=404;throw e;}if(path==='/workouts/from-selection')return submit(JSON.parse(options.body));throw Error('Unexpected API '+path);};
  const ctx={document:{getElementById:id=>nodes.get(id),createElement:t=>new Element(t)},window:{FitnessUI:{api,base:'',textNode:(t,text,cls)=>{const el=new Element(t,text);el.className=cls;return el;}},dispatchEvent:e=>events.push(e)},sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},crypto:{randomUUID:()=>crypto.randomUUID()},CustomEvent:function(type,init){this.type=type;this.detail=init?.detail;}};
  vm.runInNewContext(source,ctx);
  return {nodes,calls,events,storage,open:ctx.window.FitnessWizard.open,
    select(index=0){nodes.get('wizard-exercises').children[index].querySelectorAll('button')[0].onclick();},
    next(){nodes.get('wizard-next').onclick();},back(){nodes.get('wizard-back').onclick();},confirm(){return nodes.get('wizard-confirm').onclick();}};
}
let passed=0;async function test(name,work){await work();console.log('PASS '+name);passed++;}
(async()=>{
  await test('Opening only reads; cannot advance with no selection',async()=>{const e=env();await e.open();e.next();assert.equal(e.nodes.get('wizard-step-1').hidden,false);assert.ok(e.nodes.get('wizard-notice').textContent);assert.ok(e.calls.every(c=>!c.method));});
  await test('Three independent steps retain selected level on back/forward',async()=>{let payload;const e=env({submit:async p=>{payload=p;return {workoutId:1};}});await e.open();e.select();e.next();const select=e.nodes.get('wizard-settings').querySelectorAll('select')[0];select.value='2';select.onchange();e.next();assert.equal(e.nodes.get('wizard-step-3').hidden,false);assert.equal(e.nodes.get('wizard-step-2').hidden,true);e.back();assert.equal(e.nodes.get('wizard-settings').querySelectorAll('select')[0].value,2);e.next();await e.confirm();assert.equal(payload.items[0].levelId,2);assert.ok(e.events.some(e=>e.type==='fitnessai:workout-updated'));});
  await test('Manual targets must be paired and are preserved in request',async()=>{let payload;const e=env({submit:async p=>{payload=p;return {workoutId:1};}});await e.open();e.select(1);e.next();const [sets,reps]=e.nodes.get('wizard-settings').querySelectorAll('input');sets.value='3';sets.oninput();e.next();assert.equal(e.nodes.get('wizard-step-2').hidden,false);reps.value='10';reps.oninput();e.next();await e.confirm();assert.equal(payload.items[0].plannedSets,3);assert.equal(payload.items[0].plannedReps,10);});
  await test('Double confirm sends only one creation and never starts exercise',async()=>{let release;const gate=new Promise(r=>release=r),e=env({submit:async()=>{await gate;return {workoutId:1};}});await e.open();e.select();e.next();e.next();const a=e.confirm(),b=e.confirm();assert.equal(e.calls.filter(c=>c.method==='POST').length,1);release();await Promise.all([a,b]);assert.ok(!e.calls.some(c=>c.path.endsWith('/start')));});
  await test('Unknown outcome survives reload and retries exact request',async()=>{const storage=new Map(),sent=[];let failed=false;const submit=async p=>{sent.push(p);if(!failed){failed=true;throw Error('offline');}return {workoutId:1};};const e=env({storage,submit});await e.open();e.select();e.next();e.next();await e.confirm();const restored=env({storage,submit,current:{workoutId:1}});await restored.open();assert.equal(restored.nodes.get('wizard-step-3').hidden,false);await restored.confirm();assert.equal(sent[0].requestId,sent[1].requestId);assert.deepEqual(sent[0].items,sent[1].items);assert.equal(storage.size,0);});
  await test('Pending selections cannot leak across signed-in accounts',async()=>{const storage=new Map(),e=env({storage,submit:async()=>{throw Error('offline');}});await e.open();e.select();e.next();e.next();await e.confirm();const other=env({storage,owner:'two'});await other.open();assert.equal(other.nodes.get('wizard-counter').textContent,'0 bài đã chọn');});
  await test('Definitive validation failure permits correcting selection',async()=>{const e=env({submit:async()=>{const error=Error('Level changed');error.status=400;throw error;}});await e.open();e.select();e.next();e.next();await e.confirm();assert.equal(e.nodes.get('wizard-step-1').hidden,false);e.select();assert.equal(e.nodes.get('wizard-counter').textContent,'0 bài đã chọn');});
  await test('Existing active session returns user to session without creating',async()=>{const e=env({current:{workoutId:7}});await e.open();assert.equal(e.nodes.get('workout-wizard').open,false);assert.equal(e.calls.filter(c=>c.method==='POST').length,0);assert.ok(e.events.some(e=>e.type==='fitnessai:workout-updated'));});
  console.log(`Wizard: ${passed} scenarios passed (DOM/API fixtures only).`);
})().catch(e=>{console.error(e);process.exitCode=1;});
