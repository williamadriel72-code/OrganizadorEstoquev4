/* Bootstrap da correção de despacho + sincronização/turnos do APK. */
(async()=>{
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 const PREVIOUS='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/2d3c36449f29743c9ead384375b86aafce2096bf/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 try{
  const r=await fetch(PREVIOUS+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-load',e?.message||e)}

 if(!riderMode||window.__bmRiderSyncV8)return;
 window.__bmRiderSyncV8=true;

 function spParts(date=new Date()){
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
  const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function shiftKey(){const p=spParts();return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10'}
 function inTodayShift(iso,key){
  if(!iso)return false;
  const now=spParts(),p=spParts(new Date(iso));
  if(p.date!==now.date)return false;
  if(key==='17_24')return p.mins>=17*60;
  if(key==='10_17')return p.mins>=10*60&&p.mins<17*60;
  return false;
 }
 function zeroSummary(date){
  return {data:date,turno1:{diaria:0,taxas:0,entregas:0,total:0,fechado:false},turno2:{diaria:0,taxas:0,entregas:0,total:0,fechado:false},total_dia:0,turno_atual:shiftKey()};
 }
 function fallbackSummary(date,j,allEs){
  const out=zeroSummary(date),valid=(allEs||[]).filter(x=>x.status!=='cancelada');
  const parts=iso=>{
   if(!iso)return null;
   const p=spParts(new Date(iso));
   return p.date===date?p:null;
  };
  const e1=valid.filter(x=>{const p=parts(x.created_at);return p&&p.mins>=10*60&&p.mins<17*60});
  const e2=valid.filter(x=>{const p=parts(x.created_at);return p&&p.mins>=17*60});
  const jp=parts(j?.chegada_at);
  const d1=jp&&jp.mins>=10*60&&jp.mins<17*60?Number(j.base_valor||0):0;
  const d2=jp&&jp.mins>=17*60?Number(j.base_valor||0):0;
  const t1=e1.reduce((a,x)=>a+Number(x.valor||0),0),t2=e2.reduce((a,x)=>a+Number(x.valor||0),0);
  out.turno1={diaria:d1,taxas:t1,entregas:e1.length,total:d1+t1,fechado:date<spParts().date||shiftKey()==='17_24'};
  out.turno2={diaria:d2,taxas:t2,entregas:e2.length,total:d2+t2,fechado:date<spParts().date};
  out.total_dia=out.turno1.total+out.turno2.total;
  return out;
 }

 async function getShiftSummary(date){
  try{
   const r=await sb.rpc('kh_get_my_shift_totals',{p_data:date});
   if(!r.error&&r.data)return r.data;
  }catch(_){ }
  return null;
 }

 let riderDayInstalled=false;
 function installRiderDay(){
  if(riderDayInstalled||typeof sb==='undefined'||typeof today!=='function'||typeof rider==='undefined')return false;
  riderDayInstalled=true;

  riderDay=async function(date){
   if(!rider?.profile?.id)throw new Error('Perfil do motoboy ainda não carregado.');

   const [j,shiftSummaryRaw]=await Promise.all([
    sb.from('kh_motoboy_jornadas')
     .select('id,data,base_valor,chegada_at,chegada_tipo,fechado')
     .eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle(),
    getShiftSummary(date)
   ]);
   if(j.error)throw j.error;

   let shiftSummary=shiftSummaryRaw;
   if(!j.data){
    if(!shiftSummary)shiftSummary=zeroSummary(date);
    return {j:null,e:[],s:[],total:Number(shiftSummary.total_dia||0),shiftSummary,__bmShift:String(date)===String(today())?shiftKey():null};
   }

   const [e,so]=await Promise.all([
    sb.from('kh_motoboy_entregas')
     .select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at')
     .eq('jornada_id',j.data.id).order('created_at'),
    sb.from('kh_motoboy_saidas')
     .select('id,numero_sequencial,horario_saida,total,status,created_at,updated_at')
     .eq('jornada_id',j.data.id).order('numero_sequencial')
   ]);
   if(e.error)throw e.error;if(so.error)throw so.error;

   const allEs=e.data||[],allSs=so.data||[];
   if(!shiftSummary)shiftSummary=fallbackSummary(date,j.data,allEs);

   let es=allEs,ss=allSs,base=j.data.chegada_at?Number(j.data.base_valor||0):0,jornada={...j.data};
   if(String(date)===String(today())){
    const key=shiftKey();
    es=allEs.filter(x=>inTodayShift(x.created_at,key));
    ss=allSs.filter(x=>inTodayShift(x.horario_saida||x.created_at,key));
    base=(j.data.chegada_at&&inTodayShift(j.data.chegada_at,key))?Number(j.data.base_valor||0):0;
    jornada={...j.data,base_valor:base,chegada_at:base?j.data.chegada_at:null};
   }
   if(typeof bmSortRiderNotes==='function')es=bmSortRiderNotes(es);
   const currentTotal=base+es.filter(x=>x.status!=='cancelada').reduce((a,x)=>a+Number(x.valor||0),0);
   const total=String(date)===String(today())?currentTotal:Number(shiftSummary?.total_dia||currentTotal);
   return {j:jornada,e:es,s:ss,total,shiftSummary,__bmShift:String(date)===String(today())?shiftKey():null};
  };
  return true;
 }

 let todayHtmlInstalled=false;
 function installTodayHtml(){
  if(todayHtmlInstalled||typeof todayHtml!=='function'||typeof bmRiderNoteCard!=='function'||typeof BRL!=='function')return false;
  todayHtmlInstalled=true;

  const n=v=>Number(v||0);
  const shiftCard=(title,x,active)=>`<div class="card" style="border-color:${active?'rgba(49,217,130,.42)':'rgba(255,255,255,.08)'};box-shadow:${active?'0 0 18px rgba(49,217,130,.08)':'none'}"><div class="stat-label">${title}</div><div class="stat-value">${BRL(n(x?.total))}</div><div class="row-sub" style="margin-top:7px">Diária ${BRL(n(x?.diaria))} · Bairros ${BRL(n(x?.taxas))}</div><div class="row-sub">${Number(x?.entregas||0)} entrega${Number(x?.entregas||0)===1?'':'s'}${x?.fechado?' · fechado':''}</div></div>`;

  todayHtml=function(d){
   const s=d?.shiftSummary||zeroSummary(typeof today==='function'?today():'');
   const t1=s.turno1||{},t2=s.turno2||{};
   const current=d?.__bmShift||shiftKey();
   const valid=typeof bmSortRiderNotes==='function'?bmSortRiderNotes((d?.e||[]).filter(e=>e.status!=='cancelada')):(d?.e||[]).filter(e=>e.status!=='cancelada');
   const pending=valid.filter(e=>!e.nota_confirmada),confirmed=valid.filter(e=>e.nota_confirmada),list=[...pending,...confirmed];
   const currentName=current==='17_24'?'Turno 2 · Noite · 17:00–00:00':current==='10_17'?'Turno 1 · Manhã · 10:00–17:00':'Aguardando turno · 10:00';
   return `<section class="rider-fast-panel">
    <div class="notice" style="margin-bottom:10px;border-color:#31d98255"><strong>${currentName}</strong><br>Os valores da manhã e da noite ficam separados e o total do dia soma os dois turnos.</div>
    <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px">
     ${shiftCard('TURNO 1 · MANHÃ',t1,current==='10_17')}
     ${shiftCard('TURNO 2 · NOITE',t2,current==='17_24')}
    </div>
    <div class="card" style="margin-top:10px;border-color:rgba(241,166,55,.24)"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(n(s.total_dia))}</div><div class="row-sub" style="margin-top:6px">Manhã ${BRL(n(t1.total))} + Noite ${BRL(n(t2.total))}</div></div>
    <div class="section-title">Conferir notas · ${current==='17_24'?'Noite':'Manhã'}</div>
    <div class="notice"><b>${pending.length?`${pending.length} nota${pending.length===1?'':'s'} aguardando confirmação`:'Todas as notas estão confirmadas'}</b><br>Confira o número e o bairro da nota física do turno atual.</div>
    <div style="margin-top:10px">${list.length?list.map(bmRiderNoteCard).join(''):'<div class="card"><div class="empty">Nenhuma nota lançada neste turno.</div></div>'}</div>
   </section>`;
  };
  return true;
 }

 let historyInstalled=false;
 function installHistoryHtml(){
  if(historyInstalled||typeof dayHistoryHtml!=='function'||typeof BRL!=='function'||typeof esc!=='function')return false;
  historyInstalled=true;
  dayHistoryHtml=function(d){
   if(!d?.j&&!d?.shiftSummary)return '<div class="card"><div class="empty">Sem entregas neste dia.</div></div>';
   const sm=d?.shiftSummary||zeroSummary(rider?.selected||'');
   const t1=sm.turno1||{},t2=sm.turno2||{};
   const card=(title,x)=>`<div class="card"><div class="stat-label">${title}</div><div class="stat-value">${BRL(Number(x?.total||0))}</div><div class="row-sub" style="margin-top:6px">Diária ${BRL(Number(x?.diaria||0))} · Bairros ${BRL(Number(x?.taxas||0))}</div><div class="row-sub">${Number(x?.entregas||0)} entrega${Number(x?.entregas||0)===1?'':'s'}</div></div>`;
   return `<div class="grid two">${card('Turno 1 · Manhã',t1)}${card('Turno 2 · Noite',t2)}</div>
    <div class="card" style="margin-top:10px"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(Number(sm.total_dia||0))}</div></div>
    <div class="section-title">Entregas</div><div class="card">${(d.e||[]).map(e=>`<div class="row"><div><div class="row-title">Nota ${esc(e.nota_numero)} · ${esc(e.bairro_nome)}</div><div class="row-sub">${e.tipo==='integral'?'Taxa Integral':'Normal'}</div></div><div class="money">${BRL(e.valor)}</div></div>`).join('')||'<div class="empty">Nenhuma entrega.</div>'}</div>
    <div class="section-title">Saídas</div><div class="card">${(d.s||[]).map(s=>`<div class="row"><div><div class="row-title">Saída ${String(s.numero_sequencial).padStart(2,'0')}</div><div class="row-sub">${new Date(s.horario_saida||s.created_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</div></div><div class="money">${BRL(s.total)}</div></div>`).join('')||'<div class="empty">Nenhuma saída registrada.</div>'}</div>`;
  };
  return true;
 }

 let monthDataInstalled=false;
 function installMonthData(){
  if(monthDataInstalled||typeof sb==='undefined'||typeof isoDate!=='function'||typeof rider==='undefined')return false;
  monthDataInstalled=true;
  monthData=async function(y,m){
   const start=`${y}-${String(m+1).padStart(2,'0')}-01`,end=isoDate(new Date(y,m+1,0));
   const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at').eq('motoboy_id',rider.profile.id).gte('data',start).lte('data',end).order('data');
   if(j.error)throw j.error;
   const rows=j.data||[],sums={};
   await Promise.all(rows.map(async row=>{
    const sm=await getShiftSummary(row.data);
    if(sm){sums[row.data]=Number(sm.total_dia||0);return}
    const d=await riderDay(row.data);
    sums[row.data]=Number(d?.total||0);
   }));
   return {j:rows,e:[],sums};
  };
  return true;
 }

 let renderBusy=false,renderTimer=null,lastSignature=null,syncChannel=null,syncMid=null;
 async function renderHoje(){
  if(renderBusy||!rider?.profile||typeof renderRider!=='function')return;
  if(rider?.active&&rider.active!=='Hoje')return;
  renderBusy=true;
  try{await renderRider('Hoje')}catch(e){console.warn('rider-sync-v8-render',e?.message||e)}finally{renderBusy=false}
 }
 function scheduleRender(delay=180){clearTimeout(renderTimer);renderTimer=setTimeout(renderHoje,delay)}
 function signature(d){
  const j=d?.j||{},s=d?.shiftSummary||{},t1=s.turno1||{},t2=s.turno2||{};
  return JSON.stringify([
   shiftKey(),j.base_valor||0,j.chegada_at||null,
   t1.diaria||0,t1.taxas||0,t1.entregas||0,t1.total||0,!!t1.fechado,
   t2.diaria||0,t2.taxas||0,t2.entregas||0,t2.total||0,!!t2.fechado,s.total_dia||0,
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
  }catch(e){console.warn('rider-sync-v8-poll',e?.message||e)}
 }
 function ensureRealtime(){
  if(!rider?.profile?.id||typeof sb==='undefined')return;
  const mid=rider.profile.id;
  if(syncChannel&&syncMid===mid)return;
  syncMid=mid;
  if(syncChannel){try{sb.removeChannel(syncChannel)}catch(_){ }}
  try{
   syncChannel=sb.channel('bm-rider-sync-v8-'+mid+'-'+Date.now())
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_entregas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_saidas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_jornadas',filter:`motoboy_id=eq.${mid}`},()=>{lastSignature=null;scheduleRender(120)})
    .subscribe();
  }catch(e){console.warn('rider-sync-v8-realtime',e?.message||e)}
 }

 let lastShift=shiftKey();
 function ensureAll(){
  const a=installRiderDay(),b=installTodayHtml(),c=installHistoryHtml(),d=installMonthData();
  ensureRealtime();
  const key=shiftKey();
  if(key!==lastShift){lastShift=key;lastSignature=null;scheduleRender(60)}
  if(a||b||c||d)scheduleRender(60);
 }

 setTimeout(()=>{ensureAll();scheduleRender(0)},250);
 setTimeout(()=>{ensureAll();scheduleRender(0)},1000);
 setTimeout(()=>{ensureAll();scheduleRender(0)},2500);
 setInterval(ensureAll,3000);
 setInterval(pollChanged,5000);
 window.addEventListener('focus',()=>{ensureAll();lastSignature=null;scheduleRender(80)});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){ensureAll();lastSignature=null;scheduleRender(80)}});
})();
