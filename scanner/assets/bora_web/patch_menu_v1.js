function bmInstallMainMenuButton(){
 const head=document.querySelector('.admin-head');
 if(!head||document.getElementById('bmMainMenu'))return;
 const logout=document.getElementById('logout');
 const btn=document.createElement('button');
 btn.id='bmMainMenu';
 btn.className='btn secondary small';
 btn.textContent='← MENU PRINCIPAL';
 btn.style.marginLeft='auto';
 btn.addEventListener('click',()=>{
  adminState.selectedMoto=null;
  adminState.selectedBairro=null;
  drawAdmin();
 });
 if(logout){
  logout.style.marginLeft='8px';
  head.insertBefore(btn,logout);
 }else{
  head.appendChild(btn);
 }
}

const bmBindAdminWithMainMenu=bindAdmin;
bindAdmin=function(){
 bmBindAdminWithMainMenu();
 bmInstallMainMenuButton();
};

/* Regras consolidadas de diária por horário.
   10:00-15:00: seg-sex R$48, domingo R$55.
   Fora dessa faixa: até 18:00 inclusive mantém valor anterior.
   A partir de 18:01 entra a faixa reduzida até 18:59.
   A partir de 19:00 permanece editável. */
if(typeof bmArrivalRule==='function'){
 bmArrivalRule=function(timeStr){
  const parts=String(timeStr||'').split(':').map(Number);
  const hh=parts[0],mm=parts[1];
  if(!Number.isInteger(hh)||!Number.isInteger(mm)||hh<0||hh>23||mm<0||mm>59)return null;
  const mins=hh*60+mm;
  const [y,mo,d]=today().split('-').map(Number);
  const dow=new Date(y,mo-1,d,12,0,0).getDay();
  const sun=dow===0;

  if(mins>=10*60&&mins<=15*60){
   if(dow>=1&&dow<=5)return {tipo:'antes_18',valor:48,manual:false,faixa:'10_15'};
   if(sun)return {tipo:'antes_18',valor:55,manual:false,faixa:'10_15'};
  }

  if(mins<=18*60)return {tipo:'antes_18',valor:sun?90:70,manual:false};
  if(mins<19*60)return {tipo:'entre_18_19',valor:sun?80:60,manual:false};
  return {tipo:'apos_19_manual',valor:null,manual:true};
 };
}

/* Atualiza diretamente a tela Taxas do APK usando esta patch, que já é carregada
   pela versão instalada do aplicativo. Assim não depende de uma nova lista de patches. */
(function bmKeepRiderRatesCurrent(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmRiderRatesFromMenuV2)return;
 window.__bmRiderRatesFromMenuV2=true;
 if(typeof renderRider!=='function')return;
 const previousRenderRider=renderRider;
 renderRider=async function(active='Hoje'){
  const out=await previousRenderRider(active);
  if(active==='Taxas'){
   const reports=document.querySelector('.shell .reports');
   if(reports){
    reports.innerHTML=`
     <div class="notice" style="border-color:#22c55e55"><strong>REGRA ATUAL:</strong> até 18:00 — R$ 70,00 · a partir de 18:01 — R$ 60,00.</div>
     <div class="report-card">
      <b>Segunda a sexta</b>
      <div class="row-sub" style="margin-top:8px">10:00 às 15:00 — R$ 48,00</div>
      <div class="row-sub">15:01 às 18:00 — R$ 70,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 60,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="report-card">
      <b>Sábado</b>
      <div class="row-sub" style="margin-top:8px">Até 18:00 — R$ 70,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 60,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="report-card">
      <b>Domingo</b>
      <div class="row-sub" style="margin-top:8px">10:00 às 15:00 — R$ 55,00</div>
      <div class="row-sub">15:01 às 18:00 — R$ 90,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 80,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="notice"><strong>Taxa Integral:</strong> o valor muda conforme a nota e nunca vira preço fixo do bairro.</div>`;
   }
  }
  return out;
 };
})();

