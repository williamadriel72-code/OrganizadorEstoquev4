from pathlib import Path
import re

index_path = Path('app/src/main/assets/index.html')
app_path = Path('app/src/main/assets/app.js')

html = index_path.read_text(encoding='utf-8')
html = re.sub(r'\s*<div class="branchwrap"><select id="branch" class="branch"><option value="">Todas as filiais</option></select></div>', '', html)
html = re.sub(r'\s*<div class="panel"><h3>Filial</h3><p>Escolha a unidade que receberá os dados\.</p><select id="importBranch"><option value="">Selecione uma filial</option></select></div>', '', html)
html = html.replace('Entradas, saídas e transferências.', 'Entradas, saídas e ajustes.')
index_path.write_text(html, encoding='utf-8')

js = app_path.read_text(encoding='utf-8')
js = js.replace("$('auth').classList.add('hidden');$('app').classList.remove('hidden');await loadBranches();show('home');await checkUpdate();", "$('auth').classList.add('hidden');$('app').classList.remove('hidden');show('home');await checkUpdate();")
js = re.sub(r"async function loadBranches\(\)\{.*?\n\}\n\nfunction show", "async function loadBranches(){return;}\n\nfunction show", js, flags=re.S)

old = "const ids=data.map(p=>p.id),f=$('branch').value||null;let sq=db.from('estoques').select('produto_id,filial_id,quantidade,endereco,rua,prateleira,nivel,posicao').in('produto_id',ids);if(f)sq=sq.eq('filial_id',f);const{data:stocks}=await sq;\n let vq=db.from('validades').select('produto_id,validade,quantidade,lote').in('produto_id',ids).order('validade',{ascending:true});if(f)vq=vq.eq('filial_id',f);const{data:vals}=await vq;"
new = "const ids=data.map(p=>p.id);const{data:stocks}=await db.from('estoques').select('produto_id,quantidade,endereco,rua,prateleira,nivel,posicao').is('filial_id',null).in('produto_id',ids);\n const{data:vals}=await db.from('validades').select('produto_id,validade,quantidade,lote').is('filial_id',null).in('produto_id',ids).order('validade',{ascending:true});"
js = js.replace(old, new)
js = re.sub(r"\$\('q'\)\.oninput=e=>\{clearTimeout\(timer\);timer=setTimeout\(\(\)=>findProduct\(e\.target\.value\),350\)\};\$\('branch'\).*?;\$\('importBranch'\).*?;", "$('q').oninput=e=>{clearTimeout(timer);timer=setTimeout(()=>findProduct(e.target.value),250)};", js)

js = re.sub(r"async function ensureBranch\(\)\{.*?\nfunction productRows", "async function ensureBranch(){return null;}\nfunction productRows", js, flags=re.S)
js = js.replace('async function stockBatch(items,f){', 'async function stockBatch(items){')
js = js.replace('rows.push({filial_id:f,produto_id:id,quantidade:Number(i.quantidade||0)})', 'rows.push({filial_id:null,produto_id:id,quantidade:Number(i.quantidade||0)})')
js = js.replace("upsert(rows,{onConflict:'filial_id,produto_id'})", "upsert(rows,{onConflict:'produto_id'})")
js = js.replace('async function validityBatch(items,f){', 'async function validityBatch(items){')
js = js.replace(".eq('filial_id',f).in('produto_id',ids)", ".is('filial_id',null).in('produto_id',ids)")
js = js.replace("candidates.push({filial_id:f,produto_id:id,validade:i.validade,lote:null,quantidade:null})", "candidates.push({filial_id:null,produto_id:id,validade:i.validade,lote:null,quantidade:null})")
js = js.replace(".eq('filial_id',f).in('produto_id',cids)", ".is('filial_id',null).in('produto_id',cids)")
js = js.replace("const f=await ensureBranch(),total=Number(pending.totalItems||0);", "const total=Number(pending.totalItems||0);")
js = js.replace("await stockBatch(items,f)", "await stockBatch(items)")
js = js.replace("await validityBatch(items,f)", "await validityBatch(items)")
js = js.replace(";await loadBranches();", ";")

