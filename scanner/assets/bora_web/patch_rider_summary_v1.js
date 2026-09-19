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
  if(!map.has(key))map.set(key,{id:m?.id||null,nome:String(m?.nome||'Motoboy'),comandas:0,total:0});
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

  const q=await sb.from('kh_motoboy_relatorios_turnos')
   .select('data,turno,detalhes')
   .gte('data',range.start).lte('data',range.end)
   .order('data',{ascending:true}).order('turno',{ascending:true}).limit(500);
  if(q.error)throw q.error;
  const reports=q.data||[];
  for(const rep of reports){
   for(const d of (Array.isArray(rep.detalhes)?rep.detalhes:[])){
    const r=rowForDetail(map,d);if(!r)continue;
    const resumo=d?.resumo||{};
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
     const base=j?.chegada_at&&inShift(j.chegada_at,today,shift)?Number(j.base_valor||0):0;
     r.comandas+=es.length;
     r.total+=base+es.reduce((a,e)=>a+Number(e.valor||0),0);
    }
   }
  }

  const rows=[...map.values()].sort((a,b)=>String(a.nome).localeCompare(String(b.nome),'pt-BR'));
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
   .bm-rs-list{display:grid;gap:6px}.bm-rs-row{display:grid;grid-template-columns:minmax(0,1fr) 105px 120px;gap:8px;align-items:center;background:#121a22;border:1px solid rgba(255,255,255,.07);border-radius:12px;padding:11px 12px}.bm-rs-name{font-weight:900;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bm-rs-count{color:#a6b0ba;text-align:right;font-size:11px}.bm-rs-value{text-align:right;color:#63e99f;font-weight:950;white-space:nowrap}
   .bm-rs-total{display:grid;grid-template-columns:minmax(0,1fr) 105px 120px;gap:8px;margin-top:10px;padding:12px;border-top:1px solid rgba(255,255,255,.1);font-weight:950}.bm-rs-total .bm-rs-count,.bm-rs-total .bm-rs-value{font-size:13px}
   .bm-rs-empty{padding:28px;text-align:center;color:#8b96a1}.bm-rs-loading{padding:24px;text-align:center;color:#9aa5b0}
   @media(max-width:560px){.bm-rs-row,.bm-rs-total{grid-template-columns:minmax(0,1fr) 78px 100px}.bm-rs-modal{padding:12px}.bm-rs-head{top:-12px}.bm-rs-custom{grid-template-columns:1fr 1fr}.bm-rs-custom button{grid-column:1/-1}.bm-rs-count{font-size:9px}.bm-rs-value{font-size:11px}}
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
 async function render(silent=false){
  const body=document.getElementById('bmRsBody');if(!body)return;if(!silent)body.innerHTML='<div class="bm-rs-loading">Atualizando...</div>';
  try{
   const x=await collect();
   const rows=x.rows.map(r=>`<div class="bm-rs-row"><div class="bm-rs-name">${safe(r.nome)}</div><div class="bm-rs-count">${r.comandas} comandas</div><div class="bm-rs-value">${money(r.total)}</div></div>`).join('');
   body.innerHTML=`<div class="bm-rs-period">${safe(x.range.label)}</div><div class="bm-rs-list">${rows||'<div class="bm-rs-empty">Nenhum motoboy encontrado.</div>'}</div><div class="bm-rs-total"><div>TOTAL</div><div class="bm-rs-count">${x.totalComandas} comandas</div><div class="bm-rs-value">${money(x.totalValor)}</div></div>`;
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