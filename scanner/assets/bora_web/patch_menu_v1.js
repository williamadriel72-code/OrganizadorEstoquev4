function bmInstallMainMenuButton(){
 const head=document.querySelector('.admin-head');
 if(!head||document.getElementById('bmMainMenu'))return;
 const logout=document.getElementById('logout');
 const btn=document.createElement('button');
 btn.id='bmMainMenu';
 btn.className='btn secondary small';
 btn.textContent='← MENU PRINCIPAL';
 btn.style.marginLeft='auto';
 btn.addEventListener('click',()=>{
  adminState.selectedMoto=null;
  adminState.selectedBairro=null;
  drawAdmin();
 });
 if(logout){
  logout.style.marginLeft='8px';
  head.insertBefore(btn,logout);
 }else{
  head.appendChild(btn);
 }
}

const bmBindAdminWithMainMenu=bindAdmin;
bindAdmin=function(){
 bmBindAdminWithMainMenu();
 bmInstallMainMenuButton();
};

/* Nova faixa de diária: 10:00 até 15:00.
   Segunda a sexta: R$ 48,00 | Domingo: R$ 55,00.
   Sábado e demais horários continuam usando as regras anteriores. */
if(typeof bmArrivalRule==='function'){
 const bmArrivalRuleBeforeMidday=bmArrivalRule;
 bmArrivalRule=function(timeStr){
  const parts=String(timeStr||'').split(':').map(Number);
  const hh=parts[0],mm=parts[1];
  if(Number.isInteger(hh)&&Number.isInteger(mm)&&hh>=0&&hh<=23&&mm>=0&&mm<=59){
   const mins=hh*60+mm;
   if(mins>=10*60&&mins<=15*60){
    const [y,mo,d]=today().split('-').map(Number);
    const dow=new Date(y,mo-1,d,12,0,0).getDay();
    if(dow>=1&&dow<=5)return {tipo:'antes_18',valor:48,manual:false,faixa:'10_15'};
    if(dow===0)return {tipo:'antes_18',valor:55,manual:false,faixa:'10_15'};
   }
  }
  return bmArrivalRuleBeforeMidday(timeStr);
 };
}

/* Interface premium inspirada na referência: carregada SOMENTE no painel administrativo. */
if(new URLSearchParams(location.search).get('app')!=='motoboy'){
 fetch('https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_panel_reference_v1.js?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('panel-reference',e?.message||e));
}

/* A interface nova esconde a sidebar antiga; mantenha o botão de relatórios visível no cabeçalho. */
(function bmKeepDailyReportsVisible(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmReportsVisibleObserver)return;
 window.__bmReportsVisibleObserver=true;
 const move=()=>{
  const btn=document.getElementById('bmDailyReportsBtn');
  const head=document.querySelector('.admin-head');
  if(!btn||!head)return;
  const logout=document.getElementById('logout');
  if(btn.parentElement!==head){
   btn.className='btn secondary small';
   btn.textContent='RELATÓRIOS DIÁRIOS';
   btn.style.width='auto';
   btn.style.margin='0 8px 0 auto';
   if(logout)head.insertBefore(btn,logout);else head.appendChild(btn);
  }
 };
 const obs=new MutationObserver(move);
 obs.observe(document.documentElement,{childList:true,subtree:true});
 setTimeout(move,0);setTimeout(move,250);setTimeout(move,900);
})();
