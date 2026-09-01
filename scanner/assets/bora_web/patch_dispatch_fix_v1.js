(()=>{
 if(window.__bmDispatchFixV1)return;
 window.__bmDispatchFixV1=true;
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 const ASSET='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/motoboy-saida-mobile.webp';

 if(riderMode&&!document.getElementById('bmRiderRefreshPositionFix')){
  const ps=document.createElement('style');
  ps.id='bmRiderRefreshPositionFix';
  ps.textContent=`
   #bmRiderRefresh{right:max(40px,env(safe-area-inset-right))!important;top:max(100px,calc(env(safe-area-inset-top) + 66px))!important;left:auto!important;transform:none!important}
   #bmRiderRefresh:active{transform:scale(.94)!important}
   @media(max-width:420px){#bmRiderRefresh{right:max(36px,env(safe-area-inset-right))!important;top:max(96px,calc(env(safe-area-inset-top) + 62px))!important}}
  `;
  document.head.appendChild(ps);
 }

 if(!document.getElementById('bmDispatchFixStyle')){
  const s=document.createElement('style');
  s.id='bmDispatchFixStyle';
  s.textContent=`
   .bm-fix-idle-bike{position:absolute;z-index:1;right:150px;bottom:-18px;width:185px;pointer-events:none;opacity:.72;filter:drop-shadow(0 14px 14px rgba(0,0,0,.5));animation:bmFixIdle 2.7s ease-in-out infinite;transition:transform .45s ease,opacity .3s ease}
   .bm-fix-idle-bike img{display:block;width:100%;height:auto}.bm-fix-idle-bike.bm-fix-go{transform:translateX(62vw) scale(.94);opacity:0}
   @keyframes bmFixIdle{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}
   @media(max-width:980px){.bm-fix-idle-bike{right:120px;width:145px;opacity:.55}}@media(max-width:700px){.bm-fix-idle-bike{display:none}}
   @media(prefers-reduced-motion:reduce){.bm-fix-idle-bike{animation:none}}
  `;
  document.head.appendChild(s);
 }

 function mountAdminIdleBike(){
  if(riderMode)return;
  const host=document.querySelector('.bm-overview .bm-ov-head');
  if(!host||host.querySelector('.bm-fix-idle-bike'))return;
  host.style.position='relative';host.style.overflow='hidden';
  const el=document.createElement('div');el.className='bm-fix-idle-bike';el.setAttribute('aria-hidden','true');
  el.innerHTML=`<img src="${ASSET}" alt="">`;
  host.appendChild(el);
 }

 if(!riderMode){
  const mo=new MutationObserver(mountAdminIdleBike);mo.observe(document.documentElement,{childList:true,subtree:true});
  setTimeout(mountAdminIdleBike,0);setTimeout(mountAdminIdleBike,500);
  if(typeof bmPlayDispatchAnimation==='function'&&!window.__bmDispatchFixAdminWrapped){
   window.__bmDispatchFixAdminWrapped=true;
   const prev=bmPlayDispatchAnimation;
   bmPlayDispatchAnimation=function(opts={}){
    if((opts?.mode||'admin')==='admin'){
     mountAdminIdleBike();
     const el=document.querySelector('.bm-fix-idle-bike');
     if(el){el.classList.remove('bm-fix-go');requestAnimationFrame(()=>el.classList.add('bm-fix-go'));setTimeout(()=>el.classList.remove('bm-fix-go'),2300)}
    }
    return prev(opts);
   };
  }
  return;
 }

 const releaseLike=o=>{
  if(!o)return false;
  const st=String(o.status||'').toLowerCase();
  if(['liberada','liberado','em_andamento','em_entrega','em_rota','saida'].includes(st))return true;
  return !!o.horario_saida&&!['montando','cancelada','cancelado'].includes(st);
 };
 const releasedSeen=new Set();
 let baselineReady=false,fixChannel=null,fixMid=null,pollBusy=false;

 async function playRelease(o){
  if(!o?.id)return;
  const id=String(o.id);
  if(releasedSeen.has(id)&&rider?.lastDispatchAnimationId===id)return;
  releasedSeen.add(id);
  if(rider)rider.lastDispatchAnimationId=id;
  let notes=[];
  try{const d=await riderDay(today());notes=(d.e||[]).filter(e=>e.saida_id===o.id&&e.status!=='cancelada').map(e=>e.nota_numero).filter(Boolean)}catch(e){console.warn('dispatch-fix-notes',e?.message||e)}
  const run=()=>{
   if(typeof bmPlayDispatchAnimation==='function')bmPlayDispatchAnimation({mode:'rider',outingNumber:o.numero_sequencial,notes});
   try{nativeNotify({source:'release-animation-fix',saida_id:o.id})}catch(_){ }
  };
  if(document.hidden){
   const once=()=>{if(!document.hidden){document.removeEventListener('visibilitychange',once);run()}};
   document.addEventListener('visibilitychange',once);
  }else run();
 }

 async function pollReleased(){
  if(pollBusy||!rider?.profile)return;
  pollBusy=true;
  try{
   const d=await riderDay(today());
   const current=(d.s||[]).filter(releaseLike);
   if(!baselineReady){current.forEach(o=>o?.id&&releasedSeen.add(String(o.id)));baselineReady=true;return}
   const fresh=current.filter(o=>o?.id&&!releasedSeen.has(String(o.id)));
   current.forEach(o=>o?.id&&releasedSeen.add(String(o.id)));
   if(fresh.length)await playRelease(fresh[fresh.length-1]);
  }catch(e){console.warn('dispatch-fix-poll',e?.message||e)}finally{pollBusy=false}
 }

 function ensureFixChannel(){
  if(!rider?.profile||!sb)return;
  const mid=rider.profile.id;
  if(fixChannel&&fixMid===mid)return;
  fixMid=mid;
  if(fixChannel){try{sb.removeChannel(fixChannel)}catch(_){ }}
  fixChannel=sb.channel('bm-release-fix-'+mid+'-'+Date.now()).on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_saidas',filter:`motoboy_id=eq.${mid}`},payload=>{
   const n=payload?.new||{};
   if((payload?.eventType==='INSERT'||payload?.eventType==='UPDATE')&&releaseLike(n))playRelease(n);
   setTimeout(pollReleased,180);
  }).subscribe();
  setTimeout(pollReleased,250);
 }

 if(typeof setupRiderRealtime==='function'&&!window.__bmDispatchFixSetupWrapped){
  window.__bmDispatchFixSetupWrapped=true;
  const prev=setupRiderRealtime;
  setupRiderRealtime=function(){const out=prev.apply(this,arguments);setTimeout(ensureFixChannel,0);return out};
 }
 setInterval(()=>{ensureFixChannel();if(!document.hidden)pollReleased()},1500);
 window.addEventListener('focus',()=>{ensureFixChannel();pollReleased()});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){ensureFixChannel();pollReleased()}});

 /* Hotfix do fechamento no APK. Esta patch já é buscada com cache-busting pela versão instalada. */
 if(!window.__bmRiderShiftHotfixV2){
  window.__bmRiderShiftHotfixV2=true;

  function bmShiftSpParts(date=new Date()){
   const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
   const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
   return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
  }
  function bmShiftKeyNow(){const p=bmShiftSpParts();return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10'}
  function bmInCurrentShift(iso,key=bmShiftKeyNow()){
   if(!iso)return false;
   const now=bmShiftSpParts(),p=bmShiftSpParts(new Date(iso));
   if(p.date!==now.date)return false;
   if(key==='17_24')return p.mins>=17*60;
   if(key==='10_17')return p.mins>=10*60&&p.mins<17*60;
   return false;
  }
  function bmShiftView(d){
   const key=bmShiftKeyNow();
   const e=(d?.e||[]).filter(x=>bmInCurrentShift(x.created_at,key));
   const s=(d?.s||[]).filter(x=>bmInCurrentShift(x.horario_saida||x.created_at,key));
   const base=d?.j?.chegada_at&&bmInCurrentShift(d.j.chegada_at,key)?Number(d.j.base_valor||0):0;
   const j=d?.j?{...d.j,base_valor:base,chegada_at:base?d.j.chegada_at:null}:d?.j;
   return {...d,e,s,j,total:base+e.reduce((a,x)=>a+Number(x.valor||0),0),__bmShift:key};
  }

  /* Fallback para versões do menu que ainda não receberam a regra de turno. */
  if(!window.__bmRiderShiftResetV1&&typeof todayHtml==='function'){
   const oldTodayHtml=todayHtml;
   todayHtml=function(d){
    const view=bmShiftView(d);
    const name=view.__bmShift==='17_24'?'Turno 2 · 17:00–00:00':view.__bmShift==='10_17'?'Turno 1 · 10:00–17:00':'Aguardando turno · 10:00';
    return `<div class="notice" style="margin-bottom:10px;border-color:#31d98255"><strong>${name}</strong> · valores e entregas exibidos somente do turno atual.</div>`+oldTodayHtml(view);
   };
  }

  let bmLastShift=bmShiftKeyNow();
  let bmShiftRenderBusy=false;
  async function bmRefreshRiderShift(force=false){
   const key=bmShiftKeyNow();
   if(!force&&key===bmLastShift)return;
   bmLastShift=key;
   if(bmShiftRenderBusy||!rider?.profile||typeof renderRider!=='function')return;
   if(rider?.active&&rider.active!=='Hoje')return;
   bmShiftRenderBusy=true;
   try{await renderRider('Hoje')}catch(e){console.warn('rider-shift-hotfix',e?.message||e)}finally{bmShiftRenderBusy=false}
  }

  /* Força uma renderização logo após a patch carregar, inclusive se já passou das 17h. */
  setTimeout(()=>bmRefreshRiderShift(true),180);
  setTimeout(()=>bmRefreshRiderShift(true),850);
  setInterval(()=>bmRefreshRiderShift(false),15000);
  window.addEventListener('focus',()=>bmRefreshRiderShift(false));
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)bmRefreshRiderShift(false)});
 }
})();
