(()=>{
 if(window.__bmPre10VisibilityFixV1)return;
 window.__bmPre10VisibilityFixV1=true;
 const riderMode=new URLSearchParams(location.search).get('app')==='motoboy';
 function spParts(date=new Date()){
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);
  const o={};for(const p of parts)if(p.type!=='literal')o[p.type]=p.value;
  return {date:`${o.year}-${o.month}-${o.day}`,mins:Number(o.hour)*60+Number(o.minute)};
 }
 function before10(){return spParts().mins<10*60}

 if(riderMode){
  let installed=false,tries=0;
  const install=()=>{
   tries++;
   if(installed)return true;
   if(typeof riderDay!=='function'||typeof sb==='undefined'||typeof today!=='function'||typeof rider==='undefined'||!window.__bmRiderSyncV8)return false;
   installed=true;
   const previousRiderDay=riderDay;
   riderDay=async function(date){
    if(String(date)!==String(today())||!before10())return previousRiderDay(date);
    const d=await previousRiderDay(date);
    if(!rider?.profile?.id)return d;
    const j=await sb.from('kh_motoboy_jornadas').select('id,data,base_valor,chegada_at,chegada_tipo,fechado').eq('motoboy_id',rider.profile.id).eq('data',date).maybeSingle();
    if(j.error)throw j.error;
    if(!j.data)return d;
    const [e,s]=await Promise.all([
     sb.from('kh_motoboy_entregas').select('id,nota_numero,bairro_nome,tipo,valor,status,saida_id,nota_confirmada,nota_confirmada_at,created_at,updated_at').eq('jornada_id',j.data.id).order('created_at'),
     sb.from('kh_motoboy_saidas').select('id,numero_sequencial,horario_saida,total,status,created_at,updated_at').eq('jornada_id',j.data.id).order('numero_sequencial')
    ]);
    if(e.error)throw e.error;if(s.error)throw s.error;
    let es=e.data||[],ss=s.data||[];
    if(typeof bmSortRiderNotes==='function')es=bmSortRiderNotes(es);
    const valid=es.filter(x=>x.status!=='cancelada');
    const taxas=valid.reduce((a,x)=>a+Number(x.valor||0),0);
    const base=j.data.chegada_at?Number(j.data.base_valor||0):0;
    const total=base+taxas;
    const old=d?.shiftSummary||{};
    const turn2=old.turno2||{diaria:0,taxas:0,entregas:0,total:0,fechado:false};
    const turn1={...(old.turno1||{}),diaria:base,taxas,entregas:valid.length,total,fechado:false,inicio:'00:00',fim:'17:00'};
    const shiftSummary={...old,turno1,turno2:turn2,total_dia:total+Number(turn2.total||0),turno_atual:'10_17'};
    return {...d,j:j.data,e:es,s:ss,total,shiftSummary,__bmShift:'10_17'};
   };
   if(rider?.active==='Hoje'&&typeof renderRider==='function')setTimeout(()=>renderRider('Hoje').catch?.(()=>{}),80);
   return true;
  };
  install();setTimeout(install,250);setTimeout(install,900);setTimeout(install,1800);
  const timer=setInterval(()=>{if(install()||tries>20)clearInterval(timer)},500);
  return;
 }

 let installed=false,tries=0;
 const installAdmin=()=>{
  tries++;
  if(installed)return true;
  if(!window.__bmShiftOperationalResetV1||typeof adminState==='undefined'||typeof entregasOf!=='function'||typeof saidasOf!=='function')return false;
  installed=true;
  const previousEntregasOf=entregasOf,previousSaidasOf=saidasOf,previousMoneyMoto=typeof moneyMoto==='function'?moneyMoto:null;
  entregasOf=function(mid){
   if(before10())return (adminState.entregas||[]).filter(x=>x.motoboy_id===mid);
   return previousEntregasOf(mid);
  };
  saidasOf=function(mid){
   if(before10())return (adminState.saidas||[]).filter(x=>x.motoboy_id===mid);
   return previousSaidasOf(mid);
  };
  if(previousMoneyMoto){
   moneyMoto=function(mid){
    if(!before10())return previousMoneyMoto(mid);
    const j=typeof jornadaOf==='function'?jornadaOf(mid):null;
    const base=j?.chegada_at?Number(j.base_valor||0):0;
    return base+entregasOf(mid).filter(x=>x.status!=='cancelada').reduce((a,x)=>a+Number(x.valor||0),0);
   };
  }
  const correctBadge=()=>{
   if(!before10())return;
   const el=document.getElementById('bmCurrentShiftBadge');if(el&&el.textContent!=='TURNO 1 · ATÉ 17:00')el.textContent='TURNO 1 · ATÉ 17:00';
  };
  setInterval(correctBadge,2000);correctBadge();
  if(typeof drawAdmin==='function')setTimeout(()=>drawAdmin(),60);
  return true;
 };
 installAdmin();setTimeout(installAdmin,250);setTimeout(installAdmin,900);setTimeout(installAdmin,1800);
 const timer=setInterval(()=>{if(installAdmin()||tries>20)clearInterval(timer)},500);
})();
