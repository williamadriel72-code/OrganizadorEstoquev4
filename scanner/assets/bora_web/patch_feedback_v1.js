(()=>{
 if(window.__bmFeedbackV1)return;
 window.__bmFeedbackV1=true;
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';

 const escText=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const fmtDate=v=>{try{return new Date(v).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'})}catch(_){return ''}};
 const typeLabel={reclamacao:'Reclamação',melhoria:'Melhoria',erro:'Erro',outro:'Outro'};

 if(!document.getElementById('bmFeedbackStyle')){
  const s=document.createElement('style');
  s.id='bmFeedbackStyle';
  s.textContent=`
   .bm-feedback-overlay{position:fixed;inset:0;z-index:10050;background:rgba(0,0,0,.72);display:grid;place-items:center;padding:18px;backdrop-filter:blur(5px);-webkit-backdrop-filter:blur(5px)}
   .bm-feedback-modal{width:min(560px,100%);max-height:min(82vh,760px);overflow:auto;background:#15191d;border:1px solid rgba(255,255,255,.12);border-radius:18px;box-shadow:0 24px 70px rgba(0,0,0,.55);padding:18px;color:#f5f7f8}
   .bm-feedback-modal h2{margin:0 0 5px;font-size:21px}.bm-feedback-modal p{margin:0 0 16px;color:#aeb5ba;font-size:13px;line-height:1.45}
   .bm-feedback-field{display:grid;gap:7px;margin:13px 0}.bm-feedback-field label{font-size:12px;font-weight:900;color:#aeb5ba;text-transform:uppercase;letter-spacing:.5px}
   .bm-feedback-field select,.bm-feedback-field textarea{width:100%;box-sizing:border-box;border:1px solid rgba(255,255,255,.12);border-radius:12px;background:#0f1316;color:#fff;padding:12px;font:inherit;outline:none}
   .bm-feedback-field textarea{min-height:130px;resize:vertical}.bm-feedback-field select:focus,.bm-feedback-field textarea:focus{border-color:#31d98288;box-shadow:0 0 0 3px rgba(49,217,130,.08)}
   .bm-feedback-actions{display:flex;gap:9px;justify-content:flex-end;margin-top:14px}.bm-feedback-actions button{min-height:42px;border-radius:11px;padding:0 15px;font-weight:900;border:1px solid rgba(255,255,255,.12);cursor:pointer}
   .bm-feedback-cancel{background:#20262b;color:#d9dfe3}.bm-feedback-send{background:#159958;color:#fff;border-color:#21c875!important}.bm-feedback-send:disabled{opacity:.55;cursor:not-allowed}
   #bmFeedbackRiderBtn{position:fixed;z-index:9995;right:max(14px,env(safe-area-inset-right));bottom:max(82px,calc(env(safe-area-inset-bottom) + 72px));max-width:190px;min-height:40px;border:1px solid rgba(49,217,130,.28);border-radius:13px;background:rgba(16,22,25,.96);color:#dff8e9;padding:8px 11px;font-size:11px;font-weight:900;line-height:1.15;box-shadow:0 8px 24px rgba(0,0,0,.3)}
   #bmFeedbackRiderBtn span{display:block;color:#7f8b92;font-size:9px;font-weight:800;margin-top:2px}
   #bmFeedbackAdminBtn{position:relative}.bm-feedback-badge{display:inline-grid;place-items:center;min-width:18px;height:18px;padding:0 4px;border-radius:99px;background:#e73f3f;color:#fff;font-size:10px;font-weight:900;margin-left:5px}
   .bm-feedback-list{display:grid;gap:10px}.bm-feedback-item{border:1px solid rgba(255,255,255,.09);background:#101418;border-radius:14px;padding:13px}.bm-feedback-item[data-status="novo"]{border-color:rgba(231,63,63,.38)}.bm-feedback-item[data-status="resolvido"]{opacity:.72}
   .bm-feedback-meta{display:flex;flex-wrap:wrap;gap:7px;align-items:center;margin-bottom:8px}.bm-feedback-name{font-weight:900}.bm-feedback-chip{padding:4px 7px;border-radius:99px;background:#252b30;color:#dbe1e5;font-size:10px;font-weight:900}.bm-feedback-time{margin-left:auto;color:#89949b;font-size:11px}.bm-feedback-msg{white-space:pre-wrap;line-height:1.45;color:#eef1f3;font-size:13px}
   .bm-feedback-item-actions{display:flex;gap:7px;margin-top:11px}.bm-feedback-item-actions button{border:1px solid rgba(255,255,255,.1);border-radius:9px;background:#20262b;color:#e7ebed;padding:7px 10px;font-size:11px;font-weight:900}.bm-feedback-item-actions button[data-fb-status="resolvido"]{background:#113c29;border-color:#21c87566;color:#9af2c0}
   @media(max-width:420px){#bmFeedbackRiderBtn{right:12px;bottom:max(78px,calc(env(safe-area-inset-bottom) + 68px));max-width:160px;padding:7px 9px}.bm-feedback-modal{padding:15px;border-radius:16px}}
  `;
  document.head.appendChild(s);
 }

 function closeModal(){document.getElementById('bmFeedbackOverlay')?.remove()}
 function showMessage(msg){if(typeof toast==='function')toast(msg);else alert(msg)}

 function openRiderFeedback(){
  closeModal();
  const ov=document.createElement('div');ov.id='bmFeedbackOverlay';ov.className='bm-feedback-overlay';
  ov.innerHTML=`<div class="bm-feedback-modal" role="dialog" aria-modal="true" aria-label="Reclamações e melhorias">
   <h2>Reclamações e Melhorias</h2>
   <p>Envie uma reclamação, sugestão ou problema encontrado no APK. Seu nome, data e horário serão registrados automaticamente.</p>
   <div class="bm-feedback-field"><label>Tipo</label><select id="bmFeedbackType"><option value="reclamacao">Reclamação</option><option value="melhoria">Melhoria</option><option value="erro">Erro no APK</option><option value="outro">Outro</option></select></div>
   <div class="bm-feedback-field"><label>Mensagem</label><textarea id="bmFeedbackMessage" maxlength="2000" placeholder="Descreva aqui o que aconteceu ou o que pode melhorar..."></textarea></div>
   <div class="bm-feedback-actions"><button type="button" class="bm-feedback-cancel" id="bmFeedbackCancel">CANCELAR</button><button type="button" class="bm-feedback-send" id="bmFeedbackSend">ENVIAR</button></div>
  </div>`;
  document.body.appendChild(ov);
  ov.addEventListener('click',e=>{if(e.target===ov)closeModal()});
  ov.querySelector('#bmFeedbackCancel').onclick=closeModal;
  const send=ov.querySelector('#bmFeedbackSend');
  send.onclick=async()=>{
   if(send.disabled)return;
   const tipo=ov.querySelector('#bmFeedbackType').value;
   const mensagem=ov.querySelector('#bmFeedbackMessage').value.trim();
   if(mensagem.length<3){showMessage('Escreva uma mensagem antes de enviar.');return}
   if(!navigator.onLine){showMessage('Sem internet. Não foi possível enviar agora.');return}
   send.disabled=true;send.textContent='ENVIANDO...';
   try{
    const r=await sb.rpc('kh_submit_motoboy_feedback',{p_tipo:tipo,p_mensagem:mensagem});
    if(r.error)throw r.error;
    closeModal();showMessage('Mensagem enviada com sucesso.');
   }catch(e){console.error('feedback-submit',e);send.disabled=false;send.textContent='ENVIAR';showMessage(e?.message||'Não foi possível enviar a mensagem.');}
  };
  setTimeout(()=>ov.querySelector('#bmFeedbackMessage')?.focus(),50);
 }

 function mountRiderButton(){
  if(!riderMode||document.getElementById('bmFeedbackRiderBtn')||!document.body)return;
  const btn=document.createElement('button');
  btn.id='bmFeedbackRiderBtn';btn.type='button';
  btn.innerHTML='💬 RECLAMAÇÕES E MELHORIAS<span>Enviar feedback do APK</span>';
  btn.onclick=openRiderFeedback;
  document.body.appendChild(btn);
 }

 let adminRows=[],adminOpen=false,adminChannel=null;
 async function loadAdminFeedback(){
  if(riderMode||typeof sb==='undefined')return;
  const r=await sb.from('kh_motoboy_feedback').select('id,motoboy_id,tipo,mensagem,status,created_at,updated_at').order('created_at',{ascending:false}).limit(200);
  if(r.error)throw r.error;
  const rows=r.data||[],ids=[...new Set(rows.map(x=>x.motoboy_id).filter(Boolean))];
  let names={};
  if(ids.length){
   const m=await sb.from('kh_motoboys').select('id,nome').in('id',ids);
   if(!m.error)names=Object.fromEntries((m.data||[]).map(x=>[x.id,x.nome]));
  }
  adminRows=rows.map(x=>({...x,motoboy_nome:names[x.motoboy_id]||'Motoboy'}));
  updateAdminBadge();
 }
 function updateAdminBadge(){
  const btn=document.getElementById('bmFeedbackAdminBtn');if(!btn)return;
  const n=adminRows.filter(x=>x.status==='novo').length;
  let b=btn.querySelector('.bm-feedback-badge');
  if(n){if(!b){b=document.createElement('span');b.className='bm-feedback-badge';btn.appendChild(b)}b.textContent=String(n)}else b?.remove();
 }
 function feedbackListHtml(){
  if(!adminRows.length)return '<div class="empty">Nenhuma mensagem recebida.</div>';
  return adminRows.map(x=>`<div class="bm-feedback-item" data-status="${escText(x.status)}">
   <div class="bm-feedback-meta"><span class="bm-feedback-name">${escText(x.motoboy_nome)}</span><span class="bm-feedback-chip">${escText(typeLabel[x.tipo]||x.tipo)}</span><span class="bm-feedback-chip">${escText(x.status.toUpperCase())}</span><span class="bm-feedback-time">${escText(fmtDate(x.created_at))}</span></div>
   <div class="bm-feedback-msg">${escText(x.mensagem)}</div>
   <div class="bm-feedback-item-actions">${x.status!=='lido'?`<button type="button" data-fb-id="${x.id}" data-fb-status="lido">MARCAR COMO LIDO</button>`:''}${x.status!=='resolvido'?`<button type="button" data-fb-id="${x.id}" data-fb-status="resolvido">RESOLVIDO</button>`:''}</div>
  </div>`).join('');
 }
 async function setFeedbackStatus(id,status){
  const r=await sb.from('kh_motoboy_feedback').update({status,updated_at:new Date().toISOString()}).eq('id',id).select('id').maybeSingle();
  if(r.error)throw r.error;
  await loadAdminFeedback();renderAdminModalBody();
 }
 function renderAdminModalBody(){
  const body=document.getElementById('bmFeedbackAdminList');if(!body)return;
  body.innerHTML=feedbackListHtml();
  body.querySelectorAll('[data-fb-id]').forEach(btn=>btn.onclick=async()=>{btn.disabled=true;try{await setFeedbackStatus(btn.dataset.fbId,btn.dataset.fbStatus)}catch(e){btn.disabled=false;showMessage(e?.message||'Não foi possível atualizar.')}});
 }
 async function openAdminFeedback(){
  closeModal();adminOpen=true;
  const ov=document.createElement('div');ov.id='bmFeedbackOverlay';ov.className='bm-feedback-overlay';
  ov.innerHTML=`<div class="bm-feedback-modal" style="width:min(760px,100%)" role="dialog" aria-modal="true"><h2>Feedback dos Motoboys</h2><p>Reclamações, melhorias e erros enviados diretamente pelo APK.</p><div id="bmFeedbackAdminList"><div class="empty">Carregando...</div></div><div class="bm-feedback-actions"><button type="button" class="bm-feedback-cancel" id="bmFeedbackClose">FECHAR</button></div></div>`;
  document.body.appendChild(ov);
  ov.addEventListener('click',e=>{if(e.target===ov){adminOpen=false;closeModal()}});
  ov.querySelector('#bmFeedbackClose').onclick=()=>{adminOpen=false;closeModal()};
  try{await loadAdminFeedback();renderAdminModalBody()}catch(e){document.getElementById('bmFeedbackAdminList').innerHTML=`<div class="empty">${escText(e?.message||'Não foi possível carregar.')}</div>`}
 }
 function mountAdminButton(){
  if(riderMode||document.getElementById('bmFeedbackAdminBtn'))return;
  const head=document.querySelector('.admin-head');if(!head)return;
  const btn=document.createElement('button');btn.id='bmFeedbackAdminBtn';btn.type='button';btn.className='btn secondary small';btn.textContent='FEEDBACK';btn.onclick=openAdminFeedback;
  const logout=document.getElementById('logout');if(logout)head.insertBefore(btn,logout);else head.appendChild(btn);
  updateAdminBadge();
 }
 function ensureAdminRealtime(){
  if(riderMode||adminChannel||typeof sb==='undefined')return;
  try{
   adminChannel=sb.channel('bm-feedback-admin-'+Date.now()).on('postgres_changes',{event:'*',schema:'public',table:'kh_motoboy_feedback'},async()=>{try{await loadAdminFeedback();if(adminOpen)renderAdminModalBody()}catch(_){}}).subscribe();
  }catch(_){ }
 }

 if(riderMode){
  mountRiderButton();
  const mo=new MutationObserver(()=>{if(!document.getElementById('bmFeedbackRiderBtn'))mountRiderButton()});
  mo.observe(document.documentElement,{childList:true,subtree:true});
 }else{
  setTimeout(async()=>{mountAdminButton();ensureAdminRealtime();try{await loadAdminFeedback()}catch(_){}},500);
  setTimeout(mountAdminButton,1200);
  const mo=new MutationObserver(()=>{if(!document.getElementById('bmFeedbackAdminBtn'))mountAdminButton()});
  mo.observe(document.documentElement,{childList:true,subtree:true});
 }
})();
