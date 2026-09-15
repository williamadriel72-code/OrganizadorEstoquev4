(function bmInstallFolgasV1(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmFolgasV1)return;
 window.__bmFolgasV1=true;

 const ENDPOINT='https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bhhi-folgas';
 const state={data:null,month:new Date(),selected:null,loading:false,sig:''};

 const pad=n=>String(n).padStart(2,'0');
 const ymd=d=>`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
 const fmt=d=>new Date(String(d)+'T12:00:00').toLocaleDateString('pt-BR');
 const statusLabel=s=>s==='aprovado'?'APROVADA':s==='recusado'?'RECUSADA':'PENDENTE';
 const statusColor=s=>s==='aprovado'?'#22c55e':s==='recusado'?'#ef4444':'#f2a33c';

 if(!document.getElementById('bmFolgasStyle')){
  const st=document.createElement('style');
  st.id='bmFolgasStyle';
  st.textContent=`
   .nav{grid-template-columns:repeat(6,1fr)!important}
   .bm-folgas{max-width:760px;margin:auto}
   .bm-folga-calendar{display:grid;grid-template-columns:repeat(7,1fr);gap:6px}
   .bm-folga-dow{text-align:center;color:var(--muted);font-size:10px;font-weight:900;padding:5px 0}
   .bm-folga-day{min-height:58px;border:1px solid var(--line);border-radius:12px;background:#171a1e;color:#fff;padding:7px;display:flex;flex-direction:column;align-items:flex-start;justify-content:space-between;font-weight:900}
   .bm-folga-day small{font-size:9px;color:var(--muted);font-weight:800}
   .bm-folga-day.bm-selected{outline:2px solid var(--gold);background:#2b241a}
   .bm-folga-day.bm-blocked{background:#301b1d;border-color:#ef444455;color:#ff9da5}
   .bm-folga-day.bm-occupied{background:#26292d;border-color:#ffffff1f;color:#aeb4bd}
   .bm-folga-day:disabled{opacity:.48;cursor:not-allowed}
   .bm-folga-status{border:1px solid var(--line);border-radius:15px;background:#171a1e;padding:14px;margin-top:10px}
   .bm-folga-status-head{display:flex;align-items:center;justify-content:space-between;gap:10px}
   .bm-folga-badge{font-size:10px;font-weight:950;border-radius:999px;padding:5px 8px;border:1px solid currentColor}
   .bm-folga-message{margin-top:10px;padding:12px;border-radius:12px;background:#0d2819;border:1px solid #22c55e55;color:#b8f7d0;font-weight:800}
   .bm-folga-note{width:100%;min-height:86px;resize:vertical;border:1px solid #ffffff1c;background:#111418;color:white;border-radius:12px;padding:12px;outline:none;font:inherit}
   .bm-folga-note:focus{border-color:#f2a33c88}
   .bm-folga-selected{margin-top:12px;padding:12px;border-radius:12px;background:#1f2429;border:1px solid #f2a33c55}
   @media(max-width:420px){.nav button{font-size:8px}.nav button b{font-size:14px}.bm-folga-day{min-height:52px;padding:6px}}
  `;
  document.head.appendChild(st);
 }

 async function api(body){
  const {data:{session}}=await sb.auth.getSession();
  if(!session?.access_token)throw new Error('Sessão expirada. Abra o Bora Michael novamente.');
  const r=await fetch(ENDPOINT,{
   method:'POST',
   headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+session.access_token},
   body:JSON.stringify(body||{action:'overview'})
  });
  let j={};try{j=await r.json()}catch(_){}
  if(!r.ok)throw new Error(j?.error||'Não foi possível acessar as folgas.');
  return j;
 }

 function hasFutureAvailability(data){
  if(typeof data?.available==='boolean')return data.available;
  const base=new Date((data?.today||ymd(new Date()))+'T12:00:00');
  const blocked=new Set((data?.blocked||[]).map(x=>String(x.data)));
  const occupied=new Set((data?.occupied||[]).map(x=>String(x.data_folga||x.data||x)));
  for(let i=1;i<=30;i++){
   const d=new Date(base);d.setDate(base.getDate()+i);
   const ds=ymd(d);
   if(!blocked.has(ds)&&!occupied.has(ds))return true;
  }
  return false;
 }

 function syncNativeAvailability(data){
  try{
   const fn=window.AndroidBora?.setFolgaAvailability;
   if(typeof fn==='function')fn.call(window.AndroidBora,!!hasFutureAvailability(data));
  }catch(_){}
 }

 function navHtml(active){
  const items=[['Hoje','●'],['Calendário','▦'],['Histórico','≡'],['Relatórios','Σ'],['Taxas','R$'],['Folgas','☼']];
  return `<nav class="nav">${items.map(([n,i])=>`<button data-rnav="${n}" class="${active===n?'active':''}"><b>${i}</b>${n}</button>`).join('')}</nav>`;
 }

 navRider=navHtml;

 function calendarHtml(data){
  const now=new Date();
  if(!(state.month instanceof Date)||Number.isNaN(state.month.getTime()))state.month=new Date(now.getFullYear(),now.getMonth(),1);
  const y=state.month.getFullYear(),m=state.month.getMonth();
  const first=new Date(y,m,1),last=new Date(y,m+1,0).getDate(),offset=(first.getDay()+6)%7;
  const blocked=new Map((data.blocked||[]).map(x=>[String(x.data),x.motivo||'Data bloqueada']));
  const occupied=new Set((data.occupied||[]).map(x=>String(x.data_folga||x.data||x)));
  const todayKey=data.today||ymd(now);
  let cells=['SEG','TER','QUA','QUI','SEX','SÁB','DOM'].map(x=>`<div class="bm-folga-dow">${x}</div>`).join('');
  for(let i=0;i<offset;i++)cells+='<div></div>';
  for(let d=1;d<=last;d++){
   const ds=`${y}-${pad(m+1)}-${pad(d)}`;
   const past=ds<todayKey, reason=blocked.get(ds), used=occupied.has(ds), selected=state.selected===ds;
   const label=reason?'BLOQUEADA':used?'OCUPADA':selected?'SELECIONADA':'';
   cells+=`<button type="button" class="bm-folga-day ${selected?'bm-selected':''} ${reason?'bm-blocked':''} ${used?'bm-occupied':''}" data-folga-day="${ds}" ${past||reason||used?'disabled':''}><span>${d}</span><small>${label}</small></button>`;
  }
  const title=state.month.toLocaleDateString('pt-BR',{month:'long',year:'numeric'});
  return `<div class="month-head"><button type="button" id="bmFolgaPrev">‹</button><b style="text-transform:capitalize">${title}</b><button type="button" id="bmFolgaNext">›</button></div><div class="bm-folga-calendar">${cells}</div>`;
 }

 function requestsHtml(data){
  const arr=data.requests||[];
  if(!arr.length)return '<div class="card"><div class="empty">Você ainda não solicitou nenhuma folga.</div></div>';
  return arr.map(x=>`<article class="bm-folga-status">
   <div class="bm-folga-status-head">
    <div><b style="font-size:17px">${fmt(x.data_folga)}</b><div class="row-sub">Solicitada em ${new Date(x.created_at).toLocaleString('pt-BR')}</div></div>
    <span class="bm-folga-badge" style="color:${statusColor(x.status)}">${statusLabel(x.status)}</span>
   </div>
   ${x.observacao?`<div class="row-sub" style="margin-top:9px">Observação: ${esc(x.observacao)}</div>`:''}
   ${x.status==='aprovado'&&x.mensagem_aprovacao?`<div class="bm-folga-message">${esc(x.mensagem_aprovacao)}</div>`:''}
   ${x.status==='pendente'?`<button type="button" class="btn secondary small" data-cancel-folga="${x.id}" style="margin-top:11px">Cancelar solicitação</button>`:''}
  </article>`).join('');
 }

 function screenHtml(data){
  const selected=state.selected;
  return `<section class="bm-folgas">
   <div class="section-title">Folgas</div>
   <div class="notice"><strong>Solicite sua folga pelo aplicativo.</strong><br>Escolha uma data disponível. A prioridade é de quem confirmar primeiro.</div>
   <div class="card" style="margin-top:12px">
    <div class="stat-label">ESCOLHA O DIA</div>
    <div style="margin-top:12px">${calendarHtml(data)}</div>
    <div id="bmFolgaSelected" class="bm-folga-selected ${selected?'':'hidden'}">${selected?`Data escolhida: <b>${fmt(selected)}</b>`:''}</div>
    <label class="field" style="margin-top:12px"><span>Observação (opcional)</span><textarea id="bmFolgaObs" class="bm-folga-note" maxlength="300" placeholder="Ex.: compromisso, viagem..."></textarea></label>
    <button type="button" id="bmFolgaSend" class="btn green" style="width:100%;margin-top:12px" ${selected?'':'disabled'}>SOLICITAR FOLGA</button>
    <div class="row-sub" style="margin-top:9px">Assim que uma solicitação for confirmada, aquela data fica ocupada para os demais. Se for cancelada ou recusada, volta a ficar disponível.</div>
   </div>
   <div class="section-title">Minhas solicitações</div>
   <div id="bmFolgaRequests">${requestsHtml(data)}</div>
  </section>`;
 }

 function bind(data){
  $$('[data-rnav]').forEach(b=>b.onclick=()=>renderRider(b.dataset.rnav));
  $$('[data-folga-day]').forEach(b=>b.onclick=()=>{
   state.selected=b.dataset.folgaDay;
   renderFolgas(data,false);
  });
  $('#bmFolgaPrev')?.addEventListener('click',()=>{
   state.month=new Date(state.month.getFullYear(),state.month.getMonth()-1,1);
   renderFolgas(data,false);
  });
  $('#bmFolgaNext')?.addEventListener('click',()=>{
   state.month=new Date(state.month.getFullYear(),state.month.getMonth()+1,1);
   renderFolgas(data,false);
  });
  $('#bmFolgaSend')?.addEventListener('click',async()=>{
   if(!state.selected)return toast('Escolha a data da folga.');
   const btn=$('#bmFolgaSend'),obs=$('#bmFolgaObs')?.value||'';
   if(btn){btn.disabled=true;btn.textContent='ENVIANDO...'}
   try{
    await api({action:'request',data_folga:state.selected,observacao:obs});
    state.selected=null;
    toast('Solicitação de folga enviada.');
    await renderFolgas(null,true);
   }catch(e){
    toast(e?.message||'Não foi possível solicitar a folga.');
    if(btn){btn.disabled=false;btn.textContent='SOLICITAR FOLGA'}
   }
  });
  $$('[data-cancel-folga]').forEach(b=>b.onclick=async()=>{
   if(!confirm('Cancelar esta solicitação de folga?'))return;
   try{
    await api({action:'cancel',id:b.dataset.cancelFolga});
    toast('Solicitação cancelada.');
    await renderFolgas(null,true);
   }catch(e){toast(e?.message||'Não foi possível cancelar.')}
  });
 }

 async function renderFolgas(cached=null,reload=true){
  try{
   if(state.loading&&reload)return;
   state.loading=true;
   if(!rider.profile)rider.profile=await getRiderProfile();
   const data=reload||!cached?await api({action:'overview'}):cached;
   state.data=data;
   syncNativeAvailability(data);
   if(!state.selected&&data.today){
    const t=new Date(data.today+'T12:00:00');
    if(state.month.getFullYear()!==t.getFullYear()||state.month.getMonth()!==t.getMonth())state.month=new Date(t.getFullYear(),t.getMonth(),1);
   }
   rider.active='Folgas';
   $('#root').innerHTML=`<div class="shell">${riderHeader()}${screenHtml(data)}</div>${navHtml('Folgas')}`;
   bind(data);
   state.sig=JSON.stringify([(data.occupied||[]),...(data.requests||[]).map(x=>[x.id,x.status,x.mensagem_aprovacao,x.updated_at])]);
   appReady();
  }catch(e){
   console.error('folgas',e);
   toast(e?.message||'Não foi possível carregar as folgas.');
   if(typeof failScreen==='function')failScreen(e?.message||'Não foi possível carregar as folgas.');
  }finally{state.loading=false}
 }

 const previousRenderRider=renderRider;
 renderRider=async function(active='Hoje'){
  if(active==='Folgas')return renderFolgas(null,true);
  return previousRenderRider(active);
 };

 async function backgroundAvailabilitySync(){
  try{
   if(!rider.profile)rider.profile=await getRiderProfile();
   if(!rider.profile)return;
   const d=await api({action:'overview'});
   syncNativeAvailability(d);
  }catch(_){}
 }

 setTimeout(backgroundAvailabilitySync,2500);
 window.addEventListener('focus',()=>setTimeout(backgroundAvailabilitySync,500));

 if(new URLSearchParams(location.search).get('open')==='folgas'){
  setTimeout(()=>renderRider('Folgas'),1100);
 }

 setInterval(async()=>{
  if(rider?.active!=='Folgas'||document.visibilityState!=='visible'||state.loading)return;
  try{
   const d=await api({action:'overview'});
   syncNativeAvailability(d);
   const sig=JSON.stringify([(d.occupied||[]),...(d.requests||[]).map(x=>[x.id,x.status,x.mensagem_aprovacao,x.updated_at])]);
   if(sig!==state.sig)await renderFolgas(d,false);
  }catch(_){}
 },30000);
})();