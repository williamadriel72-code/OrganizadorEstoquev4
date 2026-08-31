async function bmRefreshAdminPanel(){
 const btn=document.getElementById('bmRefreshAdmin');
 const old=btn?.textContent||'↻ ATUALIZAR';
 if(btn){btn.disabled=true;btn.textContent='ATUALIZANDO...'}
 try{
  const selected=adminState.selectedMoto,bairro=adminState.selectedBairro;
  await loadAdmin();
  if(selected&&adminState.motoboys.some(m=>m.id===selected))adminState.selectedMoto=selected;
  adminState.selectedBairro=bairro;drawAdmin();toast('Lista de motoboys atualizada.');
 }catch(e){console.error('admin-refresh',e);toast(e?.message||'Não foi possível atualizar agora.');if(btn){btn.disabled=false;btn.textContent=old}}
}
function bmInstallAdminRefreshButton(){
 const head=document.querySelector('.moto-list-head');if(!head||document.getElementById('bmRefreshAdmin'))return;
 const btn=document.createElement('button');btn.id='bmRefreshAdmin';btn.type='button';btn.className='btn secondary small';btn.style.marginLeft='auto';btn.style.padding='9px 12px';btn.style.fontWeight='800';btn.textContent='↻ ATUALIZAR';btn.addEventListener('click',bmRefreshAdminPanel);
 const register=document.getElementById('bmRegisterMoto');if(register){register.style.marginLeft='8px';head.insertBefore(btn,register)}else head.appendChild(btn);
}
const bmBindAdminBeforeRefresh=bindAdmin;
bindAdmin=function(){bmBindAdminBeforeRefresh();bmInstallAdminRefreshButton()};

