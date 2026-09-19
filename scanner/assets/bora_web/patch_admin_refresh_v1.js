
/* GPS ORIGINAL — sincroniza a sessão do motoboy diretamente com a ponte Android */
(function bmSyncOriginalRiderGpsBridge(){
  if(new URLSearchParams(location.search).get('app')!=='motoboy') return;
  if(window.__bmOriginalGpsBridgeSyncV1) return;
  window.__bmOriginalGpsBridgeSyncV1=true;

  let lastKey='';
  async function sync(){
    try{
      if(typeof sb==='undefined' || typeof rider==='undefined' || !rider?.profile?.id) return;
      const bridge=window.AndroidBora;
      if(!bridge || typeof bridge.setRiderGpsSession!=='function') return;

      const session=(await sb.auth.getSession())?.data?.session||null;
      if(!session?.access_token) return;

      const key=String(rider.profile.id)+'|'+String(session.access_token).slice(-24);
      if(key===lastKey) return;
      lastKey=key;

      bridge.setRiderGpsSession(
        String(session.access_token),
        String(rider.profile.id),
        String(rider.profile.nome||'')
      );
    }catch(e){
      console.warn('bm-original-gps-bridge-sync',e?.message||e);
    }
  }

  const timer=setInterval(sync,2000);
  window.addEventListener('focus',sync);
  window.addEventListener('online',sync);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)sync()});
  setTimeout(sync,300);
  setTimeout(sync,1500);
  setTimeout(sync,4000);
})();

