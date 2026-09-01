/* Bootstrap da correção de despacho + fechamento do APK.
   Mantém a versão estável anterior e aplica por cima a regra operacional das 17:00. */
(async()=>{
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 const PREVIOUS='https://raw.githubusercontent.com/williamadriel72-code/OrganizadorEstoquev4/2d3c36449f29743c9ead384375b86aafce2096bf/scanner/assets/bora_web/patch_dispatch_fix_v1.js';
 try{
  const r=await fetch(PREVIOUS+'?v='+Date.now(),{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  (0,eval)(await r.text());
 }catch(e){console.warn('dispatch-stable-load',e?.message||e)}

 if(!riderMode||window.__bmRiderShiftDailyV5)return;
 window.__bmRiderShiftDailyV5=true;

 function spParts(date=new Date()){
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
  const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function shiftKey(){const p=spParts();return p.mins>=17*60?'17_24':p.mins>=10*60?'10_17':'pre_10'}
 function inShift(iso,key){
  if(!iso)return false;
  const now=spParts(),p=spParts(new Date(iso));
  if(p.date!==now.date)return false;
  if(key==='17_24')return p.mins>=17*60;
  if(key==='10_17')return p.mins>=10*60&&p.mins<17*60;
  return false;
 }

 /* Substitui riderDay por uma leitura limpa do banco para evitar que wrappers antigos
    zerem a diária lançada dentro do turno 2. */
 if(typeof sb!=='undefined'&&typeof today==='function'&&rider?.profile){
  riderDay=async function(date){
   const j=await sb.from('kh_motoboy_jornadas')
    .select('id,data,base_valor,chegada_at,chegada_tipo,fechado')
    .eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
   if(j.error)throw j.error;
   if(!j.data)return {j:null,e:[],s:[],total:0};

   const [e,so]=await Promise.all([
    sb.from('kh_motoboy_entregas')
     .select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,created_at,updated_at')
     .eq('jornada_id',j.data.id).order('created_at'),
    sb.from('kh_motoboy_saidas')
     .select('id,numero_sequencial,horario_saida,total,status,created_at')
     .eq('jornada_id',j.data.id).order('numero_sequencial')
   ]);
   if(e.error)throw e.error;if(so.error)throw so.error;

   let es=e.data||[],ss=so.data||[],base=j.data.chegada_at?Number(j.data.base_valor||0):0,jornada={...j.data};
   if(String(date)===String(today())){
    const key=shiftKey();
    es=es.filter(x=>inShift(x.created_at,key));
    ss=ss.filter(x=>inShift(x.horario_saida||x.created_at,key));
    base=(j.data.chegada_at&&inShift(j.data.chegada_at,key))?Number(j.data.base_valor||0):0;
    jornada={...j.data,base_valor:base,chegada_at:base?j.data.chegada_at:null};
   }
   const total=base+es.filter(x=>x.status!=='cancelada').reduce((a,x)=>a+Number(x.valor||0),0);
   return {j:jornada,e:es,s:ss,total,__bmShift:String(date)===String(today())?shiftKey():null};
  };
 }

 /* Corrige também a tela já montada, usando a diária do próprio turno. */
 async function forceToday(){
  if(!rider?.profile||typeof renderRider!=='function')return;
  if(rider?.active&&rider.active!=='Hoje')return;
  try{await renderRider('Hoje')}catch(e){console.warn('rider-shift-v5',e?.message||e)}
 }
 setTimeout(forceToday,200);
 setTimeout(forceToday,900);
 setTimeout(forceToday,1800);

 let lastShift=shiftKey();
 setInterval(()=>{
  const k=shiftKey();
  if(k!==lastShift){lastShift=k;forceToday()}
 },15000);
 window.addEventListener('focus',forceToday);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)forceToday()});
})();
