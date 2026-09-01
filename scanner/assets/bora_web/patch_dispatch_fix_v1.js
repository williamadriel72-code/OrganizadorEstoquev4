/* Bootstrap da correção de despacho + fechamento do APK.
   Mantém a versão estável anterior e aplica por cima a regra operacional das 17:00. */
(async()=>{
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 const PREVIOUS='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/2d3c36449f29743c9ead384375b86aafce2096bf/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 try{
  const r=await fetch(PREVIOUS+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-load',e?.message||e)}

 if(!riderMode||window.__bmRiderShiftHardZeroV3)return;
 window.__bmRiderShiftHardZeroV3=true;

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

 /* Fonte única para a tela Hoje: depois das 17h a diária é sempre R$ 0,00.
    Entregas e saídas anteriores às 17h ficam fora do turno atual. */
 if(typeof riderDay==='function'&&!window.__bmRiderDayShiftWrappedV3){
  window.__bmRiderDayShiftWrappedV3=true;
  const rawRiderDay=riderDay;
  riderDay=async function(date){
   const d=await rawRiderDay(date);
   if(String(date)!==String(today()))return d;
   const key=shiftKey();
   const e=(d?.e||[]).filter(x=>inShift(x.created_at,key));
   const s=(d?.s||[]).filter(x=>inShift(x.horario_saida||x.created_at,key));
   let base=0;
   if(key==='10_17'&&d?.j?.chegada_at&&inShift(d.j.chegada_at,key))base=Number(d.j.base_valor||0);
   const j=d?.j?{...d.j,base_valor:base,chegada_at:base?d.j.chegada_at:null}:d?.j;
   const total=base+e.reduce((a,x)=>a+Number(x.valor||0),0);
   return {...d,j,e,s,total,__bmShift:key};
  };
 }

 /* Garante o texto do turno mesmo quando uma versão antiga da tela estiver em uso. */
 if(typeof todayHtml==='function'&&!window.__bmTodayShiftLabelV3){
  window.__bmTodayShiftLabelV3=true;
  const rawTodayHtml=todayHtml;
  todayHtml=function(d){
   const key=d?.__bmShift||shiftKey();
   const title=key==='17_24'?'Turno 2 · 17:00–00:00':key==='10_17'?'Turno 1 · 10:00–17:00':'Aguardando turno · 10:00';
   return `<div class="notice" style="margin-bottom:10px;border-color:#31d98255"><strong>${title}</strong> · diária e totais referentes somente ao turno atual.</div>`+rawTodayHtml(d);
  };
 }

 let lastShift=shiftKey(),busy=false;
 async function redraw(force=false){
  const key=shiftKey();
  if(!force&&key===lastShift)return;
  lastShift=key;
  if(busy||!rider?.profile||typeof renderRider!=='function')return;
  if(rider?.active&&rider.active!=='Hoje')return;
  busy=true;
  try{await renderRider('Hoje')}catch(e){console.warn('rider-shift-v3',e?.message||e)}finally{busy=false}
 }
 setTimeout(()=>redraw(true),250);
 setTimeout(()=>redraw(true),1100);
 setInterval(()=>redraw(false),15000);
 window.addEventListener('focus',()=>redraw(true));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)redraw(true)});
})();
