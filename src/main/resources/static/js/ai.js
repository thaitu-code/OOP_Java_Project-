(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  if (!$('chat-form')) return;
  const base = document.querySelector('meta[name="app-base"]')?.content || '/';
  const csrf = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  const messages = new Map(), proposals = new Map();
  let session = null, cursor = null, loaded = false, busy = false, uncertain = null;
  const statusText = {PENDING:'Đang chờ xác nhận',CONFIRMED:'Đã thực hiện',CANCELLED:'Đã hủy',STALE:'Dữ liệu đã thay đổi',EXPIRED:'Đã hết hạn',SUPERSEDED:'Đã có bản thay thế'};
  const node = (tag, text, className) => { const el = document.createElement(tag); if (text != null) el.textContent = text; if (className) el.className = className; return el; };
  async function api(path, options = {}) {
    let response;
    try { response = await fetch(base.replace(/\/$/,'') + path, {credentials:'same-origin',cache:'no-store',...options,
      headers:{Accept:'application/json',...(options.body?{'Content-Type':'application/json'}:{}),...(options.method&&options.method!=='GET'?{[csrfHeader]:csrf}:{}),...options.headers}}); }
    catch { throw Error('Mất kết nối. Gửi lại cùng tin nhắn để kiểm tra kết quả; hệ thống sẽ không thực hiện trùng.'); }
    if (response.redirected || response.headers.get('content-type')?.includes('text/html')) throw Error('Phiên đăng nhập hết hạn. Hãy đăng nhập lại.');
    const body = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) throw Error(body?.error || `Không thực hiện được yêu cầu (HTTP ${response.status}).`);
    if (body === null && response.status !== 204) throw Error('Phản hồi máy chủ không hợp lệ.');
    return body;
  }
  function setBusy(value) { busy = value; $('send').disabled = value || !session; $('chat-text').disabled = value; if($('chat-typing'))$('chat-typing').hidden=!value; $('proposals').querySelectorAll('button').forEach(b => { b.disabled = value || b.dataset.closed === 'true'; }); }
  function renderMessages() {
    $('messages').replaceChildren();
    for (const m of [...messages.values()].sort((a,b) => a.id-b.id)) {
      const bubble = node('article', null, 'bubble '+(m.sender==='USER'?'user':''));
      const content=node('div',m.content);formatText(content,m.content);bubble.append(content);
      const footer = node('small',`${m.sender==='USER'?'Bạn':'AI'} · ${new Date(m.createdAt).toLocaleString('vi-VN')} · ${m.status==='FAILED'?'Chưa nhận được phản hồi':m.status==='PENDING'?'Đang xử lý':'Đã lưu'}`);
      const copy=node('button','Sao chép');copy.type='button';copy.onclick=async()=>{try{await navigator.clipboard.writeText(m.content);copy.textContent='Đã sao chép';}catch{$('notice').textContent='Trình duyệt chưa cho phép sao chép. Bạn có thể chọn nội dung để sao chép.';}};footer.append(copy);
      const del = node('button','Xóa'); del.type='button';
      del.onclick=async()=>{try{await api('/api/ai/chat/'+m.id,{method:'DELETE'});messages.delete(m.id);renderMessages();await loadPending();}catch(e){$('notice').textContent=e.message;}};
      footer.append(del);bubble.append(footer);$('messages').append(bubble);
    }
    if($('chat-welcome'))$('chat-welcome').hidden=messages.size>0;
  }
  // Deliberately small Markdown subset, built exclusively from DOM text nodes.
  function formatText(container,text) {
    if(!/(^|\n)(?:#{1,3} |[-*] |\d+\. |\|)|\*\*[^*]+\*\*/.test(text))return;
    container.replaceChildren();let list=null,table=null;
    const inline=(el,line)=>{for(const part of line.split(/(\*\*[^*]+\*\*|`[^`]+`)/g)){if(part.startsWith('**')&&part.endsWith('**'))el.append(node('strong',part.slice(2,-2)));else if(part.startsWith('`')&&part.endsWith('`'))el.append(node('code',part.slice(1,-1)));else el.append(node('span',part));}};
    for(const line of text.split('\n')){
      if(/^\s*\|/.test(line)) {if(/^\s*\|[\s:|-]+\|\s*$/.test(line))continue;if(!table){table=node('table');container.append(table);}const row=node('tr');for(const c of line.replace(/^\s*\||\|\s*$/g,'').split('|')){const cell=node('td');inline(cell,c.trim());row.append(cell);}table.append(row);list=null;continue;}
      table=null;const match=line.match(/^\s*(?:[-*]|\d+\.)\s+(.+)/);
      if(match){if(!list){list=node('ul');container.append(list);}const item=node('li');inline(item,match[1]);list.append(item);continue;}list=null;
      const p=node(/^#{1,3} /.test(line)?'strong':'p');inline(p,line.replace(/^#{1,3} /,''));container.append(p);
    }
  }
  function renderProposals() {
    $('proposals').replaceChildren();
    for (const p of proposals.values()) {
      const card=node('article',null,'proposal');card.append(node('h3',statusText[p.status]||p.status));card.append(node('pre',p.preview));
      if(p.result)card.append(node('p',p.result));
      if(p.status==='PENDING') {
        for(const [label,action] of [['Xác nhận','confirm'],['Chỉnh sửa','edit'],['Hủy','cancel']]) {
          const button=node('button',label);button.type='button';button.disabled=busy;
          button.onclick=()=>action==='edit'?edit(p):decide(p,action);card.append(button);
        }
        card.append(node('small','Chưa lưu thay đổi. Đề xuất có hiệu lực trong 30 phút.'));
      }
      if(p.recommendationId) {
        const a=node('a','Xem kế hoạch');a.href=base+(p.kind==='WORKOUT_PLAN'?'workout#ai-calendar':'nutrition#meal-plan');card.append(a);
      }
      $('proposals').append(card);
    }
  }
  function edit(p) { $('chat-text').value=`Chỉnh đề xuất ${p.id}: `;$('chat-text').focus();$('notice').textContent='Nói phần bạn muốn sửa rồi bấm Gửi. Bản hiện tại chưa được áp dụng.'; }
  async function loadPending() {
    const pending=await api('/api/ai/proposals?sessionId='+encodeURIComponent(session));
    const ids=new Set(pending.map(p=>p.id));
    for(const [id,p] of proposals)if(p.status==='PENDING'&&!ids.has(id))proposals.delete(id);
    for(const p of pending)proposals.set(p.id,p);renderProposals();
  }
  async function decide(p,action) {
    if(busy)return;setBusy(true);$('notice').textContent='Đang xử lý xác nhận…';
    try {
      const result=await api(`/api/ai/proposals/${p.id}/${action}`,{method:'POST'});
      proposals.set(result.proposal.id,result.proposal);
      for(const m of result.messages||[])messages.set(m.id,m);
      renderMessages();renderProposals();$('notice').textContent=result.proposal.result;
      await refreshSaved();await loadNotifications();
      window.dispatchEvent(new CustomEvent('fitnessai:data-updated'));
    }catch(e){$('notice').textContent=e.message;}
    finally{setBusy(false);}
  }
  function remember() { sessionStorage.setItem('fitness-ai-pending',JSON.stringify(uncertain)); }
  function accept(result) {
    if(result.userMessage)messages.set(result.userMessage.id,result.userMessage);
    if(result.aiMessage)messages.set(result.aiMessage.id,result.aiMessage);
    for(const p of result.proposals||[])proposals.set(p.id,p);
    renderMessages();renderProposals();
  }
  $('more').onclick=async()=>{
    $('more').disabled=true;
    try{const page=await api('/api/ai/chat'+(loaded&&cursor?'?before='+cursor:''));for(const m of page.messages)messages.set(m.id,m);cursor=page.nextCursor;loaded=true;$('more').hidden=!cursor;renderMessages();}
    catch(e){$('notice').textContent=e.message;}finally{$('more').disabled=false;}
  };
  $('chat-form').onsubmit=async event=>{
    event.preventDefault();const content=$('chat-text').value.trim();if(!content||busy||!session)return;
    if(uncertain&&uncertain.content!==content){$('notice').textContent='Tin trước chưa có kết quả chắc chắn. Gửi lại nội dung cũ để kiểm tra trước khi gửi tin khác.';$('chat-text').value=uncertain.content;return;}
    uncertain=uncertain||{sessionId:session,content,requestId:crypto.randomUUID()};remember();setBusy(true);$('notice').textContent='AI đang đọc yêu cầu…';
    try {
      let result=await api('/api/ai/chat',{method:'POST',body:JSON.stringify(uncertain)});accept(result);
      for(let i=0;result.status==='PENDING'&&i<35;i++){await new Promise(resolve=>setTimeout(resolve,1000));result=await api('/api/ai/chat/turns/'+uncertain.requestId);accept(result);}
      if(result.status==='PENDING')throw Error('Yêu cầu vẫn đang xử lý. Gửi lại cùng nội dung để kiểm tra kết quả.');
      if(result.status==='FAILED'){$('notice').textContent=result.error||'AI chưa trả được phản hồi. Bấm Gửi để thử lại cùng lượt.';if(result.retryable===false){uncertain=null;remember();}}
      else{uncertain=null;remember();$('chat-text').value='';$('notice').textContent='';await loadPending();await refreshSaved();}
      $('messages').scrollTop=$('messages').scrollHeight;const thread=$('messages').parentElement;thread?.scrollTo?.({top:thread.scrollHeight,behavior:'smooth'});
    }catch(e){$('notice').textContent=e.message;}
    finally{setBusy(false);}
  };
  async function refreshSaved() {
    const list=await api('/api/ai/recommendations');$('recs').replaceChildren();
    if(!list.length)$('recs').append(node('p','Chưa có kế hoạch. Hãy trao đổi với AI để bắt đầu.','muted'));
    for(const p of list){const card=node('article',null,'rec');card.append(node('strong',p.title),node('p',p.content));const a=node('a','Xem chi tiết');a.href=base+(p.type==='WORKOUT_PLAN'?'workout#ai-calendar':'nutrition#meal-plan');card.append(a);$('recs').append(card);}
  }
  async function loadNotifications() {
    if(!$('notifications'))return;
    const all=await api('/api/ai/notifications');$('notifications').replaceChildren();$('notification-count').textContent=all.filter(n=>!n.read).length||'';
    for(const n of all){const row=node('article',null,'rec');row.append(node('p',n.content+(n.applicable?'':' · Không còn áp dụng')));const link=node('a','Xem');link.href=base.replace(/\/$/,'')+n.link;row.append(link);if(!n.read){const b=node('button','Đánh dấu đã đọc');b.onclick=async()=>{try{await api('/api/ai/notifications/'+n.id+'/read',{method:'PATCH'});await loadNotifications();}catch(e){$('notice').textContent=e.message;}};row.append(b);}$('notifications').append(row);}
  }
  async function initialize() {
    setBusy(true);
    try {
      const data=await api('/api/ai/chat/session');session=data.sessionId;
      const old=sessionStorage.getItem('fitness-ai-session');
      if(old!==session)sessionStorage.removeItem('fitness-ai-pending');
      sessionStorage.setItem('fitness-ai-session',session);
      uncertain=JSON.parse(sessionStorage.getItem('fitness-ai-pending')||'null');
      if(uncertain){$('chat-text').value=uncertain.content;$('notice').textContent='Tin trước chưa nhận đủ kết quả. Bấm Gửi để kiểm tra hoặc thử lại.';}
      else { const prefill=new URLSearchParams(location.search).get('prompt'); if(prefill)$('chat-text').value=prefill.slice(0,3000); }
      await $('more').onclick();await loadPending();await refreshSaved();await loadNotifications();
    }catch(e){$('notice').textContent=e.message;}
    finally{setBusy(false);}
  }
  initialize();
  document.querySelectorAll?.('[data-prompt]').forEach(b=>b.addEventListener('click',()=>{if(busy)return;$('chat-text').value=b.dataset.prompt;$('chat-text').focus();}));
  $('chat-text').addEventListener?.('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();$('chat-form').requestSubmit();}});
  setInterval(()=>{if(!document.hidden&&!busy&&session)loadNotifications().catch(()=>{});},60000);
})();