# Troca o fluxo de várias consultas por uma chamada transacional em lote no Supabase.
rpc_batch = r'''async function stockBatch(items){
 const payload=items.map(i=>({codigo_interno:String(i.codigoInterno||''),codigo_barras:i.ean?String(i.ean):null,nome:i.nome||null,unidades_por_caixa:i.unCx==null?null:Number(i.unCx),quantidade:Number(i.quantidade||0)}));
 const{data,error}=await db.rpc('importar_estoque_geral_lote',{p_itens:payload});if(error)throw error;return data||{processados:0};
}
async function validityBatch(items){
 const payload=items.map(i=>({codigo_interno:String(i.codigoInterno||''),validade:i.validade||null}));
 const{data,error}=await db.rpc('importar_validades_geral_lote',{p_itens:payload});if(error)throw error;return{inserted:Number(data?.inseridos||0),notInStock:Number(data?.nao_encontrados||0),duplicates:Number(data?.duplicados||0)};
}
'''
js = re.sub(r"function productRows\(items\)\{.*?\n\}\n\$\('confirmImport'\)", rpc_batch + "$('confirmImport')", js, flags=re.S)

js = re.sub(
    r"function apkUrl\(path\)\{return`\$\{SUPABASE_URL\}/storage/v1/object/public/app-updates/\$\{String\(path\|\|''\)\.split\('/'\)\.map\(encodeURIComponent\)\.join\('/'\)\}`\}",
    "function apkUrl(path){const p=String(path||'');if(/^https?:\\/\\//i.test(p))return p;return`${SUPABASE_URL}/storage/v1/object/public/app-updates/${p.split('/').map(encodeURIComponent).join('/')}`}",
    js
)

