(() => {
  const select=document.getElementById('meal-ai-select');
  if(!select)return;
  const calendar=document.getElementById('meal-ai-calendar');
  const notice=document.getElementById('meal-ai-notice');
  const names={BREAKFAST:'Bữa sáng',LUNCH:'Bữa trưa',AFTERNOON:'Bữa phụ chiều',DINNER:'Bữa tối'};
  async function fetchJson(url){const res=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}});if(!res.ok)throw Error('HTTP '+res.status);return res.json()}
  function elt(tag,text){const e=document.createElement(tag);e.textContent=text;return e}
  async function show(){calendar.replaceChildren();if(!select.value){notice.textContent='Chưa có thực đơn. Hãy tạo từ Chat AI.';return}
    const detail=await fetchJson('/api/ai/recommendations/'+encodeURIComponent(select.value));
    const byDate=new Map();for(const item of detail.meals){if(!byDate.has(item.date))byDate.set(item.date,[]);byDate.get(item.date).push(item)}
    notice.textContent=detail.recommendation.title+' — '+byDate.size+' ngày';
    for(const [date,items] of byDate){const card=elt('article','');card.className='n-card';card.style.padding='18px';
      card.append(elt('h3',new Date(date+'T12:00:00').toLocaleDateString('vi-VN',{weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'})));
      let total=0;
      for(const meal of ['BREAKFAST','LUNCH','AFTERNOON','DINNER']){const group=items.filter(x=>x.mealType===meal);if(!group.length)continue;
        card.append(elt('h4',names[meal]));const list=elt('ul','');for(const item of group){total+=Number(item.calories);list.append(elt('li',`${item.foodName} — ${item.grams} g — ${item.calories} kcal`))}card.append(list)}
      card.append(elt('strong','Tổng dự kiến: '+total.toFixed(0)+' kcal'));calendar.append(card)}
  }
  async function load(){try{const plans=(await fetchJson('/api/ai/recommendations')).filter(x=>x.type==='MEAL_PLAN');const previous=select.value;select.replaceChildren();
    for(const p of plans)select.add(new Option(p.title+' · '+new Date(p.createdAt).toLocaleDateString('vi-VN'),p.id));
    if(plans.some(p=>String(p.id)===previous))select.value=previous;await show();
  }catch(e){notice.textContent='Không tải được lịch ăn uống: '+e.message}}
  document.getElementById('meal-ai-log-today').addEventListener('click',async()=>{
    if(!select.value)return;
    if(!confirm('Xác nhận bạn đã ăn các món trong thực đơn AI hôm nay? Hành động này sẽ ghi vào nhật ký và có thể tạo mục trùng nếu đã ghi trước đó.'))return;
    const csrf=document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader=document.querySelector('meta[name="_csrf_header"]')?.content||'X-CSRF-TOKEN';
    try{const res=await fetch('/api/ai/recommendations/'+encodeURIComponent(select.value)+'/log-today',{method:'POST',credentials:'same-origin',headers:{[csrfHeader]:csrf}});
      const body=await res.json().catch(()=>({}));if(!res.ok)throw Error(body.error||'HTTP '+res.status);
      notice.textContent=body.message+' — chuyển sang Nhật ký để xem.';
    }catch(e){notice.textContent=e.message}
  });
  select.addEventListener('change',()=>show().catch(e=>notice.textContent=e.message));
  document.getElementById('meal-ai-refresh').addEventListener('click',load);
  document.querySelector('[data-tab="meal-plan"]')?.addEventListener('click',load);
  if(location.hash==='#meal-plan')load();
})();
