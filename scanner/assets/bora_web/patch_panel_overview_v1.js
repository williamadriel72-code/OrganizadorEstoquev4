(()=>{
  if(new URLSearchParams(location.search).get('app')==='motoboy')return;
  if(window.__bmPanelOverviewV1)return;

  const money=v=>{try{return BRL(Number(v||0))}catch(_){return `R$ ${Number(v||0).toFixed(2).replace('.',',')}`}};
  const hms=v=>{try{return new Date(v).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}catch(_){return '--:--'}};
  const safe=s=>typeof esc==='function'?esc(String(s??'')):String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
  const ts=v=>{const n=new Date(v||0).getTime();return Number.isFinite(n)?n:0};
  const activeStatuses=new Set(['liberada','em_andamento','em_entrega','andamento']);
  const closedStatuses=new Set(['entregue','finalizada','finalizado','concluida','concluido','cancelada','cancelado','fechada','fechado']);

  function allRows(){
    const motos=adminState?.motoboys||[];
    const rows=[];
    for(const m of motos){
      const mid=m.id;
      const j=typeof jornadaOf==='function'?jornadaOf(mid):null;
      const es=(typeof entregasOf==='function'?entregasOf(mid):[]).filter(x=>x?.status!=='cancelada');
      const outs=(typeof saidasOf==='function'?saidasOf(mid):[]).filter(Boolean);
      rows.push({m,j,es,outs});
    }
    return rows;
  }

  function isInDelivery(row){
    if(row.outs.some(o=>activeStatuses.has(String(o.status||'').toLowerCase())))return true;
    return row.es.some(e=>e.saida_id&&!closedStatuses.has(String(e.status||'').toLowerCase()));
  }

  function currentTurnWindow(){
    const now=new Date();
    const start=new Date(now);start.setHours(0,0,0,0);
    const mins=now.getHours()*60+now.getMinutes();
    let label='Antes do turno';
    if(mins>=10*60&&mins<17*60){start.setHours(10,0,0,0);label='Turno 10:00 → 17:00'}
    else if(mins>=17*60){start.setHours(17,0,0,0);label='Turno após 17:00'}
    else {start.setHours(0,0,0,0);label='Antes das 10:00'}
    return {start:start.getTime(),end:now.getTime(),label};
  }

  function events(rows){
    const out=[];
    for(const r of rows){
      const nome=r.m?.nome||'Motoboy';
      if(r.j?.chegada_tipo==='nao_compareceu'){
        out.push({at:r.j.updated_at||r.j.created_at||r.j.data,label:`${nome} · Não compareceu`,kind:'red'});
      }else if(r.j?.chegada_at){
        out.push({at:r.j.chegada_at,label:`${nome} · Chegada registrada`,kind:'blue'});
      }
      for(const o of r.outs){
        const n=Number(o.numero_sequencial)||0;
        out.push({at:o.horario_saida||o.created_at,label:`${nome} · Saída ${n?String(n).padStart(2,'0'):'liberada'}`,kind:'green'});
      }
      for(const e of r.es){
        const nota=e.nota_numero?` ${e.nota_numero}`:'';
        let label=`${nome} · Comanda${nota} lançada`;
        const st=String(e.status||'').toLowerCase();
        if(['entregue','finalizada','finalizado','concluida','concluido'].includes(st))label=`${nome} · Entrega finalizada${nota}`;
        out.push({at:e.updated_at||e.created_at,label,kind:st==='entregue'||st==='finalizada'?'green':'blue'});
      }
    }
    return out.filter(x=>ts(x.at)>0).sort((a,b)=>ts(b.at)-ts(a.at));
  }

  function overviewHtml(){
    const rows=allRows();
    const activeToday=rows.filter(r=>r.j?.chegada_at&&r.j?.chegada_tipo!=='nao_compareceu').length;
    const onDelivery=rows.filter(isInDelivery).length;
    const noShow=rows.filter(r=>r.j?.chegada_tipo==='nao_compareceu').length;
    const outings=rows.reduce((a,r)=>a+r.outs.length,0);
    const deliveries=rows.reduce((a,r)=>a+r.es.length,0);
    const deliveryValue=rows.reduce((a,r)=>a+r.es.reduce((s,e)=>s+Number(e.valor||0),0),0);
    const ev=events(rows);
    const turn=currentTurnWindow();
    const inTurn=t=>{const n=ts(t);return n>=turn.start&&n<=turn.end};
    const turnOutings=rows.reduce((a,r)=>a+r.outs.filter(o=>inTurn(o.horario_saida||o.created_at)).length,0);
    const turnDeliveries=rows.reduce((a,r)=>a+r.es.filter(e=>inTurn(e.created_at||e.updated_at)).length,0);
    const turnValue=rows.reduce((a,r)=>a+r.es.filter(e=>inTurn(e.created_at||e.updated_at)).reduce((s,e)=>s+Number(e.valor||0),0),0);
    const turnActive=new Set();
    for(const r of rows){
      if((r.j?.chegada_at&&inTurn(r.j.chegada_at))||r.outs.some(o=>inTurn(o.horario_saida||o.created_at))||r.es.some(e=>inTurn(e.created_at||e.updated_at)))turnActive.add(String(r.m.id));
    }

    const recent=ev.slice(0,8);
    const recentHtml=recent.length?recent.map(x=>`<div class="bm-ov-event"><span class="bm-ov-dot ${x.kind||''}"></span><time>${hms(x.at)}</time><div>${safe(x.label)}</div></div>`).join(''):'<div class="bm-ov-empty">Nenhuma movimentação registrada hoje.</div>';

    return `<section class="bm-overview" id="bmPanelOverview">
      <div class="bm-ov-head">
        <div><span>PAINEL ADMINISTRATIVO</span><h2>Visão geral do painel</h2><p>Acompanhe o movimento do dia sem precisar abrir um motoboy.</p></div>
        <div class="bm-ov-clock"><b id="bmOverviewClock">${new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b><small>${new Date().toLocaleDateString('pt-BR')}</small></div>
      </div>

      <div class="bm-ov-stats">
        <div class="bm-ov-stat green"><small>Motoboys ativos hoje</small><strong>${activeToday}</strong><em>com chegada registrada</em></div>
        <div class="bm-ov-stat blue"><small>Em entrega</small><strong>${onDelivery}</strong><em>saída em andamento</em></div>
        <div class="bm-ov-stat red"><small>Não compareceram</small><strong>${noShow}</strong><em>diária zerada</em></div>
        <div class="bm-ov-stat"><small>Saídas realizadas</small><strong>${outings}</strong><em>registradas hoje</em></div>
        <div class="bm-ov-stat"><small>Comandas do dia</small><strong>${deliveries}</strong><em>lançamentos válidos</em></div>
        <div class="bm-ov-stat green"><small>Valor em entregas</small><strong>${money(deliveryValue)}</strong><em>total das taxas</em></div>
      </div>

      <div class="bm-ov-grid">
        <article class="bm-ov-card bm-ov-movements">
          <div class="bm-ov-card-head"><div><span>MOVIMENTAÇÕES</span><h3>Atividade recente</h3></div><b>${recent.length}</b></div>
          <div class="bm-ov-events">${recentHtml}</div>
        </article>

        <article class="bm-ov-card bm-ov-turn">
          <div class="bm-ov-card-head"><div><span>TURNO ATUAL</span><h3>${safe(turn.label)}</h3></div><i></i></div>
          <div class="bm-ov-turn-grid">
            <div><small>Entregas</small><strong>${turnDeliveries}</strong></div>
            <div><small>Saídas</small><strong>${turnOutings}</strong></div>
            <div><small>Valor acumulado</small><strong>${money(turnValue)}</strong></div>
            <div><small>Motoboys ativos</small><strong>${turnActive.size}</strong></div>
          </div>
          <div class="bm-ov-turn-note">Às 17:00 começa uma nova contagem de turno sem apagar o histórico anterior.</div>
        </article>
      </div>
    </section>`;
  }

  function install(){
    if(window.__bmPanelOverviewV1)return true;
    if(!window.__bmPanelReferenceV1||typeof selectedPanel!=='function')return false;
    window.__bmPanelOverviewV1=true;

    if(!document.getElementById('bmPanelOverviewStyle')){
      const style=document.createElement('style');
      style.id='bmPanelOverviewStyle';
      style.textContent=`
      .bm-overview{min-width:0;padding:3px 0 30px}
      .bm-ov-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:16px;padding:20px 22px;border:1px solid rgba(255,255,255,.08);border-radius:20px;background:linear-gradient(135deg,#10171f,#0a1016);box-shadow:0 18px 55px rgba(0,0,0,.22)}
      .bm-ov-head span,.bm-ov-card-head span{font-size:9px;font-weight:950;letter-spacing:.13em;color:#ff6678}.bm-ov-head h2{margin:5px 0 5px;font-size:27px}.bm-ov-head p{margin:0;color:#9da8b4;font-size:12px}
      .bm-ov-clock{min-width:105px;text-align:right}.bm-ov-clock b{display:block;font-size:25px}.bm-ov-clock small{color:#8f9aa6}
      .bm-ov-stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px;margin-bottom:12px}
      .bm-ov-stat{min-height:92px;padding:14px;border-radius:16px;border:1px solid rgba(255,255,255,.075);background:linear-gradient(145deg,#111922,#0c1219)}
      .bm-ov-stat small{display:block;color:#9da8b4;font-size:9px;font-weight:900;text-transform:uppercase;letter-spacing:.07em}.bm-ov-stat strong{display:block;margin-top:8px;font-size:24px;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-ov-stat em{display:block;margin-top:5px;color:#6f7c88;font-size:10px;font-style:normal}
      .bm-ov-stat.green strong{color:#58e89d}.bm-ov-stat.blue strong{color:#79b8ff}.bm-ov-stat.red strong{color:#ff687b}
      .bm-ov-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(330px,.75fr);gap:12px}
      .bm-ov-card{min-height:270px;padding:17px 18px;border-radius:18px;border:1px solid rgba(255,255,255,.075);background:linear-gradient(145deg,#10171f,#0b1117);box-shadow:0 18px 55px rgba(0,0,0,.16)}
      .bm-ov-card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding-bottom:12px;border-bottom:1px solid rgba(255,255,255,.06)}.bm-ov-card-head h3{margin:4px 0 0;font-size:18px}.bm-ov-card-head>b{display:grid;place-items:center;min-width:28px;height:28px;border-radius:9px;background:#18222d;color:#8fa2b4}.bm-ov-card-head i{width:9px;height:9px;margin-top:9px;border-radius:50%;background:#2bd180;box-shadow:0 0 14px #2bd180}
      .bm-ov-events{margin-top:7px}.bm-ov-event{display:grid;grid-template-columns:9px 48px minmax(0,1fr);align-items:center;gap:9px;padding:10px 3px;border-bottom:1px solid rgba(255,255,255,.045);font-size:12px}.bm-ov-event time{color:#8f9ca9;font-weight:800}.bm-ov-event div{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-ov-dot{width:7px;height:7px;border-radius:50%;background:#8794a1}.bm-ov-dot.green{background:#2bd180;box-shadow:0 0 8px #2bd18066}.bm-ov-dot.red{background:#ff4359}.bm-ov-dot.blue{background:#4d9cff}.bm-ov-empty{padding:28px 4px;color:#7f8b96;text-align:center}
      .bm-ov-turn-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.bm-ov-turn-grid>div{padding:14px;border-radius:14px;background:#0c131a;border:1px solid rgba(255,255,255,.065)}.bm-ov-turn-grid small{display:block;color:#8f9ca9;font-size:10px;font-weight:900;text-transform:uppercase}.bm-ov-turn-grid strong{display:block;margin-top:6px;font-size:21px;color:#fff}.bm-ov-turn-note{margin-top:13px;padding:11px 12px;border-radius:12px;background:rgba(77,156,255,.08);border:1px solid rgba(77,156,255,.16);color:#9eb8d4;font-size:11px;line-height:1.45}
      @media(max-width:1300px){.bm-ov-stats{grid-template-columns:repeat(3,minmax(0,1fr))}.bm-ov-grid{grid-template-columns:1fr 1fr}}
      @media(max-width:950px){.bm-ov-grid{grid-template-columns:1fr}.bm-ov-stats{grid-template-columns:repeat(2,minmax(0,1fr))}}
      @media(max-width:600px){.bm-ov-head{padding:16px}.bm-ov-head h2{font-size:22px}.bm-ov-clock{display:none}.bm-ov-stats{grid-template-columns:1fr 1fr}.bm-ov-stat{min-height:80px}.bm-ov-grid{grid-template-columns:1fr}.bm-ov-turn-grid{grid-template-columns:1fr 1fr}}
      `;
      document.head.appendChild(style);
    }

    const prevSelectedPanel=selectedPanel;
    selectedPanel=function(){
      if(!adminState?.selectedMoto)return overviewHtml();
      return prevSelectedPanel();
    };

    const tick=()=>{
      const el=document.getElementById('bmOverviewClock');
      if(el)el.textContent=new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
    };
    setInterval(tick,30000);
    tick();
    if(!adminState?.selectedMoto&&typeof drawAdmin==='function')setTimeout(()=>drawAdmin(),0);
    return true;
  }

  let tries=0;
  const timer=setInterval(()=>{
    tries++;
    if(install()||tries>80)clearInterval(timer);
  },100);
})();
