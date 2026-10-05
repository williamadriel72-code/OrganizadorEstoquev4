/*
 BORA MICHAEL — MENU + CONTINUIDADE AUTORITATIVA V4
 Mantém o menu original intacto a partir de um commit imutável e aplica a regra
 de continuidade somente depois que todas as demais patches da página já carregaram.
*/
(()=>{
 if(window.__bmMenuContinuityBootstrapV4)return;
 window.__bmMenuContinuityBootstrapV4=true;

 const LEGACY_MENU='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/385754d83906f20a938e5b29d8c1708124ec7544/scanner/assets/bora_web/patch_menu_v1.js';
 const STATE_API=U+'/functions/v1/bora-turno-excecao';
 const ADMIN_API=U+'/functions/v1/bora-turno-excecao-admin';
 const state=window.bmOperationalStateV4||{active:false,date:null,openedAt:null};
 window.bmOperationalStateV4=state;

 function spDate(){
  try{
   const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
   const o={};for(const p of ps)if(p.type!=='literal')o[p.type]=p.value;
   return `${o.year}-${o.month}-${o.day}`;
  }catch(_){return new Date().toISOString().slice(0,10)}
 }
 function opDate(){return state.active&&state.date?state.date:spDate()}
 function brDate(v){const p=String(v||'').split('-');return p.length===3?`${p[2]}/${p[1]}/${p[0]}`:String(v||'—')}
 function apply(ex){
  state.active=!!ex?.ativa;
  state.date=ex?.data_operacional||null;
  state.openedAt=ex?.aberta_em||null;
  window.bmOperationalStateV4=state;
  window.bmOperationalState=state;
  window.bmOperationalDate=opDate;
  if(typeof rider!=='undefined'&&rider&&state.active)rider.selected=opDate();
 }
 async function readState(){
  const r=await fetch(STATE_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'state'}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));
  if(!r.ok)throw Error(j.error||'Falha ao consultar a continuação do turno.');
  return j.exception||{};
 }
 async function adminAction(action){
  const session=(await sb.auth.getSession())?.data?.session;
  if(!session?.access_token)throw Error('Sessão administrativa expirada.');
  const r=await fetch(ADMIN_API,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+session.access_token},body:JSON.stringify({action}),cache:'no-store'});
  const j=await r.json().catch(()=>({}));
  if(!r.ok)throw Error(j.error||'Falha ao alterar a continuação.');
  return j.exception||{};
 }

 let baseToday=null,baseRiderDay=null,baseCurrentShift=null,baseInCurrentShift=null;
 let legacyTodayHtml=null,continuityTodayHtml=null;
 let baseEnsureJornada=null;

 function installCore(beforeLegacyTodayHtml){
  // A data do sistema passa a ser a data operacional enquanto a continuação estiver ativa.
  if(!baseToday&&typeof today==='function')baseToday=today;
  if(typeof today==='function')today=function(){return opDate()};

  // APK: sempre consulta a jornada do dia operacional enquanto a continuação estiver ativa.
  if(new URLSearchParams(location.search).get('app')==='motoboy'){
   if(!baseRiderDay&&typeof riderDay==='function')baseRiderDay=riderDay;
   if(baseRiderDay){
    riderDay=async function(date){
     return baseRiderDay(state.active?opDate():(date||opDate()));
    };
   }

   // A patch antiga possui um reset por relógio/data. Durante a continuação ele é proibido.
   if(!baseCurrentShift&&typeof bmRiderCurrentShift==='function')baseCurrentShift=bmRiderCurrentShift;
   if(baseCurrentShift){
    bmRiderCurrentShift=function(){return state.active?'17_24':baseCurrentShift()};
   }
   if(!baseInCurrentShift&&typeof bmRiderInCurrentShift==='function')baseInCurrentShift=bmRiderInCurrentShift;
   if(baseInCurrentShift){
    bmRiderInCurrentShift=function(value){return state.active?true:baseInCurrentShift(value)};
   }

   legacyTodayHtml=typeof todayHtml==='function'?todayHtml:null;
   continuityTodayHtml=beforeLegacyTodayHtml||legacyTodayHtml;
   if(legacyTodayHtml&&continuityTodayHtml){
    todayHtml=function(d){
     // ATIVO = não filtra por 00:00, 10:00 ou 17:00. Mostra a jornada operacional inteira.
     return state.active?continuityTodayHtml(d):legacyTodayHtml(d);
    };
   }
  }

  // PAINEL: carrega do banco a jornada inteira da data operacional, não a data do relógio.
  if(new URLSearchParams(location.search).get('app')!=='motoboy'&&typeof loadAdmin==='function'){
   loadAdmin=async function(){
    const target=opDate();
    const [m,b,j,i]=await Promise.all([
     sb.from('kh_motoboys').select('*').order('nome'),
     sb.from('kh_motoboy_bairros').select('*').eq('ativo',true).order('nome'),
     sb.from('kh_motoboy_jornadas').select('*').eq('data',target),
     sb.from('kh_motoboy_instalacoes').select('*').order('ultimo_acesso',{ascending:false}).limit(300)
    ]);
    for(const r of [m,b,j,i])if(r.error)throw r.error;
    const jornadas=j.data||[],ids=jornadas.map(x=>x.id);
    let entregas=[],saidas=[];
    if(ids.length){
     const [e,s]=await Promise.all([
      sb.from('kh_motoboy_entregas').select('*').in('jornada_id',ids).order('created_at'),
      sb.from('kh_motoboy_saidas').select('*').in('jornada_id',ids).order('created_at')
     ]);
     if(e.error)throw e.error;if(s.error)throw s.error;
     entregas=e.data||[];saidas=s.data||[];
    }
    Object.assign(adminState,{motoboys:typeof sortMotos==='function'?sortMotos(m.data||[]):m.data||[],bairros:b.data||[],jornadas,instalacoes:i.data||[],entregas,saidas});
    if(adminState.selectedMoto&&!adminState.motoboys.some(x=>String(x.id)===String(adminState.selectedMoto)))adminState.selectedMoto=null;
    window.bmLoadedOperationalDateV4=target;
   };

   if(!baseEnsureJornada&&typeof ensureJornada==='function')baseEnsureJornada=ensureJornada;
   if(baseEnsureJornada){
    ensureJornada=async function(motoboy_id,data){
     const target=state.active?opDate():(data||opDate());
     let j=(adminState.jornadas||[]).find(x=>String(x.motoboy_id)===String(motoboy_id)&&String(x.data)===String(target));
     if(j)return j;
     const q=await sb.from('kh_motoboy_jornadas').select('*').eq('motoboy_id',motoboy_id).eq('data',target).maybeSingle();
     if(q.error)throw q.error;
     if(q.data){adminState.jornadas.push(q.data);return q.data}
     return baseEnsureJornada(motoboy_id,target);
    };
   }
  }
 }

 function installBanner(){
  const old=document.getElementById('bmContinuityV4Banner');
  if(!state.active){old?.remove();return}
  const root=document.querySelector('.shell')||document.querySelector('.admin-main');
  if(!root)return;
  let b=old;
  if(!b){
   b=document.createElement('div');b.id='bmContinuityV4Banner';
   b.style.cssText='margin:10px 14px;padding:10px 12px;border:1px solid #d8871866;border-radius:12px;background:#2a1d0d;color:#f7cb78;font-size:11px;font-weight:900;line-height:1.35';
   const h=root.querySelector('.top,.admin-head');
   if(h?.nextSibling)root.insertBefore(b,h.nextSibling);else root.prepend(b);
  }
  b.textContent='CONTINUAÇÃO ATIVA · DIA OPERACIONAL '+brDate(state.date)+' · TODOS OS VALORES, COMANDAS, DIÁRIA E SAÍDAS CONTINUAM ATÉ ENCERRAR A CONTINUAÇÃO.';
 }

 function installAdminButton(){
  if(new URLSearchParams(location.search).get('app')==='motoboy')return;
  const head=document.querySelector('.admin-head');if(!head)return;
  let btn=document.getElementById('bmContinuityV4Button');
  if(!btn){
   btn=document.createElement('button');btn.id='bmContinuityV4Button';btn.type='button';
   btn.style.cssText='min-height:38px;border:1px solid #d8871888;border-radius:10px;padding:8px 11px;font-size:10px;font-weight:950;color:#fff;background:#9a5b0b;white-space:nowrap';
   const logout=document.getElementById('logout');if(logout)head.insertBefore(btn,logout);else head.appendChild(btn);
   btn.onclick=async()=>{
    if(btn.disabled)return;
    const action=state.active?'close':'open';
    const msg=state.active?'Encerrar a continuação? Ao confirmar, painel e APK passarão imediatamente para o novo dia e os valores da tela voltarão a zero.':'Continuar o turno anterior mantendo todos os valores e comandas?';
    if(!confirm(msg))return;
    btn.disabled=true;
    try{
     apply(await adminAction(action));
     if(typeof toast==='function')toast(state.active?'Continuação ativada.':'Continuação encerrada. Novo dia iniciado.');
     if(typeof renderAdmin==='function')await renderAdmin();
    }catch(e){console.error(e);if(typeof toast==='function')toast(e.message||'Falha na continuação.');else alert(e.message||e)}
    finally{btn.disabled=false;installAdminButton();installBanner()}
   };
  }
  btn.textContent=state.active?'ENCERRAR CONTINUAÇÃO · '+brDate(state.date):'CONTINUAR TURNO ANTERIOR';
  btn.style.background=state.active?'#a3202a':'#9a5b0b';
 }

 async function rerenderForStateChange(){
  if(new URLSearchParams(location.search).get('app')==='motoboy'){
   if(typeof rider!=='undefined'&&rider?.profile&&typeof renderRider==='function')await renderRider(rider.active||'Hoje');
  }else if(typeof renderAdmin==='function'&&!document.querySelector('.login')){
   await renderAdmin();
  }
 }

 let lastStateKey='';
 async function refreshState(force=false){
  try{
   const ex=await readState();
   const key=(ex?.ativa?'1':'0')+'|'+String(ex?.data_operacional||'');
   const changed=!!lastStateKey&&key!==lastStateKey;
   lastStateKey=key;
   apply(ex);installBanner();installAdminButton();
   if(force||changed)await rerenderForStateChange();
  }catch(e){console.warn('bm-continuity-v4-refresh',e?.message||e)}
 }

 const originalBoot=typeof boot==='function'?boot:null;
 const ready=(async()=>{
  // O fetch faz esta continuação rodar após as patches seguintes da página,
  // evitando que rider_notes/admin_refresh sobrescrevam a regra de continuidade.
  const [legacyText,ex]=await Promise.all([
   fetch(LEGACY_MENU,{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('menu legado '+r.status);return r.text()}),
   readState()
  ]);
  const beforeLegacyTodayHtml=typeof todayHtml==='function'?todayHtml:null;
  (0,eval)(legacyText);
  apply(ex);
  lastStateKey=(state.active?'1':'0')+'|'+String(state.date||'');
  installCore(beforeLegacyTodayHtml);
  installBanner();installAdminButton();
  return true;
 })().catch(e=>{console.error('bm-menu-continuity-v4',e);return false});

 window.__bmContinuityV4Ready=ready;
 if(originalBoot){
  boot=async function(){await ready;return originalBoot()};
 }

 const observer=new MutationObserver(()=>{installBanner();installAdminButton()});
 observer.observe(document.documentElement,{childList:true,subtree:true});
 setInterval(()=>refreshState(false),5000);
 window.addEventListener('focus',()=>refreshState(false));
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshState(false)});
})();
