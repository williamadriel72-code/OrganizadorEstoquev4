/* Bora Michael GPS-only add-on for the exact v2.6 UI.
   Does not replace or redefine any original Bora Michael function. */
(function boraMichaelGpsOnlyV26(){
  if (window.__boraMichaelGpsOnlyV26) return;
  window.__boraMichaelGpsOnlyV26 = true;

  const ENDPOINT='https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-rider-location';
  const REFRESH_MS=20000;
  let timer=null;
  let map=null;
  let markers=new Map();
  let fitDone=false;
  let state={motoboys:[],locations:[],loading:false,error:''};

  const escGps=(v)=>String(v??'').replace(/[&<>"']/g,c=>({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  }[c]));

  const ageMs=(v)=>{
    const n=new Date(v||0).getTime();
    return Number.isFinite(n)?Date.now()-n:Infinity;
  };

  const locMap=()=>new Map((state.locations||[]).map(x=>[String(x.motoboy_id),x]));

  function statusOf(loc){
    if(!loc) return {key:'none',label:'SEM GPS'};
    return ageMs(loc.updated_at)<=90000
      ? {key:'live',label:'GPS AO VIVO'}
      : {key:'stale',label:'SEM SINAL'};
  }

  function timeOf(loc){
    if(!loc?.updated_at) return 'Nunca enviou localização';
    const d=new Date(loc.updated_at);
    if(!Number.isFinite(d.getTime())) return 'Sem horário';
    const a=Math.max(0,ageMs(loc.updated_at));
    if(a<60000) return 'Agora · '+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
    if(a<3600000) return Math.max(1,Math.round(a/60000))+' min atrás · '+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
    return d.toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
  }

  function speedOf(loc){
    const v=Number(loc?.speed_mps);
    return Number.isFinite(v)&&v>=0
      ? (v*3.6).toFixed(v*3.6<10?1:0).replace('.',',')+' km/h'
      : '— km/h';
  }

  function accuracyOf(loc){
    const v=Number(loc?.accuracy_m);
    return Number.isFinite(v)&&v>=0 ? '±'+Math.round(v)+' m' : '±— m';
  }

  function ensureStyle(){
    if(document.getElementById('boraGpsOnlyStyle')) return;
    const s=document.createElement('style');
    s.id='boraGpsOnlyStyle';
    s.textContent=`
      #boraGpsOnly{margin:0 0 18px;background:#171a1e;border:1px solid #ffffff12;border-radius:17px;overflow:hidden}
      #boraGpsOnly .gps-head{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:14px 16px;border-bottom:1px solid #ffffff12}
      #boraGpsOnly .gps-title{font-weight:950}
      #boraGpsOnly .gps-sub{font-size:11px;color:#aeb4bd;margin-top:3px}
      #boraGpsOnly .gps-actions{display:flex;align-items:center;gap:9px;flex-wrap:wrap;justify-content:flex-end}
      #boraGpsOnly .gps-count{font-size:11px;color:#aeb4bd}
      #boraGpsOnly .gps-refresh{border:0;border-radius:10px;background:#2d3238;color:white;padding:8px 11px;font-weight:900}
      #boraGpsOnly .gps-body{display:grid;grid-template-columns:minmax(250px,320px) minmax(0,1fr);min-height:360px}
      #boraGpsOnly .gps-list{padding:10px;max-height:440px;overflow:auto;border-right:1px solid #ffffff12}
      #boraGpsOnly .gps-rider{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding:10px;border:1px solid #ffffff12;border-radius:12px;background:#20242a;margin-bottom:8px}
      #boraGpsOnly .gps-name{font-weight:900;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      #boraGpsOnly .gps-meta{font-size:10px;color:#aeb4bd;line-height:1.5;margin-top:4px}
      #boraGpsOnly .gps-badge{display:inline-block;margin-top:6px;padding:4px 7px;border-radius:999px;font-size:9px;font-weight:950}
      #boraGpsOnly .gps-badge.live{background:#143924;color:#6ee7a7}
      #boraGpsOnly .gps-badge.stale{background:#3a2a11;color:#f8c866}
      #boraGpsOnly .gps-badge.none{background:#2d3238;color:#b9c0c8}
      #boraGpsOnly .gps-center{align-self:center;border:0;border-radius:9px;background:#2d3238;color:#fff;padding:7px 9px;font-size:10px;font-weight:900}
      #boraGpsOnly .gps-center:disabled{opacity:.35}
      #boraGpsOnly .gps-map-wrap{position:relative;background:#dde2e5;min-height:360px}
      #boraGpsOnly #boraGpsMap{position:absolute;inset:0}
      #boraGpsOnly .gps-map-label{position:absolute;z-index:500;left:10px;top:10px;background:#11161bd9;color:#fff;border-radius:8px;padding:6px 9px;font-size:10px;pointer-events:none}
      #boraGpsOnly .gps-error{padding:14px;color:#fca5a5;font-size:11px}
      #boraGpsOnly .gps-empty{padding:24px 10px;text-align:center;color:#aeb4bd;font-size:11px}
      @media(max-width:820px){
        #boraGpsOnly .gps-body{grid-template-columns:1fr}
        #boraGpsOnly .gps-list{border-right:0;border-bottom:1px solid #ffffff12;max-height:250px}
        #boraGpsOnly .gps-map-wrap{min-height:350px}
      }
    `;
    document.head.appendChild(s);
  }

  function html(){
    const lm=locMap();
    const live=(state.motoboys||[]).filter(m=>statusOf(lm.get(String(m.id))).key==='live').length;
    const rows=(state.motoboys||[]).map(m=>{
      const loc=lm.get(String(m.id));
      const st=statusOf(loc);
      const can=!!loc && Number.isFinite(Number(loc.latitude)) && Number.isFinite(Number(loc.longitude));
      return `<div class="gps-rider">
        <div>
          <div class="gps-name">${escGps(m.nome||'Motoboy')}</div>
          <span class="gps-badge ${st.key}">${st.label}</span>
          <div class="gps-meta">${escGps(timeOf(loc))}<br>${escGps(accuracyOf(loc))} · ${escGps(speedOf(loc))}</div>
        </div>
        <button class="gps-center" data-gps-center="${escGps(m.id)}" ${can?'':'disabled'}>Centralizar</button>
      </div>`;
    }).join('');

    return `<section id="boraGpsOnly">
      <div class="gps-head">
        <div><div class="gps-title">GPS DOS MOTOBOYS</div><div class="gps-sub">Localização em tempo real · sem alterar nenhuma função do painel</div></div>
        <div class="gps-actions"><span class="gps-count">${live} online · 20s</span><button class="gps-refresh" id="boraGpsRefresh">↻ Atualizar GPS</button></div>
      </div>
      <div class="gps-body">
        <div class="gps-list">${state.error?'<div class="gps-error">'+escGps(state.error)+'</div>':(rows||'<div class="gps-empty">Nenhum motoboy ativo.</div>')}</div>
        <div class="gps-map-wrap"><div class="gps-map-label">Mapa dos Motoboys</div><div id="boraGpsMap"></div></div>
      </div>
    </section>`;
  }

  function loadLeaflet(){
    if(window.L) return Promise.resolve();
    if(window.__boraGpsLeafletPromise) return window.__boraGpsLeafletPromise;
    window.__boraGpsLeafletPromise=new Promise((resolve,reject)=>{
      if(!document.querySelector('link[data-bora-gps-leaflet]')){
        const l=document.createElement('link');
        l.rel='stylesheet';
        l.href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
        l.dataset.boraGpsLeaflet='1';
        document.head.appendChild(l);
      }
      const s=document.createElement('script');
      s.src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
      s.async=true;
      s.onload=()=>resolve();
      s.onerror=()=>reject(new Error('Não foi possível carregar o mapa.'));
      document.head.appendChild(s);
    });
    return window.__boraGpsLeafletPromise;
  }

  async function ensureMap(){
    const el=document.getElementById('boraGpsMap');
    if(!el) return;
    try{
      await loadLeaflet();
      if(!document.getElementById('boraGpsMap')) return;
      if(!map || map.getContainer()!==document.getElementById('boraGpsMap')){
        if(map) try{map.remove()}catch(_){}
        markers.clear();
        map=L.map('boraGpsMap').setView([-22.37,-41.79],12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
          maxZoom:19,
          attribution:'© OpenStreetMap contributors'
        }).addTo(map);
      }
      setTimeout(()=>map?.invalidateSize(),60);
      drawMarkers();
    }catch(e){
      state.error=e?.message||'Mapa indisponível.';
    }
  }

  function drawMarkers(){
    if(!map||!window.L) return;
    const lm=locMap(),seen=new Set(),points=[];
    (state.motoboys||[]).forEach(m=>{
      const loc=lm.get(String(m.id));
      const lat=Number(loc?.latitude),lng=Number(loc?.longitude);
      if(!Number.isFinite(lat)||!Number.isFinite(lng)) return;
      const id=String(m.id),st=statusOf(loc);
      const fill=st.key==='live'?'#22c55e':'#f2a33c';
      let mk=markers.get(id);
      if(!mk){
        mk=L.circleMarker([lat,lng],{radius:9,weight:3,color:'#fff',fillColor:fill,fillOpacity:1}).addTo(map);
        markers.set(id,mk);
      }else{
        mk.setLatLng([lat,lng]);
        mk.setStyle({fillColor:fill});
      }
      mk.bindPopup('<b>'+escGps(m.nome||'Motoboy')+'</b><br><small>'+st.label+' · '+escGps(timeOf(loc))+'</small><br><small>'+escGps(accuracyOf(loc))+' · '+escGps(speedOf(loc))+'</small>');
      seen.add(id);
      points.push([lat,lng]);
    });
    for(const [id,mk] of markers){
      if(!seen.has(id)){map.removeLayer(mk);markers.delete(id);}
    }
    if(points.length&&!fitDone){
      map.fitBounds(points,{padding:[30,30],maxZoom:15});
      fitDone=true;
    }
  }

  function bindUi(){
    const btn=document.getElementById('boraGpsRefresh');
    if(btn) btn.onclick=()=>loadGps(false);
    document.querySelectorAll('[data-gps-center]').forEach(b=>{
      b.onclick=()=>{
        const id=b.dataset.gpsCenter;
        const loc=locMap().get(String(id));
        const lat=Number(loc?.latitude),lng=Number(loc?.longitude);
        if(map&&Number.isFinite(lat)&&Number.isFinite(lng)){
          map.setView([lat,lng],16,{animate:true});
          markers.get(String(id))?.openPopup();
        }
      };
    });
  }

  function mount(){
    const main=document.querySelector('.admin-main');
    const head=main?.querySelector('.admin-head');
    if(!main||!head) return false;
    ensureStyle();

    let box=document.getElementById('boraGpsOnly');
    if(!box){
      const wrap=document.createElement('div');
      wrap.innerHTML=html();
      box=wrap.firstElementChild;
      head.insertAdjacentElement('afterend',box);
    }else{
      const wrap=document.createElement('div');
      wrap.innerHTML=html();
      const fresh=wrap.firstElementChild;
      const list=box.querySelector('.gps-list');
      const freshList=fresh.querySelector('.gps-list');
      const count=box.querySelector('.gps-count');
      const freshCount=fresh.querySelector('.gps-count');
      if(list&&freshList) list.innerHTML=freshList.innerHTML;
      if(count&&freshCount) count.textContent=freshCount.textContent;
    }
    bindUi();
    ensureMap();
    return true;
  }

  async function loadGps(silent=true){
    if(state.loading) return;
    if(typeof sb==='undefined' || !sb?.auth) return;
    state.loading=true;
    const btn=document.getElementById('boraGpsRefresh');
    if(btn&&!silent){btn.disabled=true;btn.textContent='Atualizando...';}
    try{
      const session=(await sb.auth.getSession())?.data?.session;
      if(!session?.access_token) throw new Error('Sessão administrativa expirada.');
      const r=await fetch(ENDPOINT,{
        method:'POST',
        headers:{'content-type':'application/json',authorization:'Bearer '+session.access_token},
        body:JSON.stringify({action:'list'})
      });
      const j=await r.json().catch(()=>({}));
      if(!r.ok) throw new Error(j.error||('HTTP '+r.status));
      state.motoboys=j.motoboys||[];
      state.locations=j.locations||[];
      state.error='';
    }catch(e){
      state.error=e?.message||'Não foi possível atualizar o GPS.';
    }finally{
      state.loading=false;
      mount();
      const b=document.getElementById('boraGpsRefresh');
      if(b){b.disabled=false;b.textContent='↻ Atualizar GPS';}
    }
  }

  const observer=new MutationObserver(()=>{
    if(document.querySelector('.admin-main')){
      if(mount() && !(state.motoboys||[]).length) loadGps(true);
    }
  });
  observer.observe(document.documentElement,{childList:true,subtree:true});

  const boot=()=>{
    if(document.querySelector('.admin-main')){
      mount();
      loadGps(true);
    }
    if(timer) clearInterval(timer);
    timer=setInterval(()=>{
      if(!document.hidden && document.querySelector('.admin-main')) loadGps(true);
    },REFRESH_MS);
  };
  setTimeout(boot,600);
})();