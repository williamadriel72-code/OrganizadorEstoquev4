(function bmLoadOriginalCompact(){
 const url='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/f00beb3658d689e506fd633b1dc3bcedbb70a293/scanner/assets/bora_web/patch_admin_header_compact_v1.js?v='+Date.now();
 fetch(url,{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('compact '+r.status);return r.text()}).then(code=>{const s=document.createElement('script');s.dataset.bmPatch='admin-compact-original';s.textContent=code;document.head.appendChild(s)}).catch(e=>console.error('bm-load-admin-compact',e));
})();

(function bmShiftExceptionV1(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmShiftExceptionV1)return;window.__bmShiftExceptionV1=true;

 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const ADMIN_API=U+'/functions/v1/bora-turno-excecao-admin';
 const originalToday=typeof today==='function'?today:null;
 const originalLoadAdmin=loadAdmin;
 const originalEnsureJornada=ensureJornada;
 const originalBoot=typeof boot==='function'?boot:null;
 const exState={active:false,date:null,openedAt:null};
 let rendering=false,lastKey='';
 window.bmShiftExceptionState=exState;

 function realToday(){return originalToday?originalToday():new Date().toISOString().slice(0,10)}
 function operationalDate(){return exState.active&&exState.date?exState.date:realToday()}
 function brDate(v){if(!v)return '—';const p=String(v).split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v)}

 loadAdmin=async function(){
  const t=operationalDate();
  const [m,b,j,i]=await Promise.all([
   sb.from('kh_motoboys').select('*').order('nome'),
   sb.from('kh_motoboy_bairros').select('*').eq('ativo',true).order('nome'),
   sb.from('kh_motoboy_jornadas').select('*').eq('data',t),
   sb.from('kh_motoboy_instalacoes').select('*').order('ultimo_acesso',{ascending:false}).limit(300)
  ]);
  for(const r of [m,b,j,i])if(r.error)throw r.error;
  const jornadas=j.data||[],jids=jornadas.map(x=>x.id);
  let entregas=[],saidas=[];
  if(jids.length){
   const [e,s]=await Promise.all([
    sb.from('kh_motoboy_entregas').select('*').in('jornada_id',jids).order('created_at'),
    sb.from('kh_motoboy_saidas').select('*').in('jornada_id',jids).order('created_at')
   ]);
   if(e.error)throw e.error;if(s.error)throw s.error;entregas=e.data||[];saidas=s.data||[];
  }
  Object.assign(adminState,{motoboys:sortMotos(m.data||[]),bairros:b.data||[],jornadas,instalacoes:i.data||[],entregas,saidas});
  if(adminState.selectedMoto&&!adminState.motoboys.some(x=>x.id===adminState.selectedMoto))adminState.selectedMoto=null;
 };
 ensureJornada=async function(motoboy_id,data){return originalEnsureJornada(motoboy_id,data||operationalDate())};

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
 function apply(ex){exState.active=!!ex?.ativa;exState.date=ex?.data_operacional||null;exState.openedAt=ex?.aberta_em||null;updateButtons()}

 function css(){
  if(document.getElementById('bmShiftExceptionStyle'))return;
  const s=document.createElement('style');s.id='bmShiftExceptionStyle';s.textContent=`
  #bmShiftExceptionBox{margin-top:10px;padding:10px;border:1px solid rgba(255,255,255,.10);border-radius:12px;background:#15191d}
  #bmShiftExceptionBox.on{border-color:#f0a63b88;background:#2a1d0d}
  #bmShiftExceptionBox small{display:block;color:#9da5ad;font-size:10px;line-height:1.35;margin:0 0 7px}
  #bmShiftExceptionBox.on small{color:#f4c56f}
  #bmShiftExceptionBtn,#bmShiftExceptionTopBtn{min-height:38px;border:0;border-radius:10px;background:#d88718;color:#fff;font-weight:900;cursor:pointer;padding:9px 11px}
  #bmShiftExceptionBtn{width:100%}#bmShiftExceptionBox.on #bmShiftExceptionBtn,#bmShiftExceptionTopBtn.on{background:#b42323}
  #bmShiftExceptionBtn:disabled,#bmShiftExceptionTopBtn:disabled{opacity:.6;cursor:wait}
  #bmShiftExceptionTopBtn{margin-right:8px;font-size:11px;white-space:nowrap}
  .bm-exception-banner{margin:0 0 12px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:12px;font-weight:850}
  @media(max-width:800px){#bmShiftExceptionBox{display:none}#bmShiftExceptionTopBtn{font-size:10px;padding:8px;min-height:34px}}
  `;document.head.appendChild(s);
 }
 function installButton(){
  css();
  const side=document.querySelector('.sidebar');
  if(side){let box=document.getElementById('bmShiftExceptionBox');if(!box){box=document.createElement('div');box.id='bmShiftExceptionBox';box.innerHTML='<small id="bmShiftExceptionInfo"></small><button id="bmShiftExceptionBtn" type="button"></button>';side.appendChild(box);box.querySelector('#bmShiftExceptionBtn').onclick=toggle}}
  const head=document.querySelector('.admin-head');
  if(head&&!document.getElementById('bmShiftExceptionTopBtn')){const btn=document.createElement('button');btn.id='bmShiftExceptionTopBtn';btn.type='button';btn.onclick=toggle;const logout=document.getElementById('logout');if(logout)head.insertBefore(btn,logout);else head.appendChild(btn)}
  updateButtons();
  const main=document.querySelector('.admin-main');
  if(main){let banner=document.getElementById('bmShiftExceptionBanner');if(exState.active){if(!banner){banner=document.createElement('div');banner.id='bmShiftExceptionBanner';banner.className='bm-exception-banner';const h=main.querySelector('.admin-head');if(h?.nextSibling)main.insertBefore(banner,h.nextSibling);else main.prepend(banner)}banner.textContent='EXCEÇÃO ABERTA — CONTINUAÇÃO DO TURNO DE '+brDate(exState.date)+' · os lançamentos e novas notas permanecem neste dia operacional.'}else banner?.remove()}
 }
 function updateButtons(){
  const box=document.getElementById('bmShiftExceptionBox'),btn=document.getElementById('bmShiftExceptionBtn'),top=document.getElementById('bmShiftExceptionTopBtn'),info=document.getElementById('bmShiftExceptionInfo');
  if(box)box.classList.toggle('on',exState.active);
  if(btn)btn.textContent=exState.active?'FINALIZAR EXCEÇÃO':'ABRIR EXCEÇÃO';
  if(top){top.textContent=exState.active?'EXCEÇÃO '+brDate(exState.date):'ABRIR EXCEÇÃO';top.classList.toggle('on',exState.active)}
  if(info)info.textContent=exState.active?'Turno ativo: '+brDate(exState.date)+' · histórico anterior restaurado.':'Após meia-noite, use para continuar o turno anterior sem zerar os lançamentos.';
 }
 async function toggle(){
  const buttons=[document.getElementById('bmShiftExceptionBtn'),document.getElementById('bmShiftExceptionTopBtn')].filter(Boolean);
  const action=exState.active?'close':'open';
  const msg=exState.active?'Finalizar a exceção e voltar para o dia atual?':'Abrir exceção e continuar todos os lançamentos do turno anterior?';
  if(!confirm(msg))return;
  buttons.forEach(b=>b.disabled=true);
  try{const ex=await adminRequest(action);apply(ex);toast(exState.active?'Exceção aberta. Turno anterior restaurado.':'Exceção finalizada.');await rerender()}catch(e){console.error('bm-shift-exception-toggle',e);toast(e?.message||'Não foi possível alterar a exceção.');updateButtons()}finally{buttons.forEach(b=>b.disabled=false)}
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
 if(originalBoot)boot=async function(){await refresh(false);return originalBoot()};

 setTimeout(()=>refresh(false),800);
 setInterval(()=>refresh(false),15000);
 window.addEventListener('focus',()=>refresh(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false)});
 try{sb.auth.onAuthStateChange((event,session)=>{if(session&&['SIGNED_IN','TOKEN_REFRESHED'].includes(event))setTimeout(()=>refresh(true),80)})}catch(_){}
})();
