(function bmShiftExceptionV1(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmShiftExceptionV1)return;window.__bmShiftExceptionV1=true;

 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const ADMIN_API=U+'/functions/v1/bora-turno-excecao-admin';
 const originalToday=typeof today==='function'?today:null;
 const exState={active:false,date:null,openedAt:null};
 let rendering=false,lastKey='';
 window.bmShiftExceptionState=exState;

 function realToday(){return originalToday?originalToday():new Date().toISOString().slice(0,10)}
 today=function(){return exState.active&&exState.date?exState.date:realToday()};
 function brDate(v){if(!v)return '—';const p=String(v).split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v)}

 async function stateRequest(){
  const r=await fetch(STATE_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'state'}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha ao consultar exceção.');return j.exception||{};
 }
 async function adminRequest(action){
  const session=(await sb.auth.getSession())?.data?.session;
  if(!session?.access_token)throw Error('Sessão administrativa expirada. Entre novamente no painel.');
  const r=await fetch(ADMIN_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+session.access_token},body:JSON.stringify({action}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.error||'Falha ao alterar a exceção.');return j.exception||{};
 }
 function apply(ex){exState.active=!!ex?.ativa;exState.date=ex?.data_operacional||null;exState.openedAt=ex?.aberta_em||null;updateButton()}

 function css(){
  if(document.getElementById('bmShiftExceptionStyle'))return;
  const s=document.createElement('style');s.id='bmShiftExceptionStyle';s.textContent=`
  #bmShiftExceptionBox{margin-top:10px;padding:10px;border:1px solid rgba(255,255,255,.10);border-radius:12px;background:#15191d}
  #bmShiftExceptionBox.on{border-color:#f0a63b88;background:#2a1d0d}
  #bmShiftExceptionBox small{display:block;color:#9da5ad;font-size:10px;line-height:1.35;margin:0 0 7px}
  #bmShiftExceptionBox.on small{color:#f4c56f}
  #bmShiftExceptionBtn{width:100%;min-height:38px;border:0;border-radius:10px;background:#d88718;color:#fff;font-weight:900;cursor:pointer;padding:9px 10px}
  #bmShiftExceptionBox.on #bmShiftExceptionBtn{background:#b42323}
  #bmShiftExceptionBtn:disabled{opacity:.6;cursor:wait}
  .bm-exception-banner{margin:0 0 12px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:12px;font-weight:850}
  `;document.head.appendChild(s);
 }
 function installButton(){
  css();const side=document.querySelector('.sidebar');if(!side)return;
  let box=document.getElementById('bmShiftExceptionBox');
  if(!box){box=document.createElement('div');box.id='bmShiftExceptionBox';box.innerHTML='<small id="bmShiftExceptionInfo"></small><button id="bmShiftExceptionBtn" type="button"></button>';side.appendChild(box);box.querySelector('#bmShiftExceptionBtn').onclick=toggle}
  updateButton();
  const main=document.querySelector('.admin-main');
  if(main){let banner=document.getElementById('bmShiftExceptionBanner');if(exState.active){if(!banner){banner=document.createElement('div');banner.id='bmShiftExceptionBanner';banner.className='bm-exception-banner';const head=main.querySelector('.admin-head');if(head?.nextSibling)main.insertBefore(banner,head.nextSibling);else main.prepend(banner)}banner.textContent='EXCEÇÃO ABERTA — CONTINUAÇÃO DO TURNO DE '+brDate(exState.date)+' · novos lançamentos continuam neste dia operacional.'}else banner?.remove()}
 }
 function updateButton(){
  const box=document.getElementById('bmShiftExceptionBox'),btn=document.getElementById('bmShiftExceptionBtn'),info=document.getElementById('bmShiftExceptionInfo');if(!box||!btn||!info)return;
  box.classList.toggle('on',exState.active);
  btn.textContent=exState.active?'FINALIZAR EXCEÇÃO':'ABRIR EXCEÇÃO';
  info.textContent=exState.active?'Turno ativo: '+brDate(exState.date)+' · histórico anterior restaurado.':'Após meia-noite, use para continuar o turno anterior sem zerar os lançamentos.';
 }
 async function toggle(){
  const btn=document.getElementById('bmShiftExceptionBtn');
  const action=exState.active?'close':'open';
  const msg=exState.active?'Finalizar a exceção e voltar para o dia atual?':'Abrir exceção e continuar todos os lançamentos do turno anterior?';
  if(!confirm(msg))return;
  if(btn){btn.disabled=true;btn.textContent=exState.active?'FINALIZANDO...':'ABRINDO...'}
  try{const ex=await adminRequest(action);apply(ex);toast(exState.active?'Exceção aberta. Turno anterior restaurado.':'Exceção finalizada.');await rerender()}catch(e){console.error('bm-shift-exception-toggle',e);toast(e?.message||'Não foi possível alterar a exceção.');updateButton()}finally{if(btn)btn.disabled=false}
 }
 async function rerender(){
  if(rendering||typeof renderAdmin!=='function'||document.querySelector('.login'))return;
  rendering=true;try{await renderAdmin()}finally{rendering=false}
 }
 async function refresh(forceRender=false){
  try{
   const ex=await stateRequest();const key=(ex?.ativa?'1':'0')+'|'+String(ex?.data_operacional||'');const changed=key!==lastKey;lastKey=key;apply(ex);
   if((changed||forceRender)&&document.querySelector('.admin-layout'))await rerender();
  }catch(e){console.warn('bm-shift-exception-state',e?.message||e)}
 }

 const bindBeforeException=bindAdmin;
 bindAdmin=function(){bindBeforeException();installButton()};

 setTimeout(()=>refresh(true),80);
 setTimeout(()=>refresh(false),800);
 setInterval(()=>refresh(false),15000);
 window.addEventListener('focus',()=>refresh(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false)});
 try{sb.auth.onAuthStateChange((event,session)=>{if(session&&['SIGNED_IN','TOKEN_REFRESHED'].includes(event))setTimeout(()=>refresh(true),80)})}catch(_){}
})();
