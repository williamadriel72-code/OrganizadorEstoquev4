(()=>{
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmContinuityFullV2)return;window.__bmContinuityFullV2=true;

 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const ADMIN_API=U+'/functions/v1/bora-turno-excecao-admin';
 const state=window.bmContinuityFullState||{active:false,date:null,openedAt:null};
 window.bmContinuityFullState=state;
 let rendering=false,lastKey='';

 function spDate(){
  const p=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  const o={};for(const x of p)if(x.type!=='literal')o[x.type]=x.value;
  return `${o.year}-${o.month}-${o.day}`;
 }
 function opDate(){return state.active&&state.date?state.date:spDate()}
 function brDate(v){if(!v)return '—';const p=String(v).split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v)}
 function money(v){try{return BRL(Number(v||0))}catch(_){return 'R$ '+Number(v||0).toFixed(2).replace('.',',')}}
 window.bmOperationalDate=opDate;

 const previousLoadAdmin=typeof loadAdmin==='function'?loadAdmin:null;
 loadAdmin=async function(){
  const t=opDate();
  const [m,b,j,i]=await Promise.all([
   sb.from('kh_motoboys').select('*').order('nome'),
   sb.from('kh_motoboy_bairros').select('*').eq('ativo',true).order('nome'),
   sb.from('kh_motoboy_jornadas').select('*').eq('data',t),
   sb.from('kh_motoboy_instalacoes').select('*').order('ultimo_acesso',{ascending:false}).limit(300)
  ]);
  for(const r of [m,b,j,i])if(r.error)throw r.error;
  const jornadas=j.data||[],jids=jornadas.map(x=>x.id);
  let entregas=[],saidas=[];
  if(jids.length){
   const [e,s]=await Promise.all([
    sb.from('kh_motoboy_entregas').select('*').in('jornada_id',jids).order('created_at'),
    sb.from('kh_motoboy_saidas').select('*').in('jornada_id',jids).order('created_at')
   ]);
   if(e.error)throw e.error;if(s.error)throw s.error;
   entregas=e.data||[];saidas=s.data||[];
  }
  Object.assign(adminState,{motoboys:sortMotos(m.data||[]),bairros:b.data||[],jornadas,instalacoes:i.data||[],entregas,saidas});
  if(adminState.selectedMoto&&!adminState.motoboys.some(x=>x.id===adminState.selectedMoto))adminState.selectedMoto=null;
  window.bmLoadedOperationalDate=t;
 };

 const previousEnsure=typeof ensureJornada==='function'?ensureJornada:null;
 ensureJornada=async function(motoboy_id,data){
  const target=data||opDate();
  let j=(adminState.jornadas||[]).find(x=>x.motoboy_id===motoboy_id&&x.data===target);
  if(j)return j;
  const q=await sb.from('kh_motoboy_jornadas').select('*').eq('motoboy_id',motoboy_id).eq('data',target).maybeSingle();
  if(q.error)throw q.error;
  if(q.data){adminState.jornadas.push(q.data);return q.data}
  if(previousEnsure)return previousEnsure(motoboy_id,target);
  const r=await sb.from('kh_motoboy_jornadas').insert({motoboy_id,data:target,chegada_tipo:'nao_informada',base_valor:0}).select().single();
  if(r.error)throw r.error;adminState.jornadas.push(r.data);return r.data;
 };

 async function stateRequest(){
  const r=await fetch(STATE_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'state'}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha ao consultar a continuidade.');return j.exception||{};
 }
 async function adminRequest(action){
  const session=(await sb.auth.getSession())?.data?.session;
  if(!session?.access_token)throw Error('Sessão administrativa expirada. Entre novamente no painel.');
  const r=await fetch(ADMIN_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+session.access_token},body:JSON.stringify({action}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha ao alterar a continuidade.');return j.exception||{};
 }
 function apply(ex){
  state.active=!!ex?.ativa;state.date=ex?.data_operacional||null;state.openedAt=ex?.aberta_em||null;
  if(window.bmShiftExceptionState){window.bmShiftExceptionState.active=state.active;window.bmShiftExceptionState.date=state.date;window.bmShiftExceptionState.openedAt=state.openedAt}
  updateUi();
 }

 function css(){
  if(document.getElementById('bmContinuityV2Style'))return;
  const s=document.createElement('style');s.id='bmContinuityV2Style';s.textContent=`
   #bmShiftExceptionTopBtn,#bmShiftExceptionBox{display:none!important}
   #bmContinuityV2Btn{min-height:38px;border:1px solid #e29a2d88;border-radius:10px;background:#b86b09;color:#fff;font-weight:950;padding:9px 12px;margin-right:8px;white-space:nowrap;cursor:pointer}
   #bmContinuityV2Btn.on{background:#a91f28;border-color:#e54652aa}
   #bmContinuityV2Btn:disabled{opacity:.6}
   #bmContinuityV2Banner{margin:0 0 12px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:12px;font-weight:900}
   @media(max-width:800px){#bmContinuityV2Btn{font-size:9px;padding:8px;min-height:34px}}
  `;document.head.appendChild(s);
 }
 function installButton(){
  css();const head=document.querySelector('.admin-head');if(!head)return;
  let btn=document.getElementById('bmContinuityV2Btn');
  if(!btn){btn=document.createElement('button');btn.id='bmContinuityV2Btn';btn.type='button';btn.onclick=toggle;const logout=document.getElementById('logout');if(logout)head.insertBefore(btn,logout);else head.appendChild(btn)}
  let banner=document.getElementById('bmContinuityV2Banner');const main=document.querySelector('.admin-main');
  if(state.active&&main){if(!banner){banner=document.createElement('div');banner.id='bmContinuityV2Banner';const h=main.querySelector('.admin-head');if(h?.nextSibling)main.insertBefore(banner,h.nextSibling);else main.prepend(banner)}banner.textContent='TURNO EM CONTINUIDADE — '+brDate(state.date)+' · todo o histórico permanece carregado e os novos lançamentos continuam somando sem zerar.'}else banner?.remove();
  updateUi();
 }
 function updateUi(){
  const btn=document.getElementById('bmContinuityV2Btn');if(!btn)return;
  btn.classList.toggle('on',state.active);
  btn.textContent=state.active?'ENCERRAR CONTINUIDADE · '+brDate(state.date):'CONTINUAR TURNO';
 }

 function rows(){
  const out=[];for(const m of (adminState?.motoboys||[])){
   const j=typeof jornadaOf==='function'?jornadaOf(m.id):null;
   const es=(typeof entregasOf==='function'?entregasOf(m.id):[]).filter(x=>x?.status!=='cancelada');
   const os=(typeof saidasOf==='function'?saidasOf(m.id):[]).filter(Boolean);
   out.push({m,j,es,os});
  }return out;
 }
 function isInDelivery(r){
  const active=new Set(['liberada','em_andamento','em_entrega','andamento']);
  const closed=new Set(['entregue','finalizada','finalizado','concluida','concluido','cancelada','cancelado','fechada','fechado']);
  if(r.os.some(o=>active.has(String(o.status||'').toLowerCase())))return true;
  return r.es.some(e=>e.saida_id&&!closed.has(String(e.status||'').toLowerCase()));
 }
 function setStrong(el,val){const s=el?.querySelector('strong');if(s)s.textContent=String(val)}
 function fixOverview(){
  if(!state.active)return;
  const box=document.getElementById('bmPanelOverview');if(!box)return;
  const rs=rows();
  const active=rs.filter(r=>r.j?.chegada_at&&r.j?.chegada_tipo!=='nao_compareceu').length;
  const delivery=rs.filter(isInDelivery).length;
  const noShow=rs.filter(r=>r.j?.chegada_tipo==='nao_compareceu').length;
  const outings=rs.reduce((a,r)=>a+r.os.length,0);
  const deliveries=rs.reduce((a,r)=>a+r.es.length,0);
  const value=rs.reduce((a,r)=>a+r.es.reduce((s,e)=>s+Number(e.valor||0),0),0);
  const stats=box.querySelectorAll('.bm-ov-stats .bm-ov-stat');
  setStrong(stats[0],active);setStrong(stats[1],delivery);setStrong(stats[2],noShow);setStrong(stats[3],outings);setStrong(stats[4],deliveries);setStrong(stats[5],money(value));
  const title=box.querySelector('.bm-ov-turn .bm-ov-card-head h3');if(title)title.textContent='Continuação de '+brDate(state.date);
  const tv=box.querySelectorAll('.bm-ov-turn-grid>div');
  setStrong(tv[0],deliveries);setStrong(tv[1],outings);setStrong(tv[2],money(value));setStrong(tv[3],active);
  const note=box.querySelector('.bm-ov-turn-note');if(note)note.textContent='Continuidade ativa: todo o histórico de '+brDate(state.date)+' permanece no turno e os novos lançamentos continuam somando sem zerar.';
  box.querySelectorAll('.bm-ov-stat em').forEach((e,i)=>{if([3,4,5].includes(i))e.textContent=i===3?'registradas no turno continuado':i===4?'histórico completo do dia operacional':'taxas acumuladas no turno'});
 }

 async function rerender(){
  if(rendering||typeof renderAdmin!=='function'||document.querySelector('.login'))return;
  rendering=true;try{await renderAdmin();requestAnimationFrame(()=>{installButton();fixOverview()})}finally{rendering=false}
 }
 async function refresh(force=false){
  try{
   const ex=await stateRequest();const key=(ex?.ativa?'1':'0')+'|'+String(ex?.data_operacional||'');const changed=key!==lastKey;lastKey=key;apply(ex);
   if((changed||force)&&document.querySelector('.admin-layout'))await rerender();
  }catch(e){console.warn('bm-continuity-v2',e?.message||e)}
 }
 async function toggle(){
  const btn=document.getElementById('bmContinuityV2Btn');const action=state.active?'close':'open';
  const msg=state.active?'Encerrar a continuidade? Os próximos lançamentos passarão para o novo dia.':'Continuar o turno anterior mantendo todo o histórico e os valores já lançados?';
  if(!confirm(msg))return;if(btn)btn.disabled=true;
  try{apply(await adminRequest(action));await rerender();if(typeof toast==='function')toast(state.active?'Continuidade ativada. Histórico restaurado e valores continuam somando.':'Continuidade encerrada.')}
  catch(e){console.error(e);if(typeof toast==='function')toast(e?.message||'Não foi possível alterar a continuidade.');else alert(e?.message||e)}finally{if(btn)btn.disabled=false}
 }

 const originalBoot=typeof boot==='function'?boot:null;
 if(originalBoot)boot=async function(){await refresh(false);return originalBoot()};

 let raf=0;const observer=new MutationObserver(()=>{if(raf)return;raf=requestAnimationFrame(()=>{raf=0;installButton();fixOverview()})});
 observer.observe(document.documentElement,{childList:true,subtree:true});
 setInterval(()=>refresh(false),12000);
 window.addEventListener('focus',()=>refresh(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false)});
 setTimeout(()=>{installButton();fixOverview()},250);
})();