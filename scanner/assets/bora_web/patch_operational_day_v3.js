(()=>{
 if(window.__bmOperationalDayV3)return;
 window.__bmOperationalDayV3=true;

 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const originalToday=typeof today==='function'?today:null;
 const state=window.bmOperationalState||{active:false,date:null,openedAt:null};
 window.bmOperationalState=state;

 function spDateNow(){
  try{
   const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
   const o={};for(const p of ps)if(p.type!=='literal')o[p.type]=p.value;
   return `${o.year}-${o.month}-${o.day}`;
  }catch(_){return originalToday?originalToday():new Date().toISOString().slice(0,10)}
 }
 function operationalDate(){return state.active&&state.date?state.date:spDateNow()}
 function brDate(v){if(!v)return '—';const p=String(v).split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v)}
 function money(v){try{return BRL(Number(v||0))}catch(_){return 'R$ '+Number(v||0).toFixed(2).replace('.',',')}}
 window.bmOperationalDate=operationalDate;
 window.bmOperationalState=state;

 // Regra central: enquanto a continuidade estiver ativa, "hoje" para o sistema
 // é o dia operacional aberto, não a virada do relógio do aparelho.
 if(originalToday){
  today=function(){return operationalDate()};
 }

 async function stateRequest(){
  const r=await fetch(STATE_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'state'}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));
  if(!r.ok)throw Error(j.error||'Falha ao consultar a continuidade do turno.');
  return j.exception||{};
 }
 function apply(ex){
  state.active=!!ex?.ativa;
  state.date=ex?.data_operacional||null;
  state.openedAt=ex?.aberta_em||null;
  window.bmOperationalState=state;
  if(typeof rider!=='undefined'&&rider&&state.active)rider.selected=operationalDate();
 }

 function installAdminRules(){
  if(new URLSearchParams(location.search).get('app')==='motoboy')return;
  if(window.__bmOperationalAdminV3)return;
  window.__bmOperationalAdminV3=true;

  const oldEnsure=typeof ensureJornada==='function'?ensureJornada:null;
  loadAdmin=async function(){
   const t=operationalDate();
   const [m,b,j,i]=await Promise.all([
    sb.from('kh_motoboys').select('*').order('nome'),
    sb.from('kh_motoboy_bairros').select('*').eq('ativo',true).order('nome'),
    sb.from('kh_motoboy_jornadas').select('*').eq('data',t),
    sb.from('kh_motoboy_instalacoes').select('*').order('ultimo_acesso',{ascending:false}).limit(300)
   ]);
   for(const r of [m,b,j,i])if(r.error)throw r.error;
   const jornadas=j.data||[],ids=jornadas.map(x=>x.id);
   let entregas=[],saidas=[];
   if(ids.length){
    const [e,s]=await Promise.all([
     sb.from('kh_motoboy_entregas').select('*').in('jornada_id',ids).order('created_at'),
     sb.from('kh_motoboy_saidas').select('*').in('jornada_id',ids).order('created_at')
    ]);
    if(e.error)throw e.error;if(s.error)throw s.error;
    entregas=e.data||[];saidas=s.data||[];
   }
   Object.assign(adminState,{motoboys:sortMotos(m.data||[]),bairros:b.data||[],jornadas,instalacoes:i.data||[],entregas,saidas});
   if(adminState.selectedMoto&&!adminState.motoboys.some(x=>x.id===adminState.selectedMoto))adminState.selectedMoto=null;
   window.bmLoadedOperationalDate=t;
  };

  ensureJornada=async function(motoboy_id,data){
   const target=data||operationalDate();
   let j=(adminState.jornadas||[]).find(x=>String(x.motoboy_id)===String(motoboy_id)&&String(x.data)===String(target));
   if(j)return j;
   const q=await sb.from('kh_motoboy_jornadas').select('*').eq('motoboy_id',motoboy_id).eq('data',target).maybeSingle();
   if(q.error)throw q.error;
   if(q.data){adminState.jornadas.push(q.data);return q.data}
   if(oldEnsure)return oldEnsure(motoboy_id,target);
   const r=await sb.from('kh_motoboy_jornadas').insert({motoboy_id,data:target,chegada_tipo:'nao_informada',base_valor:0}).select().single();
   if(r.error)throw r.error;adminState.jornadas.push(r.data);return r.data;
  };
 }

 function installRiderRules(){
  if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
  if(window.__bmOperationalRiderV3)return;
  window.__bmOperationalRiderV3=true;

  const rawRiderDay=typeof riderDay==='function'?riderDay:null;
  if(rawRiderDay){
   riderDay=async function(date){
    const requested=state.active?operationalDate():(date||operationalDate());
    return rawRiderDay(requested);
   };
  }

  // A continuidade após 00:00 ainda pertence ao turno da noite do dia operacional.
  if(typeof bmRiderCurrentShift==='function'){
   bmRiderCurrentShift=function(){
    if(state.active&&state.date&&spDateNow()!==state.date)return '17_24';
    const d=new Date(),mins=d.getHours()*60+d.getMinutes();
    return mins>=17*60?'17_24':mins>=10*60?'10_17':'pre_10';
   };
  }
  if(typeof bmRiderInCurrentShift==='function'){
   bmRiderInCurrentShift=function(value){
    if(!value)return false;
    if(state.active&&state.date){
     let p;
     try{
      const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(value));
      const o={};for(const x of ps)if(x.type!=='literal')o[x.type]=x.value;
      p={date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
     }catch(_){return true}
     if(p.date>state.date)return true;
     return p.date===state.date&&p.mins>=17*60;
    }
    const now=new Date(),d=new Date(value),mins=now.getHours()*60+now.getMinutes(),vmins=d.getHours()*60+d.getMinutes();
    if(d.toDateString()!==now.toDateString())return false;
    if(mins>=17*60)return vmins>=17*60;
    if(mins>=10*60)return vmins>=10*60&&vmins<17*60;
    return false;
   };
  }
 }

 function installBanner(){
  if(!state.active){document.getElementById('bmOperationalBanner')?.remove();return}
  if(document.getElementById('bmOperationalBanner'))return;
  const root=document.querySelector('.shell')||document.querySelector('.admin-main');
  if(!root)return;
  const d=document.createElement('div');
  d.id='bmOperationalBanner';
  d.textContent='CONTINUIDADE ATIVA · TURNO '+brDate(state.date)+' · valores, comandas, saídas e diária continuam no mesmo expediente.';
  d.style.cssText='margin:10px 14px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:11px;font-weight:900;line-height:1.35';
  const header=root.querySelector('.top,.admin-head');
  if(header?.nextSibling)root.insertBefore(d,header.nextSibling);else root.prepend(d);
 }

 function fixAdminOverview(){
  if(!state.active||new URLSearchParams(location.search).get('app')==='motoboy')return;
  const box=document.getElementById('bmPanelOverview');if(!box||typeof adminState==='undefined')return;
  const motos=adminState.motoboys||[];
  const rows=motos.map(m=>({
   m,
   j:typeof jornadaOf==='function'?jornadaOf(m.id):null,
   es:(typeof entregasOf==='function'?entregasOf(m.id):[]).filter(x=>String(x?.status||'').toLowerCase()!=='cancelada'),
   os:typeof saidasOf==='function'?saidasOf(m.id):[]
  }));
  const active=rows.filter(r=>r.j?.chegada_at&&r.j?.chegada_tipo!=='nao_compareceu').length;
  const outings=rows.reduce((a,r)=>a+r.os.length,0);
  const deliveries=rows.reduce((a,r)=>a+r.es.length,0);
  const value=rows.reduce((a,r)=>a+r.es.reduce((s,e)=>s+Number(e.valor||0),0),0);
  const stats=box.querySelectorAll('.bm-ov-stats .bm-ov-stat');
  const set=(el,v)=>{const x=el?.querySelector('strong');if(x)x.textContent=String(v)};
  set(stats[0],active);set(stats[3],outings);set(stats[4],deliveries);set(stats[5],money(value));
  const title=box.querySelector('.bm-ov-turn .bm-ov-card-head h3');if(title)title.textContent='Continuação de '+brDate(state.date);
  const tv=box.querySelectorAll('.bm-ov-turn-grid>div');set(tv[0],deliveries);set(tv[1],outings);set(tv[2],money(value));set(tv[3],active);
  const note=box.querySelector('.bm-ov-turn-note');if(note)note.textContent='Continuidade ativa: o painel está usando '+brDate(state.date)+' como dia operacional até o expediente ser encerrado.';
 }

 async function refresh(force=false){
  try{
   const before=(state.active?'1':'0')+'|'+String(state.date||'');
   apply(await stateRequest());
   const after=(state.active?'1':'0')+'|'+String(state.date||'');
   installBanner();fixAdminOverview();
   if(force||before!==after){
    if(new URLSearchParams(location.search).get('app')==='motoboy'){
     if(typeof rider!=='undefined'&&rider?.profile&&typeof renderRider==='function')await renderRider(rider.active||'Hoje');
    }else if(typeof renderAdmin==='function'&&!document.querySelector('.login'))await renderAdmin();
   }
  }catch(e){console.warn('bm-operational-refresh',e?.message||e)}
 }

 window.__bmOperationalReady=(async()=>{
  try{apply(await stateRequest())}catch(e){console.warn('bm-operational-init',e?.message||e)}
  installAdminRules();
  installRiderRules();
  if(typeof rider!=='undefined'&&rider&&state.active)rider.selected=operationalDate();
  setTimeout(()=>{installBanner();fixAdminOverview()},120);
  return state;
 })();

 let raf=0;
 const obs=new MutationObserver(()=>{if(raf)return;raf=requestAnimationFrame(()=>{raf=0;installBanner();fixAdminOverview()})});
 obs.observe(document.documentElement,{childList:true,subtree:true});
 setInterval(()=>refresh(false),12000);
 window.addEventListener('focus',()=>refresh(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false)});
})();