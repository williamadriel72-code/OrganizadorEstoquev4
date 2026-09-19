/* DIAGNÓSTICO GPS ORIGINAL — mostra se o APK instalado possui a ponte nativa */
(function bmGpsNativeDiagnostic(){
  if(new URLSearchParams(location.search).get('app')!=='motoboy') return;
  if(window.__bmGpsNativeDiagnosticV1) return;
  window.__bmGpsNativeDiagnosticV1=true;

  function status(){
    const bridge=window.AndroidBora;
    const nativeGps=!!(bridge && typeof bridge.setRiderGpsSession==='function');
    return nativeGps;
  }

  function render(){
    try{
      const root=document.querySelector('.shell');
      if(!root) return;
      let box=document.getElementById('bmGpsNativeDiag');
      if(!box){
        box=document.createElement('div');
        box.id='bmGpsNativeDiag';
        box.style.cssText='margin:10px 16px 0;padding:10px 12px;border-radius:12px;font-size:12px;font-weight:800;border:1px solid #ffffff18;background:#151a1f;color:#d6dde5;';
        const header=root.querySelector('.top');
        if(header?.nextSibling) root.insertBefore(box,header.nextSibling); else root.prepend(box);
      }
      const ok=status();
      box.innerHTML=ok
        ? '<span style="color:#35d07f">GPS NATIVO DETECTADO</span> · aguardando permissão/localização do Android'
        : '<span style="color:#ffcc66">GPS NATIVO NÃO DETECTADO</span> · este APK precisa ser atualizado para a versão com GPS';
    }catch(e){}
  }

  setInterval(render,1500);
  setTimeout(render,300);
})();
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
 let view={lat:-22.37,lng:-41.79,zoom:13};
 let dragging=null,resizeObs=null;

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
   .g3body{display:grid;grid-template-columns:320px minmax(0,1fr);min-height:365px}.g3list{padding:10px;border-right:1px solid #ffffff10;max-height:430px;overflow:auto;background:#0c1218}.g3map{position:relative;min-height:365px;background:#d8dee2;overflow:hidden;touch-action:none;user-select:none}.g3canvas{position:absolute;inset:0;overflow:hidden;background:#d8dee2}.g3tile{position:absolute;width:256px!important;height:256px!important;max-width:none!important;max-height:none!important;object-fit:cover;user-select:none;pointer-events:none}.g3marker{position:absolute;z-index:30;transform:translate(-50%,-50%);pointer-events:none}.g3dot{width:15px;height:15px;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 8px #0009}.g3marker.live .g3dot{background:#19c875}.g3marker.stale .g3dot{background:#f59e0b}.g3marker.none .g3dot{background:#64748b}.g3label{position:absolute;left:50%;bottom:20px;transform:translateX(-50%);white-space:nowrap;padding:4px 7px;border-radius:7px;background:#111c;color:#fff;font-size:10px;font-weight:900;box-shadow:0 2px 8px #0008}.g3zoom{position:absolute;z-index:50;right:10px;top:10px;display:grid;gap:5px}.g3zoom button{width:34px;height:34px;border:1px solid #0002;border-radius:8px;background:#fff;color:#111;font-size:20px;font-weight:900;box-shadow:0 2px 8px #0003}.g3attr{position:absolute;z-index:40;right:4px;bottom:3px;padding:2px 5px;border-radius:4px;background:#fffd;color:#333;font-size:9px}.g3hint{position:absolute;z-index:35;left:10px;top:10px;padding:5px 8px;border-radius:8px;background:#0b1220cc;color:#dbe4ee;font-size:9px}
   .g3row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding:10px;margin-bottom:8px;border:1px solid #ffffff0d;border-radius:12px;background:#121a22}.g3name{font-weight:900}.g3meta{margin-top:4px;color:#8f9ca9;font-size:10px;line-height:1.45}.g3badge{display:inline-flex;margin-top:6px;padding:4px 7px;border-radius:999px;font-size:9px;font-weight:950}.g3badge.live{background:#143924;color:#6ee7a7}.g3badge.stale{background:#3a2a11;color:#f8c866}.g3badge.none{background:#242a31;color:#9ca7b2}.g3center{align-self:center;border:1px solid #ffffff16;background:#2a3037;color:#fff;border-radius:9px;padding:7px 9px;font-size:10px;font-weight:800}.g3center:disabled{opacity:.35}.g3err{padding:14px;color:#fca5a5;font-size:11px}.g3empty{padding:24px;text-align:center;color:#7f8b96;font-size:11px}
   @media(max-width:900px){.g3body{grid-template-columns:1fr}.g3list{border-right:0;border-bottom:1px solid #ffffff10;max-height:250px}.g3map{min-height:340px}}
   `;document.head.appendChild(s);
 }

 function html(){
   const lm=byId(),online=(state.motoboys||[]).filter(m=>status(lm.get(String(m.id))).k==='live').length;
   const rows=(state.motoboys||[]).map(m=>{const l=lm.get(String(m.id)),st=status(l),ok=l&&Number.isFinite(Number(l.latitude))&&Number.isFinite(Number(l.longitude));return `
     <div class="g3row"><div><div class="g3name">${escGps(m.nome||'Motoboy')}</div><span class="g3badge ${st.k}">${st.t}</span><div class="g3meta">${escGps(last(l))}<br>${escGps(acc(l))} · ${escGps(speed(l))}</div></div><button class="g3center" data-g3="${escGps(m.id)}" ${ok?'':'disabled'}>Centralizar</button></div>`}).join('');
   return `<section id="bmGpsPanelV3"><div class="g3h"><div><b>⌖ GPS DOS MOTOBOYS</b><small>LOCALIZAÇÃO EM TEMPO REAL · SEM ROTAS</small></div><div><span id="g3online" style="color:#94a3b8;font-size:11px;margin-right:8px">${online} online · atualiza a cada 20s</span><button id="g3refresh" class="g3btn">↻ Atualizar GPS</button></div></div><div class="g3body"><div class="g3list">${state.error?'<div class="g3err">'+escGps(state.error)+'</div>':(rows||'<div class="g3empty">Nenhum motoboy ativo.</div>')}</div><div class="g3map" id="g3map"><div class="g3canvas" id="g3canvas"></div><div class="g3hint">Arraste para mover o mapa</div><div class="g3zoom"><button id="g3plus">+</button><button id="g3minus">−</button></div><div class="g3attr">© OpenStreetMap</div></div></div></section>`;
 }

 function mount(){
   css();
   const main=document.querySelector('.admin-main');
   if(!main)return false;
   if(!document.getElementById('bmGpsPanelV3')){
     document.getElementById('bmGpsPanelV2')?.remove();
     document.getElementById('bmGpsPanel')?.remove();
     const d=document.createElement('div');d.innerHTML=html();const node=d.firstElementChild;
     const ws=main.querySelector('.moto-workspace');
     if(ws)main.insertBefore(node,ws);else main.appendChild(node);
   }
   bind();renderMap();return true;
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

 function renderMap(){
   const el=document.getElementById('g3map'),canvas=document.getElementById('g3canvas');
   if(!el||!canvas)return;
   const w=Math.max(320,el.clientWidth||600),h=Math.max(300,el.clientHeight||365);
   const c=worldPx(view.lat,view.lng,view.zoom),left=c.x-w/2,top=c.y-h/2;
   const minX=Math.floor(left/TILE)-1,maxX=Math.floor((left+w)/TILE)+1,minY=Math.floor(top/TILE)-1,maxY=Math.floor((top+h)/TILE)+1;
   const maxTile=Math.pow(2,view.zoom);
   let html='';
   for(let ty=minY;ty<=maxY;ty++){
     if(ty<0||ty>=maxTile)continue;
     for(let tx=minX;tx<=maxX;tx++){
       const wrapped=((tx%maxTile)+maxTile)%maxTile;
       const px=tx*TILE-left,py=ty*TILE-top;
       const src='https://tile.openstreetmap.org/'+view.zoom+'/'+wrapped+'/'+ty+'.png';
       const fb='https://a.basemaps.cartocdn.com/light_all/'+view.zoom+'/'+wrapped+'/'+ty+'.png';
       html+='<img class="g3tile" draggable="false" style="left:'+Math.round(px)+'px;top:'+Math.round(py)+'px" src="'+src+'" onerror="if(this.dataset.fallback!==\'1\'){this.dataset.fallback=\'1\';this.src=\''+fb+'\'}">';
     }
   }
   const lm=byId();
   (state.motoboys||[]).forEach(m=>{
     const l=lm.get(String(m.id)),lat=Number(l?.latitude),lng=Number(l?.longitude);
     if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
     const p=worldPx(lat,lng,view.zoom),x=p.x-left,y=p.y-top;
     if(x<-40||x>w+40||y<-40||y>h+40)return;
     const st=status(l);
     html+='<div class="g3marker '+st.k+'" style="left:'+Math.round(x)+'px;top:'+Math.round(y)+'px"><div class="g3label">'+escGps(m.nome||'Motoboy')+'</div><div class="g3dot"></div></div>';
   });
   canvas.innerHTML=html;
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
   if(plus)plus.onclick=e=>{e.stopPropagation();view.zoom=Math.min(19,view.zoom+1);renderMap()};
   if(minus)minus.onclick=e=>{e.stopPropagation();view.zoom=Math.max(5,view.zoom-1);renderMap()};
   const el=document.getElementById('g3map');
   if(el&&!el.dataset.dragBound){
     el.dataset.dragBound='1';
     el.addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;const c=worldPx(view.lat,view.lng,view.zoom);dragging={x:e.clientX,y:e.clientY,cx:c.x,cy:c.y};el.setPointerCapture?.(e.pointerId)});
     el.addEventListener('pointermove',e=>{if(!dragging)return;const dx=e.clientX-dragging.x,dy=e.clientY-dragging.y;const ll=latLngFromWorld(dragging.cx-dx,dragging.cy-dy,view.zoom);view.lat=ll.lat;view.lng=ll.lng;renderMap()});
     const end=()=>{dragging=null};el.addEventListener('pointerup',end);el.addEventListener('pointercancel',end);
   }
   if(el&&!resizeObs&&window.ResizeObserver){resizeObs=new ResizeObserver(()=>renderMap());resizeObs.observe(el)}
 }

 function center(id){
   const l=byId().get(String(id)),lat=Number(l?.latitude),lng=Number(l?.longitude);
   if(!Number.isFinite(lat)||!Number.isFinite(lng))return;
   view.lat=lat;view.lng=lng;view.zoom=16;renderMap();
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
