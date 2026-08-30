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
 const now=new Date();
 const existing=jornada?.chegada_at?new Date(jornada.chegada_at):null;
 const defaultTime=existing&&!Number.isNaN(existing.getTime())
  ?`${String(existing.getHours()).padStart(2,'0')}:${String(existing.getMinutes()).padStart(2,'0')}`
  :`${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
 const previousManual=(jornada?.chegada_tipo==='apos_19_manual'||jornada?.chegada_tipo==='faixa_manual')&&Number(jornada?.base_valor)>0?Number(jornada.base_valor):'';
 const el=modal(`<div class="modal-head"><h3>${jornada?.chegada_at?'Alterar':'Registrar'} chegada · ${esc(moto.nome)}</h3><button class="x" data-close>×</button></div>
  <div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px;margin-top:12px">
   <label class="field"><span>Horário</span><input id="bmArrivalTime" type="time" value="${defaultTime}"></label>
   <label class="field"><span>Valor da diária</span><input id="bmArrivalValue" type="number" min="0.01" step="0.01" inputmode="decimal" value="${previousManual}" placeholder="R$ 0,00"></label>
  </div>
  <div id="bmArrivalPreview" class="notice" style="margin-top:12px;font-size:18px;font-weight:900;text-align:center"></div>
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
