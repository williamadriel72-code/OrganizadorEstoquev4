/* Regras e informações de diária exibidas no APK do motoboy. */
(function bmInstallRiderRateRulesV1(){
  if(window.__bmRiderRateRulesV1)return;
  window.__bmRiderRateRulesV1=true;

  /* Mantém 18:00 no valor anterior e troca a partir de 18:01. */
  if(typeof bmArrivalRule==='function'){
    const previous=bmArrivalRule;
    bmArrivalRule=function(timeStr){
      const parts=String(timeStr||'').split(':').map(Number);
      const hh=parts[0],mm=parts[1];
      if(Number.isInteger(hh)&&Number.isInteger(mm)&&hh>=0&&hh<=23&&mm>=0&&mm<=59){
        const mins=hh*60+mm;
        if(mins===18*60){
          const [y,mo,d]=today().split('-').map(Number);
          const sun=new Date(y,mo-1,d,12,0,0).getDay()===0;
          return {tipo:'antes_18',valor:sun?90:70,manual:false};
        }
      }
      return previous(timeStr);
    };
  }

  /* Altera somente a tela Taxas do APK, sem mexer no painel admin. */
  if(typeof appMode!=='undefined'&&appMode&&typeof renderRider==='function'){
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
  }
})();
