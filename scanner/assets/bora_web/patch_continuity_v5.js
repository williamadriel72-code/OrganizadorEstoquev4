(()=>{
 if(window.__bmContinuityV5Installed)return;
 window.__bmContinuityV5Installed=true;
 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const ADMIN_API=U+'/functions/v1/bora-turno-excecao-admin';
 const isRider=new URLSearchParams(location.search).get('app')==='motoboy';
 let state={active:false,date:null};
 let applying=false;
 const br=v=>{const p=String(v||'').split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v||'—')};
 const spParts=(value=new Date())=>{try{const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(value));const o={};for(const p of ps)if(p.type!=='literal')o[p.type]=p.value;return{date:`${o.year}-${o.month}-${o.day}`,hour:+o.hour,minute:+o.minute}}catch(_){const d=new Date(value);return{date:d.toISOString().slice(0,10),hour:d.getHours(),minute:d.getMinutes()}}};
 const realToday=()=>spParts().date;
 const opDate=()=>state.active&&state.date?state.date:realToday();
 async function readState(){const r=await fetch(STATE_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'state'}),cache:'no-store'});const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha ao consultar continuação');return j.exception||{}};
 function banner(){let el=document.getElementById('bmContV5Banner');if(!state.active){el?.remove();return}const root=document.querySelector('.shell')||document.querySelector('.admin-main');if(!root)return;if(!el){el=document.createElement('div');el.id='bmContV5Banner';el.style.cssText='margin:10px 14px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:11px;font-weight:900;line-height:1.35';root.prepend(el)}el.textContent='CONTINUAÇÃO ATIVA · TURNO '+br(state.date)+' · TODOS OS VALORES CONTINUAM SOMANDO ATÉ ENCERRAR A CONTINUAÇÃO.'}
 function forceToday(){if(typeof today==='function')today=function(){return opDate()};window.bmOperationalDate=opDate;window.bmOperationalState={active:state.active,date:state.date}}
 async function directRiderDay(){
  const date=opDate();
  const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at,chegada_tipo,fechado').eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
  if(j.error)throw j.error;if(!j.data)return{j:null,e:[],s:[],taxas:0,total:0};
  const [e,s]=await Promise.all([
   sb.from('kh_motoboy_entregas').select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at').eq('jornada_id',j.data.id).order('created_at'),
   sb.from('kh_motoboy_saidas').select('id,numero_sequencial,horario_saida,total,status,created_at').eq('jornada_id',j.data.id).order('numero_sequencial')
  ]);
  if(e.error)throw e.error;if(s.error)throw s.error;
  const rows=(e.data||[]).filter(x=>String(x.status||'').toLowerCase()!=='cancelada');
  const taxas=rows.reduce((a,x)=>a+Number(x.valor||0),0);const diaria=j.data.chegada_at?Number(j.data.base_valor||0):0;
  return{j:j.data,e:typeof bmSortRiderNotes==='function'?bmSortRiderNotes(rows):rows,s:s.data||[],taxas,total:diaria+taxas};
 }
 function noteHtml(e){if(typeof bmRiderNoteCard==='function')return bmRiderNoteCard(e);return `<div class="card"><b>COMANDA #${String(e.nota_numero||'—')}</b><div class="row-sub">${String(e.bairro_nome||'Bairro')} · ${typeof BRL==='function'?BRL(e.valor):'R$ '+Number(e.valor||0).toFixed(2)}</div></div>`}
 function riderTodayHtml(d){
  const valid=(d.e||[]).filter(x=>String(x.status||'').toLowerCase()!=='cancelada');
  const pending=valid.filter(x=>!x.nota_confirmada),confirmed=valid.filter(x=>x.nota_confirmada);
  const night=valid.filter(x=>{const p=spParts(x.created_at||x.updated_at);return p.date>opDate()||p.hour>=17});
  const morning=valid.filter(x=>!night.includes(x));
  const sum=a=>a.reduce((s,x)=>s+Number(x.valor||0),0);
  const diaria=d.j?.chegada_at?Number(d.j.base_valor||0):0;
  return `<section class="rider-fast-panel">
   <div class="notice" style="margin-bottom:10px;border-color:#d8871866;background:#2a1d0d;color:#f7cb78"><strong>CONTINUAÇÃO ATIVA · ${br(opDate())}</strong><br>O turno anterior permanece aberto. Nada zera até o painel encerrar a continuação.</div>
   <div class="today-summary"><div class="card"><div class="stat-label">DIÁRIA DO DIA</div><div class="stat-value">${BRL(diaria)}</div></div><div class="card"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(d.total||0)}</div></div></div>
   <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px"><div class="card"><div class="stat-label">MANHÃ · 10:00–17:00</div><div class="stat-value">${morning.length}</div><div class="row-sub">${BRL(sum(morning))}</div></div><div class="card"><div class="stat-label">NOITE · 17:00–CONTINUAÇÃO</div><div class="stat-value">${night.length}</div><div class="row-sub">${BRL(sum(night))}</div></div></div>
   <div class="notice" style="margin-top:10px">${d.j?.chegada_at?'Chegada registrada · diária '+BRL(diaria):'Sem chegada registrada'} · taxas acumuladas ${BRL(d.taxas||0)}.</div>
   <div class="section-title">Comandas pendentes <span style="float:right;color:#e5b544">${BRL(sum(pending))}</span></div>${pending.length?pending.map(noteHtml).join(''):'<div class="card"><div class="empty">Nenhuma comanda pendente.</div></div>'}
   <div class="section-title">Comandas confirmadas <span style="float:right;color:#e5b544">${BRL(sum(confirmed))}</span></div>${confirmed.length?confirmed.map(noteHtml).join(''):'<div class="card"><div class="empty">Nenhuma comanda confirmada.</div></div>'}
  </section>`;
 }
 function installRider(){if(!isRider||!state.active||typeof rider==='undefined'||!rider?.profile)return;forceToday();riderDay=async function(){return directRiderDay()};todayHtml=riderTodayHtml;if(typeof bmRiderCurrentShift==='function')bmRiderCurrentShift=()=> '17_24';if(typeof bmRiderInCurrentShift==='function')bmRiderInCurrentShift=()=>true;rider.selected=opDate();}
 function installAdmin(){if(isRider||!state.active||typeof adminState==='undefined')return;forceToday();if(typeof loadAdmin==='function')loadAdmin=async function(){const target=opDate();const [m,b,j,i]=await Promise.all([sb.from('kh_motoboys').select('*').order('nome'),sb.from('kh_motoboy_bairros').select('*').eq('ativo',true).order('nome'),sb.from('kh_motoboy_jornadas').select('*').eq('data',target),sb.from('kh_motoboy_instalacoes').select('*').order('ultimo_acesso',{ascending:false}).limit(300)]);for(const r of[m,b,j,i])if(r.error)throw r.error;const jornadas=j.data||[],ids=jornadas.map(x=>x.id);let entregas=[],saidas=[];if(ids.length){const[e,s]=await Promise.all([sb.from('kh_motoboy_entregas').select('*').in('jornada_id',ids).order('created_at'),sb.from('kh_motoboy_saidas').select('*').in('jornada_id',ids).order('created_at')]);if(e.error)throw e.error;if(s.error)throw s.error;entregas=e.data||[];saidas=s.data||[]}Object.assign(adminState,{motoboys:typeof sortMotos==='function'?sortMotos(m.data||[]):m.data||[],bairros:b.data||[],jornadas,instalacoes:i.data||[],entregas,saidas})};}
 function adminButton(){if(isRider)return;const head=document.querySelector('.admin-head');if(!head)return;let btn=document.getElementById('bmContV5Button');if(!btn){btn=document.createElement('button');btn.id='bmContV5Button';btn.className='btn secondary small';const logout=document.getElementById('logout');if(logout)head.insertBefore(btn,logout);else head.appendChild(btn);btn.onclick=async()=>{const action=state.active?'close':'open';if(!confirm(state.active?'Encerrar a continuação? Depois disso painel e APK iniciam o novo dia zerado.':'Continuar o turno anterior sem zerar valores?'))return;btn.disabled=true;try{const session=(await sb.auth.getSession())?.data?.session;if(!session?.access_token)throw Error('Sessão expirada.');const r=await fetch(ADMIN_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+session.access_token},body:JSON.stringify({action}),cache:'no-store'});const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha na continuação');location.reload()}catch(e){alert(e.message||e)}finally{btn.disabled=false}}}btn.textContent=state.active?'ENCERRAR CONTINUAÇÃO · '+br(state.date):'CONTINUAR TURNO ANTERIOR';btn.style.background=state.active?'#a3202a':'#9a5b0b'}
 async function applyState(ex,rerender){const before=state.active+'|'+state.date;state={active:!!ex?.ativa,date:ex?.data_operacional||null};forceToday();if(state.active){installRider();installAdmin()}banner();adminButton();const after=state.active+'|'+state.date;if(rerender||before!==after){if(isRider&&rider?.profile&&typeof renderRider==='function')await renderRider('Hoje');else if(!isRider&&typeof renderAdmin==='function'&&!document.querySelector('.login'))await renderAdmin()}}
 async function refresh(force=false){if(applying)return;applying=true;try{await applyState(await readState(),force)}catch(e){console.warn('continuity-v5',e?.message||e)}finally{applying=false}}
 setTimeout(()=>refresh(true),200);
 setInterval(()=>refresh(false),5000);
 window.addEventListener('focus',()=>refresh(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false)});
})();