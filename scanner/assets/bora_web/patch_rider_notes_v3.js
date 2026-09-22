function bmSortRiderNotes(items){
 return [...(items||[])].sort((a,b)=>{
  const ap=!!a.nota_confirmada, bp=!!b.nota_confirmada;
  if(ap!==bp)return ap?1:-1;
  const ad=new Date(ap?(a.nota_confirmada_at||a.updated_at||a.created_at):(a.created_at||a.updated_at)).getTime()||0;
  const bd=new Date(bp?(b.nota_confirmada_at||b.updated_at||b.created_at):(b.created_at||b.updated_at)).getTime()||0;
  return bd-ad;
 });
}

let bmRiderApiToken='';
async function bmRiderApi(action,data={}){
 const endpoint=U+'/functions/v1/bora-ifood-test-rider';
 async function login(){
  const r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'login_name',riderId:rider.profile.id})});
  const j=await r.json().catch(()=>({}));
  if(!r.ok||!j.session_token)throw Error(j.error||'Não foi possível carregar os dados da comanda.');
  bmRiderApiToken=String(j.session_token);
 }
 if(!bmRiderApiToken)await login();
 let r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+bmRiderApiToken},body:JSON.stringify({action,...data})});
 if(r.status===401){bmRiderApiToken='';await login();r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+bmRiderApiToken},body:JSON.stringify({action,...data})})}
 const j=await r.json().catch(()=>({}));
 if(!r.ok)throw Error(j.error||'Falha ao carregar as informações do pedido.');
 return j;
}
async function bmRiderOnlineDetails(date){
 if(date!==today())return new Map();
 try{
  const j=await bmRiderApi('list_orders');
  return new Map((j.orders||[]).map(o=>[String(o.order_id),o]));
 }catch(e){
  console.warn('rider-online-details',e?.message||e);
  return new Map();
 }
}

riderDay=async function(date){
 const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at,chegada_tipo,fechado').eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
 if(j.error)throw j.error;
 if(!j.data)return {j:null,e:[],s:[],total:0};
 const [e,so,details]=await Promise.all([
  sb.from('kh_motoboy_entregas').select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at').eq('jornada_id',j.data.id),
  sb.from('kh_motoboy_saidas').select('id,numero_sequencial,horario_saida,total,status,created_at').eq('jornada_id',j.data.id).order('numero_sequencial'),
  bmRiderOnlineDetails(date)
 ]);
 if(e.error)throw e.error;
 if(so.error)throw so.error;
 const entregas=(e.data||[]).map(x=>({...x,bm_order:details.get(String(x.id))||null}));
 const total=(j.data.chegada_at?Number(j.data.base_valor||0):0)+entregas.reduce((a,x)=>a+Number(x.valor||0),0);
 return {j:j.data,e:bmSortRiderNotes(entregas),s:so.data||[],total};
};

