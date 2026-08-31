function bmSortRiderNotes(items){
 return [...(items||[])].sort((a,b)=>{
  const ap=!!a.nota_confirmada, bp=!!b.nota_confirmada;
  if(ap!==bp)return ap?1:-1;
  const ad=new Date(ap?(a.nota_confirmada_at||a.updated_at||a.created_at):(a.created_at||a.updated_at)).getTime()||0;
  const bd=new Date(bp?(b.nota_confirmada_at||b.updated_at||b.created_at):(b.created_at||b.updated_at)).getTime()||0;
  return bd-ad;
 });
}

riderDay=async function(date){
 const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at,chegada_tipo,fechado').eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
 if(j.error)throw j.error;
 if(!j.data)return {j:null,e:[],s:[],total:0};
 const [e,so]=await Promise.all([
  sb.from('kh_motoboy_entregas').select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at').eq('jornada_id',j.data.id),
  sb.from('kh_motoboy_saidas').select('id,numero_sequencial,horario_saida,total,status,created_at').eq('jornada_id',j.data.id).order('numero_sequencial')
 ]);
 if(e.error)throw e.error;
 if(so.error)throw so.error;
 const entregas=e.data||[];
 const total=(j.data.chegada_at?Number(j.data.base_valor||0):0)+entregas.reduce((a,x)=>a+Number(x.valor||0),0);
 return {j:j.data,e:bmSortRiderNotes(entregas),s:so.data||[],total};
};

function bmRiderNoteCard(e){
 const confirmed=!!e.nota_confirmada;
 const confirmedAt=e.nota_confirmada_at?new Date(e.nota_confirmada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
 const bairro=esc(e.bairro_nome||'Bairro não informado');
 if(confirmed){
  return `<div data-note-card="${e.id}" style="display:flex;align-items:center;gap:12px;padding:14px 16px;margin:9px 0;border:1px solid rgba(33,200,117,.82);border-radius:15px;background:linear-gradient(180deg,rgba(25,29,32,.98),rgba(18,21,23,.98));box-shadow:0 0 14px rgba(33,200,117,.16)">
   <div style="min-width:0;flex:1 1 42%">
    <div style="font-size:27px;line-height:1;font-weight:900;letter-spacing:.5px;color:#f5f7f8">${esc(e.nota_numero)}</div>
    <div style="margin-top:6px;font-size:12px;font-weight:700;color:#9fa7ad;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${bairro}</div>
   </div>
   <div style="width:1px;height:42px;background:rgba(255,255,255,.08);flex:0 0 auto"></div>
   <div style="display:flex;align-items:center;gap:8px;min-width:0;flex:1 1 auto;color:#28cf7b;font-weight:900">
    <span style="display:inline-grid;place-items:center;width:30px;height:30px;border-radius:50%;background:#28cf7b;color:#07140d;font-size:20px;flex:0 0 auto">✓</span>
    <span style="font-size:15px;white-space:nowrap">CONFIRMADA</span>
   </div>
   <div style="font-size:13px;font-weight:800;color:#9ba3aa;white-space:nowrap">${confirmedAt}</div>
  </div>`;
 }
 return `<div data-note-card="${e.id}" style="padding:15px 16px;margin:9px 0;border:1px solid rgba(255,255,255,.08);border-radius:15px;background:#171a1d">
  <div style="display:flex;align-items:flex-start;gap:12px">
   <div style="min-width:0;flex:1">
    <div class="stat-label">NÚMERO DA NOTA</div>
    <div data-note-number style="font-size:31px;font-weight:900;letter-spacing:.5px;margin-top:3px">${esc(e.nota_numero)}</div>
    <div style="margin-top:5px;font-size:13px;font-weight:800;color:#aeb5ba">BAIRRO · <span style="color:#fff">${bairro}</span></div>
   </div>
  </div>
  <button type="button" class="btn" data-confirm-note="${e.id}" onclick="window.bmConfirmRiderNote('${e.id}')" style="width:100%;min-height:48px;margin-top:13px;font-weight:900;background:#159958!important;color:#fff!important;border:1px solid #21c875!important">CONFIRMAR NOTA</button>
 </div>`;
}

todayHtml=function(d){
 const valid=bmSortRiderNotes(d.e.filter(e=>e.status!=='cancelada'));
 const pending=valid.filter(e=>!e.nota_confirmada);
 const confirmed=valid.filter(e=>e.nota_confirmada);
 const list=[...pending,...confirmed];
 return `<section class="rider-fast-panel">
  <div class="today-summary">
   <div class="card"><div class="stat-label">ENTREGAS</div><div class="stat-value">${valid.length}</div></div>
   <div class="card"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(d.total)}</div></div>
  </div>
  ${d.j?.chegada_at?`<div class="notice" style="margin-top:10px">Chegada registrada às <b>${new Date(d.j.chegada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b> · diária ${BRL(d.j.base_valor||0)}</div>`:''}
  <div class="section-title">Conferir notas</div>
  <div class="notice"><b>${pending.length?`${pending.length} nota${pending.length===1?'':'s'} aguardando confirmação`:'Todas as notas estão confirmadas'}</b><br>Confira o número e o bairro da nota física.</div>
  <div style="margin-top:10px">${list.length?list.map(bmRiderNoteCard).join(''):'<div class="card"><div class="empty">Nenhuma nota lançada hoje.</div></div>'}</div>
 </section>`;
};

async function bmConfirmRiderNote(id){
 rider.confirmingNotes=rider.confirmingNotes||new Set();
 if(rider.confirmingNotes.has(id))return;
 rider.confirmingNotes.add(id);
 const btn=document.querySelector(`[data-confirm-note="${id}"]`);
 if(btn){btn.disabled=true;btn.textContent='CONFIRMANDO...'}
 try{
  rider.silentConfirmId=id;
  const r=await sb.rpc('kh_confirm_motoboy_delivery_note',{p_delivery_id:id});
  if(r.error)throw r.error;
  if(rider.todayCache?.e){
   const item=rider.todayCache.e.find(x=>x.id===id);
   if(item){item.nota_confirmada=true;item.nota_confirmada_at=r.data?.nota_confirmada_at||new Date().toISOString()}
  }
  toast(`Nota ${r.data?.nota_numero||''} confirmada.`);
  await renderRider('Hoje');
 }catch(e){
  console.error('confirm-note',e);
  toast(e?.message||'Não foi possível confirmar a nota.');
  if(btn){btn.disabled=false;btn.textContent='CONFIRMAR NOTA'}
 }finally{
  rider.confirmingNotes.delete(id);
  setTimeout(()=>{if(rider.silentConfirmId===id)rider.silentConfirmId=null},1500);
 }
}
window.bmConfirmRiderNote=bmConfirmRiderNote;
