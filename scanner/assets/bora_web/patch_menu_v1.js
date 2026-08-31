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

/* Regras consolidadas de diária por horário.
   10:00-15:00: seg-sex R$48, domingo R$55.
   Fora dessa faixa: até 18:00 inclusive mantém valor anterior.
   A partir de 18:01 entra a faixa reduzida até 18:59.
   A partir de 19:00 permanece editável. */
if(typeof bmArrivalRule==='function'){
 bmArrivalRule=function(timeStr){
  const parts=String(timeStr||'').split(':').map(Number);
  const hh=parts[0],mm=parts[1];
  if(!Number.isInteger(hh)||!Number.isInteger(mm)||hh<0||hh>23||mm<0||mm>59)return null;
  const mins=hh*60+mm;
  const [y,mo,d]=today().split('-').map(Number);
  const dow=new Date(y,mo-1,d,12,0,0).getDay();
  const sun=dow===0;

  if(mins>=10*60&&mins<=15*60){
   if(dow>=1&&dow<=5)return {tipo:'antes_18',valor:48,manual:false,faixa:'10_15'};
   if(sun)return {tipo:'antes_18',valor:55,manual:false,faixa:'10_15'};
  }

  if(mins<=18*60)return {tipo:'antes_18',valor:sun?90:70,manual:false};
  if(mins<19*60)return {tipo:'entre_18_19',valor:sun?80:60,manual:false};
  return {tipo:'apos_19_manual',valor:null,manual:true};
 };
}

/* Atualiza diretamente a tela Taxas do APK usando esta patch, que já é carregada
   pela versão instalada do aplicativo. Assim não depende de uma nova lista de patches. */
(function bmKeepRiderRatesCurrent(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmRiderRatesFromMenuV2)return;
 window.__bmRiderRatesFromMenuV2=true;
 if(typeof renderRider!=='function')return;
 const previousRenderRider=renderRider;
 renderRider=async function(active='Hoje'){
  const out=await previousRenderRider(active);
  if(active==='Taxas'){
   const reports=document.querySelector('.shell .reports');
   if(reports){
    reports.innerHTML=`
     <div class="report-card">
      <b>Segunda a sexta</b>
      <div class="row-sub" style="margin-top:8px">10:00 às 15:00 — R$ 48,00</div>
      <div class="row-sub">15:01 às 18:00 — R$ 70,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 60,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="report-card">
      <b>Sábado</b>
      <div class="row-sub" style="margin-top:8px">Até 18:00 — R$ 70,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 60,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="report-card">
      <b>Domingo</b>
      <div class="row-sub" style="margin-top:8px">10:00 às 15:00 — R$ 55,00</div>
      <div class="row-sub">15:01 às 18:00 — R$ 90,00</div>
      <div class="row-sub">18:01 às 18:59 — R$ 80,00</div>
      <div class="row-sub">A partir das 19:00 — valor editável</div>
     </div>
     <div class="notice"><strong>Taxa Integral:</strong> o valor muda conforme a nota e nunca vira preço fixo do bairro.</div>`;
   }
  }
  return out;
 };
})();

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

/* Relatórios automáticos por turno, SOMENTE no painel. O APK não recebe esta interface. */
if(new URLSearchParams(location.search).get('app')!=='motoboy'){
 fetch('https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/patch_shift_reports_v1.js?v='+Date.now(),{cache:'no-store'})
  .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
  .then(code=>(0,eval)(code))
  .catch(e=>console.warn('shift-reports',e?.message||e));
}
