(function bmLoadOriginalCompact(){
 const url='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/f00beb3658d689e506fd633b1dc3bcedbb70a293/scanner/assets/bora_web/patch_admin_header_compact_v1.js?v='+Date.now();
 fetch(url,{cache:'no-store'})
  .then(r=>{if(!r.ok)throw Error('compact '+r.status);return r.text()})
  .then(code=>{const s=document.createElement('script');s.dataset.bmPatch='admin-compact-original';s.textContent=code;document.head.appendChild(s)})
  .catch(e=>console.error('bm-load-admin-compact',e));
})();

/*
 CONTINUIDADE AUTORITATIVA V3
 Este arquivo é o último patch da versão atualmente publicada na Vercel.
 Ele segura o boot do painel e do APK até a regra de dia operacional estar carregada.
*/
(function bmOperationalBootstrapV3(){
 if(window.__bmOperationalBootstrapV3)return;
 window.__bmOperationalBootstrapV3=true;

 const originalBoot=typeof boot==='function'?boot:null;
 const url='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_operational_day_v3.js?v='+Date.now();

 const ready=fetch(url,{cache:'no-store'})
  .then(r=>{if(!r.ok)throw Error('continuidade '+r.status);return r.text()})
  .then(async code=>{
   (0,eval)(code);
   if(window.__bmOperationalReady)await window.__bmOperationalReady;
   return true;
  })
  .catch(e=>{
   console.error('bm-operational-bootstrap',e);
   return false;
  });

 window.__bmOperationalBootstrapReady=ready;

 if(originalBoot){
  boot=async function(){
   await ready;
   return originalBoot();
  };
 }
})();