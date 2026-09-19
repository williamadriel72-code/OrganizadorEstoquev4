/* === GPS PANEL ORIGINAL — executa primeiro para não depender dos demais efeitos === */
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
  const main=document.querySelector('.admin-main');
  if(!main)return false;
  const holder=document.createElement('div');holder.innerHTML=shellHtml();
  const node=holder.firstElementChild;
  const workspace=main.querySelector('.moto-workspace');
  if(workspace)main.insertBefore(node,workspace);else main.appendChild(node);
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
  timer=setInterval(()=>{if(!document.hidden&&document.querySelector('.admin-main'))loadGps(true)},REFRESH_MS);
 }
 let tries=0;
 const wait=setInterval(()=>{
  tries++;
  if(typeof bindAdmin!=='function'||typeof sb==='undefined'){if(tries>150)clearInterval(wait);return}
  clearInterval(wait);
  const prevBind=bindAdmin;
  bindAdmin=function(){prevBind();setTimeout(()=>{if(mountShell()){ensureMap(true);if(!(state.motoboys||[]).length)loadGps(true)}},0)};
  const obs=new MutationObserver(()=>{if(document.querySelector('.admin-main'))setTimeout(()=>{if(mountShell())ensureMap(true)},0)});
  obs.observe(document.documentElement,{childList:true,subtree:true});
  mountShell();loadGps(true);schedule();
 },100);
})();


async function bmRefreshAdminPanel(){
 const btn=document.getElementById('bmRefreshAdmin');
 const old=btn?.textContent||'↻ ATUALIZAR';
 if(btn){btn.disabled=true;btn.textContent='ATUALIZANDO...'}
 try{
  const selected=adminState.selectedMoto,bairro=adminState.selectedBairro;
  await loadAdmin();
  if(selected&&adminState.motoboys.some(m=>m.id===selected))adminState.selectedMoto=selected;
  adminState.selectedBairro=bairro;drawAdmin();toast('Lista de motoboys atualizada.');
 }catch(e){console.error('admin-refresh',e);toast(e?.message||'Não foi possível atualizar agora.');if(btn){btn.disabled=false;btn.textContent=old}}
}
function bmInstallAdminRefreshButton(){
 const head=document.querySelector('.moto-list-head');if(!head||document.getElementById('bmRefreshAdmin'))return;
 const btn=document.createElement('button');btn.id='bmRefreshAdmin';btn.type='button';btn.className='btn secondary small';btn.style.marginLeft='auto';btn.style.padding='9px 12px';btn.style.fontWeight='800';btn.textContent='↻ ATUALIZAR';btn.addEventListener('click',bmRefreshAdminPanel);
 const register=document.getElementById('bmRegisterMoto');if(register){register.style.marginLeft='8px';head.insertBefore(btn,register)}else head.appendChild(btn);
}
const bmBindAdminBeforeRefresh=bindAdmin;
bindAdmin=function(){bmBindAdminBeforeRefresh();bmInstallAdminRefreshButton()};

