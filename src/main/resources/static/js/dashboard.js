(() => {
  'use strict';
  if(!document.getElementById('dashboard-app'))return;
  const {api,base,textNode:n}=window.FitnessUI,$=id=>document.getElementById(id);
  const fmt=value=>new Intl.NumberFormat('vi-VN',{maximumFractionDigits:1}).format(Number(value)||0);
  let data=null,busy=false;
  const svg=(tag,attrs={},text)=>{const el=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [k,v] of Object.entries(attrs))el.setAttribute(k,String(v));if(text!==undefined)el.textContent=text;return el;};
  function chart(){
    if(!data)return;const mode=$('chart-metric').value,days=data.days;
    const max=Math.max(100,...days.flatMap(d=>[mode==='burned'?0:Number(d.nutrition.calories),mode==='intake'?0:Number(d.burnedCalories)]))*1.15;
    const root=svg('svg',{viewBox:'0 0 620 220',role:'img','aria-label':'Năng lượng đã ghi nhận trong bảy ngày'});
    for(let i=0;i<4;i++){const y=20+i*50;root.append(svg('line',{x1:46,y1:y,x2:610,y2:y,stroke:'#edf2f9','stroke-dasharray':'4 5'}),svg('text',{x:38,y:y+4,fill:'#a3afc0','font-size':10,'text-anchor':'end'},fmt(max*(1-i/3))));}
    days.forEach((d,i)=>{const x=78+i*79;for(const [kind,value,offset,color] of [['intake',d.nutrition.calories,-13,'#7ca8fa'],['burned',d.burnedCalories,8,'#bed2fc']]){if(mode!=='both'&&mode!==kind)continue;const height=Number(value)/max*150;const rect=svg('rect',{x:x+offset,y:170-height,width:17,height:Math.max(0,height),rx:5,fill:color});rect.append(svg('title',{},`${d.date}: ${kind==='intake'?'Đã ăn':'Đã đốt'} ${fmt(value)} kcal`));root.append(rect);}root.append(svg('text',{x:x+6,y:199,'text-anchor':'middle',fill:'#95a3b9','font-size':10},d.date.slice(8)+'/'+d.date.slice(5,7)));});
    $('weekly-chart').replaceChildren(root);$('weekly-chart').setAttribute('aria-busy','false');
    const table=n('table'),head=n('tr');['Ngày','Đã ăn (kcal)','Đã đốt (kcal)'].forEach(t=>head.append(n('th',t)));table.append(head);days.forEach(d=>{const row=n('tr');[d.date.split('-').reverse().join('/'),fmt(d.nutrition.calories),fmt(d.burnedCalories)].forEach(t=>row.append(n('td',t)));table.append(row);});$('weekly-table').replaceChildren(table);
  }
  function goals(){
    const current=data.days.at(-1).nutrition,box=$('daily-goals');box.replaceChildren();
    for(const [key,label,unit,color] of [['calories','Năng lượng','kcal','#6d9af2'],['protein','Đạm','g','#6fbea4'],['carbs','Tinh bột','g','#f1b470']]){
      const target=Number(data.targets?.[key]),actual=Number(current[key]),percent=target>0?Math.round(actual/target*100):null;
      const card=n('article','','goal-ring'),ring=svg('svg',{viewBox:'0 0 100 100','aria-label':label+': '+(percent===null?'Chưa thiết lập mục tiêu':percent+' phần trăm')});
      ring.append(svg('circle',{cx:50,cy:50,r:40,fill:'none',stroke:'#eef3fa','stroke-width':7}));if(percent!==null)ring.append(svg('circle',{cx:50,cy:50,r:40,fill:'none',stroke:color,'stroke-width':7,'stroke-linecap':'round','stroke-dasharray':`${Math.min(100,percent)*2.513} 251.3`,transform:'rotate(-90 50 50)'}));ring.append(svg('text',{x:50,y:55,'text-anchor':'middle',fill:'#61738e','font-size':17,'font-weight':650},percent===null?'—':percent+'%'));card.append(ring,n('strong',label),n('span',fmt(actual)+(target>0?' / '+fmt(target):'')+' '+unit),n('span',target>0?'Mục tiêu đã xác nhận':'Chưa thiết lập'));box.append(card);
    }
  }
  async function load(){
    if(busy)return;busy=true;$('dashboard-refresh').disabled=true;$('dashboard-notice').hidden=true;
    try{data=await api('/api/dashboard/summary');if(!data?.days?.length)throw Error('Chưa tải được tổng quan.');const today=data.days.at(-1);
      $('dashboard-date').textContent='Hôm nay · '+data.today.split('-').reverse().join('/');$('dash-intake').textContent=fmt(today.nutrition.calories)+' kcal';$('dash-burned').textContent=fmt(today.burnedCalories)+' kcal';$('dash-protein').textContent=fmt(today.nutrition.protein)+' g';chart();goals();
      if(data.nextWorkout?.rest&&$('dash-streak-note').dataset.checked==='false')$('dash-streak-note').textContent='Hôm nay là ngày nghỉ';
      const next=data.nextWorkout,link=$('next-workout-link');
      if(data.currentWorkout){$('next-workout-title').textContent='Tiếp tục buổi tập #'+data.currentWorkout.workoutId;$('next-workout-description').textContent='Buổi tập của bạn chưa kết thúc. Quay lại để xem trạng thái và tiếp tục.';link.textContent='Về buổi đang tập →';link.href=base+'/workout#session';}
      else if(next){$('next-workout-title').textContent=next.rest?'Hôm nay dành cho hồi phục':'Lịch tập · '+next.date.split('-').reverse().join('/');$('next-workout-description').textContent=next.rest?'Ngày nghỉ theo lịch đã chấp nhận.':next.title+' · '+next.exercises.join(', ');link.textContent='Xem lịch đã chấp nhận →';link.href=base+'/workout#ai-calendar';}
      else{$('next-workout-title').textContent='Sẵn sàng cho một khởi đầu?';$('next-workout-description').textContent='Bạn chưa có lịch tập hiệu lực trong bảy ngày tới. Tạo buổi tập hoặc trao đổi với AI để lập lịch.';link.textContent='Khám phá bài tập →';link.href=base+'/workout#library';}
    }catch(e){$('dashboard-notice').textContent=e.message;$('dashboard-notice').hidden=false;$('dashboard-date').textContent='Chưa tải được tổng quan';}finally{busy=false;$('dashboard-refresh').disabled=false;}
  }
  $('dashboard-refresh').onclick=load;$('chart-metric').onchange=chart;$('show-badges').onclick=()=>$('badges-dialog').showModal();$('close-badges').onclick=()=>$('badges-dialog').close();
  window.addEventListener('fitnessai:data-updated',load);load();
})();
