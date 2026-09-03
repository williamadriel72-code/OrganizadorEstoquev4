/* ROLLBACK ESTÁVEL + correção isolada para pedidos antes das 10h. */
(async()=>{
 if(window.__bmDispatchStableRollbackV2)return;
 window.__bmDispatchStableRollbackV2=true;
 const BASE='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/80e786078a55d37605bc955b52cc4aa7aaa9845f/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 const FIX='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_pre10_visibility_fix_v1.js';
 try{
  const r=await fetch(BASE+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-rollback',e?.message||e)}
 try{
  const r=await fetch(FIX+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('pre10-visibility-fix',e?.message||e)}
})();