/* Visão geral do painel: aparece apenas quando nenhum motoboy está selecionado. */
(function bmInstallPanelOverview(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 let tries=0;
 const wait=setInterval(()=>{
  tries++;
  if(window.__bmPanelOverviewInlineV1){clearInterval(wait);return}
  if(!window.__bmPanelReferenceV1||typeof selectedPanel!=='function'){if(tries>100)clearInterval(wait);return}
  clearInterval(wait);window.__bmPanelOverviewInlineV1=true;

  const css=document.createElement('style');css.id='bmPanelOverviewInlineStyle';css.textContent=`
  .bm-overview{padding:2px 0 28px;min-width:0}.bm-ov-head{display:flex;justify-content:space-between;gap:20px;padding:20px 22px;margin-bottom:12px;border:1px solid #ffffff14;border-radius:20px;background:linear-gradient(135deg,#10171f,#0a1016);box-shadow:0 18px 55px #0004}.bm-ov-kicker{font-size:9px;font-weight:950;letter-spacing:.13em;color:#ff6678}.bm-ov-head h2{margin:5px 0;font-size:27px}.bm-ov-head p{margin:0;color:#9da8b4;font-size:12px}.bm-ov-clock{text-align:right}.bm-ov-clock b{display:block;font-size:25px}.bm-ov-clock small{color:#8f9aa6}
  .bm-ov-stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:9px;margin-bottom:12px}.bm-ov-stat{min-height:88px;padding:13px;border:1px solid #ffffff12;border-radius:16px;background:linear-gradient(145deg,#111922,#0c1219)}.bm-ov-stat small{display:block;color:#9da8b4;font-size:9px;font-weight:900;text-transform:uppercase}.bm-ov-stat strong{display:block;margin-top:8px;font-size:23px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-ov-stat em{display:block;margin-top:4px;color:#6f7c88;font-size:9px;font-style:normal}.bm-ov-green strong{color:#58e89d}.bm-ov-blue strong{color:#79b8ff}.bm-ov-red strong{color:#ff687b}
  .bm-ov-grid{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(330px,.8fr);gap:12px}.bm-ov-card{min-height:255px;padding:17px 18px;border:1px solid #ffffff12;border-radius:18px;background:linear-gradient(145deg,#10171f,#0b1117);box-shadow:0 18px 55px #0003}.bm-ov-title{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:11px;border-bottom:1px solid #ffffff0d}.bm-ov-title h3{margin:4px 0 0;font-size:18px}.bm-ov-badge{display:grid;place-items:center;min-width:28px;height:28px;border-radius:9px;background:#18222d;color:#8fa2b4}.bm-ov-events{margin-top:7px}.bm-ov-event{display:grid;grid-template-columns:8px 45px minmax(0,1fr);align-items:center;gap:9px;padding:9px 2px;border-bottom:1px solid #ffffff0b;font-size:12px}.bm-ov-event time{color:#8f9ca9;font-weight:800}.bm-ov-event div{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bm-ov-dot{width:7px;height:7px;border-radius:50%;background:#4d9cff}.bm-ov-dot.green{background:#2bd180}.bm-ov-dot.red{background:#ff4359}.bm-ov-empty{padding:30px 5px;text-align:center;color:#7f8b96}
  .bm-ov-turn{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.bm-ov-turn div{padding:14px;border:1px solid #ffffff10;border-radius:14px;background:#0c131a}.bm-ov-turn small{display:block;color:#8f9ca9;font-size:9px;font-weight:900;text-transform:uppercase}.bm-ov-turn strong{display:block;margin-top:6px;font-size:21px}.bm-ov-note{margin-top:12px;padding:11px 12px;border:1px solid #4d9cff28;border-radius:12px;background:#4d9cff12;color:#9eb8d4;font-size:11px;line-height:1.4}
  @media(max-width:1300px){.bm-ov-stats{grid-template-columns:repeat(3,1fr)}}@media(max-width:950px){.bm-ov-grid{grid-template-columns:1fr}.bm-ov-stats{grid-template-columns:repeat(2,1fr)}}@media(max-width:600px){.bm-ov-clock{display:none}.bm-ov-head{padding:16px}.bm-ov-head h2{font-size:22px}}
  `;document.head.appendChild(css);

  const prev=selectedPanel;
  const val=v=>{try{return BRL(Number(v||0))}catch(_){return `R$ ${Number(v||0).toFixed(2).replace('.',',')}`}};
  const tm=v=>{try{return new Date(v).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}catch(_){return '--:--'}};
  const stamp=v=>{const n=new Date(v||0).getTime();return Number.isFinite(n)?n:0};
  const esc2=v=>typeof esc==='function'?esc(String(v??'')):String(v??'');

  function rows(){return (adminState?.motoboys||[]).map(m=>({m,j:typeof jornadaOf==='function'?jornadaOf(m.id):null,e:(typeof entregasOf==='function'?entregasOf(m.id):[]).filter(x=>x.status!=='cancelada'),s:typeof saidasOf==='function'?saidasOf(m.id):[]}))}
  function overview(){
   const r=rows(),now=new Date(),mins=now.getHours()*60+now.getMinutes();
   const start=new Date(now);start.setHours(mins>=17*60?17:mins>=10*60?10:0,0,0,0);const a=start.getTime(),b=now.getTime();
   const turnLabel=mins>=17*60?'Turno após 17:00':mins>=10*60?'Turno 10:00 → 17:00':'Antes das 10:00';const inside=v=>{const n=stamp(v);return n>=a&&n<=b};
   const active=r.filter(x=>x.j?.chegada_at&&x.j?.chegada_tipo!=='nao_compareceu').length;
   const noshow=r.filter(x=>x.j?.chegada_tipo==='nao_compareceu').length;
   const inDelivery=r.filter(x=>x.s.some(s=>['liberada','em_andamento','em_entrega'].includes(String(s.status||'').toLowerCase()))||x.e.some(e=>e.saida_id&&!['entregue','finalizada','cancelada'].includes(String(e.status||'').toLowerCase()))).length;
   const outs=r.reduce((n,x)=>n+x.s.length,0),cmds=r.reduce((n,x)=>n+x.e.length,0),total=r.reduce((n,x)=>n+x.e.reduce((z,e)=>z+Number(e.valor||0),0),0);
   const tOut=r.reduce((n,x)=>n+x.s.filter(s=>inside(s.horario_saida||s.created_at)).length,0),tCmd=r.reduce((n,x)=>n+x.e.filter(e=>inside(e.created_at||e.updated_at)).length,0),tVal=r.reduce((n,x)=>n+x.e.filter(e=>inside(e.created_at||e.updated_at)).reduce((z,e)=>z+Number(e.valor||0),0),0);
   const tMoto=new Set();r.forEach(x=>{if((x.j?.chegada_at&&inside(x.j.chegada_at))||x.s.some(s=>inside(s.horario_saida||s.created_at))||x.e.some(e=>inside(e.created_at||e.updated_at)))tMoto.add(x.m.id)});
   const ev=[];r.forEach(x=>{const n=x.m?.nome||'Motoboy';if(x.j?.chegada_tipo==='nao_compareceu')ev.push({at:x.j.updated_at||x.j.created_at,txt:`${n} · Não compareceu`,c:'red'});else if(x.j?.chegada_at)ev.push({at:x.j.chegada_at,txt:`${n} · Chegada registrada`,c:'blue'});x.s.forEach(s=>ev.push({at:s.horario_saida||s.created_at,txt:`${n} · Saída ${s.numero_sequencial?String(s.numero_sequencial).padStart(2,'0'):'liberada'}`,c:'green'}));x.e.forEach(e=>ev.push({at:e.updated_at||e.created_at,txt:`${n} · Comanda ${e.nota_numero||''} lançada`,c:'blue'}))});
   ev.sort((x,y)=>stamp(y.at)-stamp(x.at));const list=ev.filter(x=>stamp(x.at)).slice(0,8);const recent=list.length?list.map(x=>`<div class="bm-ov-event"><span class="bm-ov-dot ${x.c}"></span><time>${tm(x.at)}</time><div>${esc2(x.txt)}</div></div>`).join(''):'<div class="bm-ov-empty">Nenhuma movimentação registrada hoje.</div>';
   return `<section class="bm-overview"><div class="bm-ov-head"><div><div class="bm-ov-kicker">PAINEL ADMINISTRATIVO</div><h2>Visão geral do painel</h2><p>Acompanhe o movimento do dia sem precisar abrir um motoboy.</p></div><div class="bm-ov-clock"><b id="bmOverviewClock">${now.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b><small>${now.toLocaleDateString('pt-BR')}</small></div></div><div class="bm-ov-stats"><div class="bm-ov-stat bm-ov-green"><small>Motoboys ativos hoje</small><strong>${active}</strong><em>com chegada registrada</em></div><div class="bm-ov-stat bm-ov-blue"><small>Em entrega</small><strong>${inDelivery}</strong><em>saída em andamento</em></div><div class="bm-ov-stat bm-ov-red"><small>Não compareceram</small><strong>${noshow}</strong><em>diária zerada</em></div><div class="bm-ov-stat"><small>Saídas realizadas</small><strong>${outs}</strong><em>registradas hoje</em></div><div class="bm-ov-stat"><small>Comandas do dia</small><strong>${cmds}</strong><em>lançamentos válidos</em></div><div class="bm-ov-stat bm-ov-green"><small>Valor em entregas</small><strong>${val(total)}</strong><em>total das taxas</em></div></div><div class="bm-ov-grid"><article class="bm-ov-card"><div class="bm-ov-title"><div><div class="bm-ov-kicker">MOVIMENTAÇÕES</div><h3>Atividade recente</h3></div><b class="bm-ov-badge">${list.length}</b></div><div class="bm-ov-events">${recent}</div></article><article class="bm-ov-card"><div class="bm-ov-title"><div><div class="bm-ov-kicker">TURNO ATUAL</div><h3>${turnLabel}</h3></div><span class="bm-ov-dot green" style="margin-top:8px"></span></div><div class="bm-ov-turn"><div><small>Entregas</small><strong>${tCmd}</strong></div><div><small>Saídas</small><strong>${tOut}</strong></div><div><small>Valor acumulado</small><strong>${val(tVal)}</strong></div><div><small>Motoboys ativos</small><strong>${tMoto.size}</strong></div></div><div class="bm-ov-note">Às 17:00 começa uma nova contagem de turno sem apagar o histórico anterior.</div></article></div></section>`;
  }
  selectedPanel=function(){return adminState?.selectedMoto?prev():overview()};
  setInterval(()=>{const x=document.getElementById('bmOverviewClock');if(x)x.textContent=new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})},30000);
  if(!adminState?.selectedMoto&&typeof drawAdmin==='function')setTimeout(()=>drawAdmin(),0);
 },100);
})();

