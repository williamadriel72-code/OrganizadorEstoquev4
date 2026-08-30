riderDay=async function(date){
 const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at,chegada_tipo,fechado').eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
 if(j.error)throw j.error;
 if(!j.data)return {j:null,e:[],s:[],total:0};
 const [e,so]=await Promise.all([
  sb.from('kh_motoboy_entregas').select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at').eq('jornada_id',j.data.id).order('created_at'),
  sb.from('kh_motoboy_saidas').select('id,numero_sequencial,horario_saida,total,status,created_at').eq('jornada_id',j.data.id).order('numero_sequencial')
 ]);
 if(e.error)throw e.error;
 if(so.error)throw so.error;
 const total=Number(j.data.base_valor||0)+(e.data||[]).reduce((a,x)=>a+Number(x.valor||0),0);
 return {j:j.data,e:e.data||[],s:so.data||[],total};
};

function bmRiderNoteCard(e){
 const confirmed=!!e.nota_confirmada;
 const confirmedAt=e.nota_confirmada_at?new Date(e.nota_confirmada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
 const cardStyle=confirmed
  ? 'padding:16px;margin:10px 0;border:2px solid #21c875;border-radius:14px;background:rgba(33,200,117,.18);box-shadow:0 0 0 1px rgba(33,200,117,.08) inset'
  : 'padding:16px 0;border-bottom:1px solid rgba(255,255,255,.08)';
 return `<div data-note-card="${e.id}" style="${cardStyle}">
  <div class="stat-label" style="${confirmed?'color:#5df0a8':''}">NÚMERO DA NOTA</div>
  <div style="font-size:34px;font-weight:900;letter-spacing:1px;margin:4px 0 8px;${confirmed?'color:#5df0a8':''}">${esc(e.nota_numero)}</div>
  ${confirmed
   ? `<div style="margin-top:8px;padding:12px 14px;border-radius:10px;background:#159958;color:#fff;font-weight:900;text-align:center">✓ NOTA CONFIRMADA${confirmedAt?` · ${confirmedAt}`:''}</div>`
   : `<div class="row-sub" style="margin-bottom:10px">Compare este número com a nota que você pegou. Se estiver igual, confirme abaixo.</div><button class="btn" style="width:100%;font-weight:900;background:#159958;color:#fff;border:1px solid #21c875" data-confirm-note="${e.id}">CONFIRMAR NOTA</button>`}
 </div>`;
}

todayHtml=function(d){
 const valid=d.e.filter(e=>e.status!=='cancelada');
 const pending=valid.filter(e=>!e.nota_confirmada).length;
 return `<section class="rider-fast-panel">
  <div class="today-summary">
   <div class="card"><div class="stat-label">ENTREGAS</div><div class="stat-value">${valid.length}</div></div>
   <div class="card"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(d.total)}</div></div>
  </div>
  ${d.j?.chegada_at?`<div class="notice" style="margin-top:10px">Chegada registrada às <b>${new Date(d.j.chegada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b> · diária ${BRL(d.j.base_valor||0)}</div>`:''}
  <div class="section-title">Conferir notas</div>
  <div class="notice"><b>${pending?`${pending} nota${pending===1?'':'s'} aguardando confirmação`:'Todas as notas estão confirmadas'}</b><br>O motoboy apenas confere se o número exibido bate com a nota física que recebeu.</div>
  <div class="card" style="margin-top:10px">${valid.length?valid.map(bmRiderNoteCard).join(''):'<div class="empty">Nenhuma nota lançada hoje.</div>'}</div>
 </section>`;
};

async function bmConfirmRiderNote(id){
 const btn=document.querySelector(`[data-confirm-note="${id}"]`);
 if(btn){btn.disabled=true;btn.textContent='CONFIRMANDO...';btn.style.background='#159958';btn.style.color='#fff'}
 try{
  rider.silentConfirmId=id;
  const r=await sb.rpc('kh_confirm_motoboy_delivery_note',{p_delivery_id:id});
  if(r.error)throw r.error;
  const card=document.querySelector(`[data-note-card="${id}"]`);
  if(card){
   card.style.padding='16px';
   card.style.margin='10px 0';
   card.style.border='2px solid #21c875';
   card.style.borderRadius='14px';
   card.style.background='rgba(33,200,117,.18)';
   const noteNumber=card.querySelector('div[style*="font-size:34px"]');
   if(noteNumber)noteNumber.style.color='#5df0a8';
   if(btn)btn.outerHTML='<div style="margin-top:8px;padding:12px 14px;border-radius:10px;background:#159958;color:#fff;font-weight:900;text-align:center">✓ NOTA CONFIRMADA</div>';
  }
  if(rider.todayCache?.e){
   const item=rider.todayCache.e.find(x=>x.id===id);
   if(item){item.nota_confirmada=true;item.nota_confirmada_at=new Date().toISOString()}
  }
  toast(`Nota ${r.data?.nota_numero||''} confirmada.`);
  setTimeout(async()=>{try{await renderRider('Hoje')}catch(_){ }finally{if(rider.silentConfirmId===id)rider.silentConfirmId=null}},700);
 }catch(e){
  rider.silentConfirmId=null;
  console.error(e);
  toast(e?.message||'Não foi possível confirmar a nota.');
  if(btn){btn.disabled=false;btn.textContent='CONFIRMAR NOTA';btn.style.background='#159958';btn.style.color='#fff'}
 }
}

const bmRenderRiderBeforeNoteConfirm=renderRider;
renderRider=async function(active='Hoje'){
 await bmRenderRiderBeforeNoteConfirm(active);
 if(active==='Hoje'){
  $$('[data-confirm-note]').forEach(btn=>btn.addEventListener('click',()=>bmConfirmRiderNote(btn.dataset.confirmNote)));
 }
};

if(typeof nativeNotify==='function'){
 const bmNativeNotifyBeforeNoteConfirm=nativeNotify;
 nativeNotify=function(payload){
  const pid=payload?.new?.id||payload?.old?.id||null;
  if(rider?.silentConfirmId&&pid===rider.silentConfirmId){return}
  return bmNativeNotifyBeforeNoteConfirm(payload);
 };
}