# Busca ultrarrápida para o scanner: índice local + busca exata + detalhes em paralelo.
fast_scan = r'''function expClass(v){const t=new Date();t.setHours(0,0,0,0);const d=new Date(v+'T00:00:00');const days=Math.round((d-t)/86400000);return days<0?'bad':days<=30?'warn':''}
const PRODUCT_INDEX_KEY='aws_product_index_v2';
const productByCode=new Map();
let productIndexRefreshing=false,searchSerial=0,scanSerial=0;
function indexProduct(p){if(!p?.id)return;if(p.codigo_barras)productByCode.set('e:'+String(p.codigo_barras),p);if(p.codigo_interno)productByCode.set('c:'+String(p.codigo_interno),p)}
function loadLocalProductIndex(){try{const rows=JSON.parse(localStorage.getItem(PRODUCT_INDEX_KEY)||'[]');if(Array.isArray(rows))rows.forEach(indexProduct)}catch(e){console.warn('Índice local inválido',e)}}
loadLocalProductIndex();
async function refreshProductIndex(force=false){
 if(productIndexRefreshing)return;productIndexRefreshing=true;
 try{let from=0,all=[];const step=1000;while(true){const{data,error}=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('ativo',true).range(from,from+step-1);if(error)throw error;const rows=data||[];all.push(...rows);rows.forEach(indexProduct);if(rows.length<step)break;from+=step}if(all.length)localStorage.setItem(PRODUCT_INDEX_KEY,JSON.stringify(all))}catch(e){console.warn('Não foi possível atualizar o índice local',e)}finally{productIndexRefreshing=false}
}
function cachedProduct(code){const s=String(code||'').trim();return productByCode.get('e:'+s)||productByCode.get('c:'+s)||null}
function productCard(p,stocks=[],vals=[],loading=false){
 const qty=loading?'…':stocks.reduce((a,s)=>a+Number(s.quantidade||0),0);
 const first=stocks[0];const addr=loading?'Carregando...':(first?.endereco||[first?.rua,first?.prateleira,first?.nivel,first?.posicao].filter(Boolean).join(' - ')||'Sem endereço');
 let expiry;if(loading)expiry='<small>Carregando validades...</small>';else expiry=vals.length?vals.map(v=>`<div class="exprow ${expClass(v.validade)}"><b>${brDate(v.validade)}</b><span>${v.quantidade==null?'Qtd. não informada':Number(v.quantidade)+' un.'}</span></div>`).join(''):'<small>Sem validade cadastrada.</small>';
 return`<article class="product"><h3>${esc(p.nome||'Produto')}</h3><div class="meta"><div><span>Código:</span> <b>${esc(p.codigo_interno||'—')}</b></div><div><span>EAN:</span> <b>${esc(p.codigo_barras||'—')}</b></div><div><span>Estoque:</span> <b class="stock">${qty}</b></div><div><span>Endereço:</span> <b>${esc(addr)}</b></div></div><div class="exp"><h4>Validades ${loading?'':'('+vals.length+')'}</h4>${expiry}</div></article>`
}
async function detailsFor(products){
 const ids=products.map(p=>p.id);if(!ids.length)return{stocks:[],vals:[]};
 const [sr,vr]=await Promise.all([
  db.from('estoques').select('produto_id,quantidade,endereco,rua,prateleira,nivel,posicao').is('filial_id',null).in('produto_id',ids),
  db.from('validades').select('produto_id,validade,quantidade,lote').is('filial_id',null).in('produto_id',ids).order('validade',{ascending:true})
 ]);
 if(sr.error)throw sr.error;if(vr.error)throw vr.error;return{stocks:sr.data||[],vals:vr.data||[]}
}
function renderDetailed(products,stocks,vals){const sm={},vm={};for(const s of stocks)(sm[s.produto_id]??=[]).push(s);for(const v of vals)(vm[v.produto_id]??=[]).push(v);$('results').innerHTML=products.map(p=>productCard(p,sm[p.id]||[],vm[p.id]||[],false)).join('')}
async function exactProduct(code){
 const s=String(code||'').trim();const cached=cachedProduct(s);if(cached)return cached;
 let r=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('ativo',true).eq('codigo_barras',s).limit(1);if(r.error)throw r.error;let p=r.data?.[0];
 if(!p){r=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('ativo',true).eq('codigo_interno',s).limit(1);if(r.error)throw r.error;p=r.data?.[0]}
 if(p)indexProduct(p);return p||null
}
async function scanProduct(code){
 const s=String(code||'').trim();if(!s)return;const serial=++scanSerial;$('q').value=s;show('search');
 const cached=cachedProduct(s);if(cached)$('results').innerHTML=productCard(cached,[],[],true);else $('results').innerHTML=`<div class="empty"><b>Código ${esc(s)}</b><br>Localizando produto...</div>`;
 try{const p=cached||await exactProduct(s);if(serial!==scanSerial)return;if(!p){$('results').innerHTML='<div class="empty">Produto não encontrado.</div>';return toast('Produto não encontrado.','err')}
  $('results').innerHTML=productCard(p,[],[],true);const d=await detailsFor([p]);if(serial!==scanSerial)return;renderDetailed([p],d.stocks,d.vals);toast('Produto localizado.','ok')
 }catch(e){console.error(e);if(serial===scanSerial){$('results').innerHTML='<div class="empty">Erro ao consultar o produto.</div>';toast('Erro ao consultar produto.','err')}}
}
async function findProduct(term){
 term=(term||'').trim();const serial=++searchSerial;if(!term)return $('results').innerHTML='<div class="empty">Digite alguma informação para localizar um produto.</div>';
 const cached=/^\d+$/.test(term)?cachedProduct(term):null;if(cached){$('results').innerHTML=productCard(cached,[],[],true);try{const d=await detailsFor([cached]);if(serial===searchSerial)renderDetailed([cached],d.stocks,d.vals)}catch(e){console.error(e)}return}
 $('results').innerHTML='<div class="empty">Buscando...</div>';const safe=term.replace(/[,()%\'\"]/g,' ');
 const{data,error}=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('ativo',true).or(`nome.ilike.%${safe}%,codigo_interno.ilike.%${safe}%,codigo_barras.ilike.%${safe}%`).limit(20);if(serial!==searchSerial)return;
 if(error){console.error(error);return $('results').innerHTML='<div class="empty">Erro ao consultar produtos.</div>'}if(!data?.length)return $('results').innerHTML='<div class="empty">Nenhum produto encontrado.</div>';data.forEach(indexProduct);$('results').innerHTML=data.map(p=>productCard(p,[],[],true)).join('');
 try{const d=await detailsFor(data);if(serial===searchSerial)renderDetailed(data,d.stocks,d.vals)}catch(e){console.error(e);if(serial===searchSerial)toast('Produto localizado, mas houve erro ao carregar detalhes.','err')}
}
$('q').oninput=e=>{clearTimeout(timer);timer=setTimeout(()=>findProduct(e.target.value),220)};
window.onNativeBarcode=code=>scanProduct(code);
'''
js = re.sub(r"function expClass\(v\)\{.*?window\.onNativeBarcode=.*?;window\.onNativeScannerError", fast_scan + "window.onNativeScannerError", js, flags=re.S)
js = js.replace("show('home');await checkUpdate();", "show('home');refreshProductIndex();await checkUpdate();")

app_path.write_text(js, encoding='utf-8')
print('Interface preparada: estoque geral, importação em lote e scanner instantâneo com cache local.')
