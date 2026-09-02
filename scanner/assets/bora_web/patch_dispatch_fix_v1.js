/* ROLLBACK DE SEGURANÇA: carrega somente a versão estável validada do APK. */
(async()=>{
 if(window.__bmDispatchStableRollbackV1)return;
 window.__bmDispatchStableRollbackV1=true;
 const BASE='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/80e786078a55d37605bc955b52cc4aa7aaa9845f/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 try{
  const r=await fetch(BASE+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-rollback',e?.message||e)}
})();
