/* Bootstrap da correção de despacho + sincronização/turnos do APK. */
(async()=>{
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 const PREVIOUS='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/2d3c36449f29743c9ead384375b86aafce2096bf/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 try{
  const r=await fetch(PREVIOUS+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-load',e?.message||e)}

 if(!riderMode||window.__bmRiderSyncV6)return;
 window.__bmRiderSyncV6=true;

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

 let riderDayInstalled=false;
 function installRiderDay(){
  if(riderDayInstalled||typeof sb==='undefined'||typeof today!=='function'||typeof rider==='undefined')return false;
  riderDayInstalled=true;

  riderDay=async function(date){
   if(!rider?.profile?.id)throw new Error('Perfil do motoboy ainda não carregado.');
   const j=await sb.from('kh_motoboy_jornadas')
    .select('id,data,base_valor,chegada_at,chegada_tipo,fechado')
    .eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
   if(j.error)throw j.error;
   if(!j.data)return {j:null,e:[],s:[],total:0,__bmShift:String(date)===String(today())?shiftKey():null};

   const [e,so]=await Promise.all([
    sb.from('kh_motoboy_entregas')
     .select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at')
     .eq('jornada_id',j.data.id).order('created_at'),
    sb.from('kh_motoboy_saidas')
     .select('id,numero_sequencial,horario_saida,total,status,created_at,updated_at')
     .eq('jornada_id',j.data.id).order('numero_sequencial')
   ]);
   if(e.error)throw e.error;if(so.error)throw so.error;

   let es=e.data||[],ss=so.data||[],base=j.data.chegada_at?Number(j.data.base_valor||0):0,jornada={...j.data};
   if(String(date)===String(today())){
    const key=shiftKey();
    es=es.filter(x=>inShift(x.created_at,key));
    ss=ss.filter(x=>inShift(x.horario_saida||x.created_at,key));
    base=(j.data.chegada_at&&inShift(j.data.chegada_at,key))?Number(j.data.base_valor||0):0;
    jornada={...j.data,base_valor:base,chegada_at:base?j.data.chegada_at:null};
   }
   if(typeof bmSortRiderNotes==='function')es=bmSortRiderNotes(es);
   const total=base+es.filter(x=>x.status!=='cancelada').reduce((a,x)=>a+Number(x.valor||0),0);
   return {j:jornada,e:es,s:ss,total,__bmShift:String(date)===String(today())?shiftKey():null};
  };
  return true;
 }

 let renderBusy=false,renderTimer=null,lastSignature=null,syncChannel=null,syncMid=null;
 async function renderHoje(){
  if(renderBusy||!rider?.profile||typeof renderRider!=='function')return;
  if(rider?.active&&rider.active!=='Hoje')return;
  renderBusy=true;
  try{await renderRider('Hoje')}catch(e){console.warn('rider-sync-v6-render',e?.message||e)}finally{renderBusy=false}
 }
 function scheduleRender(delay=180){
  clearTimeout(renderTimer);
  renderTimer=setTimeout(renderHoje,delay);
 }
 function signature(d){
  const j=d?.j||{};
  return JSON.stringify([
   shiftKey(),j.base_valor||0,j.chegada_at||null,
   ...(d?.e||[]).map(x=>[x.id,x.status,x.valor,!!x.nota_confirmada,x.nota_confirmada_at||null,x.updated_at||null]),
   ...(d?.s||[]).map(x=>[x.id,x.status,x.horario_saida||null,x.updated_at||null])
  ]);
 }
 async function pollChanged(){
  if(!rider?.profile||typeof riderDay!=='function'||document.hidden)return;
  try{
   const d=await riderDay(today());
   const sig=signature(d);
   if(lastSignature===null){lastSignature=sig;return}
   if(sig!==lastSignature){lastSignature=sig;scheduleRender(80)}
  }catch(e){console.warn('rider-sync-v6-poll',e?.message||e)}
 }
 function ensureRealtime(){
  if(!rider?.profile?.id||typeof sb==='undefined')return;
  const mid=rider.profile.id;
  if(syncChannel&&syncMid===mid)return;
  syncMid=mid;
  if(syncChannel){try{sb.removeChannel(syncChannel)}catch(_){ }}
  try{
   syncChannel=sb.channel('bm-rider-sync-v6-'+mid+'-'+Date.now())
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_entregas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_saidas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_jornadas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .subscribe();
  }catch(e){console.warn('rider-sync-v6-realtime',e?.message||e)}
 }

 let lastShift=shiftKey();
 function ensureAll(){
  installRiderDay();
  ensureRealtime();
  const key=shiftKey();
  if(key!==lastShift){lastShift=key;lastSignature=null;scheduleRender(60)}
 }

 /* Não depende do perfil já estar pronto quando a patch carrega. */
 setTimeout(()=>{ensureAll();scheduleRender(0)},250);
 setTimeout(()=>{ensureAll();scheduleRender(0)},1000);
 setTimeout(()=>{ensureAll();scheduleRender(0)},2500);
 setInterval(ensureAll,3000);
 setInterval(pollChanged,5000);
 window.addEventListener('focus',()=>{ensureAll();lastSignature=null;scheduleRender(80)});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){ensureAll();lastSignature=null;scheduleRender(80)}});
})();
