(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const form = $('weekly-ai-form');
  if (!form) return;
  const days = ['Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7','Chủ nhật'];
  const notice = text => { $('weekly-ai-notice').textContent = text; };
  async function request(path, options={}) {
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    let response;
    try { response=await fetch(path,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json',...(options.method?{'Content-Type':'application/json',[csrfHeader]:csrf}: {})},...options}); }
    catch { throw new Error('Không kết nối được backend. Hãy kiểm tra Spring Boot, cổng 8081 và kết nối mạng.'); }
    if (response.redirected || response.headers.get('content-type')?.includes('text/html')) throw new Error('Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.');
    const result=await response.json().catch(()=>null);
    if (!response.ok) throw new Error(result?.error || `Server lỗi HTTP ${response.status}`);
    return result;
  }
  async function show(id) {
    const title=$('weekly-plan-title'), grid=$('weekly-calendar');
    title.replaceChildren();grid.replaceChildren();
    if (!id) return;
    const detail=await request(`/api/ai/recommendations/${encodeURIComponent(id)}`);
    const heading=document.createElement('h3');heading.textContent=detail.recommendation.title;
    const desc=document.createElement('p');desc.textContent=detail.recommendation.content;
    title.append(heading,desc);
    for(let i=1;i<=7;i++) {
      const card=document.createElement('article');card.className='ai-week-day';
      const day=document.createElement('h4');day.textContent=days[i-1];card.append(day);
      const entries=detail.exercises.filter(e=>e.dayOfWeek===i);
      if(!entries.length) {const rest=document.createElement('p');rest.className='ai-rest';rest.textContent='🌿 Nghỉ / phục hồi';card.append(rest);}
      else {const list=document.createElement('ul');for(const entry of entries){const li=document.createElement('li');
        const name=document.createElement('strong');name.textContent=entry.exerciseName;
        const reps=document.createElement('small');reps.textContent=`${entry.sets} hiệp × ${entry.reps} lần`;
        li.append(name,reps);list.append(li);}card.append(list);}
      grid.append(card);
    }
  }
  async function refresh(selected) {
    const current=selected||$('weekly-plan-select').value;
    const all=await request('/api/ai/recommendations');
    const plans=all.filter(x=>x.type==='WORKOUT_PLAN');
    const select=$('weekly-plan-select');select.replaceChildren();
    if(!plans.length){const option=new Option('Chưa có lịch','');select.add(option);await show(null);return;}
    plans.forEach(p=>select.add(new Option(`${p.title} · ${new Date(p.createdAt).toLocaleDateString('vi-VN')}`,String(p.id))));
    select.value=plans.some(p=>String(p.id)===String(current))?String(current):String(plans[0].id);
    await show(select.value);
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const button=$('weekly-generate');button.disabled=true;notice('Đang tạo kế hoạch từ các bài tập có sẵn…');
    try {const data=Object.fromEntries(new FormData(form));data.daysPerWeek=Number(data.daysPerWeek);
      const result=await request('/api/ai/weekly-workout',{method:'POST',body:JSON.stringify(data)});
      await refresh(result.recommendation.id);notice('Đã tạo và lưu lịch 7 ngày. Bạn có thể xem từng ngày ở bên dưới.');
    }catch(e){notice(`Không tạo được lịch: ${e.message}`);}finally{button.disabled=false;}
  });
  $('weekly-plan-select').addEventListener('change',()=>show($('weekly-plan-select').value).catch(e=>notice(e.message)));
  $('weekly-refresh').addEventListener('click',()=>refresh().catch(e=>notice(e.message)));
  refresh().catch(e=>notice(`Không tải được lịch đã lưu: ${e.message}`));
})();
