/* Approved meal-plan UI. Plans and journal entries remain separate; the server owns writes. */
(() => {
  'use strict';
  const $=id=>document.getElementById(id),select=$('meal-ai-select');if(!select)return;
  const base=(document.querySelector('meta[name="app-base"]')?.content||'/').replace(/\/$/,'');
  const storageKey='fitness-meal-pending:'+($('nutrition-app')?.dataset.owner||'current-account');
  const meals={BREAKFAST:['Bữa sáng','☀'],LUNCH:['Bữa trưa','♜'],AFTERNOON:['Bữa phụ chiều','♧'],DINNER:['Bữa tối','☾']};
  const state={detail:null,planId:null,date:null,week:null,inputs:[],drafts:new Map(),epoch:0,listEpoch:0,loading:false,writing:false,ready:false,locked:false,pending:null};
  const fmt=v=>new Intl.NumberFormat('vi-VN',{maximumFractionDigits:1}).format(v);
  const today=()=>{const p=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const get=k=>p.find(x=>x.type===k).value;return `${get('year')}-${get('month')}-${get('day')}`;};
  const validDate=d=>/^\d{4}-\d{2}-\d{2}$/.test(d||'')&&Number.isFinite(Date.parse(d+'T12:00:00Z'))&&new Date(d+'T12:00:00Z').toISOString().slice(0,10)===d;
  const add=(d,n)=>{const date=new Date(d+'T12:00:00Z');date.setUTCDate(date.getUTCDate()+n);return date.toISOString().slice(0,10);};
  const monday=d=>add(d,-((new Date(d+'T12:00:00Z').getUTCDay()+6)%7));
  const longDate=d=>new Date(d+'T12:00:00Z').toLocaleDateString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'});
  const n=(tag,text,cls)=>{const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;};
  const message=(text,error=false)=>{$('meal-ai-notice').textContent=text;$('meal-ai-notice').className='mp-notice'+(error?' error':'');};
  function persist(){try{if(state.pending)sessionStorage.setItem(storageKey,JSON.stringify(state.pending));else sessionStorage.removeItem(storageKey);}catch{ /* In-memory pending payload still protects retries in this tab. */ }}
  try{const p=JSON.parse(sessionStorage.getItem(storageKey)||'null');if(p&&Number.isSafeInteger(p.planId)&&validDate(p.date)&&Array.isArray(p.items)&&p.items.length)state.pending=p;}catch{ /* Invalid local state never authorizes a write. */ }
  async function request(path,options={}){
    const writing=options.method&&options.method!=='GET',controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),20000);
    try{
      const headers={Accept:'application/json'};
      if(writing){const csrf=document.querySelector('meta[name="_csrf"]')?.content;if(!csrf)throw Error('Phiên bảo vệ đã hết. Hãy tải lại trang.');headers[document.querySelector('meta[name="_csrf_header"]')?.content||'X-CSRF-TOKEN']=csrf;headers['Content-Type']='application/json';}
      const res=await fetch(base+path,{credentials:'same-origin',cache:'no-store',...options,headers,signal:controller.signal});
      if(res.redirected||res.status===401||res.headers.get('content-type')?.includes('text/html')){state.locked=true;throw Error('Phiên đăng nhập hết hạn. Hãy đăng nhập lại.');}
      const body=await res.json().catch(()=>null);
      if(!res.ok){const e=Error(body?.error||body?.message||'Không tải được dữ liệu. Hãy thử lại.');e.status=res.status;if(res.status===403)state.locked=true;throw e;}
      if(!body)throw Error('Chưa nhận được phản hồi hợp lệ từ máy chủ.');return body;
    }catch(e){if(e.name==='AbortError'||e instanceof TypeError)throw Error(writing?'Chưa xác nhận được kết quả ghi. Hãy kiểm tra hoặc thử lại yêu cầu cũ.':'Không tải được thực đơn. Kiểm tra kết nối và bấm Làm mới.');throw e;}
    finally{clearTimeout(timeout);}
  }
  const rows=()=>state.detail?.meals.filter(i=>i.date===state.date)||[];
  const editable=()=>state.ready&&!state.loading&&!state.writing&&!state.locked&&!state.pending&&state.date===today()&&state.detail?.recommendation.status!=='REPLACED';
  const weightValid=value=>value!==''&&Number.isFinite(Number(value))&&Number(value)>=0.01&&Number(value)<=5000&&Math.abs(Number(value)*100-Math.round(Number(value)*100))<1e-7;
  function rememberInputs(){for(const input of state.inputs)state.drafts.set(input.item.id,{checked:input.check.checked,grams:input.grams.value});}
  function chosen(){return state.inputs.filter(i=>i.check.checked&&!i.item.logged);}
  function amount(item,grams){return Number(item.grams)>0?Number(item.calories)*Number(grams)/Number(item.grams):0;}
  function controls(){
    const blocked=state.loading||state.writing||state.locked;
    select.disabled=blocked||!!state.pending;$('meal-ai-refresh').disabled=blocked;
    for(const input of state.inputs){input.check.disabled=!editable();input.grams.disabled=!editable();}
    $('meal-week-prev').disabled=blocked||!!state.pending||!state.ready||state.week<=monday(state.minimum||today());
    $('meal-week-next').disabled=blocked||!!state.pending||!state.ready||state.week>=monday(state.maximum||today());
    for(const b of state.dayButtons||[])b.disabled=blocked||!!state.pending;
    const selected=chosen();
    $('meal-ai-log-today').disabled=blocked||!state.ready||(state.pending?state.pending.date!==today():!editable()||!selected.length||selected.some(i=>!weightValid(i.grams.value)));
    $('meal-ai-log-today').textContent=state.writing?'Đang ghi nhật ký…':state.pending?'Kiểm tra / thử lại yêu cầu cũ':selected.length?`Ghi ${selected.length} món đã ăn`:'Ghi các món đã ăn';
  }
  function summary(){
    const selected=chosen(),pending=state.pending;
    const list=$('meal-selected-list');list.replaceChildren();
    const display=pending?pending.items.map(p=>({item:state.detail?.meals.find(i=>i.id===p.id),grams:p.grams})):selected.map(i=>({item:i.item,grams:i.grams.value}));
    $('meal-selected-count').textContent=display.length+' món đã chọn';
    let total=0,invalid=false;
    if(!display.length)list.append(n('p','Chọn những món bạn đã ăn từ thực đơn bên cạnh.'));
    else{const ul=n('ul');for(const row of display){const li=n('li');li.append(n('span',row.item?.foodName||'Món trong yêu cầu trước'),n('small',weightValid(String(row.grams))?fmt(Number(row.grams))+' g':'Kiểm tra gram'));ul.append(li);if(row.item&&weightValid(String(row.grams)))total+=amount(row.item,row.grams);else invalid=true;}list.append(ul);}
    $('meal-selected-calories').textContent=invalid?'—':fmt(total)+' kcal';
    $('meal-summary-note').textContent=state.locked?'Hãy đăng nhập lại để tiếp tục.':pending?'Yêu cầu trước chưa rõ kết quả. Giữ nguyên lượng đã xác nhận.':state.detail?.recommendation.status==='REPLACED'?'Thực đơn đã thay thế. Hãy chọn phiên bản mới.':state.date!==today()?'Chỉ ghi món ăn cho hôm nay theo giờ Việt Nam.':selected.some(i=>!weightValid(i.grams.value))?'Khối lượng cần từ 0,01 đến 5.000 g, tối đa 2 chữ số thập phân.':'Bạn có thể sửa gram trước khi ghi.';
    controls();
  }
  function targets(){
    const box=$('meal-ai-targets');box.replaceChildren();const rec=state.detail.recommendation;
    for(const [key,label,unit,icon] of [['targetCalories','Năng lượng','kcal','ϟ'],['targetProtein','Đạm','g','P'],['targetCarbs','Tinh bột','g','◉'],['targetFat','Chất béo','g','♧']]){
      const card=n('article',null,'mp-target'),data=n('div');card.append(n('span',icon,'mp-target-icon'));const value=rec[key];data.append(n('small',label));const number=n('strong',value==null?'Chưa đặt':fmt(Number(value)));if(value!=null)number.append(n('span',unit,'mp-unit'));data.append(number);card.append(data);box.append(card);
    }
  }
  function week(){
    const end=add(state.week,6),sameMonth=state.week.slice(0,7)===end.slice(0,7);
    $('meal-week-label').textContent=sameMonth?`${state.week.slice(8)} – ${end.slice(8)} tháng ${Number(end.slice(5,7))}, ${end.slice(0,4)}`:`${state.week.slice(8)}/${state.week.slice(5,7)} – ${end.slice(8)}/${end.slice(5,7)}/${end.slice(0,4)}`;
    const dates=new Set(state.detail.meals.map(i=>i.date)),box=$('meal-week-days');box.replaceChildren();state.dayButtons=[];
    for(let i=0;i<7;i++){
      const date=add(state.week,i),button=n('button',null,'mp-day-button'+(date===state.date?' active':'')+(dates.has(date)?' has-meals':'')+(date===today()?' today':''));button.type='button';button.setAttribute('aria-label',longDate(date)+(dates.has(date)?' · có món dự kiến':' · chưa có món'));button.setAttribute('aria-pressed',String(date===state.date));button.append(n('span',i===6?'CN':'T'+(i+2)),n('strong',String(Number(date.slice(8)))));
      button.addEventListener('click',()=>{if(state.loading||state.writing||state.pending)return;rememberInputs();state.date=date;renderDay();});box.append(button);state.dayButtons.push(button);
    }
  }
  function safeImage(value){
    if(typeof value!=='string')return null;
    if(/^\/images\/[a-zA-Z0-9_-]+\.(?:jpg|jpeg|png|webp|gif)$/.test(value))return base+value;
    try{let url=new URL(value);if(!['http:','https:'].includes(url.protocol)||url.username||url.password||!url.hostname)return null;
      if(url.hostname==='github.com'&&/^\/[^/]+\/[^/]+\/(blob|raw)\/.+/.test(url.pathname))url=new URL('https://raw.githubusercontent.com'+url.pathname.replace(/^(\/[^/]+\/[^/]+)\/(blob|raw)\//,'$1/'));
      return /\.(png|jpe?g|webp|gif)$/i.test(url.pathname)?url.href:null;
    }catch{return null;}
  }
  function foodRow(item){
    const row=n('div',null,'mp-food-row'+(item.logged?' is-logged':'')),url=safeImage(item.imageUrl),fallback=n('span','◉','mp-food-placeholder');fallback.setAttribute('aria-hidden','true');
    if(url){const img=n('img',null,'mp-food-image');img.src=url;img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';img.addEventListener('error',()=>{img.hidden=true;fallback.hidden=false;});fallback.hidden=true;row.append(img,fallback);}else row.append(fallback);
    // The fallback image shares one grid cell with the real image (only one is visible).
    const text=n('div',null,'mp-food-text'),energy=n('small',fmt(Number(item.calories))+' kcal','mp-food-energy');text.append(n('strong',item.foodName,'mp-food-name'),energy);row.append(text);
    if(item.logged){
      energy.textContent=item.loggedCalories!=null?'Đã ghi '+fmt(Number(item.loggedCalories))+' kcal':'Dự kiến '+fmt(Number(item.calories))+' kcal';
      const status=n('span',item.loggedGrams!=null?fmt(Number(item.loggedGrams))+' g':fmt(Number(item.grams))+' g dự kiến','mp-row-quantity');status.append(n('small','✓ Đã ghi nhật ký','mp-logged'));row.append(status);return row;
    }
    const draft=state.drafts.get(item.id)||{checked:false,grams:String(item.grams)},field=n('div',null,'mp-grams-field'),grams=n('input');grams.className='mp-grams';grams.type='number';grams.min='0.01';grams.max='5000';grams.step='0.01';grams.value=String(draft.grams);grams.setAttribute('aria-label','Khối lượng thực ăn của '+item.foodName+' (g)');
    energy.textContent=weightValid(grams.value)?fmt(amount(item,grams.value))+' kcal':'Kiểm tra khối lượng';grams.setAttribute('aria-invalid',String(!weightValid(grams.value)));
    const check=n('input');check.type='checkbox';check.className='mp-check';check.checked=draft.checked;check.setAttribute('aria-label','Đã ăn '+item.foodName);
    const update=()=>{state.drafts.set(item.id,{checked:check.checked,grams:grams.value});const valid=weightValid(grams.value);grams.setAttribute('aria-invalid',String(!valid));energy.textContent=valid?fmt(amount(item,grams.value))+' kcal':'Kiểm tra khối lượng';summary();};
    grams.addEventListener('input',update);check.addEventListener('change',update);field.append(grams,n('span','g'));row.append(field,check);state.inputs.push({item,grams,check});
    return row;
  }
  function renderDay(){
    const rec=state.detail.recommendation;state.inputs=[];week();
    $('meal-ai-date').textContent=longDate(state.date);
    $('meal-ai-date-note').textContent=rec.status==='REPLACED'?'Thực đơn đã được thay thế; chỉ xem lại.':state.date===today()?'Chọn món đã ăn và nhập lượng thực tế.':'Bạn đang xem kế hoạch của ngày khác; chỉ ghi món cho hôm nay.';
    const box=$('meal-ai-calendar');box.replaceChildren();box.setAttribute('aria-busy','false');
    for(const [key,[name,icon]] of Object.entries(meals)){
      const items=rows().filter(i=>i.mealType===key),card=n('article',null,'mp-meal '+key),head=n('div',null,'mp-meal-heading'),text=n('div');head.append(n('span',icon,'mp-meal-icon'));text.append(n('h4',name),n('small',items.length+' món'));head.append(text);card.append(head);
      if(!items.length)card.append(n('p','Chưa có món dự kiến trong bữa này.','mp-meal-empty'));
      else for(const item of items)card.append(foodRow(item));box.append(card);
    }
    summary();
  }
  function notifyUpdated(){window.dispatchEvent(new CustomEvent('fitnessai:nutrition-updated'));}
  function reconcilePending(){
    if(!state.pending)return false;const pending=state.pending;
    if(pending.items.every(p=>state.detail.meals.some(i=>i.id===p.id&&i.logged))){for(const p of pending.items)state.drafts.delete(p.id);state.pending=null;persist();notifyUpdated();message('Các món trong yêu cầu trước đã được ghi. Không ghi lại trùng.');return true;}
    if(pending.date!==today()){state.pending=null;persist();message('Đã sang ngày mới. Yêu cầu cũ không được gửi lại; hãy kiểm tra nhật ký ngày trước.');return true;}return false;
  }
  async function show(){
    const epoch=++state.epoch,id=select.value;rememberInputs();state.ready=false;state.loading=true;controls();
    $('meal-ai-calendar').setAttribute('aria-busy','true');$('meal-ai-calendar').replaceChildren(n('p','Đang tải thực đơn…','mp-skeleton'));
    try{
      if(!id){state.detail=null;state.inputs=[];$('meal-ai-content').hidden=true;$('meal-ai-empty').hidden=false;$('meal-ai-plan-status').textContent='';message('');return;}
      const detail=await request('/api/ai/recommendations/'+encodeURIComponent(id));if(epoch!==state.epoch||id!==select.value)return;
      if(!detail.recommendation||detail.recommendation.type&&detail.recommendation.type!=='MEAL_PLAN'||!Array.isArray(detail.meals)||detail.meals.some(i=>!validDate(i.date)||!Number.isFinite(Number(i.grams))||Number(i.grams)<=0))throw Error('Dữ liệu thực đơn không hợp lệ. Bấm Làm mới để kiểm tra lại.');
      const changed=state.planId!==String(id);state.planId=String(id);state.detail=detail;
      const dates=[...new Set(detail.meals.map(i=>i.date))].sort(),localToday=today();
      if(state.pending)state.date=state.pending.date;else if(changed||!state.date)state.date=dates.includes(localToday)?localToday:dates.find(d=>d>=localToday)||dates.at(-1)||localToday;
      state.week=monday(state.date);state.minimum=[localToday,...dates].sort()[0];state.maximum=[localToday,...dates].sort().at(-1);
      $('meal-ai-content').hidden=false;$('meal-ai-empty').hidden=true;
      const replaced=detail.recommendation.status==='REPLACED',status=$('meal-ai-plan-status');status.className='mp-status'+(replaced?' replaced':detail.confirmed?'':' saved');status.textContent=replaced?'Đã thay thế':detail.confirmed?'✓ Đã xác nhận':'Đã lưu';
      message(replaced?'Thực đơn này đã có phiên bản thay thế. Hãy chọn thực đơn mới để ghi món.':state.pending?'Yêu cầu trước chưa rõ kết quả. Kiểm tra hoặc thử lại đúng các món đã xác nhận.':'');
      reconcilePending();state.ready=true;targets();renderDay();
    }catch(e){if(epoch===state.epoch){state.ready=false;state.inputs=[];$('meal-ai-calendar').replaceChildren(n('p','Chưa tải được thực đơn. Bấm Làm mới để thử lại.','mp-skeleton'));message(e.message,true);}}
    finally{if(epoch===state.epoch){state.loading=false;summary();}}
  }
  async function load(){
    if(state.loading||state.writing||state.locked)return;const listEpoch=++state.listEpoch;++state.epoch;state.loading=true;state.ready=false;controls();
    message('Đang tải danh sách thực đơn…');
    try{
      const all=await request('/api/ai/recommendations');if(listEpoch!==state.listEpoch)return;if(!Array.isArray(all))throw Error('Danh sách thực đơn không hợp lệ.');
      const plans=all.filter(p=>p.type==='MEAL_PLAN'),old=state.pending?String(state.pending.planId):select.value;select.replaceChildren();
      for(const p of plans)select.add(new Option((p.status==='REPLACED'?'[Đã thay thế] ':'')+p.title,p.id));
      if(state.pending&&!plans.some(p=>String(p.id)===old))select.add(new Option('Kiểm tra thực đơn của yêu cầu trước',state.pending.planId));
      select.value=plans.some(p=>String(p.id)===old)||state.pending?old:String(plans.find(p=>p.status!=='REPLACED')?.id||plans[0]?.id||'');
      state.loading=false;await show();
    }catch(e){state.ready=false;message(e.message,true);}finally{state.loading=false;controls();}
  }
  async function log(){
    if(state.writing||state.loading||state.locked||!state.ready)return;
    rememberInputs();const selected=chosen();
    if(!state.pending){
      if(!editable()){message('Chỉ ghi các món của hôm nay từ thực đơn chưa bị thay thế.');return;}
      if(!selected.length){message('Hãy chọn những món bạn đã ăn.');return;}
      if(selected.some(i=>!weightValid(i.grams.value))){message('Khối lượng phải từ 0,01 đến 5.000 g, tối đa 2 chữ số thập phân.',true);return;}
      const expectedDate=state.date;
      if(!confirm(`Ghi ${selected.length} món đã chọn vào nhật ký ngày ${expectedDate.split('-').reverse().join('/')} với lượng gram vừa nhập?`))return;
      if(expectedDate!==today()){renderDay();message('Đã sang ngày mới. Chọn hôm nay và kiểm tra lại các món trước khi ghi.');return;}
      state.pending={planId:Number(select.value),date:expectedDate,items:selected.map(i=>({id:i.item.id,grams:Number(i.grams.value)}))};persist();
    }
    if(state.pending.date!==today()){await show();return;}
    state.writing=true;controls();const pending=state.pending;
    try{
      const result=await request('/api/ai/recommendations/'+pending.planId+'/log-meals',{method:'POST',body:JSON.stringify({items:pending.items})});
      for(const item of pending.items)state.drafts.delete(item.id);state.pending=null;persist();notifyUpdated();
      // A successful write is never replayed merely because the following read failed.
      state.writing=false;await show();message((result.message||'Đã cập nhật nhật ký hôm nay.')+(state.ready?'':' Chưa tải lại được thực đơn; bấm Làm mới để kiểm tra.'),!state.ready);
    }catch(e){
      if(e.status>=400&&e.status<500){state.pending=null;persist();state.writing=false;await show();}
      message(e.message+(state.pending?' Giữ nguyên yêu cầu và bấm kiểm tra / thử lại; hệ thống chống ghi món trùng.':''),true);
    }finally{state.writing=false;summary();}
  }
  $('meal-ai-log-today').addEventListener('click',log);
  select.addEventListener('change',()=>{if(!state.writing&&!state.pending)show();});
  $('meal-ai-refresh').addEventListener('click',load);
  function moveWeek(delta){if(!state.ready||state.loading||state.writing||state.pending)return;rememberInputs();state.date=add(state.date,delta*7);state.week=monday(state.date);renderDay();}
  $('meal-week-prev').addEventListener('click',()=>moveWeek(-1));$('meal-week-next').addEventListener('click',()=>moveWeek(1));
  document.querySelector('[data-tab="meal-plan"]')?.addEventListener('click',load);
  let lastToday=today();setInterval(()=>{const date=today();if(date!==lastToday){lastToday=date;if(state.ready){rememberInputs();renderDay();message('Đã sang ngày mới theo giờ Việt Nam. Chọn ngày hôm nay trước khi ghi món.');}}},30000);
  if(location.hash==='#meal-plan')load();
})();