/* GPS PANEL V3 — mapa nativo por tiles, sem Leaflet */
(function(){
 if(new URLSearchParams(location.search).get('app')==='motoboy') return;
 if(window.__bmGpsPanelV3) return;
 window.__bmGpsPanelV3=true;

 const ENDPOINT='https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-rider-location';
 const REFRESH=20000;
 const TILE=256;
 let state={motoboys:[],locations:[],loading:false,error:''};
 let view={lat:-22.37,lng:-41.79,zoom:16};
 let dragging=null,resizeObs=null,lastTileSig='',modernMap=null,modernLib=null,modernMarkers=[],modernLoading=null;
 let savedMapMode='';try{savedMapMode=localStorage.getItem('bm_gps_map_mode_v2')||''}catch(_){savedMapMode=''}
 let mapMode=['modern','street','sat'].includes(savedMapMode)?savedMapMode:'modern';

 function escGps(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
 function age(v){const n=new Date(v||0).getTime();return Number.isFinite(n)?Date.now()-n:Infinity}
 function status(loc){if(!loc)return {k:'none',t:'SEM GPS'};return age(loc.updated_at)<=90000?{k:'live',t:'GPS AO VIVO'}:{k:'stale',t:'SEM SINAL'}}
 function last(loc){
   if(!loc?.updated_at)return 'Nunca enviou localização';
   const d=new Date(loc.updated_at),a=age(loc.updated_at);
   if(a<60000)return 'Agora · '+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
   if(a<3600000)return Math.max(1,Math.round(a/60000))+' min atrás';
   return d.toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
 }
 function speed(loc){const v=Number(loc?.speed_mps);return Number.isFinite(v)&&v>=0?(v*3.6).toFixed(v*3.6<10?1:0).replace('.',',')+' km/h':'— km/h'}
 function acc(loc){const v=Number(loc?.accuracy_m);return Number.isFinite(v)&&v>=0?'±'+Math.round(v)+' m':'±— m'}
 function byId(){return new Map((state.locations||[]).map(x=>[String(x.motoboy_id),x]))}

 function css(){
   if(document.getElementById('bmGpsV3Style'))return;
   const s=document.createElement('style');s.id='bmGpsV3Style';s.textContent=`
   #bmGpsPanelV3{margin:0 0 14px;border:1px solid #ffffff16;border-radius:18px;overflow:hidden;background:linear-gradient(145deg,#10171f,#0a1016);box-shadow:0 18px 55px #0003}
   .g3h{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:14px 16px;border-bottom:1px solid #ffffff10}.g3h b{font-size:17px}.g3h small{display:block;color:#8e9aa6;margin-top:2px}.g3btn{border:1px solid #ffffff16;background:#252b32;color:#fff;border-radius:10px;padding:8px 11px;font-weight:800;cursor:pointer}
   .g3mapbar{display:flex;align-items:center;gap:7px;flex-wrap:wrap;padding:9px 12px;border-bottom:1px solid #ffffff0d;background:#0b1117}.g3mapbar>span{font-size:9px;font-weight:950;letter-spacing:.1em;color:#7f8c99;margin-right:2px}.g3mode{border:1px solid #ffffff18;background:#17202a;color:#aeb9c4;border-radius:9px;padding:7px 10px;font-size:9px;font-weight:950;cursor:pointer}.g3mode.active{border-color:#39d98a66;background:#153323;color:#7aefb0;box-shadow:inset 0 0 0 1px #39d98a16}.g3mode-status{margin-left:auto;color:#8f9ca9;font-size:9px;white-space:nowrap}.g3mode-status b{color:#fff}.g3mode-status:before{content:'●';color:#2bd180;margin-right:5px;filter:drop-shadow(0 0 4px #2bd180)}
   .g3body{display:grid;grid-template-columns:320px minmax(0,1fr);min-height:365px}.g3list{padding:10px;border-right:1px solid #ffffff10;max-height:430px;overflow:auto;background:#0c1218}.g3map{position:relative;min-height:365px;background:#d8dee2;overflow:hidden;touch-action:none;user-select:none}.g3canvas{position:absolute;inset:0;overflow:hidden;background:#d8dee2}.g3modern{position:absolute;inset:0;z-index:5}.g3modern.hidden{display:none}.g3modern-marker{display:flex;align-items:center;gap:6px;transform:translateY(-8px)}.g3modern-dot{width:15px;height:15px;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 10px #0008}.g3modern-dot.live{background:#19c875}.g3modern-dot.stale{background:#f59e0b}.g3modern-dot.none{background:#64748b}.g3modern-name{padding:4px 7px;border-radius:7px;background:#111d;color:#fff;font-size:10px;font-weight:900;box-shadow:0 2px 8px #0007;white-space:nowrap}.g3tilt{position:absolute;z-index:55;right:10px;bottom:12px;border:1px solid #ffffff55;background:#101820e8;color:#fff;border-radius:9px;padding:7px 9px;font-size:9px;font-weight:900;cursor:pointer}.g3tile{position:absolute;width:256px!important;height:256px!important;max-width:none!important;max-height:none!important;object-fit:cover;user-select:none;pointer-events:none}.g3marker{position:absolute;z-index:30;transform:translate(-50%,-50%);pointer-events:none}.g3dot{width:15px;height:15px;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 8px #0009}.g3marker.live .g3dot{background:#19c875}.g3marker.stale .g3dot{background:#f59e0b}.g3marker.none .g3dot{background:#64748b}.g3label{position:absolute;left:50%;bottom:20px;transform:translateX(-50%);white-space:nowrap;padding:4px 7px;border-radius:7px;background:#111c;color:#fff;font-size:10px;font-weight:900;box-shadow:0 2px 8px #0008}.g3zoom{position:absolute;z-index:50;right:10px;top:10px;display:grid;gap:5px}.g3zoom button{width:34px;height:34px;border:1px solid #0002;border-radius:8px;background:#fff;color:#111;font-size:20px;font-weight:900;box-shadow:0 2px 8px #0003}.g3attr{position:absolute;z-index:40;right:4px;bottom:3px;padding:2px 5px;border-radius:4px;background:#fffd;color:#333;font-size:9px}.g3hint{position:absolute;z-index:35;left:10px;top:10px;padding:5px 8px;border-radius:8px;background:#0b1220cc;color:#dbe4ee;font-size:9px}
   .g3row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding:10px;margin-bottom:8px;border:1px solid #ffffff0d;border-radius:12px;background:#121a22}.g3name{font-weight:900}.g3meta{margin-top:4px;color:#8f9ca9;font-size:10px;line-height:1.45}.g3badge{display:inline-flex;margin-top:6px;padding:4px 7px;border-radius:999px;font-size:9px;font-weight:950}.g3badge.live{background:#143924;color:#6ee7a7}.g3badge.stale{background:#3a2a11;color:#f8c866}.g3badge.none{background:#242a31;color:#9ca7b2}.g3center{align-self:center;border:1px solid #ffffff16;background:#2a3037;color:#fff;border-radius:9px;padding:7px 9px;font-size:10px;font-weight:800}.g3center:disabled{opacity:.35}.g3err{padding:14px;color:#fca5a5;font-size:11px}.g3empty{padding:24px;text-align:center;color:#7f8b96;font-size:11px}
   @media(max-width:900px){.g3h{align-items:flex-start}.g3h>div:last-child{display:flex;align-items:flex-end;flex-direction:column;gap:6px}.g3mapbar{position:sticky;top:0;z-index:60}.g3mode-status{width:100%;margin-left:0}.g3body{grid-template-columns:1fr}.g3list{border-right:0;border-bottom:1px solid #ffffff10;max-height:250px}.g3map{min-height:340px}}
   `;document.head.appendChild(s);
 }

 function html(){
   const lm=byId(),online=(state.motoboys||[]).filter(m=>status(lm.get(String(m.id))).k==='live').length;
   const rows=(state.motoboys||[]).map(m=>{const l=lm.get(String(m.id)),st=status(l),ok=l&&Number.isFinite(Number(l.latitude))&&Number.isFinite(Number(l.longitude));return `
     <div class="g3row"><div><div class="g3name">${escGps(m.nome||'Motoboy')}</div><span class="g3badge ${st.k}">${st.t}</span><div class="g3meta">${escGps(last(l))}<br>${escGps(acc(l))} · ${escGps(speed(l))}</div></div><button class="g3center" data-g3="${escGps(m.id)}" ${ok?'':'disabled'}>Centralizar</button></div>`}).join('');
   return `<section id="bmGpsPanelV3"><div class="g3h"><div><b>⌖ GPS DOS MOTOBOYS</b><small>LOCALIZAÇÃO EM TEMPO REAL · MAPA VETORIAL 3D</small></div><div><span id="g3online" style="color:#94a3b8;font-size:11px;margin-right:8px">${online} online · atualiza a cada 20s</span><button id="g3refresh" class="g3btn">↻ Atualizar GPS</button></div></div><div class="g3mapbar"><span>MAPAS ATIVOS</span><button id="g3modernBtn" class="g3mode ${mapMode==='modern'?'active':''}">3D Moderno</button><button id="g3street" class="g3mode ${mapMode==='street'?'active':''}">Ruas HD</button><button id="g3sat" class="g3mode ${mapMode==='sat'?'active':''}">Satélite</button><div id="g3activeMap" class="g3mode-status">Ativo: <b>${mapMode==='modern'?'3D Moderno':mapMode==='sat'?'Satélite':'Ruas HD'}</b></div></div><div class="g3body"><div class="g3list">${state.error?'<div class="g3err">'+escGps(state.error)+'</div>':(rows||'<div class="g3empty">Nenhum motoboy ativo.</div>')}</div><div class="g3map" id="g3map"><div class="g3canvas" id="g3canvas"></div><div class="g3modern ${mapMode==='modern'?'':'hidden'}" id="g3modern"></div><div class="g3hint" id="g3hint">${mapMode==='modern'?'Arraste · incline · gire o mapa':'Arraste para mover o mapa'}</div><div class="g3zoom"><button id="g3plus">+</button><button id="g3minus">−</button></div><button id="g3tilt" class="g3tilt" type="button">3D</button><div class="g3attr" id="g3attr">${mapMode==='modern'?'© OpenFreeMap · OpenStreetMap · MapLibre':'© Esri · HERE · Garmin · OpenStreetMap contributors'}</div></div></div></section>`;
 }

 function mount(){
   css();
   const main=document.querySelector('.admin-main');
   if(!main)return false;
   let created=false;
   if(!document.getElementById('bmGpsPanelV3')){
     document.getElementById('bmGpsPanelV2')?.remove();
     document.getElementById('bmGpsPanel')?.remove();
     const d=document.createElement('div');d.innerHTML=html();const node=d.firstElementChild;
     const ws=main.querySelector('.moto-workspace');
     if(ws)main.insertBefore(node,ws);else main.appendChild(node);
     created=true;
   }
   bind();
   if(created)requestAnimationFrame(()=>renderMap(true));
   return true;
 }

 function repaint(){
   const old=document.getElementById('bmGpsPanelV3');
   if(!old){mount();return}
   const d=document.createElement('div');d.innerHTML=html();const fresh=d.firstElementChild;
   const list=old.querySelector('.g3list'),fl=fresh.querySelector('.g3list');
   if(list&&fl)list.innerHTML=fl.innerHTML;
   const o=old.querySelector('#g3online'),fo=fresh.querySelector('#g3online');if(o&&fo)o.textContent=fo.textContent;
   bind();renderMap();
 }

 function worldPx(lat,lng,z){
   const n=Math.pow(2,z)*TILE;
   const x=(lng+180)/360*n;
   const cl=Math.max(-85.05112878,Math.min(85.05112878,lat));
   const rad=cl*Math.PI/180;
   const y=(1-Math.log(Math.tan(rad)+1/Math.cos(rad))/Math.PI)/2*n;
   return {x,y};
 }
 function latLngFromWorld(x,y,z){
   const n=Math.pow(2,z)*TILE;
   const lng=x/n*360-180;
   const a=Math.PI*(1-2*y/n);
   const lat=180/Math.PI*Math.atan(Math.sinh(a));
   return {lat,lng};
 }

 async function loadModernLib(){
   if(modernLib)return modernLib;
   if(window.maplibregl){modernLib=window.maplibregl;return modernLib}
   if(modernLoading)return modernLoading;
   modernLoading=new Promise((resolve,reject)=>{
     if(!document.querySelector('link[data-bm-maplibre]')){
       const l=document.createElement('link');l.rel='stylesheet';l.dataset.bmMaplibre='1';l.href='https://unpkg.com/maplibre-gl@5/dist/maplibre-gl.css';document.head.appendChild(l);
     }
     const existing=document.querySelector('script[data-bm-maplibre]');
     if(existing){
       const poll=setInterval(()=>{if(window.maplibregl){clearInterval(poll);modernLib=window.maplibregl;resolve(modernLib)}},80);
       setTimeout(()=>{clearInterval(poll);if(!window.maplibregl)reject(new Error('MapLibre não carregou.'))},10000);
       return;
     }
     const s=document.createElement('script');s.src='https://unpkg.com/maplibre-gl@5/dist/maplibre-gl.js';s.async=true;s.dataset.bmMaplibre='1';
     s.onload=()=>{if(window.maplibregl){modernLib=window.maplibregl;resolve(modernLib)}else reject(new Error('MapLibre indisponível.'))};
     s.onerror=()=>reject(new Error('Falha ao carregar o mapa 3D.'));
     document.head.appendChild(s);
   });
   try{return await modernLoading}finally{modernLoading=null}
 }
 function clearModernMarkers(){modernMarkers.forEach(m=>{try{m.remove()}catch(_){}});modernMarkers=[]}
 function addModernBuildings(){
   if(!modernMap||!modernMap.isStyleLoaded?.())return;
   if(modernMap.getLayer('bm-3d-buildings'))return;
   const layers=modernMap.getStyle()?.layers||[];
   const ref=layers.find(l=>l?.['source-layer']==='building'&&l?.source&&l.type==='fill')||layers.find(l=>String(l?.id||'').toLowerCase().includes('building')&&l?.source&&l?.['source-layer']);
   if(!ref)return;
   try{
     modernMap.addLayer({
       id:'bm-3d-buildings',type:'fill-extrusion',source:ref.source,'source-layer':ref['source-layer'],minzoom:14,
       filter:ref.filter||undefined,
       paint:{
         'fill-extrusion-color':['interpolate',['linear'],['zoom'],14,'#cfd6df',17,'#f2f5f8'],
         'fill-extrusion-height':['coalesce',['to-number',['get','render_height']],['to-number',['get','height']],['*',['coalesce',['to-number',['get','building:levels']],3],3],8],
         'fill-extrusion-base':['coalesce',['to-number',['get','render_min_height']],['to-number',['get','min_height']],0],
         'fill-extrusion-opacity':0.78
       }
     });
   }catch(e){console.warn('bm-3d-buildings',e?.message||e)}
 }
 function syncModernMarkers(){
   if(!modernMap||!modernLib)return;
   clearModernMarkers();
   const lm=byId();
   (state.motoboys||[]).forEach(m=>{
     const l=lm.get(String(m.id)),lat=Number(l?.latitude),lng=Number(l?.longitude);
     if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
     const st=status(l),el=document.createElement('div');el.className='g3modern-marker';
     el.innerHTML='<div class="g3modern-dot '+st.k+'"></div><div class="g3modern-name">'+escGps(m.nome||'Motoboy')+'</div>';
     try{modernMarkers.push(new modernLib.Marker({element:el,anchor:'bottom'}).setLngLat([lng,lat]).addTo(modernMap))}catch(_){}
   });
 }
 function fallbackModern(reason){
   console.warn('bm-modern-map',reason?.message||reason||'falha');
   mapMode='street';lastTileSig='';
   try{localStorage.setItem('bm_gps_map_mode_v2','street')}catch(_){}
   document.getElementById('g3modernBtn')?.classList.remove('active');
   document.getElementById('g3street')?.classList.add('active');
   document.getElementById('g3sat')?.classList.remove('active');
   const active=document.getElementById('g3activeMap');if(active)active.innerHTML='Ativo: <b>Ruas HD</b>';
   const h=document.getElementById('g3hint');if(h)h.textContent='3D indisponível neste navegador · Ruas HD ativado';
   setModernVisibility();renderMap(true);
 }
 async function ensureModernMap(){
   if(mapMode!=='modern')return;
   const host=document.getElementById('g3modern');if(!host)return;
   host.classList.remove('hidden');
   try{
     const ml=await loadModernLib();
     if(mapMode!=='modern')return;
     if(!modernMap){
       let loaded=false;
       modernMap=new ml.Map({
         container:host,
         style:'https://tiles.openfreemap.org/styles/liberty',
         center:[view.lng,view.lat],zoom:view.zoom,pitch:55,bearing:-18,
         attributionControl:false,maxPitch:75
       });
       const timeout=setTimeout(()=>{if(!loaded&&mapMode==='modern')fallbackModern(new Error('Tempo esgotado ao carregar o 3D.'))},12000);
       modernMap.on('load',()=>{loaded=true;clearTimeout(timeout);addModernBuildings();syncModernMarkers();modernMap.resize()});
       modernMap.on('styledata',()=>addModernBuildings());
       modernMap.on('moveend',()=>{const cc=modernMap.getCenter();view.lng=cc.lng;view.lat=cc.lat;view.zoom=modernMap.getZoom()});
       modernMap.on('error',ev=>{
         const msg=String(ev?.error?.message||'');
         if(!loaded&&/style|source|webgl|worker|fetch|network/i.test(msg))fallbackModern(ev.error||new Error(msg));
       });
     }else{
       modernMap.resize();syncModernMarkers();
     }
   }catch(e){fallbackModern(e)}
 }
 function setModernVisibility(){
   const modern=document.getElementById('g3modern'),canvas=document.getElementById('g3canvas');
   const hint=document.getElementById('g3hint'),attr=document.getElementById('g3attr'),tilt=document.getElementById('g3tilt');
   if(modern)modern.classList.toggle('hidden',mapMode!=='modern');
   if(canvas)canvas.style.display=mapMode==='modern'?'none':'block';
   if(hint)hint.textContent=mapMode==='modern'?'Arraste · incline · gire o mapa':'Arraste para mover o mapa';
   if(attr)attr.textContent=mapMode==='modern'?'© OpenFreeMap · OpenStreetMap · MapLibre':'© Esri · HERE · Garmin · OpenStreetMap contributors';
   if(tilt)tilt.style.display=mapMode==='modern'?'block':'none';
   if(mapMode==='modern')void ensureModernMap();
 }
 function renderMap(forceTiles=false){
   const el=document.getElementById('g3map'),canvas=document.getElementById('g3canvas');
   if(!el||!canvas)return;
   setModernVisibility();
   if(mapMode==='modern'){if(modernMap){modernMap.resize();syncModernMarkers()}return;}
   const w=Math.max(320,el.clientWidth||600),h=Math.max(300,el.clientHeight||365);
   const center=worldPx(view.lat,view.lng,view.zoom),left=center.x-w/2,top=center.y-h/2;
   const minX=Math.floor(left/TILE)-1,maxX=Math.floor((left+w)/TILE)+1,minY=Math.floor(top/TILE)-1,maxY=Math.floor((top+h)/TILE)+1;
   const maxTile=Math.pow(2,view.zoom);
   const tileSig=[mapMode,view.zoom,Math.round(left),Math.round(top),Math.round(w),Math.round(h)].join('|');
   let tileLayer=canvas.querySelector('.g3tiles');
   let markerLayer=canvas.querySelector('.g3markers');
   if(!tileLayer){tileLayer=document.createElement('div');tileLayer.className='g3tiles';tileLayer.style.cssText='position:absolute;inset:0;overflow:hidden';canvas.appendChild(tileLayer)}
   if(!markerLayer){markerLayer=document.createElement('div');markerLayer.className='g3markers';markerLayer.style.cssText='position:absolute;inset:0;pointer-events:none';canvas.appendChild(markerLayer)}
   if(forceTiles||tileSig!==lastTileSig){
     lastTileSig=tileSig;
     let tiles='';
     for(let ty=minY;ty<=maxY;ty++){
       if(ty<0||ty>=maxTile)continue;
       for(let tx=minX;tx<=maxX;tx++){
         const wrapped=((tx%maxTile)+maxTile)%maxTile;
         const px=tx*TILE-left,py=ty*TILE-top;
         const street='https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/'+view.zoom+'/'+ty+'/'+wrapped;
         const satellite='https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/'+view.zoom+'/'+ty+'/'+wrapped;
         const labels='https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/'+view.zoom+'/'+ty+'/'+wrapped;
         const fallback='https://tile.openstreetmap.org/'+view.zoom+'/'+wrapped+'/'+ty+'.png';
         const src=mapMode==='sat'?satellite:street;
         tiles+='<img class="g3tile" draggable="false" style="left:'+Math.round(px)+'px;top:'+Math.round(py)+'px;z-index:1" src="'+src+'" onerror="if(this.dataset.fb!==\'1\'){this.dataset.fb=\'1\';this.src=\''+fallback+'\'}">';
         if(mapMode==='sat')tiles+='<img class="g3tile" draggable="false" style="left:'+Math.round(px)+'px;top:'+Math.round(py)+'px;z-index:2;pointer-events:none" src="'+labels+'">';
       }
     }
     tileLayer.innerHTML=tiles;
   }
   const lm=byId();
   let markers='';
   (state.motoboys||[]).forEach(m=>{
     const l=lm.get(String(m.id)),lat=Number(l?.latitude),lng=Number(l?.longitude);
     if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
     const p=worldPx(lat,lng,view.zoom),x=p.x-left,y=p.y-top;
     if(x<-40||x>w+40||y<-40||y>h+40)return;
     const st=status(l);
     markers+='<div class="g3marker '+st.k+'" style="left:'+Math.round(x)+'px;top:'+Math.round(y)+'px"><div class="g3label">'+escGps(m.nome||'Motoboy')+'</div><div class="g3dot"></div></div>';
   });
   markerLayer.innerHTML=markers;
 }
 function fitAll(){
   const lm=byId(),pts=[];
   (state.motoboys||[]).forEach(m=>{const l=lm.get(String(m.id)),lat=Number(l?.latitude),lng=Number(l?.longitude);if(Number.isFinite(lat)&&Number.isFinite(lng))pts.push({lat,lng})});
   if(!pts.length)return;
   view.lat=pts.reduce((a,p)=>a+p.lat,0)/pts.length;view.lng=pts.reduce((a,p)=>a+p.lng,0)/pts.length;
   const el=document.getElementById('g3map'),w=el?.clientWidth||600,h=el?.clientHeight||365;
   for(let z=17;z>=8;z--){
     const ps=pts.map(p=>worldPx(p.lat,p.lng,z));
     const xs=ps.map(p=>p.x),ys=ps.map(p=>p.y);
     if(Math.max(...xs)-Math.min(...xs)<=w-100&&Math.max(...ys)-Math.min(...ys)<=h-100){view.zoom=z;break}
   }
 }

 function bind(){
   const r=document.getElementById('g3refresh');if(r)r.onclick=()=>load(false);
   document.querySelectorAll('[data-g3]').forEach(b=>b.onclick=()=>center(b.dataset.g3));
   const plus=document.getElementById('g3plus'),minus=document.getElementById('g3minus');
   if(plus)plus.onclick=e=>{e.stopPropagation();if(mapMode==='modern'&&modernMap){modernMap.zoomIn();return}view.zoom=Math.min(19,view.zoom+1);renderMap(true)};
   if(minus)minus.onclick=e=>{e.stopPropagation();if(mapMode==='modern'&&modernMap){modernMap.zoomOut();return}view.zoom=Math.max(5,view.zoom-1);renderMap(true)};
   const modernBtn=document.getElementById('g3modernBtn'),streetBtn=document.getElementById('g3street'),satBtn=document.getElementById('g3sat'),activeMap=document.getElementById('g3activeMap');
   const setMode=mode=>{
     mapMode=['modern','sat'].includes(mode)?mode:'street';lastTileSig='';
     try{localStorage.setItem('bm_gps_map_mode_v2',mapMode)}catch(_){}
     modernBtn?.classList.toggle('active',mapMode==='modern');
     streetBtn?.classList.toggle('active',mapMode==='street');
     satBtn?.classList.toggle('active',mapMode==='sat');
     if(activeMap)activeMap.innerHTML='Ativo: <b>'+(mapMode==='modern'?'3D Moderno':mapMode==='sat'?'Satélite':'Ruas HD')+'</b>';
     setModernVisibility();renderMap(true);
   };
   if(modernBtn){modernBtn.classList.toggle('active',mapMode==='modern');modernBtn.onclick=e=>{e.stopPropagation();setMode('modern')}}
   if(streetBtn){streetBtn.classList.toggle('active',mapMode==='street');streetBtn.onclick=e=>{e.stopPropagation();setMode('street')}}
   if(satBtn){satBtn.classList.toggle('active',mapMode==='sat');satBtn.onclick=e=>{e.stopPropagation();setMode('sat')}}
   const el=document.getElementById('g3map');
   if(el&&!el.dataset.dragBound){
     el.dataset.dragBound='1';
     el.addEventListener('pointerdown',e=>{if(mapMode==='modern'||e.target.closest('button'))return;const c=worldPx(view.lat,view.lng,view.zoom);dragging={x:e.clientX,y:e.clientY,cx:c.x,cy:c.y};el.setPointerCapture?.(e.pointerId)});
     el.addEventListener('pointermove',e=>{if(mapMode==='modern'||!dragging)return;const dx=e.clientX-dragging.x,dy=e.clientY-dragging.y;const ll=latLngFromWorld(dragging.cx-dx,dragging.cy-dy,view.zoom);view.lat=ll.lat;view.lng=ll.lng;renderMap()});
     const end=()=>{dragging=null};el.addEventListener('pointerup',end);el.addEventListener('pointercancel',end);
   }
   const tilt=document.getElementById('g3tilt');if(tilt)tilt.onclick=e=>{e.stopPropagation();if(!modernMap)return;const p=modernMap.getPitch();modernMap.easeTo({pitch:p>20?0:58,bearing:p>20?0:-18,duration:500})};
   if(el&&!resizeObs&&window.ResizeObserver){resizeObs=new ResizeObserver(()=>renderMap(true));resizeObs.observe(el)}
 }

 function center(id){
   const l=byId().get(String(id)),lat=Number(l?.latitude),lng=Number(l?.longitude);
   if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
   view.lat=lat;view.lng=lng;view.zoom=16;if(mapMode==='modern'&&modernMap){modernMap.flyTo({center:[lng,lat],zoom:17,pitch:55,bearing:-18,duration:800});return}renderMap();
 }

 async function load(silent=true){
   if(state.loading)return;state.loading=true;
   try{
     if(typeof sb==='undefined')throw new Error('Supabase ainda não carregou.');
     const sess=(await sb.auth.getSession())?.data?.session;if(!sess?.access_token)throw new Error('Sessão administrativa expirada.');
     const r=await fetch(ENDPOINT,{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+sess.access_token},body:JSON.stringify({action:'list'})});
     const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||('HTTP '+r.status));
     const first=!(state.locations||[]).length;
     state.motoboys=j.motoboys||[];state.locations=j.locations||[];state.error='';
     if(first&&state.locations.length)fitAll();
     if(mapMode==='modern'&&modernMap)syncModernMarkers();
   }catch(e){state.error=e?.message||'Não foi possível atualizar o GPS.'}
   finally{state.loading=false;repaint()}
 }

 const tick=setInterval(()=>{if(mount()){if(!state.loading&&!(state.motoboys||[]).length)load(true)}},700);
 setTimeout(()=>load(true),1200);
 setInterval(()=>{if(!document.hidden&&document.querySelector('.admin-main'))load(true)},REFRESH);
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


/* RESUMO COMPACTO DOS MOTOBOYS — carregado pelo patch principal do painel */
(()=>{
 if(new URLSearchParams(location.search).get('app')==='motoboy')return;
 if(window.__bmRiderSummaryV1)return;window.__bmRiderSummaryV1=true;

 const money=v=>{try{return BRL(Number(v||0))}catch(_){return 'R$ '+Number(v||0).toFixed(2).replace('.',',')}};
 const safe=s=>typeof esc==='function'?esc(String(s??'')):String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
 const norm=s=>String(s??'').trim().toLocaleLowerCase('pt-BR');
 let period='today',customStart='',customEnd='',refreshTimer=null;

 function spNow(){
  const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date());
  const o={};for(const p of ps)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function addDays(ds,n){const d=new Date(ds+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10)}
 function rangeFor(kind){
  const today=spNow().date;
  if(kind==='today')return {start:today,end:today,label:'HOJE'};
  if(kind==='week'){const d=new Date(today+'T12:00:00Z'),dow=d.getUTCDay(),back=dow===0?6:dow-1,start=addDays(today,-back);return {start,end:addDays(start,6),label:'ESTA SEMANA'}}
  if(kind==='month'){const [y,m]=today.split('-').map(Number),last=new Date(Date.UTC(y,m,0,12)).toISOString().slice(0,10);return {start:`${y}-${String(m).padStart(2,'0')}-01`,end:last,label:'ESTE MÊS'}}
  if(kind==='custom'&&customStart&&customEnd)return {start:customStart,end:customEnd,label:`${customStart.split('-').reverse().join('/')} a ${customEnd.split('-').reverse().join('/')}`};
  return {start:today,end:today,label:'HOJE'};
 }
 function currentShift(){const p=spNow();return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10'}
 function spParts(v){
  if(!v)return null;const d=v instanceof Date?v:new Date(v);if(!Number.isFinite(d.getTime()))return null;
  const ps=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(d);
  const o={};for(const p of ps)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function inShift(iso,date,shift){
  const p=spParts(iso);if(!p||p.date!==date)return false;
  if(shift==='10_17')return p.mins>=10*60&&p.mins<17*60;
  if(shift==='17_24')return p.mins>=17*60;
  return false;
 }
 function ensureRow(map,m){
  const key=String(m?.id||norm(m?.nome));
  if(!map.has(key))map.set(key,{id:m?.id||null,nome:String(m?.nome||'Motoboy'),comandas:0,total:0,worked:false});
  return map.get(key);
 }
 function rowForDetail(map,d){
  const byId=d?.motoboy_id&&map.get(String(d.motoboy_id));if(byId)return byId;
  const name=norm(d?.nome);for(const r of map.values())if(norm(r.nome)===name)return r;
  return null;
 }
 async function collect(){
  const range=rangeFor(period),map=new Map();
  for(const m of (adminState?.motoboys||[]))ensureRow(map,m);

  const [q,jq]=await Promise.all([
   sb.from('kh_motoboy_relatorios_turnos')
    .select('data,turno,detalhes')
    .gte('data',range.start).lte('data',range.end)
    .order('data',{ascending:true}).order('turno',{ascending:true}).limit(500),
   sb.from('kh_motoboy_jornadas')
    .select('motoboy_id,data,chegada_at,chegada_tipo')
    .gte('data',range.start).lte('data',range.end)
    .limit(3000)
  ]);
  if(q.error)throw q.error;if(jq.error)throw jq.error;
  const reports=q.data||[];
  for(const j of (jq.data||[])){
   if(!j?.chegada_at||j?.chegada_tipo==='nao_compareceu')continue;
   const m=(adminState?.motoboys||[]).find(x=>String(x.id)===String(j.motoboy_id));
   if(m)ensureRow(map,m).worked=true;
  }
  for(const rep of reports){
   for(const d of (Array.isArray(rep.detalhes)?rep.detalhes:[])){
    const r=rowForDetail(map,d);if(!r)continue;
    const resumo=d?.resumo||{};
    r.worked=true;
    r.comandas+=Number(resumo.entregas||0);
    r.total+=Number(d?.diaria||0)+Number(resumo.taxas||0);
   }
  }

  const today=spNow().date,shift=currentShift();
  if(range.start<=today&&range.end>=today&&shift!=='pre_10'){
   const alreadyClosed=reports.some(x=>String(x.data)===today&&String(x.turno)===shift);
   if(!alreadyClosed){
    for(const m of (adminState?.motoboys||[])){
     const r=ensureRow(map,m);
     const all=typeof entregasOf==='function'?entregasOf(m.id):[];
     const es=(all||[]).filter(e=>e?.status!=='cancelada'&&inShift(e?.created_at||e?.updated_at,today,shift));
     const j=typeof jornadaOf==='function'?jornadaOf(m.id):null;
     if(j?.chegada_at&&j?.chegada_tipo!=='nao_compareceu')r.worked=true;
     const base=j?.chegada_at&&inShift(j.chegada_at,today,shift)?Number(j.base_valor||0):0;
     r.comandas+=es.length;
     r.total+=base+es.reduce((a,e)=>a+Number(e.valor||0),0);
    }
   }
  }

  const rows=[...map.values()]
   .filter(r=>period==='today'?r.worked:(r.worked||r.comandas>0||r.total>0))
   .sort((a,b)=>String(a.nome).localeCompare(String(b.nome),'pt-BR'));
  return {range,rows,totalComandas:rows.reduce((a,x)=>a+x.comandas,0),totalValor:rows.reduce((a,x)=>a+x.total,0)};
 }

 function installStyle(){
  if(document.getElementById('bmRiderSummaryStyle'))return;
  const s=document.createElement('style');s.id='bmRiderSummaryStyle';s.textContent=`
   #bmRiderSummaryBtn{width:auto!important;margin:0 8px 0 0!important;background:#18212b!important;border:1px solid rgba(255,255,255,.09)!important;color:#fff!important}
   .bm-rs-overlay{position:fixed;inset:0;z-index:100001;background:rgba(0,0,0,.78);display:flex;align-items:center;justify-content:center;padding:14px}
   .bm-rs-modal{width:min(700px,96vw);max-height:92vh;overflow:auto;background:#0d1217;border:1px solid rgba(255,255,255,.10);border-radius:18px;box-shadow:0 28px 90px rgba(0,0,0,.58);padding:16px}
   .bm-rs-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;position:sticky;top:-16px;background:#0d1217;padding:14px 0 12px;z-index:2;border-bottom:1px solid rgba(255,255,255,.08)}
   .bm-rs-head h3{margin:0;font-size:20px}.bm-rs-head small{display:block;margin-top:4px;color:#8f9aa6}.bm-rs-close{border:0;background:#252c33;color:#fff;width:36px;height:36px;border-radius:10px;font-size:22px;cursor:pointer}
   .bm-rs-tabs{display:flex;gap:6px;flex-wrap:wrap;margin:13px 0}.bm-rs-tab{border:1px solid rgba(255,255,255,.09);background:#151d25;color:#a9b3bd;border-radius:10px;padding:8px 10px;font-size:10px;font-weight:900;cursor:pointer}.bm-rs-tab.active{background:#173224;color:#82efb1;border-color:#2bd18055}
   .bm-rs-custom{display:grid;grid-template-columns:1fr 1fr auto;gap:7px;margin-bottom:12px}.bm-rs-custom input{min-width:0;background:#101820;color:#fff;border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:9px}.bm-rs-custom button{border:0;border-radius:10px;background:#27313b;color:#fff;padding:9px 12px;font-weight:900;cursor:pointer}
   .bm-rs-period{margin:5px 0 9px;color:#9aa5b0;font-size:10px;font-weight:900;text-transform:uppercase;letter-spacing:.06em}
   .bm-rs-actions{display:flex;justify-content:flex-end;margin:0 0 9px}.bm-rs-copy-all,.bm-rs-copy-one{border:1px solid rgba(43,209,128,.28);background:rgba(43,209,128,.09);color:#82efb1;border-radius:9px;font-weight:900;cursor:pointer}.bm-rs-copy-all{padding:8px 11px;font-size:10px}.bm-rs-copy-one{padding:6px 8px;font-size:8px}
   .bm-rs-list{display:grid;gap:6px}.bm-rs-row{display:grid;grid-template-columns:minmax(0,1fr) 105px 120px 62px;gap:8px;align-items:center;background:#121a22;border:1px solid rgba(255,255,255,.07);border-radius:12px;padding:11px 12px}.bm-rs-name{font-weight:900;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-rs-count{color:#a6b0ba;text-align:right;font-size:11px}.bm-rs-value{text-align:right;color:#63e99f;font-weight:950;white-space:nowrap}
   .bm-rs-total{display:grid;grid-template-columns:minmax(0,1fr) 105px 120px 62px;gap:8px;margin-top:10px;padding:12px;border-top:1px solid rgba(255,255,255,.1);font-weight:950}.bm-rs-total .bm-rs-count,.bm-rs-total .bm-rs-value{font-size:13px}
   .bm-rs-empty{padding:28px;text-align:center;color:#8b96a1}.bm-rs-loading{padding:24px;text-align:center;color:#9aa5b0}
   @media(max-width:560px){.bm-rs-row,.bm-rs-total{grid-template-columns:minmax(0,1fr) 62px 82px 54px;gap:5px}.bm-rs-modal{padding:12px}.bm-rs-head{top:-12px}.bm-rs-custom{grid-template-columns:1fr 1fr}.bm-rs-custom button{grid-column:1/-1}.bm-rs-count{font-size:8px}.bm-rs-value{font-size:10px}.bm-rs-copy-one{padding:5px 6px;font-size:7px}}
  `;document.head.appendChild(s);
 }
 function close(){if(refreshTimer){clearInterval(refreshTimer);refreshTimer=null}document.getElementById('bmRiderSummaryOverlay')?.remove()}
 function shell(){
  close();installStyle();
  const el=document.createElement('div');el.id='bmRiderSummaryOverlay';el.className='bm-rs-overlay';
  el.innerHTML=`<div class="bm-rs-modal"><div class="bm-rs-head"><div><h3>Resumo dos motoboys</h3><small>Nome · comandas · valor total</small></div><button class="bm-rs-close">×</button></div><div class="bm-rs-tabs"><button class="bm-rs-tab" data-rs-period="today">HOJE</button><button class="bm-rs-tab" data-rs-period="week">ESTA SEMANA</button><button class="bm-rs-tab" data-rs-period="month">ESTE MÊS</button><button class="bm-rs-tab" data-rs-period="custom">PERÍODO</button></div><div class="bm-rs-custom" id="bmRsCustom"><input id="bmRsStart" type="date"><input id="bmRsEnd" type="date"><button id="bmRsApply">APLICAR</button></div><div id="bmRsBody" class="bm-rs-loading">Carregando resumo...</div></div>`;
  document.body.appendChild(el);
  el.querySelector('.bm-rs-close').onclick=close;el.onclick=e=>{if(e.target===el)close()};
  el.querySelectorAll('[data-rs-period]').forEach(b=>b.onclick=()=>{period=b.dataset.rsPeriod;paintTabs();void render()});
  document.getElementById('bmRsApply').onclick=()=>{const s=document.getElementById('bmRsStart').value,e=document.getElementById('bmRsEnd').value;if(!s||!e)return typeof toast==='function'?toast('Escolha as duas datas.'):alert('Escolha as duas datas.');if(s>e)return typeof toast==='function'?toast('Período inválido.'):alert('Período inválido.');customStart=s;customEnd=e;period='custom';paintTabs();void render()};
  paintTabs();void render();refreshTimer=setInterval(()=>{if(document.getElementById('bmRiderSummaryOverlay'))void render(true)},30000);
 }
 function paintTabs(){document.querySelectorAll('[data-rs-period]').forEach(b=>b.classList.toggle('active',b.dataset.rsPeriod===period));const custom=document.getElementById('bmRsCustom');if(custom)custom.style.display=period==='custom'?'grid':'none'}
 async function copyText(text,msg='Resumo copiado.'){
  try{await navigator.clipboard.writeText(text)}
  catch(_){
   const ta=document.createElement('textarea');ta.value=text;ta.style.position='fixed';ta.style.opacity='0';document.body.appendChild(ta);ta.select();document.execCommand('copy');ta.remove();
  }
  if(typeof toast==='function')toast(msg);
 }
 function summaryText(x){
  const lines=[`RESUMO DOS MOTOBOYS — ${x.range.label}`,''];
  for(const r of x.rows)lines.push(`${r.nome} — ${r.comandas} comandas — ${money(r.total)}`);
  lines.push('',`Total de comandas: ${x.totalComandas}`,`Valor geral: ${money(x.totalValor)}`);
  return lines.join('\n');
 }
 async function render(silent=false){
  const body=document.getElementById('bmRsBody');if(!body)return;if(!silent)body.innerHTML='<div class="bm-rs-loading">Atualizando...</div>';
  try{
   const x=await collect();
   const rows=x.rows.map((r,i)=>`<div class="bm-rs-row"><div class="bm-rs-name">${safe(r.nome)}</div><div class="bm-rs-count">${r.comandas} comandas</div><div class="bm-rs-value">${money(r.total)}</div><button class="bm-rs-copy-one" data-rs-copy="${i}">COPIAR</button></div>`).join('');
   body.innerHTML=`<div class="bm-rs-period">${safe(x.range.label)}</div><div class="bm-rs-actions"><button id="bmRsCopyAll" class="bm-rs-copy-all">📋 COPIAR TUDO</button></div><div class="bm-rs-list">${rows||'<div class="bm-rs-empty">Nenhum motoboy trabalhou neste período.</div>'}</div><div class="bm-rs-total"><div>TOTAL</div><div class="bm-rs-count">${x.totalComandas} comandas</div><div class="bm-rs-value">${money(x.totalValor)}</div><div></div></div>`;
   document.getElementById('bmRsCopyAll').onclick=()=>copyText(summaryText(x),'Resumo copiado.');
   body.querySelectorAll('[data-rs-copy]').forEach(btn=>btn.onclick=()=>{const r=x.rows[Number(btn.dataset.rsCopy)];if(r)copyText(`${r.nome} — ${r.comandas} comandas — ${money(r.total)}`,`${r.nome} copiado.`)});

  }catch(e){console.error('rider-summary',e);body.innerHTML=`<div class="bm-rs-empty">Não foi possível carregar o resumo: ${safe(e?.message||'erro')}</div>`}
 }
 function installButton(){
  const head=document.querySelector('.admin-head');if(!head)return;
  let btn=document.getElementById('bmRiderSummaryBtn');
  if(!btn){btn=document.createElement('button');btn.id='bmRiderSummaryBtn';btn.className='btn secondary small';btn.type='button';btn.textContent='RESUMO';btn.onclick=shell}
  const logout=document.getElementById('logout');if(btn.parentElement!==head){if(logout)head.insertBefore(btn,logout);else head.appendChild(btn)}
 }
 installStyle();const obs=new MutationObserver(installButton);obs.observe(document.documentElement,{childList:true,subtree:true});setTimeout(installButton,0);setTimeout(installButton,350);setTimeout(installButton,1100);
})();
