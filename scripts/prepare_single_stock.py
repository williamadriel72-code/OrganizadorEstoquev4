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
js = re.sub(r"\$\('q'\)\.oninput=e=>\{clearTimeout\(timer\);timer=setTimeout\(\(\)=>findProduct\(e\.target\.value\),350\)\};\$\('branch'\).*?;\$\('importBranch'\).*?;", "$('q').oninput=e=>{clearTimeout(timer);timer=setTimeout(()=>findProduct(e.target.value),350)};", js)

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
app_path.write_text(js, encoding='utf-8')

print('Interface preparada para estoque geral sem filial e importação RPC em lote.')
