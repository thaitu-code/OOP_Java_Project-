(() => {
  'use strict';
  const select=document.getElementById('meal-ai-select');if(!select)return;
  const base=document.querySelector('meta[name="app-base"]')?.content||'/';
  const calendar=document.getElementById('meal-ai-calendar'),notice=document.getElementById('meal-ai-notice'),logButton=document.getElementById('meal-ai-log-today');
  const names={BREAKFAST:'Bữa sáng',LUNCH:'Bữa trưa',AFTERNOON:'Bữa phụ chiều',DINNER:'Bữa tối'};
  let epoch=0,inputs=[];
  const today=()=>{const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const get=k=>parts.find(x=>x.type===k).value;return `${get('year')}-${get('month')}-${get('day')}`;};
  function elt(tag,text){const e=document.createElement(tag);if(text!=null)e.textContent=text;return e;}
  async function request(url,options={}){
    const csrf=document.querySelector('meta[name="_csrf"]')?.content,header=document.querySelector('meta[name="_csrf_header"]')?.content||'X-CSRF-TOKEN';
    const res=await fetch(base.replace(/\/$/,'')+url,{credentials:'same-origin',cache:'no-store',...options,headers:{Accept:'application/json',...(options.method?{[header]:csrf,'Content-Type':'application/json'}:{})}});
    if(res.redirected||res.headers.get('content-type')?.includes('text/html'))throw Error('Phiên đăng nhập hết hạn. Hãy đăng nhập lại.');
    const body=await res.json().catch(()=>null);if(!res.ok||!body)throw Error(body?.error||'Không tải được dữ liệu.');return body;
  }
  async function show(){
    const render=++epoch;calendar.replaceChildren();inputs=[];logButton.disabled=true;
    if(!select.value){notice.textContent='Chưa có thực đơn. Hãy tạo từ chat AI.';return;}
    const detail=await request('/api/ai/recommendations/'+encodeURIComponent(select.value));if(render!==epoch)return;
    const rec=detail.recommendation,byDate=new Map();
    for(const item of detail.meals){if(!byDate.has(item.date))byDate.set(item.date,[]);byDate.get(item.date).push(item);}
    notice.textContent=rec.title+(rec.status==='REPLACED'?' · Đã có phiên bản thay thế':' · Chọn đúng món đã ăn và sửa gram thực tế trước khi ghi.');
    const target=elt('p',rec.targetCalories==null?'Thực đơn cũ chưa lưu mục tiêu dinh dưỡng.':`Mục tiêu mỗi ngày: ${rec.targetCalories} kcal · Đạm ${rec.targetProtein??'chưa đặt'} g · Tinh bột ${rec.targetCarbs??'chưa đặt'} g · Béo ${rec.targetFat??'chưa đặt'} g`);calendar.append(target);
    for(const [date,items]of byDate){
      const card=elt('article');card.className='n-card';card.style.padding='18px';
      card.append(elt('h3',new Date(date+'T12:00:00Z').toLocaleDateString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'})));
      const total={calories:0,protein:0,carbs:0,fat:0};
      for(const meal of Object.keys(names)){
        const group=items.filter(i=>i.mealType===meal);if(!group.length)continue;card.append(elt('h4',names[meal]));const list=elt('ul');
        for(const item of group){
          for(const k of Object.keys(total))total[k]+=Number(item[k]);
          const li=elt('li');li.append(elt('span',`${item.foodName} — ${item.grams} g — ${item.calories} kcal${item.logged?' · Đã ghi vào nhật ký':''}`));
          if(date===today()&&!item.logged&&rec.status!=='REPLACED'){
            const label=elt('label'),check=elt('input');check.type='checkbox';check.setAttribute('aria-label','Đã ăn '+item.foodName);
            const grams=elt('input');grams.type='number';grams.min='0.01';grams.max='5000';grams.step='0.01';grams.value=item.grams;grams.setAttribute('aria-label','Khối lượng thực ăn của '+item.foodName+' (g)');
            label.append(check,elt('span',' Đã ăn · '),grams,elt('span',' g'));li.append(label);inputs.push({id:item.id,check,grams});
          }
          list.append(li);
        }card.append(list);
      }
      card.append(elt('strong',`Tổng dự kiến: ${total.calories.toFixed(0)} kcal · Đạm ${total.protein.toFixed(1)} g · Tinh bột ${total.carbs.toFixed(1)} g · Béo ${total.fat.toFixed(1)} g`));calendar.append(card);
    }
    logButton.disabled=!inputs.length;
  }
  async function load(){try{const plans=(await request('/api/ai/recommendations')).filter(p=>p.type==='MEAL_PLAN');const previous=select.value;select.replaceChildren();
    for(const p of plans)select.add(new Option((p.status==='REPLACED'?'[Đã thay thế] ':'')+p.title+' · '+new Date(p.createdAt).toLocaleDateString('vi-VN'),p.id));
    if(plans.some(p=>String(p.id)===previous))select.value=previous;await show();
  }catch(e){notice.textContent=e.message;}}
  logButton.addEventListener('click',async()=>{
    const chosen=inputs.filter(i=>i.check.checked);if(!chosen.length){notice.textContent='Hãy chọn những món bạn đã ăn.';return;}
    if(chosen.some(i=>!i.grams.checkValidity())){notice.textContent='Khối lượng phải từ 0.01 đến 5000 g.';return;}
    if(!confirm(`Xác nhận đã ăn ${chosen.length} món đã chọn với khối lượng vừa nhập?`))return;
    logButton.disabled=true;
    try{const result=await request('/api/ai/recommendations/'+encodeURIComponent(select.value)+'/log-meals',{method:'POST',body:JSON.stringify({items:chosen.map(i=>({id:i.id,grams:Number(i.grams.value)}))})});
      await show();notice.textContent=result.message;window.dispatchEvent(new CustomEvent('fitnessai:nutrition-updated'));
    }catch(e){notice.textContent=e.message;logButton.disabled=false;}
  });
  select.addEventListener('change',()=>show().catch(e=>notice.textContent=e.message));
  document.getElementById('meal-ai-refresh').addEventListener('click',load);
  document.querySelector('[data-tab="meal-plan"]')?.addEventListener('click',load);
  if(location.hash==='#meal-plan')load();
})();
