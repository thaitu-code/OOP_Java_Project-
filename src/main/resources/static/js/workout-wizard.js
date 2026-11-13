(() => {
  'use strict';
  const dialog=document.getElementById('workout-wizard');if(!dialog)return;
  const $=id=>document.getElementById(id), {api,textNode:n}=window.FitnessUI;
  const chosen=new Map();let exercises=[],step=1,busy=false,pending=null;
  const storageKey='fitness-workout-selection:'+dialog.dataset.owner;
  const levels=e=>(e.levels||[]).filter(l=>l.active);
  const setNotice=text=>{$('wizard-notice').textContent=text;};
  function save(){try{sessionStorage.setItem(storageKey,JSON.stringify({pending,items:[...chosen.values()]}));}catch{setNotice('Không lưu được bản nháp trong trình duyệt. Giữ trang này mở nếu cần thử lại.');}}
  function config(item){const e=exercises.find(e=>e.exerciseId===item.exerciseId);if(!e)return 'Bài đã thay đổi trong thư viện. Kiểm tra kết quả yêu cầu cũ trước khi sửa.';const l=levels(e).find(l=>l.id===item.levelId);return l?`${l.name} · ${l.sets} hiệp${l.reps?' × '+l.reps+' lần':''} · ${l.secondsPerSet}s/hiệp · nghỉ ${l.restSeconds}s · dự kiến ${Math.ceil(l.estimatedSeconds/60)} phút`:`Thủ công · tối thiểu ${e.minDurationMinutes} phút${item.plannedSets?' · '+item.plannedSets+' hiệp × '+item.plannedReps+' lần':''}`;}
  function go(next){
    if(busy)return;
    if(pending&&next!==3){setNotice('Hãy kiểm tra kết quả yêu cầu đã gửi trước khi chỉnh sửa.');return;}
    if(next>1&&!chosen.size){setNotice('Hãy chọn ít nhất một bài tập.');return;}
    if(next>2&&!$('wizard-settings').reportValidity())return;
    step=next;for(let i=1;i<=3;i++){$('wizard-step-'+i).hidden=i!==step;const marker=$('wizard-marker-'+i);marker.setAttribute('aria-current',i===step?'step':'false');marker.classList.toggle('active',i<=step);}
    $('wizard-back').hidden=step===1;$('wizard-next').hidden=step===3;$('wizard-confirm').hidden=step!==3;
    $('wizard-counter').textContent=chosen.size+' bài đã chọn';setNotice('');if(step===2)renderSettings();if(step===3)renderPreview();
    $('wizard-title').focus();
  }
  function renderCatalog(){
    const keyword=$('wizard-search').value.toLocaleLowerCase('vi'),category=$('wizard-category').value;
    const items=exercises.filter(e=>(!category||String(e.categoryId)===category)&&(e.exerciseName+' '+e.categoryName).toLocaleLowerCase('vi').includes(keyword));
    const list=$('wizard-exercises');list.replaceChildren();
    if(!items.length)list.append(n('p','Không có bài phù hợp. Hãy đổi bộ lọc.','empty-state'));
    for(const e of items){const card=n('article','','wizard-exercise');
      card.append(n('span',e.categoryName,'wizard-category'),n('h3',e.exerciseName),n('p',e.description||'Chưa có mô tả hướng dẫn.','muted'));
      const details=n('details'),summary=n('summary','Xem hướng dẫn');details.append(summary,n('p',e.description||'Chưa có hướng dẫn chi tiết.'));
      // Use only the media URL validated by the exercise library; no unrelated stock demonstrations.
      const url=e.mediaUrl;
      if(typeof url==='string'&&/^\/exercise-media\/[a-f0-9-]+\.(gif|mp4|webm)$/.test(url)){
        const media=document.createElement(/\.(mp4|webm)$/.test(url)?'video':'img');media.src=window.FitnessUI.base+url;if(media.tagName==='VIDEO'){media.controls=true;media.preload='none';}else{media.alt='Hướng dẫn '+e.exerciseName;media.loading='lazy';}details.append(media);
      }
      const b=n('button',chosen.has(e.exerciseId)?'Đã chọn ✓':'＋ Chọn bài','ui-button '+(chosen.has(e.exerciseId)?'':'secondary'));b.type='button';b.setAttribute('aria-pressed',String(chosen.has(e.exerciseId)));b.disabled=!!pending;
      b.onclick=()=>{if(chosen.has(e.exerciseId))chosen.delete(e.exerciseId);else{if(chosen.size>=50){setNotice('Mỗi buổi chọn tối đa 50 bài.');return;}chosen.set(e.exerciseId,{exerciseId:e.exerciseId,levelId:levels(e)[0]?.id||null,plannedSets:null,plannedReps:null});}save();renderCatalog();$('wizard-counter').textContent=chosen.size+' bài đã chọn';};
      card.append(details,b);list.append(card);
    }
  }
  function renderSettings(){
    const list=$('wizard-settings');list.replaceChildren();
    for(const item of chosen.values()){
      const e=exercises.find(e=>e.exerciseId===item.exerciseId),card=n('article','','wizard-setting');card.append(n('h3',e.exerciseName));
      if(levels(e).length){const label=n('label','Cấp độ'),select=document.createElement('select');select.required=true;select.disabled=!!pending;
        for(const l of levels(e)){const opt=n('option',l.name);opt.value=l.id;select.append(opt);}select.value=item.levelId;
        const description=n('p',config(item),'muted');select.onchange=()=>{item.levelId=Number(select.value);description.textContent=config(item);save();};label.append(select);card.append(label,description);
      }else{
        card.append(n('p',`Tối thiểu ${e.minDurationMinutes} phút. Bạn duyệt thời gian nghỉ khi vào bài.`,'muted'));
        const row=n('div','','wizard-fields');for(const [key,title,max] of [['plannedSets','Số hiệp dự kiến',30],['plannedReps','Số lần / hiệp',200]]){const label=n('label',title),input=document.createElement('input');input.type='number';input.min='1';input.max=String(max);input.step='1';input.value=item[key]??'';input.required=item.plannedSets!=null||item.plannedReps!=null;input.disabled=!!pending;input.oninput=()=>{item[key]=input.value?Number(input.value):null;const inputs=[...row.querySelectorAll('input')];inputs.forEach(i=>i.required=inputs.some(other=>other.value!==''));save();};label.append(input);row.append(label);}card.append(row);
      }list.append(card);
    }
  }
  function renderPreview(){
    const list=$('wizard-preview');list.replaceChildren();let order=0;
    for(const item of chosen.values()){const e=exercises.find(e=>e.exerciseId===item.exerciseId),card=n('article','','wizard-preview-row');card.append(n('span',String(++order).padStart(2,'0'),'wizard-order'),n('div',e?.exerciseName||'Bài #'+item.exerciseId),n('p',config(item),'muted'));list.append(card);}
    $('wizard-confirm').textContent=pending?'Kiểm tra / thử lại yêu cầu cũ':'Xác nhận tạo buổi tập';
  }
  async function open(initialId){
    if(busy)return;dialog.showModal();setNotice('Đang tải thư viện…');busy=true;$('wizard-next').disabled=true;
    try {
      const saved=JSON.parse(sessionStorage.getItem(storageKey)||'null');
      if(saved&&chosen.size===0){pending=saved.pending;for(const item of saved.items||[])chosen.set(item.exerciseId,item);}
      const [catalog,current]=await Promise.all([api('/exercises'),api('/workouts/current/state').catch(e=>{if(e.status===404)return null;throw e;})]);
      // Server still enforces this invariant if the current state changes after this read.
      if(current?.workout&&!pending){dialog.close();window.dispatchEvent(new CustomEvent('fitnessai:workout-updated',{detail:{message:'Tiếp tục buổi tập chưa kết thúc của bạn.'}}));return;}
      exercises=catalog.filter(e=>e.active&&(!(e.levels||[]).length||levels(e).length));
      if(!pending)for(const [id,item] of chosen) {const e=exercises.find(e=>e.exerciseId===id);if(!e)chosen.delete(id);else if(item.levelId&&!levels(e).some(l=>l.id===item.levelId))item.levelId=levels(e)[0]?.id||null;}
      if(initialId&&!pending&&exercises.some(e=>e.exerciseId===initialId)&&!chosen.has(initialId)){const e=exercises.find(e=>e.exerciseId===initialId);chosen.set(initialId,{exerciseId:initialId,levelId:levels(e)[0]?.id||null,plannedSets:null,plannedReps:null});}
      const select=$('wizard-category');select.replaceChildren(n('option','Tất cả nhóm'));select.firstChild.value='';
      for(const [id,name] of new Map(exercises.map(e=>[e.categoryId,e.categoryName]))){const opt=n('option',name);opt.value=id;select.append(opt);}renderCatalog();busy=false;go(pending?3:1);
    }catch(e){setNotice(e.message+' Đóng và mở lại để thử lại.');}finally{busy=false;$('wizard-next').disabled=false;}
  }
  $('wizard-confirm').onclick=async()=>{
    if(busy)return;busy=true;dialog.setAttribute('aria-busy','true');$('wizard-confirm').disabled=true;$('wizard-back').disabled=true;$('wizard-close').disabled=true;setNotice('Đang tạo buổi tập…');
    pending=pending||{requestId:crypto.randomUUID(),items:[...chosen.values()].map(i=>({...i}))};save();
    try{const result=await api('/workouts/from-selection',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(pending)});if(!result?.workoutId)throw Error('Chưa nhận được kết quả tạo buổi.');pending=null;chosen.clear();sessionStorage.removeItem(storageKey);dialog.close();window.dispatchEvent(new CustomEvent('fitnessai:workout-updated',{detail:{message:'Đã tạo buổi tập. Chọn Bắt đầu bài khi bạn sẵn sàng.'}}));}
    catch(e){if(e.status===400||e.status===409){pending=null;for(const id of chosen.keys())if(!exercises.some(e=>e.exerciseId===id))chosen.delete(id);save();busy=false;go(1);renderCatalog();setNotice(e.message+' Hãy kiểm tra lại lựa chọn hoặc đóng và mở lại để tải thư viện mới nhất.');}else{setNotice(e.message+' Nếu kết quả chưa rõ, giữ nguyên yêu cầu và bấm kiểm tra / thử lại.');$('wizard-confirm').textContent='Kiểm tra / thử lại yêu cầu cũ';}}
    finally{busy=false;dialog.setAttribute('aria-busy','false');$('wizard-confirm').disabled=false;$('wizard-back').disabled=false;$('wizard-close').disabled=false;}
  };
  $('wizard-next').onclick=()=>go(step+1);$('wizard-back').onclick=()=>go(step-1);
  $('wizard-close').onclick=()=>{if(!busy)dialog.close();};dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});
  $('wizard-search').oninput=renderCatalog;$('wizard-category').onchange=renderCatalog;
  window.FitnessWizard={open};
})();
