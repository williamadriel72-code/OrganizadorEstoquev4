function bmArrivalRule(timeStr){
 const parts=String(timeStr||'').split(':').map(Number);
 const hh=parts[0],mm=parts[1];
 if(!Number.isInteger(hh)||!Number.isInteger(mm)||hh<0||hh>23||mm<0||mm>59)return null;
 const mins=hh*60+mm;
 const [y,mo,d]=today().split('-').map(Number);
 const sun=new Date(y,mo-1,d,12,0,0).getDay()===0;
 if(mins<18*60)return {tipo:'antes_18',valor:sun?90:70,manual:false};
 if(mins<19*60)return {tipo:'entre_18_19',valor:sun?80:60,manual:false};
 return {tipo:'apos_19_manual',valor:null,manual:true};
}

function bmOpenArrivalModal(mid){
 if(!mid){toast('Selecione um motoboy.');return}
 const moto=adminState.motoboys.find(x=>x.id===mid);
 if(!moto){toast('Motoboy não encontrado.');return}
 const jornada=jornadaOf(mid);
 const naoCompareceu=jornada?.chegada_tipo==='nao_compareceu';
 const now=new Date();
 const existing=jornada?.chegada_at?new Date(jornada.chegada_at):null;
 const defaultTime=existing&&!Number.isNaN(existing.getTime())
  ?`${String(existing.getHours()).padStart(2,'0')}:${String(existing.getMinutes()).padStart(2,'0')}`
  :`${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
 const previousManual=(jornada?.chegada_tipo==='apos_19_manual'||jornada?.chegada_tipo==='faixa_manual')&&Number(jornada?.base_valor)>0?Number(jornada.base_valor):'';
 const el=modal(`<div class="modal-head"><h3>${jornada?.chegada_at||naoCompareceu?'Alterar':'Registrar'} chegada · ${esc(moto.nome)}</h3><button class="x" data-close>×</button></div>
  ${naoCompareceu?'<div class="notice bm-no-show-note" style="margin-top:12px"><b>Marcado como não compareceu.</b> Diária atual: R$ 0,00. Você pode registrar uma chegada abaixo caso isso tenha sido marcado por engano.</div>':''}
  <div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px;margin-top:12px">
   <label class="field"><span>Horário</span><input id="bmArrivalTime" type="time" value="${defaultTime}"></label>
   <label class="field"><span>Valor da diária</span><input id="bmArrivalValue" type="number" min="0.01" step="0.01" inputmode="decimal" value="${previousManual}" placeholder="R$ 0,00"></label>
  </div>
  <div id="bmArrivalPreview" class="notice" style="margin-top:12px;font-size:18px;font-weight:900;text-align:center"></div>
  <button id="bmNoShowArrival" class="btn danger" style="width:100%;margin-top:12px">NÃO FOI / DIÁRIA R$ 0,00</button>
  <div class="row-sub" style="margin-top:7px;text-align:center">Use esta opção quando o motoboy não comparecer. O valor zero será salvo como válido no histórico.</div>
  <div class="quick-actions" style="margin-top:14px"><button class="btn secondary" data-close>Cancelar</button><button id="bmConfirmArrival" class="btn green">CONFIRMAR CHEGADA</button></div>`);

 const timeInput=$('#bmArrivalTime');
 const valueInput=$('#bmArrivalValue');
 const preview=$('#bmArrivalPreview');
 const refresh=()=>{
  const rule=bmArrivalRule(timeInput?.value);
  if(!rule)return;
  if(rule.manual){
   valueInput.disabled=false;
   valueInput.readOnly=false;
   valueInput.style.opacity='1';
   if(!valueInput.value&&previousManual)valueInput.value=previousManual;
   const shown=Number(valueInput.value);
   preview.textContent=`${timeInput.value} — ${Number.isFinite(shown)&&shown>0?BRL(shown):'R$ EDITÁVEL'}`;
  }else{
   valueInput.value=String(rule.valor);
   valueInput.disabled=true;
   valueInput.readOnly=true;
   valueInput.style.opacity='.75';
   preview.textContent=`${timeInput.value} — ${BRL(rule.valor)}`;
  }
 };
 timeInput?.addEventListener('input',refresh);
 valueInput?.addEventListener('input',refresh);
 el.querySelectorAll('[data-close]').forEach(btn=>btn.addEventListener('click',()=>el.remove()));
 $('#bmNoShowArrival')?.addEventListener('click',async()=>{
  if(!confirm(`Marcar ${moto.nome} como não compareceu hoje e deixar a diária em R$ 0,00?`))return;
  const btn=$('#bmNoShowArrival');
  if(btn){btn.disabled=true;btn.textContent='SALVANDO...'}
  try{
   const j=await ensureJornada(mid);
   const r=await sb.from('kh_motoboy_jornadas').update({
    chegada_at:null,
    chegada_tipo:'nao_compareceu',
    base_valor:0,
    updated_at:new Date().toISOString()
   }).eq('id',j.id).select().single();
   if(r.error)throw r.error;
   el.remove();
   toast(`${moto.nome}: não compareceu · diária R$ 0,00.`);
   await renderAdmin();
  }catch(e){
   console.error(e);
   toast(e?.message||'Não foi possível registrar a ausência.');
   if(btn){btn.disabled=false;btn.textContent='NÃO FOI / DIÁRIA R$ 0,00'}
  }
 });
 $('#bmConfirmArrival')?.addEventListener('click',async()=>{
  const time=timeInput?.value;
  const rule=bmArrivalRule(time);
  if(!rule){toast('Selecione um horário válido.');return}
  let valor=rule.valor;
  if(rule.manual){
   valor=Number(valueInput?.value);
   if(!Number.isFinite(valor)||valor<=0){toast('Informe o valor da diária.');return}
  }
  const [hh,mm]=time.split(':').map(Number);
  const [y,mo,d]=today().split('-').map(Number);
  const chegada=new Date(y,mo-1,d,hh,mm,0,0);
  if(Number.isNaN(chegada.getTime())){toast('Horário inválido.');return}
  const btn=$('#bmConfirmArrival');
  if(btn){btn.disabled=true;btn.textContent='SALVANDO...'}
  try{
   const j=await ensureJornada(mid);
   const r=await sb.from('kh_motoboy_jornadas').update({
    chegada_at:chegada.toISOString(),
    chegada_tipo:rule.tipo,
    base_valor:valor,
    updated_at:new Date().toISOString()
   }).eq('id',j.id).select().single();
   if(r.error)throw r.error;
   el.remove();
   toast(`Chegada ${time} · ${BRL(valor)}`);
   await renderAdmin();
  }catch(e){
   console.error(e);
   toast(e?.message||'Não foi possível registrar a chegada.');
   if(btn){btn.disabled=false;btn.textContent='CONFIRMAR CHEGADA'}
  }
 });
 refresh();
}

registerArrival=async function(mid){bmOpenArrivalModal(mid)};

/* Exibição clara da ausência e motoboy aguardando na área vazia do painel. */
(function bmInstallAttendanceAndDispatchEnhancements(){
 if(window.__bmAttendanceDispatchV3)return;
 window.__bmAttendanceDispatchV3=true;

 if(!document.getElementById('bmAttendanceDispatchStyle')){
  const style=document.createElement('style');
  style.id='bmAttendanceDispatchStyle';
  style.textContent=`
   .bm-no-show-note{border-color:rgba(239,68,68,.42)!important;background:rgba(127,29,29,.18)!important;color:#fecaca!important}
   .bm-no-show-pill{display:inline-flex;align-items:center;gap:6px;margin-top:6px;padding:5px 9px;border-radius:999px;border:1px solid rgba(239,68,68,.42);background:rgba(127,29,29,.18);color:#fecaca;font-size:11px;font-weight:900}
   .moto-hero{position:relative;overflow:hidden;min-height:82px}
   .bm-idle-rider{position:absolute;right:170px;bottom:-11px;width:138px;max-height:88px;pointer-events:none;opacity:.9;filter:drop-shadow(0 10px 12px rgba(0,0,0,.5));transform-origin:50% 100%;animation:bmIdleRider 2.5s ease-in-out infinite}
   .bm-idle-rider::after{content:'';position:absolute;left:13%;right:4%;bottom:6px;height:4px;border-radius:50%;background:linear-gradient(90deg,transparent,rgba(242,163,60,.55),transparent);filter:blur(3px)}
   .bm-idle-rider img{display:block;width:100%;height:auto;max-height:88px;object-fit:contain}
   .bm-idle-rider span{display:none;font-size:58px}
   .bm-idle-rider.bm-img-fail img{display:none}.bm-idle-rider.bm-img-fail span{display:block}
   .bm-idle-rider.bm-leaving{opacity:0;transform:translateX(70px) scale(.92);transition:opacity .18s ease,transform .18s ease;animation:none}
   @keyframes bmIdleRider{0%,100%{transform:translateY(0)}50%{transform:translateY(-3px)}}
   @media(max-width:1050px){.bm-idle-rider{right:145px;width:112px}.moto-hero{min-height:74px}}
   @media(max-width:760px){.bm-idle-rider{display:none}.moto-hero{min-height:0}}
   @media(prefers-reduced-motion:reduce){.bm-idle-rider{animation:none}}
  `;
  document.head.appendChild(style);
 }

 if(typeof arrivalLabel==='function'){
  const originalArrivalLabel=arrivalLabel;
  arrivalLabel=function(j){
   if(j?.chegada_tipo==='nao_compareceu')return `Não compareceu · Diária ${BRL(0)}`;
   return originalArrivalLabel(j);
  };
 }

 if(typeof selectedPanel==='function'){
  const originalSelectedPanel=selectedPanel;
  selectedPanel=function(){
   let html=originalSelectedPanel();
   const j=adminState?.selectedMoto?jornadaOf(adminState.selectedMoto):null;
   if(adminState?.selectedMoto&&html.includes('<div class="moto-hero">')){
    const idle=`<div class="bm-idle-rider" aria-hidden="true"><img src="${BM_DISPATCH_RIDER_ASSET}" alt=""><span>🏍️</span></div>`;
    html=html.replace('<div class="moto-hero">','<div class="moto-hero">'+idle);
   }
   if(j?.chegada_tipo==='nao_compareceu'){
    html=html
      .replace(/Sem chegada registrada · diária R\$\s*0,00/g,'Não compareceu · diária R$ 0,00')
      .replace(/<strong>R\$\s*0,00<\/strong><div class="row-sub">Sem chegada registrada\. A diária fica zerada\.<\/div>/g,'<strong>R$ 0,00</strong><div class="row-sub">Não compareceu hoje. O valor zero foi registrado.</div><span class="bm-no-show-pill">Não compareceu</span>')
      .replace('REGISTRAR CHEGADA AGORA','ALTERAR / REGISTRAR CHEGADA');
   }
   return html;
  };
 }

 if(typeof bindAdmin==='function'){
  const originalBindAdmin=bindAdmin;
  bindAdmin=function(){
   originalBindAdmin();
   document.querySelectorAll('.bm-idle-rider img').forEach(img=>img.addEventListener('error',()=>img.parentElement?.classList.add('bm-img-fail'),{once:true}));
  };
 }

 if(typeof bmPlayDispatchAnimation==='function'){
  const originalPlay=bmPlayDispatchAnimation;
  bmPlayDispatchAnimation=function(opts={}){
   if((opts?.mode||'admin')==='admin')document.querySelector('.bm-idle-rider')?.classList.add('bm-leaving');
   return originalPlay(opts);
  };
 }

 if(typeof todayHtml==='function'){
  const originalTodayHtml=todayHtml;
  todayHtml=function(d){
   let html=originalTodayHtml(d);
   if(d?.j?.chegada_tipo==='nao_compareceu'){
    const notice='<div class="notice bm-no-show-note" style="margin-top:10px"><b>Não compareceu hoje</b> · diária R$ 0,00</div>';
    html=html.replace('<div class="section-title">Entregas de hoje</div>',notice+'<div class="section-title">Entregas de hoje</div>');
   }
   return html;
  };
 }

 if(typeof dayHistoryHtml==='function'){
  const originalDayHistoryHtml=dayHistoryHtml;
  dayHistoryHtml=function(d){
   let html=originalDayHistoryHtml(d);
   if(d?.j?.chegada_tipo==='nao_compareceu')html='<div class="notice bm-no-show-note"><b>Não compareceu</b> · diária R$ 0,00 registrada neste dia.</div>'+html;
   return html;
  };
 }

 if(typeof bmReportPersonHtml==='function'){
  const originalReportPerson=bmReportPersonHtml;
  bmReportPersonHtml=function(m){
   let html=originalReportPerson(m);
   if(m?.chegada_tipo==='nao_compareceu')html=html.replace('Chegada: <b>Não registrada</b>','Chegada: <b>Não compareceu</b>');
   return html;
  };
 }
})();