/* Interface premium inspirada na referência: carregada SOMENTE no painel administrativo. */
if(new URLSearchParams(location.search).get('app')!=='motoboy'){
 fetch('https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_panel_reference_v1.js?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('panel-reference',e?.message||e));
}

/* A interface nova esconde a sidebar antiga; mantenha o botão de relatórios visível no cabeçalho. */
(function bmKeepDailyReportsVisible(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmReportsVisibleObserver)return;
 window.__bmReportsVisibleObserver=true;
 const move=()=>{
  const btn=document.getElementById('bmDailyReportsBtn');
  const head=document.querySelector('.admin-head');
  if(!btn||!head)return;
  const logout=document.getElementById('logout');
  if(btn.parentElement!==head){
   btn.className='btn secondary small';
   btn.textContent='RELATÓRIOS DIÁRIOS';
   btn.style.width='auto';
   btn.style.margin='0 8px 0 auto';
   if(logout)head.insertBefore(btn,logout);else head.appendChild(btn);
  }
 };
 const obs=new MutationObserver(move);
 obs.observe(document.documentElement,{childList:true,subtree:true});
 setTimeout(move,0);setTimeout(move,250);setTimeout(move,900);
})();

/* Relatórios automáticos por turno, SOMENTE no painel. O APK não recebe esta interface. */
if(new URLSearchParams(location.search).get('app')!=='motoboy'){
 fetch('https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_shift_reports_v1.js?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('shift-reports',e?.message||e));
}

/* Correção da animação de despacho no painel e no APK. */
fetch('https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_dispatch_fix_v1.js?v='+Date.now(),{cache:'no-store'})
 .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
 .then(code=>(0,eval)(code))
 .catch(e=>console.warn('dispatch-fix',e?.message||e));

/* Botão de atualização manual no APK + aviso quando esta patch mudar no servidor. */
(function bmInstallRiderRefreshButton(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmRiderRefreshButtonV1)return;
 window.__bmRiderRefreshButtonV1=true;

 const SOURCE='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_menu_v1.js';
 let baseline=null,checking=false;
 const ICON='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6v5h-5"/><path d="M18.2 9A7.5 7.5 0 1 0 19 15"/></svg>';

 const hash=text=>{
  let h=2166136261;
  for(let i=0;i<text.length;i++){h^=text.charCodeAt(i);h=Math.imul(h,16777619)}
  return (h>>>0).toString(16);
 };

 if(!document.getElementById('bmRiderRefreshStyle')){
  const s=document.createElement('style');s.id='bmRiderRefreshStyle';s.textContent=`
   #bmRiderRefresh{position:fixed;z-index:9998;right:max(16px,env(safe-area-inset-right));top:max(76px,calc(env(safe-area-inset-top) + 42px));width:42px;height:42px;padding:0;display:grid;place-items:center;border:1px solid rgba(255,255,255,.15);border-radius:50%;background:rgba(18,23,29,.94);color:#dce3e9;box-shadow:0 8px 24px rgba(0,0,0,.32),inset 0 1px 0 rgba(255,255,255,.04);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}
   #bmRiderRefresh svg{width:21px;height:21px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none}
   #bmRiderRefresh:active{transform:scale(.94)}
   #bmRiderRefresh.bm-update-available{border-color:#31d982;color:#8af1bb;background:rgba(9,43,27,.96);box-shadow:0 0 0 1px rgba(49,217,130,.18),0 0 22px rgba(49,217,130,.25);animation:bmRefreshPulse 1.5s ease-in-out infinite}
   #bmRiderRefresh.bm-refreshing{opacity:.78;pointer-events:none}
   #bmRiderRefresh.bm-refreshing svg{animation:bmRefreshSpin .75s linear infinite}
   @keyframes bmRefreshSpin{to{transform:rotate(360deg)}}
   @keyframes bmRefreshPulse{0%,100%{box-shadow:0 0 0 0 rgba(49,217,130,.25),0 8px 24px rgba(0,0,0,.32)}50%{box-shadow:0 0 0 7px rgba(49,217,130,0),0 0 25px rgba(49,217,130,.3)}}
   @media(max-width:420px){#bmRiderRefresh{right:max(14px,env(safe-area-inset-right));top:max(72px,calc(env(safe-area-inset-top) + 38px));width:40px;height:40px}#bmRiderRefresh svg{width:20px;height:20px}}
   @media(prefers-reduced-motion:reduce){#bmRiderRefresh.bm-update-available,#bmRiderRefresh.bm-refreshing svg{animation:none}}
  `;document.head.appendChild(s);
 }

 const mount=()=>{
  if(document.getElementById('bmRiderRefresh'))return;
  const btn=document.createElement('button');
  btn.id='bmRiderRefresh';btn.type='button';btn.innerHTML=ICON;btn.setAttribute('aria-label','Atualizar aplicativo');btn.setAttribute('title','Atualizar');
  btn.onclick=()=>{
   btn.classList.add('bm-refreshing');
   try{
    const u=new URL(location.href);u.searchParams.set('refresh',Date.now().toString());location.replace(u.toString());
   }catch(_){location.reload()}
  };
  document.body.appendChild(btn);
 };

 const markUpdate=()=>{
  mount();const btn=document.getElementById('bmRiderRefresh');if(!btn)return;
  btn.classList.add('bm-update-available');btn.innerHTML=ICON;
 };

 const check=async()=>{
  if(checking||!navigator.onLine)return;checking=true;
  try{
   const r=await fetch(SOURCE+'?v='+Date.now(),{cache:'no-store'});if(!r.ok)return;
   const h=hash(await r.text());
   if(baseline===null)baseline=h;else if(h!==baseline)markUpdate();
  }catch(_){ }finally{checking=false}
 };

 const mo=new MutationObserver(mount);mo.observe(document.documentElement,{childList:true,subtree:true});
 mount();check();setInterval(check,30000);
 window.addEventListener('focus',check);window.addEventListener('online',check);
})();

