(()=>{
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmShiftReportsV1)return;
 window.__bmShiftReportsV1=true;

 const label=t=>t==='10_17'?'10:00–17:00':'17:00–00:00';
 const fmtDate=d=>{try{return new Date(String(d)+'T12:00:00').toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'2-digit',year:'numeric'})}catch(_){return String(d||'')}};
 const fmtTime=iso=>{try{return new Date(iso).toLocaleTimeString('pt-BR',{timeZone:'America/Sao_Paulo',hour:'2-digit',minute:'2-digit'})}catch(_){return '—'}};
 const money=v=>{try{return BRL(Number(v||0))}catch(_){return 'R$ 0,00'}};

 function installStyle(){
  if(document.getElementById('bmShiftReportStyle'))return;
  const s=document.createElement('style');s.id='bmShiftReportStyle';s.textContent=`
   #bmShiftReportsBtn{width:auto!important;margin:0 8px 0 0!important;background:#18212b!important;border:1px solid rgba(255,255,255,.09)!important;color:#fff!important}
   .bm-shift-overlay{position:fixed;inset:0;z-index:100000;background:rgba(0,0,0,.78);display:flex;align-items:center;justify-content:center;padding:14px}
   .bm-shift-modal{width:min(1040px,96vw);max-height:92vh;overflow:auto;background:#0d1217;border:1px solid rgba(255,255,255,.10);border-radius:18px;box-shadow:0 28px 90px rgba(0,0,0,.58);padding:16px}
   .bm-shift-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;position:sticky;top:-16px;background:#0d1217;padding:14px 0 12px;z-index:2;border-bottom:1px solid rgba(255,255,255,.08)}
   .bm-shift-head h3{margin:0;font-size:20px}.bm-shift-sub{color:#98a3af;font-size:12px;margin-top:5px}.bm-shift-close{border:0;background:#252c33;color:#fff;width:36px;height:36px;border-radius:10px;font-size:22px;cursor:pointer}
   .bm-shift-info{margin:13px 0;padding:11px 12px;border-radius:12px;background:#111922;border:1px solid rgba(255,255,255,.08);color:#aeb8c3;font-size:12px}.bm-shift-info b{color:#fff}
   .bm-shift-list{display:grid;gap:9px}.bm-shift-row{display:grid;grid-template-columns:1fr auto;gap:10px;text-align:left;background:#121a22;border:1px solid rgba(255,255,255,.08);color:#fff;padding:13px;border-radius:14px;cursor:pointer}.bm-shift-row:hover{border-color:rgba(255,55,78,.48)}
   .bm-shift-title{font-size:16px;font-weight:950}.bm-shift-meta{color:#98a3af;font-size:11px;margin-top:4px}.bm-shift-total{font-size:17px;font-weight:950;color:#58e89d;align-self:center;white-space:nowrap}
   .bm-shift-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin:14px 0}.bm-shift-stat{background:#121a22;border:1px solid rgba(255,255,255,.08);border-radius:13px;padding:12px}.bm-shift-stat small{display:block;color:#929da8;font-size:9px;font-weight:900;text-transform:uppercase}.bm-shift-stat strong{display:block;margin-top:5px;font-size:18px}
   .bm-shift-person{background:#121a22;border:1px solid rgba(255,255,255,.08);border-radius:14px;margin:9px 0;overflow:hidden}.bm-shift-person summary{cursor:pointer;list-style:none;padding:13px;font-weight:900;display:flex;justify-content:space-between;gap:10px}.bm-shift-person summary::-webkit-details-marker{display:none}.bm-shift-body{padding:0 13px 13px}.bm-shift-mini{display:flex;gap:8px;flex-wrap:wrap;color:#9da8b3;font-size:11px;margin-bottom:9px}.bm-shift-mini b{color:#fff}.bm-shift-delivery{display:grid;grid-template-columns:1fr auto;gap:8px;padding:8px 0;border-top:1px solid rgba(255,255,255,.06)}
   .bm-shift-back{border:1px solid rgba(255,255,255,.1);background:#252c33;color:#fff;border-radius:10px;padding:8px 11px;font-weight:850;cursor:pointer;margin-bottom:11px}
   @media(max-width:720px){.bm-shift-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.bm-shift-modal{padding:12px}.bm-shift-head{top:-12px}}
  `;document.head.appendChild(s);
 }

 function close(){document.getElementById('bmShiftOverlay')?.remove()}
 function createModal(){
  close();installStyle();
  const el=document.createElement('div');el.id='bmShiftOverlay';el.className='bm-shift-overlay';
  el.innerHTML=`<div class="bm-shift-modal"><div class="bm-shift-head"><div><h3>Relatórios por turno</h3><div class="bm-shift-sub">Fechamentos automáticos separados por horário</div></div><button class="bm-shift-close">×</button></div><div class="bm-shift-info"><b>Turno 1:</b> 10:00–17:00 · fechado automaticamente às 17:00. <b>Turno 2:</b> 17:00–00:00 · começa zerado às 17:00 e fecha à meia-noite. Os dados anteriores não são apagados; ficam salvos no histórico.</div><div id="bmShiftBody"><div class="empty">Carregando...</div></div></div>`;
  document.body.appendChild(el);el.querySelector('.bm-shift-close').onclick=close;el.onclick=e=>{if(e.target===el)close()};return el;
 }

 async function openList(){
  createModal();const body=document.getElementById('bmShiftBody');
  try{
   const r=await sb.from('kh_motoboy_relatorios_turnos').select('id,data,turno,inicio_at,fim_at,gerado_at,total_motoboys,total_entregas,total_confirmadas,total_canceladas,total_saidas,total_diarias,total_taxas,total_geral').order('data',{ascending:false}).order('turno',{ascending:true}).limit(120);
   if(r.error)throw r.error;const rows=r.data||[];
   body.innerHTML=rows.length?`<div class="bm-shift-list">${rows.map(x=>`<button class="bm-shift-row" data-shift-id="${esc(x.id)}"><div><div class="bm-shift-title">${esc(fmtDate(x.data))} · ${label(x.turno)}</div><div class="bm-shift-meta">${Number(x.total_motoboys||0)} motoboys · ${Number(x.total_entregas||0)} entregas · ${Number(x.total_saidas||0)} saídas · gerado ${fmtTime(x.gerado_at)}</div></div><div class="bm-shift-total">${money(x.total_geral)}</div></button>`).join('')}</div>`:'<div class="empty">Os relatórios de turno começarão a aparecer automaticamente após o primeiro fechamento das 17:00.</div>';
   body.querySelectorAll('[data-shift-id]').forEach(b=>b.onclick=()=>openDetail(b.dataset.shiftId));
  }catch(e){console.error('shift-reports',e);body.innerHTML=`<div class="notice">Não foi possível carregar os relatórios de turno: ${esc(e?.message||'erro')}</div>`}
 }

 function personHtml(m){
  const r=m?.resumo||{},es=m?.entregas||[],total=Number(r.total_turno||0),diaria=Number(m?.diaria||0);
  return `<details class="bm-shift-person"><summary><span>${esc(m?.nome||'Motoboy')} · ${Number(r.entregas||0)} entrega(s)</span><span>${money(total)}</span></summary><div class="bm-shift-body"><div class="bm-shift-mini"><span>Diária do turno: <b>${money(diaria)}</b></span><span>Taxas: <b>${money(r.taxas||0)}</b></span><span>Confirmadas: <b>${Number(r.confirmadas||0)}</b></span><span>Saídas: <b>${Number(r.saidas||0)}</b></span></div>${es.length?es.map(e=>`<div class="bm-shift-delivery"><div><b>Nota ${esc(e.nota_numero||'—')}</b><div class="bm-shift-meta">${esc(e.bairro_nome||'Sem bairro')} · ${e.status==='cancelada'?'Cancelada':e.nota_confirmada?'Confirmada':'Pendente'}</div></div><strong>${money(e.valor||0)}</strong></div>`).join(''):'<div class="empty" style="padding:12px">Nenhuma entrega neste turno.</div>'}</div></details>`;
 }

 async function openDetail(id){
  const body=document.getElementById('bmShiftBody');if(!body)return;body.innerHTML='<div class="empty">Abrindo...</div>';
  try{
   const r=await sb.from('kh_motoboy_relatorios_turnos').select('*').eq('id',id).single();if(r.error)throw r.error;const x=r.data;
   body.innerHTML=`<button class="bm-shift-back" id="bmShiftBack">← Voltar</button><div class="bm-shift-title">${esc(fmtDate(x.data))} · ${label(x.turno)}</div><div class="bm-shift-meta">Janela ${fmtTime(x.inicio_at)} até ${fmtTime(x.fim_at)} · gerado ${fmtTime(x.gerado_at)}</div><div class="bm-shift-grid"><div class="bm-shift-stat"><small>Motoboys</small><strong>${Number(x.total_motoboys||0)}</strong></div><div class="bm-shift-stat"><small>Entregas</small><strong>${Number(x.total_entregas||0)}</strong></div><div class="bm-shift-stat"><small>Saídas</small><strong>${Number(x.total_saidas||0)}</strong></div><div class="bm-shift-stat"><small>Confirmadas</small><strong>${Number(x.total_confirmadas||0)}</strong></div><div class="bm-shift-stat"><small>Diárias</small><strong>${money(x.total_diarias)}</strong></div><div class="bm-shift-stat"><small>Taxas</small><strong>${money(x.total_taxas)}</strong></div><div class="bm-shift-stat"><small>Total do turno</small><strong>${money(x.total_geral)}</strong></div><div class="bm-shift-stat"><small>Canceladas</small><strong>${Number(x.total_canceladas||0)}</strong></div></div><div class="section-title" style="margin-top:14px">Motoboys do turno</div>${(x.detalhes||[]).map(personHtml).join('')||'<div class="empty">Nenhum movimento neste turno.</div>'}`;
   document.getElementById('bmShiftBack').onclick=openList;
  }catch(e){console.error('shift-detail',e);body.innerHTML=`<button class="bm-shift-back" id="bmShiftBack">← Voltar</button><div class="notice">Não foi possível abrir: ${esc(e?.message||'erro')}</div>`;document.getElementById('bmShiftBack').onclick=openList}
 }

 function installButton(){
  const head=document.querySelector('.admin-head');if(!head)return;
  let btn=document.getElementById('bmShiftReportsBtn');
  if(!btn){btn=document.createElement('button');btn.id='bmShiftReportsBtn';btn.className='btn secondary small';btn.type='button';btn.textContent='RELATÓRIOS TURNOS';btn.onclick=openList;}
  const logout=document.getElementById('logout');if(btn.parentElement!==head){if(logout)head.insertBefore(btn,logout);else head.appendChild(btn)}
 }
 installStyle();const obs=new MutationObserver(installButton);obs.observe(document.documentElement,{childList:true,subtree:true});setTimeout(installButton,0);setTimeout(installButton,350);setTimeout(installButton,1100);
})();
