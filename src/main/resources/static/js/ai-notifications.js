(() => {
  'use strict';
  const main=document.querySelector('main');if(!main)return;
  const box=document.createElement('details'),title=document.createElement('summary'),list=document.createElement('div');
  box.hidden=true;box.style.cssText='padding:12px;border:1px solid #cbd5e1;border-radius:8px;margin:12px 0;background:white';
  box.append(title,list);main.prepend(box);
  const base=document.querySelector('meta[name="app-base"]')?.content||'/';
  async function refresh(){
    const res=await fetch(base.replace(/\/$/,'')+'/api/ai/notifications',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
    if(!res.ok||res.redirected||!res.headers.get('content-type')?.includes('json'))return;
    const items=await res.json();if(!Array.isArray(items))return;
    const unread=items.filter(n=>!n.read).length;title.textContent=`Thông báo (${unread} chưa đọc)`;box.hidden=!items.length;list.replaceChildren();
    for(const n of items.slice(0,10)){
      const row=document.createElement('p'),link=document.createElement('a');row.textContent=n.content+(n.applicable?'':' · Không còn áp dụng')+' ';
      link.textContent='Xem';link.href=base.replace(/\/$/,'')+n.link;row.append(link);
      if(!n.read){const button=document.createElement('button');button.type='button';button.textContent='Đã đọc';button.onclick=async()=>{
        button.disabled=true;
        try{const token=document.querySelector('meta[name="_csrf"]')?.content,header=document.querySelector('meta[name="_csrf_header"]')?.content||'X-CSRF-TOKEN';
          const result=await fetch(base.replace(/\/$/,'')+'/api/ai/notifications/'+n.id+'/read',{method:'PATCH',credentials:'same-origin',headers:{[header]:token}});
          if(!result.ok)throw Error('Chưa đánh dấu được');await refresh();
        }catch{button.disabled=false;button.textContent='Thử lại';}
      };row.append(button);}list.append(row);
    }
  }
  refresh().catch(()=>{});
  setInterval(()=>{if(!document.hidden)refresh().catch(()=>{});},60000);
  for(const event of ['fitnessai:nutrition-updated','fitnessai:workout-updated'])window.addEventListener(event,()=>refresh().catch(()=>{}));
})();
