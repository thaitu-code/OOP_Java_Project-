(() => {
  'use strict';
  const form=document.getElementById('profile-form');if(!form)return;
  const fields=document.getElementById('profile-fields'),edit=document.getElementById('profile-edit'),cancel=document.getElementById('profile-cancel'),save=document.getElementById('profile-save');
  const sync=()=>{edit.hidden=!fields.disabled;cancel.hidden=fields.disabled;save.hidden=fields.disabled;};
  edit.onclick=()=>{fields.disabled=false;sync();fields.querySelector('input:not([type=hidden])')?.focus();};
  cancel.onclick=()=>{form.reset();fields.disabled=true;sync();edit.focus();};
  form.addEventListener('submit',e=>{if(fields.disabled)e.preventDefault();});sync();
})();
