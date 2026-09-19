/* GPS dos motoboys — reaproveita a infraestrutura validada no painel de teste.
   Somente localização: não cria rotas e não altera o fluxo de entregas. */
(function bmInstallRiderGpsPanelV1(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmRiderGpsPanelV1)return;window.__bmRiderGpsPanelV1=true;
 const ENDPOINT='https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-rider-location';
 const REFRESH_MS=20000;
 let timer=null,map=null,markers=new Map(),fitDone=false,state={motoboys:[],locations:[],loading:false,error:''};

 const escGps=v=>typeof esc==='function'?esc(String(v??'')):String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const ageMs=v=>{const n=new Date(v||0).getTime();return Number.isFinite(n)?Date.now()-n:Infinity};
 const locMap=()=>new Map((state.locations||[]).map(x=>[String(x.motoboy_id),x]));
 function gpsStatus(loc){
  if(!loc)return {key:'none',label:'SEM GPS'};
  const a=ageMs(loc.updated_at);
  if(a<=90000)return {key:'live',label:'GPS AO VIVO'};
  return {key:'stale',label:'SEM SINAL'};
 }
 function lastLabel(loc){
  if(!loc?.updated_at)return 'Nunca enviou localização';
  const d=new Date(loc.updated_at);
  if(!Number.isFinite(d.getTime()))return 'Sem horário';
  const a=Math.max(0,ageMs(loc.updated_at));
  if(a<60000)return 'Agora · '+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
  if(a<3600000)return Math.max(1,Math.round(a/60000))+' min atrás · '+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
  return d.toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
 }
 function speedLabel(loc){
  const v=Number(loc?.speed_mps);
  return Number.isFinite(v)&&v>=0?(v*3.6).toFixed(v*3.6<10?1:0).replace('.',',')+' km/h':'— km/h';
 }
 function accuracyLabel(loc){
  const v=Number(loc?.accuracy_m);
  return Number.isFinite(v)&&v>=0?'±'+Math.round(v)+' m':'±— m';
 }
 function gpsCss(){
  if(document.getElementById('bmGpsPanelStyle'))return;
  const s=document.createElement('style');s.id='bmGpsPanelStyle';s.textContent=`
   .bm-gps{margin:12px 0 14px;border:1px solid #ffffff13;border-radius:18px;background:linear-gradient(145deg,#10171f,#0a1016);overflow:hidden;box-shadow:0 18px 55px #0003}
   .bm-gps-head{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px 15px;border-bottom:1px solid #ffffff10}
   .bm-gps-title{display:flex;align-items:center;gap:10px}.bm-gps-pin{width:34px;height:34px;display:grid;place-items:center;border-radius:11px;background:#ef444422;color:#ff6577;font-size:18px}
   .bm-gps-title b{display:block;font-size:16px}.bm-gps-title small{display:block;color:#8e9aa6;margin-top:2px;font-size:10px}
   .bm-gps-head-actions{display:flex;gap:8px;align-items:center}.bm-gps-online{font-size:11px;color:#94a3b8;white-space:nowrap}
   .bm-gps-refresh{border:1px solid #ffffff12;background:#252b32;color:#fff;border-radius:10px;padding:8px 11px;font-weight:850;cursor:pointer}
   .bm-gps-refresh:disabled{opacity:.55}
   .bm-gps-body{display:grid;grid-template-columns:minmax(270px,330px) minmax(0,1fr);min-height:365px}
   .bm-gps-list{border-right:1px solid #ffffff10;background:#0c1218;padding:10px;max-height:470px;overflow:auto}
   .bm-gps-rider{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding:11px;border:1px solid #ffffff0d;border-radius:13px;background:#121a22;margin-bottom:8px}
   .bm-gps-rider.live{border-color:#21d47d36}.bm-gps-rider.stale{border-color:#f59e0b36}.bm-gps-rider.none{opacity:.78}
   .bm-gps-name{font-size:13px;font-weight:900;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bm-gps-meta{margin-top:4px;color:#8f9ca9;font-size:10px;line-height:1.45}
   .bm-gps-badge{display:inline-flex;margin-top:6px;border-radius:999px;padding:4px 7px;font-size:9px;font-weight:950;letter-spacing:.02em}
   .bm-gps-badge.live{background:#143924;color:#6ee7a7}.bm-gps-badge.stale{background:#3a2a11;color:#f8c866}.bm-gps-badge.none{background:#242a31;color:#9ca7b2}
   .bm-gps-center{align-self:center;border:1px solid #ffffff13;background:#2a3037;color:#fff;border-radius:9px;padding:7px 9px;font-size:10px;font-weight:800;cursor:pointer}
   .bm-gps-center:disabled{opacity:.35;cursor:default}
   .bm-gps-map-wrap{position:relative;min-width:0;background:#dfe5e8}.bm-gps-map{position:absolute;inset:0;min-height:365px}
   .bm-gps-map-note{position:absolute;z-index:500;left:10px;top:10px;padding:6px 9px;border-radius:9px;background:#10151ad9;color:#e5e7eb;font-size:10px;box-shadow:0 4px 18px #0004;pointer-events:none}
   .bm-gps-error{padding:14px;color:#fca5a5;font-size:11px}.bm-gps-empty{padding:28px 10px;text-align:center;color:#7f8b96;font-size:11px}
   .bm-gps-popup b{font-size:13px}.bm-gps-popup small{display:block;margin-top:3px;color:#56616c}
   @media(max-width:900px){.bm-gps-body{grid-template-columns:1fr}.bm-gps-list{border-right:0;border-bottom:1px solid #ffffff10;max-height:260px}.bm-gps-map-wrap{min-height:360px}.bm-gps-head{align-items:flex-start}.bm-gps-head-actions{flex-wrap:wrap;justify-content:flex-end}}
  `;document.head.appendChild(s);
 }
 function shellHtml(){
  const lm=locMap(),online=(state.motoboys||[]).filter(m=>gpsStatus(lm.get(String(m.id))).key==='live').length;
  const rows=(state.motoboys||[]).map(m=>{
   const loc=lm.get(String(m.id)),st=gpsStatus(loc),can=!!loc&&Number.isFinite(Number(loc.latitude))&&Number.isFinite(Number(loc.longitude));
   return '<div class="bm-gps-rider '+st.key+'"><div><div class="bm-gps-name">'+escGps(m.nome||'Motoboy')+'</div><span class="bm-gps-badge '+st.key+'">'+st.label+'</span><div class="bm-gps-meta">'+escGps(lastLabel(loc))+'<br>'+escGps(accuracyLabel(loc))+' · '+escGps(speedLabel(loc))+'</div></div><button class="bm-gps-center" data-bm-gps-center="'+escGps(m.id)+'" '+(can?'':'disabled')+'>Centralizar</button></div>';
  }).join('');
  return '<section class="bm-gps" id="bmGpsPanel"><div class="bm-gps-head"><div class="bm-gps-title"><span class="bm-gps-pin">⌖</span><div><b>GPS DOS MOTOBOYS</b><small>LOCALIZAÇÃO EM TEMPO REAL · SEM ROTAS</small></div></div><div class="bm-gps-head-actions"><span class="bm-gps-online">'+online+' online · atualiza a cada 20s</span><button class="bm-gps-refresh" id="bmGpsRefresh">↻ Atualizar GPS</button></div></div><div class="bm-gps-body"><div class="bm-gps-list" id="bmGpsList">'+(state.error?'<div class="bm-gps-error">'+escGps(state.error)+'</div>':rows||'<div class="bm-gps-empty">Nenhum motoboy ativo.</div>')+'</div><div class="bm-gps-map-wrap"><div class="bm-gps-map-note">Mapa dos Motoboys</div><div class="bm-gps-map" id="bmGpsMap"></div></div></div></section>';
 }
 function mountShell(){
  gpsCss();
  if(document.getElementById('bmGpsPanel'))return true;
  const ov=document.querySelector('.bm-overview');
  if(!ov)return false;
  const holder=document.createElement('div');holder.innerHTML=shellHtml();
  const node=holder.firstElementChild;
  const grid=ov.querySelector('.bm-ov-grid');
  if(grid)ov.insertBefore(node,grid);else ov.appendChild(node);
  bindUi();ensureMap();
  return true;
 }
 function redrawShell(){
  const panel=document.getElementById('bmGpsPanel');
  if(!panel){mountShell();return}
  const wrap=document.createElement('div');wrap.innerHTML=shellHtml();const fresh=wrap.firstElementChild;
  const list=panel.querySelector('#bmGpsList'),freshList=fresh?.querySelector('#bmGpsList');
  const online=panel.querySelector('.bm-gps-online'),freshOnline=fresh?.querySelector('.bm-gps-online');
  if(list&&freshList)list.innerHTML=freshList.innerHTML;
  if(online&&freshOnline)online.textContent=freshOnline.textContent;
  const btn=panel.querySelector('#bmGpsRefresh');if(btn){btn.disabled=false;btn.textContent='↻ Atualizar GPS'}
  bindUi();ensureMap(true);
 }
 function bindUi(){
  const r=document.getElementById('bmGpsRefresh');if(r)r.onclick=()=>loadGps(false);
  document.querySelectorAll('[data-bm-gps-center]').forEach(b=>b.onclick=()=>centerOn(b.dataset.bmGpsCenter));
 }
 function loadLeaflet(){
  if(window.L)return Promise.resolve();
  if(window.__bmLeafletPromise)return window.__bmLeafletPromise;
  window.__bmLeafletPromise=new Promise((resolve,reject)=>{
   if(!document.querySelector('link[data-bm-leaflet]')){
    const l=document.createElement('link');l.rel='stylesheet';l.href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';l.dataset.bmLeaflet='1';document.head.appendChild(l);
   }
   const s=document.createElement('script');s.src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';s.async=true;s.onload=()=>resolve();s.onerror=()=>reject(new Error('Não foi possível carregar o mapa.'));document.head.appendChild(s);
  });
  return window.__bmLeafletPromise;
 }
 async function ensureMap(keepView=false){
  const el=document.getElementById('bmGpsMap');if(!el)return;
  try{
   await loadLeaflet();
   if(!document.getElementById('bmGpsMap'))return;
   if(!map){
    map=L.map('bmGpsMap',{zoomControl:true,attributionControl:true}).setView([-22.37,-41.79],12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
   }else if(map.getContainer()!==document.getElementById('bmGpsMap')){
    map.remove();map=null;markers.clear();return ensureMap(keepView);
   }
   setTimeout(()=>map?.invalidateSize(),80);drawMarkers(keepView);
  }catch(e){state.error=e?.message||'Mapa indisponível.'}
 }
 function drawMarkers(keepView=false){
  if(!map||!window.L)return;
  const lm=locMap(),seen=new Set(),points=[];
  (state.motoboys||[]).forEach(m=>{
   const loc=lm.get(String(m.id)),lat=Number(loc?.latitude),lng=Number(loc?.longitude);
   if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
   const id=String(m.id),st=gpsStatus(loc),color=st.key==='live'?'#19c875':st.key==='stale'?'#f59e0b':'#64748b';
   let mk=markers.get(id);
   if(!mk){mk=L.circleMarker([lat,lng],{radius:9,weight:3,color:'#ffffff',fillColor:color,fillOpacity:1}).addTo(map);markers.set(id,mk)}
   else{mk.setLatLng([lat,lng]);mk.setStyle({fillColor:color})}
   mk.bindPopup('<div class="bm-gps-popup"><b>'+escGps(m.nome||'Motoboy')+'</b><small>'+st.label+' · '+escGps(lastLabel(loc))+'</small><small>'+escGps(accuracyLabel(loc))+' · '+escGps(speedLabel(loc))+'</small></div>');
   seen.add(id);points.push([lat,lng]);
  });
  for(const [id,mk] of markers){if(!seen.has(id)){map.removeLayer(mk);markers.delete(id)}}
  if(points.length&&!fitDone&&!keepView){map.fitBounds(points,{padding:[34,34],maxZoom:15});fitDone=true}
 }
 function centerOn(id){
  const lm=locMap(),loc=lm.get(String(id)),lat=Number(loc?.latitude),lng=Number(loc?.longitude);
  if(!map||!Number.isFinite(lat)||!Number.isFinite(lng))return;
  map.setView([lat,lng],16,{animate:true});markers.get(String(id))?.openPopup();
 }
 async function loadGps(silent=true){
  if(state.loading)return;state.loading=true;
  const btn=document.getElementById('bmGpsRefresh');if(btn){btn.disabled=true;if(!silent)btn.textContent='Atualizando...'}
  try{
   const sess=(await sb.auth.getSession())?.data?.session;
   if(!sess?.access_token)throw new Error('Sessão administrativa expirada.');
   const r=await fetch(ENDPOINT,{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+sess.access_token},body:JSON.stringify({action:'list'})});
   const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||('HTTP '+r.status));
   state.motoboys=j.motoboys||[];state.locations=j.locations||[];state.error='';
  }catch(e){state.error=e?.message||'Não foi possível atualizar o GPS.'}
  finally{state.loading=false;redrawShell()}
 }
 function schedule(){
  if(timer)clearInterval(timer);
  timer=setInterval(()=>{if(!document.hidden&&document.querySelector('.bm-overview'))loadGps(true)},REFRESH_MS);
 }
 let tries=0;
 const wait=setInterval(()=>{
  tries++;
  if(typeof bindAdmin!=='function'||typeof sb==='undefined'){if(tries>150)clearInterval(wait);return}
  clearInterval(wait);
  const prevBind=bindAdmin;
  bindAdmin=function(){prevBind();setTimeout(()=>{if(mountShell()){ensureMap(true);if(!(state.motoboys||[]).length)loadGps(true)}},0)};
  const obs=new MutationObserver(()=>{if(document.querySelector('.bm-overview'))setTimeout(()=>{if(mountShell())ensureMap(true)},0)});
  obs.observe(document.documentElement,{childList:true,subtree:true});
  mountShell();loadGps(true);schedule();
 },100);
})();
