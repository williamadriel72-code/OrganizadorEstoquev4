function bmArrivalRule(timeStr){
 const parts=String(timeStr||'').split(':').map(Number);
 const hh=parts[0],mm=parts[1];
 if(!Number.isInteger(hh)||!Number.isInteger(mm))return null;
 const mins=hh*60+mm;
 const [y,mo,d]=today().split('-').map(Number);
 const sun=new Date(y,mo-1,d,12,0,0).getDay()===0;
 if(mins<18*60)return {tipo:'antes_18',valor:sun?90:70,label:'Antes das 18:00'};
 if(mins>=18*60+30)return {tipo:'apos_1830',valor:sun?80:60,label:'Após 18:30'};
 return {tipo:'faixa_manual',valor:null,label:'Entre 18:00 e 18:30'};
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
 const el=modal(`<div class="modal-head"><h3>${jornada?.chegada_at?'Alterar':'Registrar'} chegada · ${esc(moto.nome)}</h3><button class="x" data-close>×</button></div>
 <div class="notice">Selecione o <b>horário real da chegada</b>. A diária será definida por esse horário e ficará fixa no dia.</div>
 <label class="field" style="margin-top:14px"><span>Horário da chegada</span><input id="bmArrivalTime" type="time" value="${defaultTime}"></label>
 <button id="bmArrivalNow" class="btn secondary" style="width:100%;margin-top:8px">USAR HORÁRIO ATUAL</button>
 <div id="bmArrivalRule" class="notice" style="margin-top:12px"></div>
 <div id="bmArrivalManual" class="hidden" style="margin-top:12px"><label class="field"><span>Valor da diária para 18:00–18:30</span><input id="bmArrivalManualValue" type="number" min="0.01" step="0.01" placeholder="R$ 0,00" value="${jornada?.chegada_tipo==='faixa_manual'&&Number(jornada?.base_valor)>0?Number(jornada.base_valor):''}"></label></div>
 <div class="notice" style="margin-top:12px"><b>Regras:</b><br>Segunda a sábado: antes das 18:00 = R$ 70,00 · após 18:30 = R$ 60,00.<br>Domingo: antes das 18:00 = R$ 90,00 · após 18:30 = R$ 80,00.</div>
 <div class="quick-actions" style="margin-top:14px"><button class="btn secondary" data-close>Cancelar</button><button id="bmConfirmArrival" class="btn green">CONFIRMAR CHEGADA</button></div>`);
 const refresh=()=>{
  const rule=bmArrivalRule($('#bmArrivalTime')?.value);
  if(!rule)return;
  const box=$('#bmArrivalRule');
  const manual=$('#bmArrivalManual');
  if(rule.tipo==='faixa_manual'){
   box.innerHTML=`<b>${rule.label}</b><br>Essa faixa não possui valor automático. Escolha o valor da diária abaixo.`;
   manual?.classList.remove('hidden');
  }else{
   box.innerHTML=`<b>${rule.label}</b><br>Diária que será registrada: <strong>${BRL(rule.valor)}</strong>`;
   manual?.classList.add('hidden');
  }
 };
 $('#bmArrivalTime')?.addEventListener('input',refresh);
 $('#bmArrivalNow')?.addEventListener('click',()=>{
  const n=new Date();
  $('#bmArrivalTime').value=`${String(n.getHours()).padStart(2,'0')}:${String(n.getMinutes()).padStart(2,'0')}`;
  refresh();
 });
 $('#bmConfirmArrival')?.addEventListener('click',async()=>{
  const time=$('#bmArrivalTime')?.value;
  const rule=bmArrivalRule(time);
  if(!rule){toast('Selecione um horário válido.');return}
  let valor=rule.valor;
  if(rule.tipo==='faixa_manual'){
   valor=Number($('#bmArrivalManualValue')?.value);
   if(!Number.isFinite(valor)||valor<=0){toast('Informe o valor da diária para essa faixa.');return}
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
   toast(`Chegada ${time} registrada. Diária ${BRL(valor)}.`);
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

const bmBindAdminOriginal=bindAdmin;
bindAdmin=function(){
 bmBindAdminOriginal();
 const first=$('#arrivalNow');
 if(first)first.textContent='REGISTRAR CHEGADA';
 const mid=adminState.selectedMoto;
 const j=mid?jornadaOf(mid):null;
 const arrivalBox=document.querySelector('.arrival');
 if(j?.chegada_at&&arrivalBox&&!document.getElementById('bmEditArrival')){
  const btn=document.createElement('button');
  btn.id='bmEditArrival';
  btn.className='btn secondary';
  btn.textContent='ALTERAR CHEGADA';
  btn.addEventListener('click',()=>bmOpenArrivalModal(mid));
  arrivalBox.appendChild(btn);
 }
};

function bmNormalizeName(v){
 return String(v||'').replace(/\s+/g,' ').trim();
}

function bmOpenRegisterMotoModal(){
 const el=modal(`<div class="modal-head"><h3>Cadastrar motoboy</h3><button class="x" data-close>×</button></div>
 <div class="notice"><b>Motoboy sem APK / iPhone</b><br>Este cadastro funciona normalmente no painel para chegada, diária, notas, Taxa Integral, saídas e histórico. Nenhum aparelho ou usuário Android será criado.</div>
 <label class="field" style="margin-top:14px"><span>Nome do motoboy</span><input id="bmNewMotoName" autocomplete="off" maxlength="80" placeholder="Digite o nome"></label>
 <label class="field" style="margin-top:10px"><span>Telefone (opcional)</span><input id="bmNewMotoPhone" inputmode="tel" autocomplete="off" maxlength="30" placeholder="(22) 99999-9999"></label>
 <label class="field" style="margin-top:10px"><span>Tipo de acesso</span><select id="bmNewMotoAccess"><option value="manual">Sem APK / iPhone</option></select></label>
 <div class="quick-actions" style="margin-top:14px"><button class="btn secondary" data-close>Cancelar</button><button id="bmSaveNewMoto" class="btn green">CADASTRAR MOTOBOY</button></div>`);
 setTimeout(()=>$('#bmNewMotoName')?.focus(),30);
 $('#bmSaveNewMoto')?.addEventListener('click',async()=>{
  const nome=bmNormalizeName($('#bmNewMotoName')?.value);
  const telefone=String($('#bmNewMotoPhone')?.value||'').trim();
  if(nome.length<2){toast('Digite o nome do motoboy.');return}
  const duplicate=adminState.motoboys.some(m=>bmNormalizeName(m.nome).toLocaleLowerCase('pt-BR')===nome.toLocaleLowerCase('pt-BR'));
  if(duplicate){toast('Já existe um motoboy cadastrado com esse nome.');return}
  const btn=$('#bmSaveNewMoto');
  if(btn){btn.disabled=true;btn.textContent='CADASTRANDO...'}
  try{
   const payload={nome,ativo:true,login_numero:null,user_id:null,telefone:telefone||null,updated_at:new Date().toISOString()};
   const r=await sb.from('kh_motoboys').insert(payload).select('*').single();
   if(r.error)throw r.error;
   adminState.selectedMoto=r.data.id;
   el.remove();
   toast(`${nome} cadastrado sem APK.`);
   await renderAdmin();
  }catch(e){
   console.error(e);
   toast(e?.message||'Não foi possível cadastrar o motoboy.');
   if(btn){btn.disabled=false;btn.textContent='CADASTRAR MOTOBOY'}
  }
 });
}

function bmInstallRegisterMotoButton(){
 const head=document.querySelector('.moto-list-head');
 if(!head||document.getElementById('bmRegisterMoto'))return;
 const btn=document.createElement('button');
 btn.id='bmRegisterMoto';
 btn.className='btn green';
 btn.style.marginLeft='auto';
 btn.style.padding='10px 14px';
 btn.style.fontWeight='800';
 btn.textContent='+ CADASTRAR MOTOBOY';
 btn.addEventListener('click',bmOpenRegisterMotoModal);
 head.appendChild(btn);
}

const bmBindAdminWithArrival=bindAdmin;
bindAdmin=function(){
 bmBindAdminWithArrival();
 bmInstallRegisterMotoButton();
};
