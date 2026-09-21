/* OTA: dados completos do cliente nas comandas do APK já instalado. */
(function bmRiderCustomerDetailsOtaV1(){
 if(new URLSearchParams(location.search).get('app')!=='motoboy')return;
 if(window.__bmRiderCustomerDetailsOtaV1)return;
 window.__bmRiderCustomerDetailsOtaV1=true;

 const escv=v=>typeof esc==='function'?esc(v):String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const money=v=>typeof BRL==='function'?BRL(v):new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(v||0));
 let apiToken='';

 async function api(action,data={}){
  const endpoint=U+'/functions/v1/bora-ifood-test-rider';
  async function login(){
   const r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K},body:JSON.stringify({action:'login_name',riderId:rider.profile.id})});
   const j=await r.json().catch(()=>({}));
   if(!r.ok||!j.session_token)throw Error(j.error||'Não foi possível carregar os dados da comanda.');
   apiToken=String(j.session_token);
  }
  if(!apiToken)await login();
  let r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+apiToken},body:JSON.stringify({action,...data})});
  if(r.status===401){
   apiToken='';await login();
   r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',apikey:K,Authorization:'Bearer '+apiToken},body:JSON.stringify({action,...data})});
  }
  const j=await r.json().catch(()=>({}));
  if(!r.ok)throw Error(j.error||'Falha ao carregar as informações do pedido.');
  return j;
 }

 const previousRiderDay=riderDay;
 riderDay=async function(date){
  const d=await previousRiderDay(date);
  if(!d?.e?.length)return d;
  if(d.e.every(x=>Object.prototype.hasOwnProperty.call(x,'bm_order')))return d;
  if(typeof today==='function'&&date!==today())return d;
  try{
   const j=await api('list_orders');
   const byId=new Map((j.orders||[]).map(o=>[String(o.order_id),o]));
   d.e=d.e.map(x=>({...x,bm_order:byId.get(String(x.id))||null}));
  }catch(e){console.warn('rider-customer-ota',e?.message||e)}
  return d;
 };

 function line(label,value){
  if(value===null||value===undefined||String(value).trim()==='')return '';
  return `<div style="display:grid;grid-template-columns:92px minmax(0,1fr);gap:8px;padding:4px 0;font-size:12px;line-height:1.35"><span style="color:#7f8992;font-weight:800">${escv(label)}</span><span style="color:#eef2f5;font-weight:700;overflow-wrap:anywhere">${escv(value)}</span></div>`;
 }
 function details(e){
  const o=e.bm_order||{};
  const phone=String(o.customer_phone||'').trim();
  const phoneDigits=phone.replace(/\D/g,'');
  const source=String(o.source||'Painel Original');
  const address=o.formatted_address||[o.street_name,o.street_number,o.neighborhood,o.city,o.state].filter(Boolean).join(', ');
  const received=o.received_at?new Date(o.received_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
  const sourceColor=source.toLowerCase().includes('card')?'#6ee7a7':source.toLowerCase().includes('ifood')?'#ff9b9b':'#d1d5db';
  const rows=[
   line('CLIENTE',o.customer_name&&o.customer_name!=='Comanda'?o.customer_name:''),
   phone?`<div style="display:grid;grid-template-columns:92px minmax(0,1fr);gap:8px;padding:4px 0;font-size:12px"><span style="color:#7f8992;font-weight:800">TELEFONE</span><a href="tel:${escv(phoneDigits)}" style="color:#73b7ff;font-weight:900;text-decoration:none">${escv(phone)}</a></div>`:'',
   line('ENDEREÇO',address),
   line('RUA',o.street_name),
   line('NÚMERO',o.street_number),
   line('BAIRRO',o.original_neighborhood||o.neighborhood||e.bairro_nome),
   line('CIDADE',[o.city,o.state].filter(Boolean).join(' - ')),
   line('CEP',o.zip_code),
   line('COMPLEMENTO',o.complement),
   line('REFERÊNCIA',o.reference),
   line('LOCALIZADOR',o.locator),
   line('CÓDIGO',o.pickup_code),
   line('OBSERVAÇÃO',o.observation),
   line('LOJA',o.merchant_name),
   line('PEDIDO',o.ifood_order_id),
   line('RECEBIDO',received)
  ].filter(Boolean).join('');
  return `<div style="margin-top:10px;padding:10px 11px;border-radius:11px;background:#0f1418;border:1px solid rgba(255,255,255,.06)"><div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:4px"><span style="font-size:10px;font-weight:950;color:${sourceColor};padding:3px 7px;border-radius:999px;background:#ffffff08">${escv(source)}</span>${o.merchant_name?`<span style="font-size:10px;color:#8f989f;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escv(o.merchant_name)}</span>`:''}</div>${rows||'<div style="font-size:11px;color:#7f8992">Esta comanda não possui dados adicionais do cliente.</div>'}</div>`;
 }

 bmRiderNoteCard=function(e){
  const confirmed=!!e.nota_confirmada;
  const confirmedAt=e.nota_confirmada_at?new Date(e.nota_confirmada_at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'';
  const bairro=escv(e.bairro_nome||'Bairro não informado');
  const extra=details(e);
  if(confirmed){
   return `<div data-note-card="${e.id}" style="padding:14px 16px;margin:9px 0;border:1px solid rgba(33,200,117,.82);border-radius:15px;background:linear-gradient(180deg,rgba(25,29,32,.98),rgba(18,21,23,.98));box-shadow:0 0 14px rgba(33,200,117,.16)">
    <div style="display:flex;align-items:center;gap:10px"><div style="min-width:0;flex:1"><div style="font-size:24px;line-height:1;font-weight:900;color:#f5f7f8">COMANDA #${escv(e.nota_numero)}</div><div style="margin-top:5px;font-size:12px;font-weight:700;color:#9fa7ad">${bairro} · ${money(e.valor)}</div></div><span style="display:inline-flex;align-items:center;gap:6px;color:#28cf7b;font-size:12px;font-weight:950"><span style="display:inline-grid;place-items:center;width:25px;height:25px;border-radius:50%;background:#28cf7b;color:#07140d;font-size:17px">✓</span>CONFIRMADA ${confirmedAt?escv(confirmedAt):''}</span></div>
    ${extra}
   </div>`;
  }
  return `<div data-note-card="${e.id}" style="padding:15px 16px;margin:9px 0;border:1px solid rgba(255,255,255,.08);border-radius:15px;background:#171a1d">
   <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px"><div style="min-width:0;flex:1"><div class="stat-label">COMANDA</div><div data-note-number style="font-size:28px;font-weight:900;letter-spacing:.5px;margin-top:3px">#${escv(e.nota_numero)}</div><div style="margin-top:5px;font-size:13px;font-weight:800;color:#aeb5ba">${bairro} · <span style="color:#e5b544">${money(e.valor)}</span></div></div><span style="font-size:10px;font-weight:950;color:#6ee7a7;background:#143924;border-radius:999px;padding:5px 8px">PENDENTE</span></div>
   ${extra}
   <button type="button" class="btn" data-confirm-note="${e.id}" onclick="window.bmConfirmRiderNote('${e.id}')" style="width:100%;min-height:48px;margin-top:13px;font-weight:900;background:#159958!important;color:#fff!important;border:1px solid #21c875!important">CONFIRMAR COMANDA</button>
  </div>`;
 };

 if(rider?.active==='Hoje'){
  setTimeout(()=>{try{renderRider('Hoje')}catch(_){}},60);
 }
})();
