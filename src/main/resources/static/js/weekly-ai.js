(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const form = $('weekly-ai-form');
  if (!form) return;
  const weekdays = ['Chủ nhật','Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7'];
  let selectedPlan = null;
  // en-CA can be locale-dependent; use parts to ensure YYYY-MM-DD.
  const vnToday = () => {const parts = new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const get=k=>parts.find(x=>x.type===k).value;return `${get('year')}-${get('month')}-${get('day')}`};
  const notice = message => { $('weekly-ai-notice').textContent = message; };
  const add = (parent, tag, text, className) => {const el=document.createElement(tag);el.textContent=text;if(className)el.className=className;parent.append(el);return el;};
  function weekDate(start, offset) {const d=new Date(start+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10);}
  const fullDate = date => new Date(date+'T12:00:00Z').toLocaleDateString('vi-VN',{timeZone:'UTC',weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'});
  async function request(path, options={}) {
    const headers={Accept:'application/json'};
    if (options.method && options.method!=='GET') {
      const csrf=document.querySelector('meta[name="_csrf"]')?.content;
      const csrfHeader=document.querySelector('meta[name="_csrf_header"]')?.content||'X-CSRF-TOKEN';
      headers[csrfHeader]=csrf;
      if(options.body)headers['Content-Type']='application/json';
    }
    let response;
    try {response=await fetch(path,{credentials:'same-origin',cache:'no-store',...options,headers:{...headers,...options.headers}});}
    catch {throw Error('Không kết nối được backend. Kiểm tra ứng dụng Spring Boot.');}
    if(response.redirected||response.headers.get('content-type')?.includes('text/html'))throw Error('Phiên đăng nhập hết hạn. Hãy đăng nhập lại.');
    const result=await response.json().catch(()=>null);
    if(!response.ok)throw Error(result?.error||`Server lỗi HTTP ${response.status}`);
    return result;
  }
  async function show(id) {
    const title=$('weekly-plan-title'),grid=$('weekly-calendar'),actions=$('weekly-apply-actions');
    title.replaceChildren();grid.replaceChildren();actions.hidden=true;selectedPlan=null;
    if(!id)return;
    const detail=await request(`/api/ai/recommendations/${encodeURIComponent(id)}`);
    const rec=detail.recommendation;selectedPlan=rec;
    add(title,'h3',rec.title);
    add(title,'p',rec.content);
    const start=rec.workoutStartDate;
    if(!start){notice('Kế hoạch chưa có ngày bắt đầu. Hãy tạo lại lịch bằng phiên bản mới.');return;}
    const end=weekDate(start,6);
    add(title,'p',`Thời gian: ${fullDate(start)} – ${fullDate(end)} · trạng thái: ${rec.status==='APPLIED'?'Đang áp dụng':rec.status==='REPLACED'?'Đã thay thế':'Chưa áp dụng'}`);
    actions.hidden=false;
    const applied=rec.status==='APPLIED';
    const apply=$('weekly-apply'),startButton=$('weekly-start-today');
    apply.hidden=applied;
    const todayString=vnToday();
    const inWeek=todayString>=start&&todayString<=end;
    const dayIndex=inWeek?(Math.round((Date.parse(todayString+'T12:00:00Z')-Date.parse(start+'T12:00:00Z'))/86400000)+1):0;
    const todayItems=inWeek?detail.exercises.filter(e=>e.dayOfWeek===dayIndex):[];
    startButton.hidden=!applied||!inWeek||todayItems.length===0;
    $('weekly-apply-note').textContent = todayString<start
      ? `Lịch bắt đầu ${fullDate(start)}. Apply chỉ kích hoạt lịch, không thêm bài hôm nay (${fullDate(todayString)}).`
      : todayString>end
        ? 'Tuần tập này đã qua. Hãy tạo lịch mới để tập trong tuần tiếp theo.'
        : todayItems.length===0
          ? 'Hôm nay là ngày nghỉ; Apply không thêm bài vào buổi tập.'
          : applied
            ? `Hôm nay có ${todayItems.length} bài. Bấm “Thêm các bài hôm nay” để bổ sung vào buổi tập hiện tại.`
            : `Hôm nay có ${todayItems.length} bài; bấm Apply để kích hoạt và thêm ngay vào buổi tập hiện tại (hoặc tạo mới).`;
    for(let i=1;i<=7;i++) {
      const date=weekDate(start,i-1);
      const card=document.createElement('article');card.className='ai-week-day'+(date===todayString?' is-today':'')+(date<todayString?' is-past':'');
      add(card,'h4',weekdays[new Date(date+'T12:00:00Z').getUTCDay()]);
      add(card,'span',fullDate(date),'day-meta');
      if(date===todayString)add(card,'span','Hôm nay','today-pill');
      const dayOfWeek=new Date(date+'T12:00:00Z').getUTCDay()||7;
      const entries=detail.exercises.filter(e=>e.dayOfWeek===dayOfWeek);
      if(!entries.length)add(card,'p','🌿 Nghỉ / phục hồi','ai-rest');
      else {const list=document.createElement('ul');for(const entry of entries){const li=document.createElement('li');add(li,'strong',entry.exerciseName);add(li,'small',`${entry.sets} hiệp × ${entry.reps} lần`);list.append(li);}card.append(list);}
      grid.append(card);
    }
  }
  async function refresh(selected) {
    const current=selected||$('weekly-plan-select').value;
    const all=await request('/api/ai/recommendations');
    const plans=all.filter(x=>x.type==='WORKOUT_PLAN');
    const select=$('weekly-plan-select');select.replaceChildren();
    if(!plans.length){select.add(new Option('Chưa có lịch',''));await show(null);return;}
    plans.forEach(p=>select.add(new Option(`${p.status==='APPLIED'?'[Đang áp dụng] ':p.status==='REPLACED'?'[Đã thay thế] ':''}${p.title} · ${p.workoutStartDate?fullDate(p.workoutStartDate):new Date(p.createdAt).toLocaleDateString('vi-VN')}`,String(p.id))));
    select.value=plans.some(p=>String(p.id)===String(current))?String(current):String(plans[0].id);
    await show(select.value);
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const button=$('weekly-generate');button.disabled=true;notice('Đang tạo lịch tập từ thư viện…');
    try{const data=Object.fromEntries(new FormData(form));data.daysPerWeek=Number(data.daysPerWeek);
      const result=await request('/api/ai/weekly-workout',{method:'POST',body:JSON.stringify(data)});
      await refresh(result.recommendation.id);notice('Đã tạo lịch tập 7 ngày. Xem lịch rồi bấm Apply để xác nhận.');
    }catch(e){notice('Không tạo được lịch: '+e.message);}finally{button.disabled=false;}
  });
  $('weekly-apply').addEventListener('click',async()=>{
    if(!selectedPlan||!confirm('Kích hoạt lịch tập này và thay thế lịch đã Apply trước đó? Nếu hôm nay có bài, chúng sẽ được thêm vào buổi tập hiện tại hoặc tạo buổi mới. Nếu chưa đến ngày tập, sẽ chưa thêm bài.'))return;
    const button=$('weekly-apply');button.disabled=true;
    try{const id=selectedPlan.id;const response=await request(`/api/ai/recommendations/${id}/apply-workout`,{method:'POST'});
      await show(id);
      // Apply is the user's confirmation to write today's workout immediately.
      if(!$('weekly-start-today').hidden){
        try{const workout=await request(`/api/ai/recommendations/${id}/start-today-workout`,{method:'POST'});
          notice(workout.message);location.assign('/workout#session');return;
        }catch(e){notice('Lịch đã áp dụng, nhưng chưa thể thêm bài hôm nay: '+e.message);return;}
      }
      notice(response.message);
    }
    catch(e){notice('Không áp dụng được: '+e.message);}finally{button.disabled=false;}
  });
  $('weekly-start-today').addEventListener('click',async()=>{
    if(!selectedPlan||!confirm('Thêm bài tập hôm nay từ lịch AI vào buổi tập đang diễn ra (hoặc tạo buổi mới)? Bài trùng sẽ được bỏ qua.'))return;
    const button=$('weekly-start-today');button.disabled=true;
    try{const result=await request(`/api/ai/recommendations/${selectedPlan.id}/start-today-workout`,{method:'POST'});
      notice(result.message);location.assign('/workout#session');}
    catch(e){notice(e.message);button.disabled=false;}
  });
  $('weekly-plan-select').addEventListener('change',()=>show($('weekly-plan-select').value).catch(e=>notice(e.message)));
  $('weekly-refresh').addEventListener('click',()=>refresh().catch(e=>notice(e.message)));
  document.querySelector('[data-tab="ai-calendar"]')?.addEventListener('click',()=>refresh().catch(e=>notice(e.message)));
  refresh().catch(e=>notice('Không tải được lịch đã lưu: '+e.message));
})();