/* Luzes ambientais e microanimações premium — somente painel admin. */
(function bmInstallPanelMotionV1(){
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmPanelMotionV1)return;window.__bmPanelMotionV1=true;
 const s=document.createElement('style');s.id='bmPanelMotionV1';s.textContent=`
 body.bm-reference-admin .app{background:radial-gradient(circle at 82% 18%,rgba(255,35,62,.16),transparent 28%),radial-gradient(circle at 18% 72%,rgba(32,119,255,.11),transparent 29%),radial-gradient(circle at 58% 92%,rgba(255,126,62,.07),transparent 30%),#06090d!important;background-size:145% 145%,155% 155%,165% 165%,auto!important;animation:bmAmbientGlow 16s ease-in-out infinite alternate!important}
 @keyframes bmAmbientGlow{0%{background-position:0% 0%,100% 40%,50% 100%,0 0}45%{background-position:18% 12%,78% 58%,60% 84%,0 0}100%{background-position:34% 20%,62% 76%,42% 70%,0 0}}
 .bm-ov-head,.bm-ov-stat,.bm-ov-card,.moto-item,.moto-hero{position:relative;overflow:hidden;transition:transform .22s ease,border-color .22s ease,box-shadow .22s ease,background .22s ease}
 .bm-ov-stat:hover,.bm-ov-card:hover{transform:translateY(-3px);border-color:rgba(255,255,255,.18)!important;box-shadow:0 18px 48px rgba(0,0,0,.34),0 0 24px rgba(255,42,67,.06)}
 body.bm-reference-admin .moto-item:hover{transform:translateX(3px) translateY(-1px)!important;box-shadow:0 10px 28px rgba(0,0,0,.25),0 0 18px rgba(255,36,59,.07)!important}
 .bm-ov-stat:after,.bm-ov-card:after,.bm-ov-head:after{content:'';position:absolute;z-index:0;top:-65%;left:-42%;width:28%;height:230%;pointer-events:none;background:linear-gradient(90deg,transparent,rgba(255,255,255,.055),transparent);transform:rotate(18deg);animation:bmCardSweep 9s ease-in-out infinite}
 .bm-ov-card:nth-child(2):after{animation-delay:2.1s}.bm-ov-stat:nth-child(2):after{animation-delay:.8s}.bm-ov-stat:nth-child(3):after{animation-delay:1.5s}.bm-ov-stat:nth-child(4):after{animation-delay:2.3s}.bm-ov-stat:nth-child(5):after{animation-delay:3s}.bm-ov-stat:nth-child(6):after{animation-delay:3.7s}
 .bm-ov-stat>*,.bm-ov-card>*,.bm-ov-head>*{position:relative;z-index:1}
 @keyframes bmCardSweep{0%,68%{left:-42%;opacity:0}74%{opacity:1}88%{left:118%;opacity:.8}100%{left:118%;opacity:0}}
 .bm-ov-head h2{position:relative;width:max-content;max-width:100%}.bm-ov-head h2:after{content:'';position:absolute;left:0;bottom:-7px;width:46%;height:2px;border-radius:99px;background:linear-gradient(90deg,#ff2944,rgba(255,96,73,.68),transparent);box-shadow:0 0 14px rgba(255,41,68,.7);animation:bmTitleGlow 3.8s ease-in-out infinite}
 @keyframes bmTitleGlow{0%,100%{width:34%;opacity:.62}50%{width:76%;opacity:1}}
 .bm-ov-dot{box-shadow:0 0 0 0 rgba(77,156,255,.45);animation:bmStatusPulse 2.2s ease-out infinite}.bm-ov-dot.green{box-shadow:0 0 0 0 rgba(43,209,128,.5)}.bm-ov-dot.red{box-shadow:0 0 0 0 rgba(255,67,89,.5)}
 @keyframes bmStatusPulse{0%{transform:scale(.92);filter:brightness(.9)}45%{transform:scale(1.12);filter:brightness(1.25)}100%{transform:scale(.92);filter:brightness(.9);box-shadow:0 0 0 8px transparent}}
 .bm-ov-event{animation:bmEventIn .48s cubic-bezier(.2,.8,.2,1) both}.bm-ov-event:nth-child(2){animation-delay:.05s}.bm-ov-event:nth-child(3){animation-delay:.1s}.bm-ov-event:nth-child(4){animation-delay:.15s}.bm-ov-event:nth-child(5){animation-delay:.2s}.bm-ov-event:nth-child(6){animation-delay:.25s}.bm-ov-event:nth-child(7){animation-delay:.3s}.bm-ov-event:nth-child(8){animation-delay:.35s}
 @keyframes bmEventIn{from{opacity:0;transform:translateX(10px)}to{opacity:1;transform:translateX(0)}}
 .bm-ov-clock small:before{content:'●';display:inline-block;margin-right:6px;color:#28d17c;font-size:9px;filter:drop-shadow(0 0 5px #28d17c);animation:bmLiveBlink 1.8s ease-in-out infinite}
 @keyframes bmLiveBlink{0%,100%{opacity:.35}50%{opacity:1}}
 .bm-ov-head:before{content:'';position:absolute;z-index:0;left:-24%;top:0;width:20%;height:1px;background:linear-gradient(90deg,transparent,#ff4359,rgba(96,166,255,.9),transparent);box-shadow:0 0 12px rgba(255,67,89,.5);animation:bmScanLine 7s linear infinite;pointer-events:none}
 @keyframes bmScanLine{0%{left:-24%;opacity:0}8%{opacity:1}72%{opacity:.8}100%{left:118%;opacity:0}}
 body.bm-reference-admin .btn{transition:transform .16s ease,box-shadow .2s ease,filter .2s ease!important}body.bm-reference-admin .btn:hover{transform:translateY(-1px);filter:brightness(1.07);box-shadow:0 9px 24px rgba(0,0,0,.3)!important}
 @media(prefers-reduced-motion:reduce){body.bm-reference-admin .app,.bm-ov-stat:after,.bm-ov-card:after,.bm-ov-head:after,.bm-ov-head h2:after,.bm-ov-dot,.bm-ov-event,.bm-ov-clock small:before,.bm-ov-head:before{animation:none!important}.bm-ov-stat:hover,.bm-ov-card:hover,body.bm-reference-admin .moto-item:hover,body.bm-reference-admin .btn:hover{transform:none!important}}
 `;document.head.appendChild(s);
})();
