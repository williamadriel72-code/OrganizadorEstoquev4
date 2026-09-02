/* Bootstrap estável: preserva integralmente a versão validada do APK e carrega o feedback por cima. */
(async()=>{
 if(window.__bmDispatchFeedbackBootstrapV1)return;
 window.__bmDispatchFeedbackBootstrapV1=true;
 const BASE='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/80e786078a55d37605bc955b52cc4aa7aaa9845f/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 const FEEDBACK='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_feedback_v1.js';
 try{
  const r=await fetch(BASE+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-v8',e?.message||e)}
 try{
  const r=await fetch(FEEDBACK+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('feedback-v1',e?.message||e)}
})();