/* Visão geral do painel: aparece apenas quando nenhum motoboy está selecionado. */
(function bmInstallPanelOverview(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 let tries=0;
 const wait=setInterval(()=>{
  tries++;
  if(window.__bmPanelOverviewInlineV1){clearInterval(wait);return}
  if(!window.__bmPanelReferenceV1||typeof selectedPanel!=='function'){if(tries>100)clearInterval(wait);return}
  clearInterval(wait);window.__bmPanelOverviewInlineV1=true;

  const css=document.createElement('style');css.id='bmPanelOverviewInlineStyle';css.textContent=`
  .bm-overview{padding:2px 0 28px;min-width:0}.bm-ov-head{display:flex;justify-content:space-between;gap:20px;padding:20px 22px;margin-bottom:12px;border:1px solid #ffffff14;border-radius:20px;background:linear-gradient(135deg,#10171f,#0a1016);box-shadow:0 18px 55px #0004}.bm-ov-kicker{font-size:9px;font-weight:950;letter-spacing:.13em;color:#ff6678}.bm-ov-head h2{margin:5px 0;font-size:27px}.bm-ov-head p{margin:0;color:#9da8b4;font-size:12px}.bm-ov-clock{text-align:right}.bm-ov-clock b{display:block;font-size:25px}.bm-ov-clock small{color:#8f9aa6}
  .bm-ov-stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:9px;margin-bottom:12px}.bm-ov-stat{min-height:88px;padding:13px;border:1px solid #ffffff12;border-radius:16px;background:linear-gradient(145deg,#111922,#0c1219)}.bm-ov-stat small{display:block;color:#9da8b4;font-size:9px;font-weight:900;text-transform:uppercase}.bm-ov-stat strong{display:block;margin-top:8px;font-size:23px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-ov-stat em{display:block;margin-top:4px;color:#6f7c88;font-size:9px;font-style:normal}.bm-ov-green strong{color:#58e89d}.bm-ov-blue strong{color:#79b8ff}.bm-ov-red strong{color:#ff687b}
  .bm-ov-grid{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(330px,.8fr);gap:12px}.bm-ov-card{min-height:255px;padding:17px 18px;border:1px solid #ffffff12;border-radius:18px;background:linear-gradient(145deg,#10171f,#0b1117);box-shadow:0 18px 55px #0003}.bm-ov-title{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:11px;border-bottom:1px solid #ffffff0d}.bm-ov-title h3{margin:4px 0 0;font-size:18px}.bm-ov-badge{display:grid;place-items:center;min-width:28px;height:28px;border-radius:9px;background:#18222d;color:#8fa2b4}.bm-ov-events{margin-top:7px}.bm-ov-event{display:grid;grid-template-columns:8px 45px minmax(0,1fr);align-items:center;gap:9px;padding:9px 2px;border-bottom:1px solid #ffffff0b;font-size:12px}.bm-ov-event time{color:#8f9ca9;font-weight:800}.bm-ov-event div{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bm-ov-dot{width:7px;height:7px;border-radius:50%;background:#4d9cff}.bm-ov-dot.green{background:#2bd180}.bm-ov-dot.red{background:#ff4359}.bm-ov-empty{padding:30px 5px;text-align:center;color:#7f8b96}
  .bm-ov-turn{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.bm-ov-turn div{padding:14px;border:1px solid #ffffff10;border-radius:14px;background:#0c131a}.bm-ov-turn small{display:block;color:#8f9ca9;font-size:9px;font-weight:900;text-transform:uppercase}.bm-ov-turn strong{display:block;margin-top:6px;font-size:21px}.bm-ov-note{margin-top:12px;padding:11px 12px;border:1px solid #4d9cff28;border-radius:12px;background:#4d9cff12;color:#9eb8d4;font-size:11px;line-height:1.4}
  @media(max-width:1300px){.bm-ov-stats{grid-template-columns:repeat(3,1fr)}}@media(max-width:950px){.bm-ov-grid{grid-template-columns:1fr}.bm-ov-stats{grid-template-columns:repeat(2,1fr)}}@media(max-width:600px){.bm-ov-clock{display:none}.bm-ov-head{padding:16px}.bm-ov-head h2{font-size:22px}}
  `;document.head.appendChild(css);

  const prev=selectedPanel;
  const val=v=>{try{return BRL(Number(v||0))}catch(_){return `R$ ${Number(v||0).toFixed(2).replace('.',',')}`}};
  const tm=v=>{try{return new Date(v).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}catch(_){return '--:--'}};
  const stamp=v=>{const n=new Date(v||0).getTime();return Number.isFinite(n)?n:0};
  const esc2=v=>typeof esc==='function'?esc(String(v??'')):String(v??'');

  function rows(){return (adminState?.motoboys||[]).map(m=>({m,j:typeof jornadaOf==='function'?jornadaOf(m.id):null,e:(typeof entregasOf==='function'?entregasOf(m.id):[]).filter(x=>x.status!=='cancelada'),s:typeof saidasOf==='function'?saidasOf(m.id):[]}))}
  function overview(){
   const r=rows(),now=new Date(),mins=now.getHours()*60+now.getMinutes();
   const start=new Date(now);start.setHours(mins>=17*60?17:mins>=10*60?10:0,0,0,0);const a=start.getTime(),b=now.getTime();
   const turnLabel=mins>=17*60?'Turno após 17:00':mins>=10*60?'Turno 10:00 → 17:00':'Antes das 10:00';const inside=v=>{const n=stamp(v);return n>=a&&n<=b};
   const active=r.filter(x=>x.j?.chegada_at&&x.j?.chegada_tipo!=='nao_compareceu').length;
   const noshow=r.filter(x=>x.j?.chegada_tipo==='nao_compareceu').length;
   const inDelivery=r.filter(x=>x.s.some(s=>['liberada','em_andamento','em_entrega'].includes(String(s.status||'').toLowerCase()))||x.e.some(e=>e.saida_id&&!['entregue','finalizada','cancelada'].includes(String(e.status||'').toLowerCase()))).length;
   const outs=r.reduce((n,x)=>n+x.s.length,0),cmds=r.reduce((n,x)=>n+x.e.length,0),total=r.reduce((n,x)=>n+x.e.reduce((z,e)=>z+Number(e.valor||0),0),0);
   const tOut=r.reduce((n,x)=>n+x.s.filter(s=>inside(s.horario_saida||s.created_at)).length,0),tCmd=r.reduce((n,x)=>n+x.e.filter(e=>inside(e.created_at||e.updated_at)).length,0),tVal=r.reduce((n,x)=>n+x.e.filter(e=>inside(e.created_at||e.updated_at)).reduce((z,e)=>z+Number(e.valor||0),0),0);
   const tMoto=new Set();r.forEach(x=>{if((x.j?.chegada_at&&inside(x.j.chegada_at))||x.s.some(s=>inside(s.horario_saida||s.created_at))||x.e.some(e=>inside(e.created_at||e.updated_at)))tMoto.add(x.m.id)});
   const ev=[];r.forEach(x=>{const n=x.m?.nome||'Motoboy';if(x.j?.chegada_tipo==='nao_compareceu')ev.push({at:x.j.updated_at||x.j.created_at,txt:`${n} · Não compareceu`,c:'red'});else if(x.j?.chegada_at)ev.push({at:x.j.chegada_at,txt:`${n} · Chegada registrada`,c:'blue'});x.s.forEach(s=>ev.push({at:s.horario_saida||s.created_at,txt:`${n} · Saída ${s.numero_sequencial?String(s.numero_sequencial).padStart(2,'0'):'liberada'}`,c:'green'}));x.e.forEach(e=>ev.push({at:e.updated_at||e.created_at,txt:`${n} · Comanda ${e.nota_numero||''} lançada`,c:'blue'}))});
   ev.sort((x,y)=>stamp(y.at)-stamp(x.at));const list=ev.filter(x=>stamp(x.at)).slice(0,8);const recent=list.length?list.map(x=>`<div class="bm-ov-event"><span class="bm-ov-dot ${x.c}"></span><time>${tm(x.at)}</time><div>${esc2(x.txt)}</div></div>`).join(''):'<div class="bm-ov-empty">Nenhuma movimentação registrada hoje.</div>';
   return `<section class="bm-overview"><div class="bm-ov-head"><div><div class="bm-ov-kicker">PAINEL ADMINISTRATIVO</div><h2>Visão geral do painel</h2><p>Acompanhe o movimento do dia sem precisar abrir um motoboy.</p></div><div class="bm-ov-clock"><b id="bmOverviewClock">${now.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b><small>${now.toLocaleDateString('pt-BR')}</small></div></div><div class="bm-ov-stats"><div class="bm-ov-stat bm-ov-green"><small>Motoboys ativos hoje</small><strong>${active}</strong><em>com chegada registrada</em></div><div class="bm-ov-stat bm-ov-blue"><small>Em entrega</small><strong>${inDelivery}</strong><em>saída em andamento</em></div><div class="bm-ov-stat bm-ov-red"><small>Não compareceram</small><strong>${noshow}</strong><em>diária zerada</em></div><div class="bm-ov-stat"><small>Saídas realizadas</small><strong>${outs}</strong><em>registradas hoje</em></div><div class="bm-ov-stat"><small>Comandas do dia</small><strong>${cmds}</strong><em>lançamentos válidos</em></div><div class="bm-ov-stat bm-ov-green"><small>Valor em entregas</small><strong>${val(total)}</strong><em>total das taxas</em></div></div><div class="bm-ov-grid"><article class="bm-ov-card"><div class="bm-ov-title"><div><div class="bm-ov-kicker">MOVIMENTAÇÕES</div><h3>Atividade recente</h3></div><b class="bm-ov-badge">${list.length}</b></div><div class="bm-ov-events">${recent}</div></article><article class="bm-ov-card"><div class="bm-ov-title"><div><div class="bm-ov-kicker">TURNO ATUAL</div><h3>${turnLabel}</h3></div><span class="bm-ov-dot green" style="margin-top:8px"></span></div><div class="bm-ov-turn"><div><small>Entregas</small><strong>${tCmd}</strong></div><div><small>Saídas</small><strong>${tOut}</strong></div><div><small>Valor acumulado</small><strong>${val(tVal)}</strong></div><div><small>Motoboys ativos</small><strong>${tMoto.size}</strong></div></div><div class="bm-ov-note">Às 17:00 começa uma nova contagem de turno sem apagar o histórico anterior.</div></article></div></section>`;
  }
  selectedPanel=function(){return adminState?.selectedMoto?prev():overview()};
  setInterval(()=>{const x=document.getElementById('bmOverviewClock');if(x)x.textContent=new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})},30000);
  if(!adminState?.selectedMoto&&typeof drawAdmin==='function')setTimeout(()=>drawAdmin(),0);
 },100);
})();
