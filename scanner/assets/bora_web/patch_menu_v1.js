/* BORA MICHAEL — restaura carregamento normal e aplica continuidade sem bloquear o boot */
(()=>{
 if(window.__bmMenuSafeLoaderV5)return;
 window.__bmMenuSafeLoaderV5=true;
 const LEGACY='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/385754d83906f20a938e5b29d8c1708124ec7544/scanner/assets/bora_web/patch_menu_v1.js';
 const CONT='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_continuity_v5.js';
 fetch(LEGACY+'?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw Error('menu legado '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('bm-menu-legacy',e?.message||e));
 const loadContinuity=()=>fetch(CONT+'?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw Error('continuidade '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('bm-continuity-loader',e?.message||e));
 setTimeout(loadContinuity,900);
 window.addEventListener('load',()=>setTimeout(loadContinuity,350),{once:true});
})();