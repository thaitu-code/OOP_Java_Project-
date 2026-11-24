(() => {
  'use strict';
  const form=document.getElementById('profile-form');if(!form)return;
  const fields=document.getElementById('profile-fields'),goals=document.getElementById('profile-goal-fields'),edit=document.getElementById('profile-edit'),cancel=document.getElementById('profile-cancel'),save=document.getElementById('profile-save');
  const sync=()=>{goals.disabled=fields.disabled;edit.hidden=!fields.disabled;cancel.hidden=fields.disabled;save.hidden=fields.disabled;};
  edit.onclick=()=>{fields.disabled=false;sync();fields.querySelector('input:not([type=hidden])')?.focus();};
  cancel.onclick=()=>{form.reset();fields.disabled=true;sync();describeActivity();edit.focus();};
  form.addEventListener('submit',e=>{if(fields.disabled)e.preventDefault();});sync();
  const activity=document.getElementById('activityLevel');
  function describeActivity(){
    const descriptions={SEDENTARY:'Dành phần lớn thời gian ngồi, ít hoặc không tập luyện.',LIGHT:'Vận động nhẹ trong sinh hoạt và tập luyện.',MODERATE:'Duy trì mức vận động và tập luyện vừa phải.',ACTIVE:'Thường xuyên vận động và tập luyện ở mức cao.',VERY_ACTIVE:'Mức vận động rất cao trong sinh hoạt hoặc tập luyện.'};
    document.getElementById('activity-description').textContent=descriptions[activity.value]||'Chọn mức vận động phù hợp với sinh hoạt thực tế của bạn.';
  }
  activity.addEventListener('change',describeActivity);describeActivity();
  const page=document.getElementById('profile-page'),bmi=Number(page.dataset.bmi),age=Number(page.dataset.age);
  const note=document.getElementById('profile-bmi-note');
  if(!page.dataset.bmi||!Number.isFinite(bmi)||bmi<=0){note.textContent='Hoàn thiện hồ sơ và lưu chiều cao, cân nặng để xem chỉ số BMI của bạn.';return;}
  if(!page.dataset.age||age<20){note.textContent='Chỉ số từ hồ sơ đã lưu. Phân loại BMI cho người dưới 20 tuổi cần xét thêm tuổi và giới tính; không dùng thang người trưởng thành.';return;}
  const [label,category]=bmi<18.5?['Thiếu cân','under']:bmi<25?['Bình thường','normal']:bmi<30?['Thừa cân','over']:['Béo phì','high'];
  document.querySelectorAll('[data-bmi-category]').forEach(badge=>{badge.textContent=label;badge.className='bmi-category '+category;badge.hidden=false;});
  const position=bmi<18.5?(bmi-10)/8.5*25:bmi<25?25+(bmi-18.5)/6.5*25:bmi<30?50+(bmi-25)/5*25:75+(bmi-30)/10*25;
  const pointer=document.getElementById('bmi-pointer');pointer.style.left=Math.max(1,Math.min(99,position))+'%';pointer.hidden=false;
})();
