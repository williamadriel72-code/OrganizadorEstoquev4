(()=>{
 if(window.__bmSaturdayMorningRateV1)return;
 window.__bmSaturdayMorningRateV1=true;

 function installArrivalRule(){
  if(typeof bmArrivalRule!=='function'||typeof today!=='function')return false;
  bmArrivalRule=function(timeStr){
   const parts=String(timeStr||'').split(':').map(Number);
   const hh=parts[0],mm=parts[1];
   if(!Number.isInteger(hh)||!Number.isInteger(mm)||hh<0||hh>23||mm<0||mm>59)return null;
   const mins=hh*60+mm;
   const [y,mo,d]=today().split('-').map(Number);
   const dow=new Date(y,mo-1,d,12,0,0).getDay();
   const sun=dow===0;

   if(mins>=10*60&&mins<=15*60){
    if(dow>=1&&dow<=6)return {tipo:'antes_18',valor:48,manual:false,faixa:'10_15'};
    if(sun)return {tipo:'antes_18',valor:55,manual:false,faixa:'10_15'};
   }
   if(mins<=18*60)return {tipo:'antes_18',valor:sun?90:70,manual:false};
   if(mins<19*60)return {tipo:'entre_18_19',valor:sun?80:60,manual:false};
   return {tipo:'apos_19_manual',valor:null,manual:true};
  };
  return true;
 }
 installArrivalRule();
 setTimeout(installArrivalRule,250);
 setTimeout(installArrivalRule,900);

 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 if(!riderMode||typeof renderRider!=='function')return;
 const previousRenderRider=renderRider;
 renderRider=async function(active='Hoje'){
  const out=await previousRenderRider(active);
  if(active==='Taxas'){
   const reports=document.querySelector('.shell .reports');
   if(reports){
    reports.innerHTML=`
     <div class="notice" style="border-color:#22c55e55"><strong>REGRA ATUAL:</strong> manhã de segunda a sábado — R$ 48,00 · domingo de manhã — R$ 55,00.</div>
     <div class="report-card">
      <b>Segunda a sábado</b>
      <div class="row-sub" style="margin-top:8px">10:00 às 15:00 — R$ 48,00</div>
      <div class="row-sub">15:01 às 18:00 — R$ 70,00</div>
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
