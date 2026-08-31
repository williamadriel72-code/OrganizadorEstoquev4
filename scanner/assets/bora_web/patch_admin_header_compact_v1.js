function bmCompactAdminMotoHeader(){
 const head=document.querySelector('.moto-list-head');
 if(!head)return;
 const refresh=document.getElementById('bmRefreshAdmin');
 const register=document.getElementById('bmRegisterMoto');
 if(refresh)refresh.textContent='↻ Atualizar';
 if(register)register.textContent='+ Cadastrar';
 let actions=head.querySelector('.bm-moto-head-actions');
 if(!actions){
  actions=document.createElement('div');
  actions.className='bm-moto-head-actions';
  head.appendChild(actions);
 }
 if(refresh&&refresh.parentElement!==actions)actions.appendChild(refresh);
 if(register&&register.parentElement!==actions)actions.appendChild(register);
}

(function bmInstallCompactHeaderStyles(){
 if(document.getElementById('bmCompactAdminHeaderStyle'))return;
 const style=document.createElement('style');
 style.id='bmCompactAdminHeaderStyle';
 style.textContent=`
 .moto-list{container-type:inline-size}
 .moto-list-head{
   display:grid!important;
   grid-template-columns:auto 1fr!important;
   grid-template-areas:'title count' 'actions actions';
   align-items:center!important;
   gap:7px 10px!important;
   padding:6px 7px 9px!important;
 }
 .moto-list-head>b{grid-area:title;font-size:15px!important;white-space:nowrap}
 .moto-list-head>.moto-count{grid-area:count;justify-self:end;font-size:11px!important;white-space:nowrap}
 .bm-moto-head-actions{grid-area:actions;display:flex;gap:6px;justify-content:flex-end;align-items:center;min-width:0}
 #bmRefreshAdmin,#bmRegisterMoto{
   width:auto!important;
   min-width:0!important;
   min-height:32px!important;
   margin:0!important;
   padding:6px 10px!important;
   border-radius:9px!important;
   font-size:11px!important;
   line-height:1!important;
   font-weight:800!important;
   white-space:nowrap!important;
   letter-spacing:0!important;
 }
 #bmRegisterMoto{background:#21c875!important;color:#06150d!important;border:1px solid #31d884!important}
 #bmRefreshAdmin{background:#2c3137!important;color:#fff!important;border:1px solid rgba(255,255,255,.08)!important}
 @container (min-width:430px){
   .moto-list-head{
     grid-template-columns:auto auto 1fr!important;
     grid-template-areas:'title count actions';
   }
   .moto-list-head>.moto-count{justify-self:start}
 }
 #bmDailyReportsBtn{width:100%;margin-top:10px;background:#191d21;border:1px solid rgba(255,255,255,.10);color:#fff;font-weight:850;letter-spacing:.02em}
 .bm-report-overlay{position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.76);display:flex;align-items:center;justify-content:center;padding:14px}
 .bm-report-modal{width:min(1040px,96vw);max-height:92vh;overflow:auto;background:#111417;border:1px solid rgba(255,255,255,.10);border-radius:18px;box-shadow:0 28px 90px rgba(0,0,0,.55);padding:16px}
 .bm-report-head{display:flex;align-items:center;justify-content:space-between;gap:12px;position:sticky;top:-16px;background:#111417;padding:14px 0 12px;z-index:2;border-bottom:1px solid rgba(255,255,255,.08)}
 .bm-report-head h3{margin:0;font-size:20px}.bm-report-close{border:0;background:#2b3035;color:#fff;width:36px;height:36px;border-radius:10px;font-size:22px;cursor:pointer}
 .bm-report-list{display:grid;gap:10px;margin-top:14px}.bm-report-day{display:grid;grid-template-columns:1fr auto;gap:8px;text-align:left;background:#181c20;border:1px solid rgba(255,255,255,.08);color:#fff;padding:14px;border-radius:14px;cursor:pointer}
 .bm-report-day:hover{border-color:rgba(34,210,126,.55)}.bm-report-date{font-size:17px;font-weight:900}.bm-report-meta{color:#9da5ad;font-size:12px;margin-top:4px}.bm-report-total{font-size:18px;font-weight:950;color:#35d786;align-self:center}
 .bm-report-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin:14px 0}.bm-report-stat{background:#181c20;border:1px solid rgba(255,255,255,.08);border-radius:13px;padding:12px}.bm-report-stat small{display:block;color:#929ba4;font-size:10px;font-weight:800;text-transform:uppercase}.bm-report-stat strong{display:block;margin-top:5px;font-size:18px}
 .bm-report-person{background:#181c20;border:1px solid rgba(255,255,255,.08);border-radius:14px;margin:10px 0;overflow:hidden}.bm-report-person>summary{list-style:none;cursor:pointer;padding:14px;font-weight:900;display:flex;justify-content:space-between;gap:10px}.bm-report-person>summary::-webkit-details-marker{display:none}.bm-report-person-body{padding:0 14px 14px}.bm-report-mini{display:flex;gap:8px;flex-wrap:wrap;color:#aab1b8;font-size:12px;margin-bottom:10px}.bm-report-mini b{color:#fff}
 .bm-report-section-title{font-size:12px;font-weight:900;color:#929ba4;text-transform:uppercase;margin:13px 0 7px}.bm-report-row{display:grid;grid-template-columns:1fr auto;gap:9px;padding:9px 0;border-top:1px solid rgba(255,255,255,.06)}.bm-report-row:first-child{border-top:0}.bm-report-row-title{font-weight:800}.bm-report-row-sub{font-size:11px;color:#929ba4;margin-top:2px}.bm-report-ok{color:#36d889;font-weight:850}.bm-report-pending{color:#f3b84a;font-weight:850}.bm-report-cancel{color:#ff6d6d;font-weight:850}
 .bm-report-toolbar{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.bm-report-action{border:1px solid rgba(255,255,255,.10);background:#242a30;color:#fff;border-radius:10px;padding:9px 12px;font-weight:800;cursor:pointer}.bm-report-action.green{background:#173f2c;border-color:#2c8d5d;color:#7ef0ad}
 .bm-audit-wrap{max-height:440px;overflow:auto;border:1px solid rgba(255,255,255,.08);border-radius:12px;background:#0e1113}.bm-audit-item{border-bottom:1px solid rgba(255,255,255,.06);padding:9px 11px}.bm-audit-item:last-child{border-bottom:0}.bm-audit-item summary{cursor:pointer;font-size:12px;font-weight:800}.bm-audit-item pre{white-space:pre-wrap;word-break:break-word;font-size:10px;color:#b6bec6;background:#080a0b;padding:8px;border-radius:8px;overflow:auto}
 @media(max-width:720px){.bm-report-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.bm-report-modal{padding:12px}.bm-report-head{top:-12px}}
 `;
 document.head.appendChild(style);
})();

