(() => {
  'use strict';
  const $=id=>document.getElementById(id);if(!$('macro-chart'))return;
  let summary=null,latest=null;
  const fmt=v=>new Intl.NumberFormat('vi-VN',{maximumFractionDigits:1}).format(Number(v)||0);
  function render(){
    if(!latest)return;const {day,date}=latest,totals=day?.totals||{};
    const sum=['protein','carbs','fat'].reduce((s,k)=>s+Number(totals[k]||0),0),p=sum?Number(totals.protein||0)/sum*100:0,c=sum?Number(totals.carbs||0)/sum*100:0;
    $('macro-chart').style.background=sum?`conic-gradient(#75a3ef 0 ${p}%,#7fc7ae ${p}% ${p+c}%,#f2bd7d ${p+c}% 100%)`:'#eef2f8';
    $('macro-chart-value').textContent=fmt(totals.calories)+' kcal';$('macro-legend').replaceChildren();
    for(const [key,label,color] of [['protein','Đạm','#75a3ef'],['carbs','Tinh bột','#7fc7ae'],['fat','Chất béo','#f2bd7d']]){const row=window.FitnessUI.textNode('p','','macro-row'),dot=window.FitnessUI.textNode('i','');dot.style.background=color;row.append(dot,window.FitnessUI.textNode('span',label),window.FitnessUI.textNode('strong',fmt(totals[key])+' g'));$('macro-legend').append(row);}
    const current=summary&&date===summary.today,targets=current?summary.targets:null;
    for(const key of ['calories','protein','carbs','fat'])$('target-'+key).textContent=targets?.[key]>0?'/ '+fmt(targets[key]):'';
    $('nutrition-target-note').textContent=targets?'Mục tiêu từ thực đơn đã xác nhận cho hôm nay.':current?'Chưa có mục tiêu từ thực đơn đã xác nhận cho hôm nay.':'Đang xem lượng đã ghi nhận của ngày này. Mục tiêu hôm nay không áp dụng cho lịch sử.';
  }
  window.addEventListener('fitnessai:diary-rendered',e=>{latest=e.detail;render();});
  async function load(){try{summary=await window.FitnessUI.api('/api/dashboard/summary');render();}catch{if($('nutrition-target-note'))$('nutrition-target-note').textContent='Chưa tải được mục tiêu. Nhật ký vẫn hiển thị dữ liệu đã ghi nhận.';}}
  window.addEventListener('fitnessai:data-updated',load);load();
})();
