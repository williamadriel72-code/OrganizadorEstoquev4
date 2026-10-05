(async function bmLoadAdminEnhancements(){
 const original='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/f00beb3658d689e506fd633b1dc3bcedbb70a293/scanner/assets/bora_web/patch_admin_header_compact_v1.js';
 const exception='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_shift_exception_v1.js';
 function run(code,name){const s=document.createElement('script');s.dataset.bmPatch=name;s.textContent=code;document.head.appendChild(s)}
 try{
  const stamp=Date.now();
  const [a,b]=await Promise.all([
   fetch(original+'?v='+stamp,{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('compact '+r.status);return r.text()}),
   fetch(exception+'?v='+stamp,{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('exception '+r.status);return r.text()})
  ]);
  run(a,'admin-compact-original');
  run(b,'shift-exception-v1');
 }catch(e){console.error('bm-load-admin-enhancements',e)}
})();