function bmClientLine(label,value){
 if(value===null||value===undefined||String(value).trim()==='')return '';
 return `<div style="display:grid;grid-template-columns:92px minmax(0,1fr);gap:8px;padding:4px 0;font-size:12px;line-height:1.35"><span style="color:#7f8992;font-weight:800">${esc(label)}</span><span style="color:#eef2f5;font-weight:700;overflow-wrap:anywhere">${esc(value)}</span></div>`;
}
function bmRiderClientDetails(e){
 const o=e.bm_order||{};
 const phone=String(o.customer_phone||'').trim();
 const phoneDigits=phone.replace(/\D/g,'');
 const source=String(o.source||'Painel Original');
 const address=o.formatted_address||[o.street_name,o.street_number,o.neighborhood,o.city,o.state].filter(Boolean).join(', ');
 const received=o.received_at?new Date(o.received_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
 const sourceColor=source.toLowerCase().includes('card')?'#6ee7a7':source.toLowerCase().includes('ifood')?'#ff9b9b':'#d1d5db';
 const lines=[
  bmClientLine('CLIENTE',o.customer_name&&o.customer_name!=='Comanda'?o.customer_name:''),
  phone?`<div style="display:grid;grid-template-columns:92px minmax(0,1fr);gap:8px;padding:4px 0;font-size:12px"><span style="color:#7f8992;font-weight:800">TELEFONE</span><a href="tel:${esc(phoneDigits)}" style="color:#73b7ff;font-weight:900;text-decoration:none">${esc(phone)}</a></div>`:'',
  bmClientLine('ENDEREÇO',address),
  bmClientLine('RUA',o.street_name),
  bmClientLine('NÚMERO',o.street_number),
  bmClientLine('BAIRRO',o.original_neighborhood||o.neighborhood||e.bairro_nome),
  bmClientLine('CIDADE',[o.city,o.state].filter(Boolean).join(' - ')),
  bmClientLine('CEP',o.zip_code),
  bmClientLine('COMPLEMENTO',o.complement),
  bmClientLine('REFERÊNCIA',o.reference),
  bmClientLine('LOCALIZADOR',o.locator),
  bmClientLine('CÓDIGO',o.pickup_code),
  bmClientLine('OBSERVAÇÃO',o.observation),
  bmClientLine('LOJA',o.merchant_name),
  bmClientLine('PEDIDO',o.ifood_order_id),
  bmClientLine('RECEBIDO',received)
 ].filter(Boolean).join('');
 return `<div style="margin-top:10px;padding:10px 11px;border-radius:11px;background:#0f1418;border:1px solid rgba(255,255,255,.06)"><div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:4px"><span style="font-size:10px;font-weight:950;color:${sourceColor};padding:3px 7px;border-radius:999px;background:#ffffff08">${esc(source)}</span>${o.merchant_name?`<span style="font-size:10px;color:#8f989f;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(o.merchant_name)}</span>`:''}</div>${lines||'<div style="font-size:11px;color:#7f8992">Esta comanda não possui dados adicionais do cliente.</div>'}</div>`;
}
function bmRiderNoteCard(e){
 const confirmed=!!e.nota_confirmada;
 const confirmedAt=e.nota_confirmada_at?new Date(e.nota_confirmada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
 const bairro=esc(e.bairro_nome||'Bairro não informado');
 const details=bmRiderClientDetails(e);
 if(confirmed){
  return `<div data-note-card="${e.id}" style="padding:14px 16px;margin:9px 0;border:1px solid rgba(33,200,117,.82);border-radius:15px;background:linear-gradient(180deg,rgba(25,29,32,.98),rgba(18,21,23,.98));box-shadow:0 0 14px rgba(33,200,117,.16)">
   <div style="display:flex;align-items:center;gap:10px">
    <div style="min-width:0;flex:1"><div style="font-size:24px;line-height:1;font-weight:900;color:#f5f7f8">COMANDA #${esc(e.nota_numero)}</div><div style="margin-top:5px;font-size:12px;font-weight:700;color:#9fa7ad">${bairro} · ${BRL(e.valor)}</div></div>
    <span style="display:inline-flex;align-items:center;gap:6px;color:#28cf7b;font-size:12px;font-weight:950"><span style="display:inline-grid;place-items:center;width:25px;height:25px;border-radius:50%;background:#28cf7b;color:#07140d;font-size:17px">✓</span>CONFIRMADA ${confirmedAt?esc(confirmedAt):''}</span>
   </div>
   ${details}
  </div>`;
 }
 return `<div data-note-card="${e.id}" style="padding:15px 16px;margin:9px 0;border:1px solid rgba(255,255,255,.08);border-radius:15px;background:#171a1d">
  <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px">
   <div style="min-width:0;flex:1"><div class="stat-label">COMANDA</div><div data-note-number style="font-size:28px;font-weight:900;letter-spacing:.5px;margin-top:3px">#${esc(e.nota_numero)}</div><div style="margin-top:5px;font-size:13px;font-weight:800;color:#aeb5ba">${bairro} · <span style="color:#e5b544">${BRL(e.valor)}</span></div></div>
   <span style="font-size:10px;font-weight:950;color:#6ee7a7;background:#143924;border-radius:999px;padding:5px 8px">PENDENTE</span>
  </div>
  ${details}
  <button type="button" class="btn" data-confirm-note="${e.id}" onclick="window.bmConfirmRiderNote('${e.id}')" style="width:100%;min-height:48px;margin-top:13px;font-weight:900;background:#159958!important;color:#fff!important;border:1px solid #21c875!important">CONFIRMAR COMANDA</button>
 </div>`;
}

function bmRiderSpParts(date=new Date()){
 const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
 const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
 return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
}
function bmRiderCurrentShift(){
 const p=bmRiderSpParts();
 return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10';
}
function bmRiderInCurrentShift(value){
 if(!value)return false;
 const now=bmRiderSpParts(),p=bmRiderSpParts(new Date(value)),key=bmRiderCurrentShift();
 if(p.date!==now.date)return false;
 if(key==='17_24')return p.mins>=17*60;
 if(key==='10_17')return p.mins>=10*60&&p.mins<17*60;
 return false;
}
todayHtml=function(d){
 const currentShift=bmRiderCurrentShift();
 const shiftRows=currentShift==='pre_10'?[]:(d.e||[]).filter(e=>bmRiderInCurrentShift(e.created_at||e.updated_at));
 const valid=bmSortRiderNotes(shiftRows.filter(e=>e.status!=='cancelada'));
 const pending=valid.filter(e=>!e.nota_confirmada);
 const confirmed=valid.filter(e=>e.nota_confirmada);
 const list=[...pending,...confirmed];
 const shiftLabel=currentShift==='17_24'?'Turno 2 · 17:00–00:00':currentShift==='10_17'?'Turno 1 · 10:00–17:00':'Aguardando turno · 10:00';
 return `<section class="rider-fast-panel">
  <div class="notice" style="margin-bottom:10px;border-color:#31d98255"><strong>${shiftLabel}</strong> · as comandas desta tela mostram somente o turno atual. O valor financeiro do dia continua acumulado.</div>
  <div class="today-summary">
   <div class="card"><div class="stat-label">ENTREGAS</div><div class="stat-value">${valid.length}</div></div>
   <div class="card"><div class="stat-label">TOTAL DO DIA</div><div class="stat-value">${BRL(d.total)}</div></div>
  </div>
  ${d.j?.chegada_at?`<div class="notice" style="margin-top:10px">Chegada registrada às <b>${new Date(d.j.chegada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</b> · diária ${BRL(d.j.base_valor||0)}</div>`:''}
  <div class="section-title">Conferir notas</div>
  <div class="notice"><b>${pending.length?`${pending.length} nota${pending.length===1?'':'s'} aguardando confirmação`:'Todas as notas estão confirmadas'}</b><br>Confira o número e o bairro da nota física.</div>
  <div style="margin-top:10px">${list.length?list.map(bmRiderNoteCard).join(''):'<div class="card"><div class="empty">Nenhuma nota lançada hoje.</div></div>'}</div>
 </section>`;
};

async function bmConfirmRiderNote(id){
 rider.confirmingNotes=rider.confirmingNotes||new Set();
 if(rider.confirmingNotes.has(id))return;
 rider.confirmingNotes.add(id);
 const btn=document.querySelector(`[data-confirm-note="${id}"]`);
 if(btn){btn.disabled=true;btn.textContent='CONFIRMANDO...'}
 try{
  rider.silentConfirmId=id;
  const r=await sb.rpc('kh_confirm_motoboy_delivery_note',{p_delivery_id:id});
  if(r.error)throw r.error;
  if(rider.todayCache?.e){
   const item=rider.todayCache.e.find(x=>x.id===id);
   if(item){item.nota_confirmada=true;item.nota_confirmada_at=r.data?.nota_confirmada_at||new Date().toISOString()}
  }
  toast(`Nota ${r.data?.nota_numero||''} confirmada.`);
  await renderRider('Hoje');
 }catch(e){
  console.error('confirm-note',e);
  toast(e?.message||'Não foi possível confirmar a nota.');
  if(btn){btn.disabled=false;btn.textContent='CONFIRMAR NOTA'}
 }finally{
  rider.confirmingNotes.delete(id);
  setTimeout(()=>{if(rider.silentConfirmId===id)rider.silentConfirmId=null},1500);
 }
}
window.bmConfirmRiderNote=bmConfirmRiderNote;


/* BORA_MAPS_MULTI_API_V1 */
(function bmMapsMultiApiV1(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 setTimeout(function(){
  if(window.__bmMapsMultiApiV1)return;
  window.__bmMapsMultiApiV1=true;

 const ROUTE_ENDPOINT=U+'/functions/v1/bora-route-plan';

 function orderAddress(o,e){
  o=o||{};
  const structured=[o.street_name,o.street_number,o.neighborhood||((e||{}).bairro_nome),o.city||'Macaé',o.state||'RJ',o.zip_code].filter(Boolean).join(', ');
  return structured||o.formatted_address||'';
 }
 function orderComponents(o,e){
  o=o||{};e=e||{};
  return {
   street:o.street_name||'',
   number:o.street_number||'',
   neighborhood:o.neighborhood||o.original_neighborhood||e.bairro_nome||'',
   city:o.city||'Macaé',
   state:o.state||'RJ',
   zip:o.zip_code||''
  };
 }
 async function ensureRouteToken(){
  if(bmRiderApiToken)return bmRiderApiToken;
  await bmRiderApi('list_orders');
  if(!bmRiderApiToken)throw Error('Sessão do motoboy não disponível.');
  return bmRiderApiToken;
 }
 async function routeApi(action,data){
  data=data||{};
  let token=await ensureRouteToken();
  let r=await fetch(ROUTE_ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+token},body:JSON.stringify(Object.assign({action:action},data))});
  if(r.status===401){
   bmRiderApiToken='';
   token=await ensureRouteToken();
   r=await fetch(ROUTE_ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+token},body:JSON.stringify(Object.assign({action:action},data))});
  }
  const j=await r.json().catch(function(){return {}});
  if(!r.ok)throw Error(j.error||'Não foi possível calcular a rota.');
  return j;
 }
 function currentPosition(){
  return new Promise(function(resolve){
   if(!navigator.geolocation)return resolve(null);
   navigator.geolocation.getCurrentPosition(
    function(p){resolve({lat:p.coords.latitude,lng:p.coords.longitude})},
    function(){resolve(null)},
    {enableHighAccuracy:true,timeout:6500,maximumAge:30000}
   );
  });
 }
 function openNavigation(lat,lng,address){
  const la=Number(lat),lo=Number(lng);
  let destination='';
  if(Number.isFinite(la)&&Number.isFinite(lo))destination=String(la)+','+String(lo);
  else if(address)destination=String(address);
  if(!destination){toast('Endereço não disponível.');return}
  location.href='https://www.google.com/maps/dir/?api=1&travelmode=driving&destination='+encodeURIComponent(destination);
 }
 function googleStop(x){
  const la=Number(x?.lat),lo=Number(x?.lng);
  if(x?.number_confirmed===true&&Number.isFinite(la)&&Number.isFinite(lo))return String(la)+','+String(lo);
  return String(x?.address||x?.original_address||'').trim();
 }
 function openGoogleRoute(list){
  const stops=(Array.isArray(list)?list:[]).map(googleStop).filter(Boolean);
  if(!stops.length){toast('Nenhuma parada disponível.');return}
  if(stops.length===1){location.href='https://www.google.com/maps/dir/?api=1&travelmode=driving&destination='+encodeURIComponent(stops[0]);return}
  const destination=stops[stops.length-1];
  const waypoints=stops.slice(0,-1);
  let url='https://www.google.com/maps/dir/?api=1&travelmode=driving&destination='+encodeURIComponent(destination);
  if(waypoints.length)url+='&waypoints='+encodeURIComponent(waypoints.join('|'));
  location.href=url;
 }
 function showRoute(data){
  document.getElementById('bmRouteModal')?.remove();
  const list=data.addresses||[];
  const routing=data.routing||{};
  const meta=[
   routing.optimized?'ROTA OTIMIZADA':'ORDEM POR PROXIMIDADE',
   Number(routing.distance_m||0)>0?(Number(routing.distance_m)/1000).toFixed(1)+' km':'',
   Number(routing.duration_s||0)>0?Math.round(Number(routing.duration_s)/60)+' min':''
  ].filter(Boolean).join(' · ');
  let rows='';
  list.forEach(function(x,i){
   rows+='<div style="display:grid;grid-template-columns:34px minmax(0,1fr);gap:10px;padding:11px 0;border-top:1px solid #ffffff10"><span style="display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:#f2a33c;color:#1a1107;font-weight:950">'+(i+1)+'</span><div><b style="font-size:13px">'+esc(x.label||('Parada '+(i+1)))+'</b><div style="font-size:11px;color:#9ca3af;margin-top:3px;line-height:1.35">'+esc(x.address||x.original_address||'Endereço não informado')+'</div><div style="font-size:10px;color:#6ee7a7;margin-top:3px">'+esc(x.source||'Localização')+'</div></div></div>';
  });
  const first=list[0]||null;
  const el=document.createElement('div');
  el.id='bmRouteModal';
  el.style.cssText='position:fixed;inset:0;z-index:2147483600;background:#000c;display:grid;place-items:center;padding:16px';
  el.innerHTML='<div style="width:min(520px,96vw);max-height:88vh;overflow:auto;background:#15191d;border:1px solid #ffffff18;border-radius:18px;padding:16px;box-shadow:0 24px 80px #000a"><div style="display:flex;justify-content:space-between;gap:10px;align-items:flex-start"><div><div style="font-size:18px;font-weight:950">ROTA DAS ENTREGAS</div><div style="font-size:10px;color:#6ee7a7;font-weight:900;margin-top:4px">'+esc(meta)+'</div></div><button id="bmRouteClose" style="border:0;background:#2b3036;color:#fff;width:34px;height:34px;border-radius:10px;font-size:20px">×</button></div><div style="margin-top:10px">'+(rows||'<div style="padding:20px;text-align:center;color:#9ca3af">Nenhuma parada encontrada.</div>')+'</div>'+(first?'<button id="bmRouteGoogle" class="btn gold" style="width:100%;margin-top:12px;min-height:48px">ABRIR ROTA NO GOOGLE MAPS</button><button id="bmRouteFirst" class="btn secondary" style="width:100%;margin-top:8px;min-height:44px">ABRIR SOMENTE A 1ª ENTREGA</button>':'')+'</div>';
  document.body.appendChild(el);
  el.querySelector('#bmRouteClose').onclick=function(){el.remove()};
  el.addEventListener('click',function(ev){if(ev.target===el)el.remove()});
  if(first){
   el.querySelector('#bmRouteGoogle').onclick=function(){openGoogleRoute(list)};
   el.querySelector('#bmRouteFirst').onclick=function(){openNavigation(first.number_confirmed===true?first.lat:null,first.number_confirmed===true?first.lng:null,first.address||first.original_address||'')};
  }
 }

 window.bmNavigateOrder=async function(id){
  try{
   const e=(rider.todayCache?.e||[]).find(function(x){return String(x.id)===String(id)});
   if(!e)return toast('Comanda não encontrada.');
   const o=e.bm_order||{};
   const address=orderAddress(o,e);
   if(!address)return toast('Endereço não informado.');
   toast('Localizando endereço...');
   const j=await routeApi('resolve_address',{address:address,components:orderComponents(o,e)});
   const best=j.best||{};
   if(best.numberConfirmed===true&&best.lat!=null&&best.lng!=null){
    openNavigation(best.lat,best.lng,best.address||address);
    return;
   }
   toast('Número não confirmado pelo mapa. Abrindo o endereço original no Google Maps.');
   openNavigation(null,null,address);
  }catch(err){
   console.error('bm-navigate-order',err);
   toast(err?.message||'Não foi possível abrir a navegação.');
  }
 };

 window.bmOptimizeRiderRoute=async function(){
  try{
   const all=(rider.todayCache?.e||[]);
   const rows=all.filter(function(e){return e.status!=='cancelada'&&!e.nota_confirmada&&e.bm_order&&orderAddress(e.bm_order,e)});
   if(rows.length<2)return toast('É preciso ter pelo menos 2 entregas pendentes.');
   toast('Calculando melhor rota...');
   const origin=await currentPosition();
   const addresses=rows.map(function(e){
    const c=orderComponents(e.bm_order,e);
    return {id:String(e.id),label:'Comanda #'+String(e.nota_numero||''),address:orderAddress(e.bm_order,e),street:c.street,number:c.number,neighborhood:c.neighborhood,city:c.city,state:c.state,zip:c.zip};
   });
   const j=await routeApi('plan_addresses',{origin:origin,addresses:addresses});
   showRoute(j);
  }catch(err){
   console.error('bm-optimize-route',err);
   toast(err?.message||'Não foi possível organizar a rota.');
  }
 };

 if(typeof bmRiderClientDetails==='function'){
  const oldDetails=bmRiderClientDetails;
  bmRiderClientDetails=function(e){
   const base=oldDetails(e);
   const addr=orderAddress((e||{}).bm_order||{},e);
   if(!addr)return base;
   return base+'<button type="button" onclick="window.bmNavigateOrder(\''+esc(e.id)+'\')" style="width:100%;margin-top:8px;min-height:42px;border:1px solid #3b82f688;background:#17243a;color:#93c5fd;border-radius:10px;font-weight:950;font-size:12px">NAVEGAR</button>';
  };
 }
 if(typeof todayHtml==='function'){
  const oldTodayHtml=todayHtml;
  todayHtml=function(d){
   const base=oldTodayHtml(d);
   const n=(d?.e||[]).filter(function(e){return e.status!=='cancelada'&&!e.nota_confirmada&&e.bm_order&&orderAddress(e.bm_order,e)}).length;
   if(n<2)return base;
   return '<div style="max-width:760px;margin:0 auto 10px"><button type="button" onclick="window.bmOptimizeRiderRoute()" class="btn gold" style="width:100%;min-height:48px;font-size:13px">OTIMIZAR ROTA · '+n+' ENTREGAS</button><div style="font-size:10px;color:#8d969e;text-align:center;margin-top:5px">Número exato + Macaé · mais perto primeiro e mais longe por último</div></div>'+base;
  };
 }

  try{
   if(typeof rider!=='undefined'&&rider&&rider.profile&&typeof renderRider==='function'){
    renderRider(rider.active||'Hoje');
   }
  }catch(_){}
 },120);
})();
