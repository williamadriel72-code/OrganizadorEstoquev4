async function bmRefreshAdminPanel(){
 const btn=document.getElementById('bmRefreshAdmin');
 const old=btn?.textContent||'↻ ATUALIZAR';
 if(btn){btn.disabled=true;btn.textContent='ATUALIZANDO...'}
 try{
  const selected=adminState.selectedMoto;
  const bairro=adminState.selectedBairro;
  await loadAdmin();
  if(selected&&adminState.motoboys.some(m=>m.id===selected))adminState.selectedMoto=selected;
  adminState.selectedBairro=bairro;
  drawAdmin();
  toast('Lista de motoboys atualizada.');
 }catch(e){
  console.error('admin-refresh',e);
  toast(e?.message||'Não foi possível atualizar agora.');
  if(btn){btn.disabled=false;btn.textContent=old}
 }
}

function bmInstallAdminRefreshButton(){
 const head=document.querySelector('.moto-list-head');
 if(!head||document.getElementById('bmRefreshAdmin'))return;
 const btn=document.createElement('button');
 btn.id='bmRefreshAdmin';
 btn.type='button';
 btn.className='btn secondary small';
 btn.style.marginLeft='auto';
 btn.style.padding='9px 12px';
 btn.style.fontWeight='800';
 btn.textContent='↻ ATUALIZAR';
 btn.addEventListener('click',bmRefreshAdminPanel);
 const register=document.getElementById('bmRegisterMoto');
 if(register){
  register.style.marginLeft='8px';
  head.insertBefore(btn,register);
 }else{
  head.appendChild(btn);
 }
}

const bmBindAdminBeforeRefresh=bindAdmin;
bindAdmin=function(){
 bmBindAdminBeforeRefresh();
 bmInstallAdminRefreshButton();
};

if(new URLSearchParams(location.search).get('app')!=='motoboy'&&!document.getElementById('bmPanelOverviewLoader')){
 const s=document.createElement('script');
 s.id='bmPanelOverviewLoader';
 s.src='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_panel_overview_v1.js?v='+Date.now();
 document.head.appendChild(s);
}