function bmReportDateLabel(ds){
 try{return new Date(String(ds)+'T12:00:00').toLocaleDateString('pt-BR',{weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'})}catch(_){return String(ds||'')}
}
function bmReportTime(iso){
 if(!iso)return '—';
 try{return new Date(iso).toLocaleTimeString('pt-BR',{timeZone:'America/Sao_Paulo',hour:'2-digit',minute:'2-digit'})}catch(_){return '—'}
}
function bmReportDateTime(iso){
 if(!iso)return '—';
 try{return new Date(iso).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'})}catch(_){return '—'}
}
function bmCloseDailyReports(){document.getElementById('bmReportOverlay')?.remove();window.bmDailyReportCurrent=null}
function bmCreateDailyReportModal(){
 bmCloseDailyReports();
 const el=document.createElement('div');
 el.id='bmReportOverlay';el.className='bm-report-overlay';
 el.innerHTML=`<div class="bm-report-modal"><div class="bm-report-head"><div><h3>Relatórios diários</h3><div class="bm-report-meta">Gerados automaticamente às 23:59 · horário de Brasília</div></div><button class="bm-report-close" id="bmReportClose">×</button></div><div id="bmReportBody"><div class="empty" style="padding:28px">Carregando...</div></div></div>`;
 document.body.appendChild(el);
 el.querySelector('#bmReportClose').onclick=bmCloseDailyReports;
 el.addEventListener('click',e=>{if(e.target===el)bmCloseDailyReports()});
 return el;
}
async function bmOpenDailyReports(){
 bmCreateDailyReportModal();
 const body=document.getElementById('bmReportBody');
 try{
  const r=await sb.from('kh_motoboy_relatorios_diarios').select('id,data,gerado_at,total_motoboys,total_entregas,total_confirmadas,total_canceladas,total_saidas,total_diarias,total_taxas,total_geral,resumo').order('data',{ascending:false}).limit(90);
  if(r.error)throw r.error;
  const rows=r.data||[];
  body.innerHTML=rows.length?`<div class="bm-report-list">${rows.map(x=>`<button class="bm-report-day" data-bm-report-date="${esc(x.data)}"><div><div class="bm-report-date">${esc(bmReportDateLabel(x.data))}</div><div class="bm-report-meta">${Number(x.total_motoboys||0)} motoboys · ${Number(x.total_entregas||0)} entregas · ${Number(x.total_confirmadas||0)} confirmadas · ${Number(x.total_saidas||0)} saídas</div><div class="bm-report-meta">Gerado em ${esc(bmReportDateTime(x.gerado_at))}</div></div><div class="bm-report-total">${BRL(x.total_geral||0)}</div></button>`).join('')}</div>`:'<div class="empty" style="padding:28px">Nenhum relatório diário gerado ainda.</div>';
  body.querySelectorAll('[data-bm-report-date]').forEach(b=>b.onclick=()=>bmOpenDailyReportDetail(b.dataset.bmReportDate));
 }catch(e){console.error('daily-reports',e);body.innerHTML=`<div class="notice" style="margin:18px 0">Não foi possível carregar os relatórios: ${esc(e?.message||'erro')}</div>`}
}
function bmReportDeliveryRows(items){
 const arr=items||[];
 if(!arr.length)return '<div class="empty">Nenhuma nota.</div>';
 return arr.map(e=>{const st=e.status==='cancelada'?'<span class="bm-report-cancel">CANCELADA</span>':e.nota_confirmada?'<span class="bm-report-ok">CONFIRMADA</span>':'<span class="bm-report-pending">PENDENTE</span>';return `<div class="bm-report-row"><div><div class="bm-report-row-title">Nota ${esc(e.nota_numero||'—')} · ${esc(e.bairro_nome||'Sem bairro')}</div><div class="bm-report-row-sub">${esc(e.tipo==='integral'?'Taxa Integral':'Taxa normal')} · ${st}${e.nota_confirmada_at?` · ${esc(bmReportTime(e.nota_confirmada_at))}`:''}</div></div><strong>${BRL(e.valor||0)}</strong></div>`}).join('');
}
function bmReportOutingRows(items){
 const arr=items||[];
 if(!arr.length)return '<div class="empty">Nenhuma saída registrada.</div>';
 return arr.map(s=>`<div class="bm-report-row"><div><div class="bm-report-row-title">Saída ${String(s.numero_sequencial||0).padStart(2,'0')} · ${esc(String(s.status||''))}</div><div class="bm-report-row-sub">${esc(bmReportTime(s.horario_saida||s.created_at))} · ${(s.notas||[]).length} nota(s)</div></div><strong>${BRL(s.total||0)}</strong></div>`).join('');
}
function bmReportPersonHtml(m){
 const r=m.resumo||{},arrival=m.chegada_at?bmReportTime(m.chegada_at):'Não registrada',comandas=Number(r.entregas||0),comandaLabel=comandas===1?'comanda':'comandas';
 return `<details class="bm-report-person"><summary><span>${comandas} ${comandaLabel} · ${esc(m.nome||'Motoboy')}</span><span>${BRL(r.total_dia||0)}</span></summary><div class="bm-report-person-body"><div class="bm-report-mini"><span>Chegada: <b>${esc(arrival)}</b></span><span>Diária: <b>${BRL(m.diaria||0)}</b></span><span>Entregas: <b>${Number(r.entregas||0)}</b></span><span>Confirmadas: <b>${Number(r.confirmadas||0)}</b></span><span>Saídas: <b>${Number(r.saidas||0)}</b></span><span>Taxas: <b>${BRL(r.taxas||0)}</b></span>${Number(r.canceladas||0)?`<span>Canceladas: <b>${Number(r.canceladas||0)}</b></span>`:''}</div><div class="bm-report-section-title">Notas do dia</div>${bmReportDeliveryRows(m.entregas)}<div class="bm-report-section-title">Saídas do dia</div>${bmReportOutingRows(m.saidas)}</div></details>`;
}
function bmRenderDailyReportAudit(){
 const rep=window.bmDailyReportCurrent,box=document.getElementById('bmAuditFull');if(!rep||!box)return;
 const arr=rep.auditoria||[];
 box.innerHTML=`<div class="bm-audit-wrap">${arr.map(a=>`<details class="bm-audit-item"><summary>${esc(bmReportTime(a.horario))} · ${esc(a.acao||'')} · ${esc(a.tabela||'')}</summary><pre>${esc(JSON.stringify({registro_id:a.registro_id,usuario_id:a.usuario_id,valor_anterior:a.valor_anterior,valor_novo:a.valor_novo},null,2))}</pre></details>`).join('')}</div>`;
 document.getElementById('bmLoadAudit')?.remove();
}
async function bmOpenDailyReportDetail(date){
 const body=document.getElementById('bmReportBody');if(!body)return;body.innerHTML='<div class="empty" style="padding:28px">Abrindo relatório...</div>';
 try{
  const r=await sb.from('kh_motoboy_relatorios_diarios').select('*').eq('data',date).single();if(r.error)throw r.error;
  const x=r.data;window.bmDailyReportCurrent=x;const audit=x.auditoria||[],details=x.detalhes||[],mount=Number(x.resumo?.saidas_montando||0);
  body.innerHTML=`<div class="bm-report-toolbar"><button class="bm-report-action" id="bmBackReports">← Voltar</button></div><div class="bm-report-date">${esc(bmReportDateLabel(x.data))}</div><div class="bm-report-meta">Snapshot gerado em ${esc(bmReportDateTime(x.gerado_at))}</div><div class="bm-report-grid"><div class="bm-report-stat"><small>Motoboys</small><strong>${Number(x.total_motoboys||0)}</strong></div><div class="bm-report-stat"><small>Entregas</small><strong>${Number(x.total_entregas||0)}</strong></div><div class="bm-report-stat"><small>Confirmadas</small><strong>${Number(x.total_confirmadas||0)}</strong></div><div class="bm-report-stat"><small>Saídas liberadas</small><strong>${Number(x.total_saidas||0)}</strong></div><div class="bm-report-stat"><small>Diárias</small><strong>${BRL(x.total_diarias||0)}</strong></div><div class="bm-report-stat"><small>Taxas</small><strong>${BRL(x.total_taxas||0)}</strong></div><div class="bm-report-stat"><small>Total geral</small><strong>${BRL(x.total_geral||0)}</strong></div><div class="bm-report-stat"><small>Auditoria</small><strong>${audit.length}</strong></div></div>${mount?`<div class="notice">Também havia ${mount} saída(s) ainda em montagem no momento do relatório.</div>`:''}<div class="bm-report-section-title">Motoboys · detalhamento completo</div>${details.map(bmReportPersonHtml).join('')||'<div class="empty">Sem jornadas neste dia.</div>'}<div class="bm-report-section-title">Auditoria do dia</div><div class="bm-report-meta" style="margin-bottom:9px">O relatório preserva INSERT, UPDATE e DELETE com os valores anteriores e novos.</div><button class="bm-report-action green" id="bmLoadAudit">VER AUDITORIA COMPLETA (${audit.length})</button><div id="bmAuditFull" style="margin-top:10px"></div>`;
  document.getElementById('bmBackReports').onclick=bmOpenDailyReports;
  document.getElementById('bmLoadAudit').onclick=bmRenderDailyReportAudit;
 }catch(e){console.error('daily-report-detail',e);body.innerHTML=`<div class="bm-report-toolbar"><button class="bm-report-action" id="bmBackReports">← Voltar</button></div><div class="notice">Não foi possível abrir o relatório: ${esc(e?.message||'erro')}</div>`;document.getElementById('bmBackReports').onclick=bmOpenDailyReports}
}
function bmInstallDailyReportsButton(){
 const side=document.querySelector('.sidebar');if(!side||document.getElementById('bmDailyReportsBtn'))return;
 const btn=document.createElement('button');btn.id='bmDailyReportsBtn';btn.className='btn secondary';btn.type='button';btn.textContent='RELATÓRIOS DIÁRIOS';btn.onclick=bmOpenDailyReports;side.appendChild(btn);
}

const bmBindAdminBeforeCompactHeader=bindAdmin;
bindAdmin=function(){
 bmBindAdminBeforeCompactHeader();
 bmCompactAdminMotoHeader();
 bmInstallDailyReportsButton();
};