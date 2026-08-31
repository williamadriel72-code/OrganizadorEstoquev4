(()=>{
  if(new URLSearchParams(location.search).get('app')==='motoboy') return;
  if(window.__bmPanelReferenceV1) return;
  window.__bmPanelReferenceV1=true;

  const ASSET='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/chatgpt-bora-michael-hi-hi/scanner/assets/bora_web/motoboy-saida-mobile.webp';

  if(!document.getElementById('bmPanelReferenceStyle')){
    const s=document.createElement('style');
    s.id='bmPanelReferenceStyle';
    s.textContent=`
    body.bm-reference-admin{--bm-red:#ff243b;--bm-red2:#b70f25;--bm-deep:#070b0f;--bm-card:#0f151c;--bm-card2:#131a22;--bm-line:#ffffff14;--bm-green:#28d17c;background:#06090d!important}
    body.bm-reference-admin .app{background:radial-gradient(circle at 88% 14%,rgba(255,36,59,.13),transparent 28%),radial-gradient(circle at 60% 80%,rgba(12,92,158,.08),transparent 36%),#06090d!important}
    body.bm-reference-admin .sidebar{display:none!important}
    body.bm-reference-admin .admin-layout{display:block!important;min-height:100vh;background:transparent!important}
    body.bm-reference-admin .admin-main{max-width:1700px;margin:0 auto;padding:24px 24px 80px!important}
    body.bm-reference-admin .admin-head{min-height:66px;margin-bottom:18px!important;padding:0 2px;border-bottom:1px solid rgba(255,255,255,.04)}
    body.bm-reference-admin .admin-head h2{font-size:29px!important;letter-spacing:-.02em}
    body.bm-reference-admin .admin-head .date{font-size:14px;color:#b6c0cc!important}
    body.bm-reference-admin .admin-head .btn{background:#18212b!important;border:1px solid rgba(255,255,255,.08)!important;color:#fff!important;box-shadow:0 7px 18px rgba(0,0,0,.2)}
    body.bm-reference-admin #bmMainMenu{margin-left:auto!important}
    body.bm-reference-admin #logout{margin-left:8px!important}

    body.bm-reference-admin .moto-workspace{grid-template-columns:minmax(255px,300px) minmax(0,1fr)!important;gap:18px!important;align-items:start!important}
    body.bm-reference-admin .moto-list{position:sticky!important;top:14px!important;padding:12px!important;border-radius:20px!important;background:linear-gradient(180deg,#0d1319,#091016)!important;border:1px solid rgba(255,255,255,.08)!important;box-shadow:0 20px 60px rgba(0,0,0,.28)}
    body.bm-reference-admin .moto-list-head{padding:7px 7px 12px!important}
    body.bm-reference-admin .moto-list-head b{font-size:16px!important}
    body.bm-reference-admin .moto-count{color:#aab4bf!important}
    body.bm-reference-admin .moto-item{min-height:61px;margin:6px 0!important;padding:10px 11px!important;border-radius:14px!important;background:#0d141b!important;border:1px solid rgba(255,255,255,.06)!important;transition:.18s ease!important}
    body.bm-reference-admin .moto-item:hover{background:#111b24!important;border-color:rgba(255,255,255,.14)!important;transform:translateX(2px)}
    body.bm-reference-admin .moto-item.active{background:linear-gradient(90deg,rgba(255,36,59,.12),rgba(255,36,59,.035))!important;border-color:rgba(255,36,59,.9)!important;box-shadow:0 0 0 1px rgba(255,36,59,.18),0 0 28px rgba(255,36,59,.12)!important}
    body.bm-reference-admin .moto-item-name{font-size:14px!important}
    body.bm-reference-admin .moto-item-meta{color:#9aa6b2!important}
    body.bm-reference-admin .moto-item-value{color:#58e89d!important}

    body.bm-reference-admin .moto-detail{min-width:0}
    body.bm-reference-admin .moto-hero{min-height:92px!important;margin:0 0 12px!important;padding:15px 16px!important;align-items:center!important;border:1px solid rgba(255,255,255,.08)!important;border-radius:19px!important;background:linear-gradient(135deg,#0f161e,#0a1016)!important;box-shadow:0 18px 55px rgba(0,0,0,.22);overflow:hidden!important}
    body.bm-reference-admin .moto-hero>div:first-child{position:relative;z-index:2;padding-left:72px;min-height:58px;display:flex;flex-direction:column;justify-content:center}
    body.bm-reference-admin .moto-hero h2{font-size:28px!important;margin:0 0 5px!important}
    body.bm-reference-admin .moto-hero .row-sub{font-size:13px!important;color:#c0c8d1!important}
    body.bm-reference-admin #deleteMoto{position:relative;z-index:3;background:rgba(127,29,29,.25)!important;border-color:rgba(255,36,59,.55)!important;color:#ff8796!important}
    .bm-ref-avatar{position:absolute;left:0;top:0;width:58px;height:58px;border-radius:50%;display:grid;place-items:center;background:radial-gradient(circle at 40% 30%,#ff4b5f,#9c0b20 65%,#39040c);border:2px solid rgba(255,65,86,.7);box-shadow:0 0 22px rgba(255,36,59,.24);font-size:22px;font-weight:950;color:#fff;text-transform:uppercase}
    .bm-ref-status{display:inline-flex;align-items:center;gap:6px;width:max-content;margin-top:6px;padding:4px 9px;border-radius:999px;background:rgba(40,209,124,.13);border:1px solid rgba(40,209,124,.35);color:#72efae;font-size:10px;font-weight:900;text-transform:uppercase;letter-spacing:.05em}
    .bm-ref-status:before{content:'';width:7px;height:7px;border-radius:50%;background:#28d17c;box-shadow:0 0 10px #28d17c}

    .bm-ref-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin:0 0 12px}
    .bm-ref-stat{min-height:76px;padding:12px 13px;border-radius:15px;background:linear-gradient(145deg,#111922,#0c1219);border:1px solid rgba(255,255,255,.08);box-shadow:inset 0 1px 0 rgba(255,255,255,.02)}
    .bm-ref-stat small{display:block;color:#9ea9b6;font-size:9px;font-weight:900;text-transform:uppercase;letter-spacing:.08em}
    .bm-ref-stat strong{display:block;margin-top:6px;font-size:22px;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .bm-ref-stat.red strong{color:#ff6678}.bm-ref-stat.green strong{color:#58e89d}.bm-ref-stat.blue strong{color:#79b8ff}

    .bm-ref-stage{position:relative;min-height:270px;margin:0 0 14px;border:1px solid rgba(255,255,255,.08);border-radius:20px;overflow:hidden;background:radial-gradient(circle at 72% 42%,rgba(255,36,59,.25),transparent 25%),radial-gradient(circle at 45% 78%,rgba(255,67,45,.13),transparent 34%),linear-gradient(135deg,#0d141b,#070b10 62%,#12090d);box-shadow:0 24px 70px rgba(0,0,0,.3)}
    .bm-ref-stage:before{content:'';position:absolute;left:10%;right:15%;top:54%;height:2px;background:linear-gradient(90deg,transparent,rgba(255,36,59,.18),#ff243b,rgba(255,153,100,.72),transparent);box-shadow:0 0 20px rgba(255,36,59,.55);transform:skewY(-5deg)}
    .bm-ref-stage-copy{position:absolute;left:18px;top:18px;z-index:4;max-width:46%}
    .bm-ref-stage-kicker{font-size:10px;font-weight:950;color:#ff6b79;letter-spacing:.14em;text-transform:uppercase}
    .bm-ref-stage-title{margin-top:6px;font-size:21px;font-weight:950;line-height:1.05}
    .bm-ref-stage-sub{margin-top:7px;color:#9faab5;font-size:12px;line-height:1.45}
    .bm-ref-stage-rider{position:absolute;z-index:3;width:min(42%,420px);right:17%;bottom:-9px;filter:drop-shadow(0 18px 18px rgba(0,0,0,.55));transition:transform .32s ease,opacity .22s ease;transform-origin:center bottom}
    .bm-ref-stage-rider img{display:block;width:100%;height:auto;max-height:245px;object-fit:contain}
    .bm-ref-stage-rider.bm-ref-go{transform:translateX(52vw) scale(.94);opacity:0}
    .bm-ref-phone{position:absolute;right:18px;top:15px;bottom:15px;width:162px;z-index:5;border:5px solid #252d36;border-radius:30px;background:#070b0f;padding:12px 9px 10px;box-shadow:0 24px 50px rgba(0,0,0,.55),0 0 0 1px #6c7682 inset;overflow:hidden}
    .bm-ref-phone:before{content:'';position:absolute;top:6px;left:50%;transform:translateX(-50%);width:55px;height:8px;border-radius:999px;background:#010305}
    .bm-ref-phone-head{padding:10px 4px 8px;font-size:9px;font-weight:950;color:#fff;letter-spacing:.06em}.bm-ref-phone-head b{color:#ff3349}
    .bm-ref-phone-alert{margin-top:7px;background:#f7fff9;color:#0d1b13;border:1px solid #49dc94;border-radius:11px;padding:9px;font-size:9px;font-weight:950;box-shadow:0 0 20px rgba(40,209,124,.16)}
    .bm-ref-phone-card{position:absolute;left:8px;right:8px;bottom:13px;padding:10px;border-radius:12px;background:#f3f7fb;color:#13202d;font-size:8px;box-shadow:0 12px 28px rgba(0,0,0,.35)}
    .bm-ref-phone-card strong{display:block;font-size:11px;margin-bottom:4px}.bm-ref-phone-pill{display:inline-block;float:right;margin-top:-2px;background:#1677ff;color:white;border-radius:999px;padding:3px 6px;font-size:7px}.bm-ref-phone-line{margin-top:5px;color:#425264;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .bm-ref-phone-button{margin-top:7px;padding:6px;border-radius:7px;text-align:center;background:#dcecff;color:#1765bd;font-weight:950}

    body.bm-reference-admin .card{background:linear-gradient(145deg,#111820,#0d1319)!important;border:1px solid rgba(255,255,255,.075)!important;border-radius:17px!important;box-shadow:0 15px 45px rgba(0,0,0,.16)}
    body.bm-reference-admin .launch-card{border-color:rgba(255,36,59,.23)!important}
    body.bm-reference-admin .field input,body.bm-reference-admin .field select{background:#090e13!important;border-color:rgba(255,255,255,.1)!important}
    body.bm-reference-admin .field input:focus,body.bm-reference-admin .field select:focus{border-color:rgba(255,36,59,.65)!important;box-shadow:0 0 0 3px rgba(255,36,59,.08)}
    body.bm-reference-admin .btn.green,body.bm-reference-admin .big-action{background:linear-gradient(135deg,#22c97a,#0da65d)!important;color:#05120b!important;box-shadow:0 8px 22px rgba(34,201,122,.15)}
    body.bm-reference-admin #releaseSelected{background:linear-gradient(135deg,#ff334b,#cf1028)!important;color:#fff!important;box-shadow:0 10px 26px rgba(255,36,59,.2)}
    body.bm-reference-admin .pending-row{border-color:rgba(255,255,255,.07)!important}
    body.bm-reference-admin .money{color:#62eaa1!important}
    body.bm-reference-admin .notice{background:#0b1117!important;border-color:rgba(255,255,255,.08)!important}
    body.bm-reference-admin .bm-dispatch-copy{border-color:rgba(255,36,59,.68)!important;background:linear-gradient(135deg,rgba(21,13,17,.98),rgba(8,12,16,.96))!important}
    body.bm-reference-admin .bm-dispatch-kicker{color:#ff5265!important}

    @media(max-width:1100px){.bm-ref-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.bm-ref-stage{min-height:240px}.bm-ref-stage-rider{right:19%;width:42%}.bm-ref-phone{width:145px}}
    @media(max-width:800px){body.bm-reference-admin .admin-main{padding:15px 13px 86px!important}body.bm-reference-admin .moto-workspace{grid-template-columns:1fr!important}.bm-ref-stage{display:none}.bm-ref-stats{grid-template-columns:1fr 1fr}body.bm-reference-admin .moto-list{position:relative!important;top:auto!important}.bm-ref-avatar{width:50px;height:50px}.moto-hero>div:first-child{padding-left:62px!important}}
    `;
    document.head.appendChild(s);
  }

  const safeMoney=v=>{try{return BRL(Number(v||0))}catch(_){return `R$ ${Number(v||0).toFixed(2).replace('.',',')}`}};
  const latestOf=arr=>(arr||[]).slice().sort((a,b)=>new Date(b?.created_at||b?.horario_saida||0)-new Date(a?.created_at||a?.horario_saida||0))[0]||null;
  const initials=name=>String(name||'M').trim().split(/\s+/).slice(0,2).map(x=>x[0]||'').join('').toUpperCase()||'M';

  function statsHtml(mid){
    const es=(typeof entregasOf==='function'?entregasOf(mid):[]).filter(x=>x.status!=='cancelada');
    const outs=typeof saidasOf==='function'?saidasOf(mid):[];
    const pending=es.filter(x=>!x.saida_id);
    const value=es.reduce((a,x)=>a+Number(x.valor||0),0);
    return `<div class="bm-ref-stats">
      <div class="bm-ref-stat blue"><small>Entregas hoje</small><strong>${es.length}</strong></div>
      <div class="bm-ref-stat red"><small>Aguardando saída</small><strong>${pending.length}</strong></div>
      <div class="bm-ref-stat"><small>Saídas liberadas</small><strong>${outs.length}</strong></div>
      <div class="bm-ref-stat green"><small>Valor em entregas</small><strong>${safeMoney(value)}</strong></div>
    </div>`;
  }

  function stageHtml(mid,moto){
    const es=(typeof entregasOf==='function'?entregasOf(mid):[]).filter(x=>x.status!=='cancelada');
    const outs=typeof saidasOf==='function'?saidasOf(mid):[];
    const lastOut=latestOf(outs);
    const lastDelivery=latestOf(es);
    const released=!!lastOut;
    const note=lastDelivery?.nota_numero?`#${esc(lastDelivery.nota_numero)}`:'Sem comanda';
    const bairro=esc(lastDelivery?.bairro_nome||'Aguardando pedido');
    return `<section class="bm-ref-stage" aria-label="Visual da saída do motoboy no painel">
      <div class="bm-ref-stage-copy">
        <div class="bm-ref-stage-kicker">Bora Michael Hi Hi</div>
        <div class="bm-ref-stage-title">${released?'Pedido liberado. Moto na rua!':'Motoboy aguardando liberação'}</div>
        <div class="bm-ref-stage-sub">Este celular é apenas um mockup visual dentro do painel. O APK instalado não é alterado por esta interface.</div>
      </div>
      <div class="bm-ref-stage-rider"><img src="${ASSET}" alt=""></div>
      <div class="bm-ref-phone" aria-hidden="true">
        <div class="bm-ref-phone-head"><b>⚡ BORA</b> · MOTOBOY</div>
        <div class="bm-ref-phone-alert">${released?'✓ Pedido liberado!':'● Aguardando pedido'}</div>
        <div class="bm-ref-phone-card"><span class="bm-ref-phone-pill">${released?'Em entrega':'Em base'}</span><strong>${note}</strong><div class="bm-ref-phone-line">📍 ${bairro}</div><div class="bm-ref-phone-button">${released?'Saída liberada':'Aguardando liberação'}</div></div>
      </div>
    </section>`;
  }

  function enhance(){
    document.body.classList.add('bm-reference-admin');
    const detail=document.querySelector('.moto-detail');
    if(!detail||!adminState?.selectedMoto)return;
    const mid=adminState.selectedMoto;
    const moto=adminState.motoboys?.find(x=>x.id===mid);
    if(!moto)return;
    const hero=detail.querySelector('.moto-hero');
    if(hero){
      const left=hero.firstElementChild;
      if(left&&!left.querySelector('.bm-ref-avatar')){
        const a=document.createElement('span');a.className='bm-ref-avatar';a.textContent=initials(moto.nome);left.prepend(a);
        const st=document.createElement('span');st.className='bm-ref-status';st.textContent='Ativo';left.appendChild(st);
      }
    }
    if(!detail.querySelector('.bm-ref-stats')){
      const box=document.createElement('div');box.innerHTML=statsHtml(mid)+stageHtml(mid,moto);
      const nodes=[...box.children];
      let anchor=hero;
      for(const n of nodes){anchor?.insertAdjacentElement('afterend',n);anchor=n}
      detail.querySelector('.bm-ref-stage-rider img')?.addEventListener('error',e=>{e.currentTarget.style.display='none'});
    }
  }

  const prevBind=bindAdmin;
  bindAdmin=function(){
    prevBind();
    setTimeout(enhance,0);
  };

  if(typeof bmPlayDispatchAnimation==='function'){
    const prevPlay=bmPlayDispatchAnimation;
    bmPlayDispatchAnimation=function(opts={}){
      if((opts?.mode||'admin')==='admin'){
        const riderEl=document.querySelector('.bm-ref-stage-rider');
        if(riderEl){riderEl.classList.add('bm-ref-go');setTimeout(()=>riderEl.classList.remove('bm-ref-go'),2100)}
      }
      return prevPlay(opts);
    };
  }

  const mo=new MutationObserver(()=>{if(!document.body.classList.contains('bm-reference-admin')||document.querySelector('.moto-detail:not(:has(.bm-ref-stats))'))enhance()});
  mo.observe(document.getElementById('root')||document.body,{childList:true,subtree:true});
  setTimeout(()=>{enhance();if(adminState?.selectedMoto&&typeof drawAdmin==='function')drawAdmin()},80);
})();
