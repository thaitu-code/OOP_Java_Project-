(() => {
  'use strict';
  const base = (document.querySelector('meta[name="app-base"]')?.content || '/').replace(/\/$/, '');
  async function api(path, options = {}) {
    const headers = {Accept:'application/json', ...options.headers};
    if (options.method && options.method !== 'GET') headers[document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'] = document.querySelector('meta[name="_csrf"]')?.content || '';
    const response = await fetch(base + path, {credentials:'same-origin',cache:'no-store',...options,headers});
    if (response.redirected || response.status === 401 || response.headers.get('content-type')?.includes('text/html')) throw Error('Phiên đăng nhập đã hết. Hãy đăng nhập lại.');
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {const error=Error(data?.error || data?.message || 'Không tải được dữ liệu. Hãy thử lại.');error.status=response.status;throw error;}
    return data;
  }
  const textNode = (tag, text, className) => {const n=document.createElement(tag);n.textContent=text;if(className)n.className=className;return n;};
  window.FitnessUI = {api, base, textNode};
  const popovers = [...document.querySelectorAll('.app-popover')];
  popovers.forEach(p => p.addEventListener('toggle', () => {if(p.open)popovers.filter(other=>other!==p).forEach(other=>other.open=false);}));
  document.addEventListener('click', e => popovers.forEach(p => {if(!p.contains(e.target))p.open=false;}));
  document.addEventListener('keydown', e => {if(e.key==='Escape')popovers.forEach(p=>{if(p.open){p.open=false;p.querySelector('summary').focus();}});});
  document.querySelectorAll('.account-menu a').forEach(a=>{if(new URL(a.href).pathname===location.pathname)a.setAttribute('aria-current','page');});
  const list = document.getElementById('app-notification-list');
  let loading = false;
  async function notifications() {
    if(!list || loading)return;loading=true;
    try {
      const items=await api('/api/ai/notifications');
      if(!Array.isArray(items))throw Error('Chưa tải được thông báo.');
      const unread=items.filter(n=>!n.read).length, badge=document.getElementById('app-unread');
      badge.hidden=!unread;badge.textContent=unread>99?'99+':String(unread);
      list.replaceChildren();
      if(!items.length)list.append(textNode('p','Bạn đã xem hết thông báo.','empty-state'));
      for(const n of items){
        const row=textNode('article','','notification-row'+(!n.read?' is-unread':''));
        row.append(textNode('p',n.content),...(!n.applicable?[textNode('small','Không còn áp dụng')]:[]));
        const link=textNode('a','Xem chi tiết →');
        link.href=base+(/^\/(?!\/)/.test(n.link||'')?n.link:'/home');row.append(link);
        if(!n.read){const b=textNode('button','Đánh dấu đã đọc','text-button');b.type='button';b.onclick=async()=>{b.disabled=true;try{await api('/api/ai/notifications/'+n.id+'/read',{method:'PATCH'});loading=false;await notifications();}catch(e){b.disabled=false;b.textContent=e.message;}};row.append(b);}list.append(row);
      }
    }catch(e){list.replaceChildren(textNode('p',e.message,'muted'));}finally{loading=false;}
  }
  document.getElementById('app-notifications-refresh')?.addEventListener('click',notifications);
  notifications();setInterval(()=>{if(!document.hidden)notifications();},60000);
  ['fitnessai:data-updated','fitnessai:nutrition-updated','fitnessai:workout-updated'].forEach(event=>window.addEventListener(event,notifications));
  // The browser owns focus containment and Escape for native dialogs.
  document.querySelectorAll('dialog').forEach(dialog=>dialog.addEventListener('click',e=>{if(e.target===dialog&&dialog.getAttribute('aria-busy')!=='true'){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}}));
})();