/* No APK, a tela Hoje zera às 17:00 e passa a mostrar apenas o turno atual. */
(function bmInstallRiderShiftReset(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmRiderShiftResetV1)return;
 window.__bmRiderShiftResetV1=true;
 if(typeof todayHtml!=='function'||typeof renderRider!=='function')return;

 function spParts(date=new Date()){
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
  const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function shiftKey(){const p=spParts();return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10'}
 function inShift(iso,key){
  if(!iso)return false;
  const now=spParts(),p=spParts(new Date(iso));
  if(p.date!==now.date)return false;
  if(key==='17_24')return p.mins>=17*60;
  if(key==='10_17')return p.mins>=10*60&&p.mins<17*60;
  return false;
 }
 function currentView(d){
  const key=shiftKey();
  const e=(d?.e||[]).filter(x=>inShift(x.created_at,key));
  const s=(d?.s||[]).filter(x=>inShift(x.horario_saida||x.created_at,key));
  const base=d?.j?.chegada_at&&inShift(d.j.chegada_at,key)?Number(d.j.base_valor||0):0;
  const j=d?.j?{...d.j,base_valor:base,chegada_at:base?d.j.chegada_at:null}:d?.j;
  const total=base+e.reduce((a,x)=>a+Number(x.valor||0),0);
  return {...d,e,s,j,total,__bmShift:key};
 }

 const previousTodayHtml=todayHtml;
 todayHtml=function(d){
  const view=currentView(d);
  const label=view.__bmShift==='17_24'?'Turno 2 · 17:00–00:00':view.__bmShift==='10_17'?'Turno 1 · 10:00–17:00':'Aguardando turno · 10:00';
  return `<div class="notice" style="margin-bottom:10px;border-color:#31d98255"><strong>${label}</strong> · os contadores desta tela mostram somente o turno atual.</div>`+previousTodayHtml(view);
 };

 let lastKey=shiftKey();
 async function checkBoundary(){
  const key=shiftKey();
  if(key===lastKey)return;
  lastKey=key;
  if(rider?.active==='Hoje'){
   try{await renderRider('Hoje')}catch(e){console.warn('rider-shift-boundary',e?.message||e)}
  }
 }
 setInterval(checkBoundary,15000);
 window.addEventListener('focus',checkBoundary);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)checkBoundary()});
})();
