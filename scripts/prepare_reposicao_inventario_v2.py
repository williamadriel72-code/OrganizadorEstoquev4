from pathlib import Path
import re

index_path = Path('app/src/main/assets/index.html')
app_path = Path('app/src/main/assets/app.js')
css_path = Path('app/src/main/assets/styles.css')

html = index_path.read_text(encoding='utf-8')
js = app_path.read_text(encoding='utf-8')
css = css_path.read_text(encoding='utf-8')

# ===== Interface definitiva: Reposição + Inventário =====
repo_inv_html = r'''
    <section id="reposicao" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Reposição</h2><small>Baixa por validade · FEFO · sem alterar o estoque físico</small></div></div>
      <div class="panel op-session"><div><b id="repSessionTitle">Nenhuma reposição aberta</b><small id="repSessionInfo">Abra uma reposição para começar.</small></div><div class="actions"><button id="repNew" class="purple">＋ Nova reposição</button><button id="repFinish" class="secondary hidden">✓ Encerrar reposição</button></div></div>
      <div class="panel fefo-note"><b>FEFO</b><span>A saída é abatida primeiro da validade mais próxima. O saldo de <strong>estoque físico não é alterado</strong>.</span></div>
      <div class="panel">
        <label class="op-label">Bipar produto / Código / EAN</label>
        <div class="scanline"><input id="repCode" inputmode="numeric" placeholder="Bipe ou digite o código"><button id="repScan" class="secondary">▥ Bipar</button></div>
        <div class="actions"><button id="repLocate" class="secondary">Localizar produto</button></div>
        <div id="repProduct" class="selected-product hidden"></div>
        <div id="repDates" class="validity-stack"><div class="empty">Bipe um produto para ver as datas mais próximas.</div></div>
        <div class="op-grid rep-qty-grid"><div><label>Quantidade que saiu para reposição</label><input id="repQty" type="number" min="0.001" step="0.001" value="1"></div></div>
        <div class="actions"><button id="repAdd" class="purple">Dar baixa por validade</button></div>
        <div id="repResult" class="status"></div>
      </div>
      <h3 class="section-title">Baixas desta reposição</h3>
      <div id="repList" class="op-list"><div class="empty">Nenhuma baixa realizada.</div></div>
    </section>

    <section id="inventario" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Inventário</h2><small>Contagem real · revisão antes do acerto</small></div></div>
      <div class="panel op-session"><div><b id="invSessionTitle">Nenhum inventário aberto</b><small id="invSessionInfo">Abra um inventário para começar.</small></div><div class="actions"><button id="invNew" class="purple">＋ Novo inventário</button><button id="invFinish" class="secondary hidden">✓ Revisar e finalizar</button></div></div>
      <div class="panel inventory-note"><b>O estoque não muda durante a contagem.</b><span>Os produtos ficam acumulados para revisão. O acerto real só acontece ao finalizar o inventário.</span></div>
      <div class="panel">
        <label class="op-label">Pesquisar produto</label>
        <div class="scanline"><input id="invCode" placeholder="Nome, EAN ou código interno"><button id="invScan" class="secondary">▥ Bipar</button></div>
        <div class="actions"><button id="invSearchBtn" class="secondary">🔎 Pesquisar</button></div>
        <div id="invSearchResults" class="search-results"></div>
        <div id="invSelected" class="selected-product hidden"></div>
        <div class="op-grid inv-qty-grid"><div><label>Quantidade contada agora</label><input id="invQty" type="number" min="0" step="0.001" value="0"></div></div>
        <div class="actions"><button id="invAdd" class="purple">Adicionar contagem</button></div>
      </div>
      <div class="inventory-summary" id="invSummary"></div>
      <h3 class="section-title">Produtos conferidos</h3>
      <div id="invList" class="op-list"><div class="empty">Nenhum produto contado.</div></div>
    </section>
'''

pattern = re.compile(r'\s*<section id="reposicao" class="hidden">.*?</section>\s*<section id="inventario" class="hidden">.*?</section>', re.S)
html, n = pattern.subn('\n' + repo_inv_html, html, count=1)
if n != 1:
    raise SystemExit('Não foi possível substituir as telas de Reposição e Inventário.')

# Modal para produto repetido no inventário.
if 'id="invDuplicateOverlay"' not in html:
    duplicate_modal = r'''
<div id="invDuplicateOverlay" class="overlay hidden">
  <div class="dialog inventory-dialog">
    <div class="dialogicon">🧾</div>
    <h2>Produto já contado</h2>
    <p id="invDuplicateInfo">Este produto já está na lista.</p>
    <div class="duplicate-values"><div><small>Já contado</small><b id="invDupOld">0</b></div><div><small>Nova contagem</small><b id="invDupNew">0</b></div></div>
    <div class="dialogactions">
      <button id="invDupAdd" class="primary">SOMAR À CONTAGEM</button>
      <button id="invDupSet" class="secondary">DEFINIR COMO TOTAL</button>
      <button id="invDupCancel" class="secondary">CANCELAR</button>
    </div>
  </div>
</div>
'''
    html = html.replace('<div id="updateOverlay" class="overlay hidden">', duplicate_modal + '\n<div id="updateOverlay" class="overlay hidden">', 1)

# ===== JavaScript definitivo =====
new_logic = r'''// Reposição
let repCurrentProduct=null,repCurrentValidities=[];
function localToday(){const d=new Date();return`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
async function ensureReposicao(){
 if(repSessionId)return repSessionId;
 const{data,error}=await db.from('reposicoes').select('id,criado_em').eq('criado_por',currentUser.id).eq('status','aberta').order('criado_em',{ascending:false}).limit(1).maybeSingle();
 if(error)throw error;if(data?.id){repSessionId=data.id;return repSessionId}
 const r=await db.from('reposicoes').insert({criado_por:currentUser.id,dispositivo_id:deviceRowId,status:'aberta'}).select('id').single();if(r.error)throw r.error;repSessionId=r.data.id;return repSessionId
}
async function newReposicao(){try{if(repSessionId)return toast('Já existe uma reposição aberta.','err');await ensureReposicao();toast('Reposição aberta.','ok');await loadReposicao()}catch(e){toast(e.message||'Erro ao abrir reposição.','err')}}
function renderRepValidities(rows){
 repCurrentValidities=rows||[];
 if(!rows?.length){$('repDates').innerHTML='<div class="empty">Nenhuma validade futura cadastrada para este produto.</div>';return}
 $('repDates').innerHTML='<div class="validity-title">Datas em ordem de saída</div>'+rows.map((v,i)=>`<div class="validity-row ${i===0?'next':''}"><div><span>${i===0?'PRÓXIMA BAIXA':'Depois'}</span><b>${brDate(v.validade)}</b></div><strong>${v.quantidade==null?'Quantidade não informada':nf(v.quantidade)+' un.'}</strong></div>`).join('');
}
async function locateReposicaoProduct(code=null){
 try{
  const value=String(code??$('repCode').value).trim();if(!value)throw Error('Bipe ou informe o código do produto.');
  const p=await exactProduct(value);if(!p)throw Error('Produto não encontrado.');repCurrentProduct=p;$('repCode').value=value;
  const [s,vr]=await Promise.all([
   stockForProduct(p.id),
   db.from('validades').select('id,validade,quantidade,lote,status').is('filial_id',null).eq('produto_id',p.id).gte('validade',localToday()).order('validade',{ascending:true})
  ]);
  if(vr.error)throw vr.error;
  $('repProduct').classList.remove('hidden');$('repProduct').innerHTML=`<b>${esc(p.nome)}</b><span>Cód. ${esc(p.codigo_interno||'—')} · EAN ${esc(p.codigo_barras||'—')}</span><span>Endereço: ${esc(stockAddress(s))}</span><span>Estoque físico: <b>${nf(s.quantidade)}</b> <em>(não será alterado)</em></span>`;
  renderRepValidities(vr.data||[]);$('repResult').textContent='';
 }catch(e){repCurrentProduct=null;repCurrentValidities=[];$('repProduct').classList.add('hidden');$('repDates').innerHTML='<div class="empty">'+esc(e.message||'Erro ao localizar produto.')+'</div>';toast(e.message||'Erro ao localizar produto.','err')}
}
async function loadReposicao(){
 if(!repSessionId){const{data}=await db.from('reposicoes').select('id,criado_em').eq('criado_por',currentUser.id).eq('status','aberta').order('criado_em',{ascending:false}).limit(1).maybeSingle();repSessionId=data?.id||null}
 $('repFinish').classList.toggle('hidden',!repSessionId);$('repSessionTitle').textContent=repSessionId?'Reposição em andamento':'Nenhuma reposição aberta';$('repSessionInfo').textContent=repSessionId?'As baixas seguem FEFO e não mexem no estoque físico.':'Abra uma reposição para começar.';
 if(!repSessionId){$('repList').innerHTML='<div class="empty">Nenhuma baixa realizada.</div>';return}
 const{data,error}=await db.from('reposicao_itens').select('id,produto_id,quantidade_coletada,coletado_em,criado_em,produtos(nome,codigo_interno),reposicao_validade_baixas(validade,quantidade)').eq('reposicao_id',repSessionId).order('criado_em',{ascending:false});if(error)throw error;
 $('repList').innerHTML=(data||[]).length?(data||[]).map(i=>{const baixas=(i.reposicao_validade_baixas||[]).sort((a,b)=>String(a.validade).localeCompare(String(b.validade))).map(b=>`${brDate(b.validade)}: <b>${nf(b.quantidade)}</b>`).join(' · ');return opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Saída <b>${nf(i.quantidade_coletada)}</b></span><span>Validades: ${baixas||'—'}</span><span>${dt(i.coletado_em||i.criado_em)}</span><span class="op-ok">Estoque físico preservado</span>`) }).join(''):'<div class="empty">Nenhuma baixa realizada.</div>'
}
async function addReposicaoItem(){
 try{
  const id=await ensureReposicao();if(!repCurrentProduct){await locateReposicaoProduct();if(!repCurrentProduct)return}
  const q=num($('repQty').value);if(q<=0)throw Error('Quantidade precisa ser maior que zero.');
  const{data,error}=await db.rpc('registrar_reposicao_fefo',{p_reposicao_id:id,p_produto_id:repCurrentProduct.id,p_quantidade:q});if(error)throw error;
  const details=(data?.baixas||[]).map(b=>`${brDate(b.validade)}: ${nf(b.quantidade)}`).join(' · ');
  $('repResult').textContent=`Baixa concluída: ${nf(q)} un. · ${details}. Estoque físico não alterado.`;toast('Baixa FEFO registrada.','ok');
  await locateReposicaoProduct($('repCode').value);await loadReposicao();
 }catch(e){console.error(e);$('repResult').textContent=e.message||'Erro na baixa.';toast(e.message||'Erro na reposição.','err')}
}
async function finishReposicao(){if(!repSessionId)return;try{const{error}=await db.from('reposicoes').update({status:'concluida',concluido_em:new Date().toISOString()}).eq('id',repSessionId);if(error)throw error;repSessionId=null;repCurrentProduct=null;$('repProduct').classList.add('hidden');$('repDates').innerHTML='<div class="empty">Bipe um produto para ver as datas mais próximas.</div>';$('repResult').textContent='';toast('Reposição encerrada.','ok');await loadReposicao()}catch(e){toast(e.message||'Erro ao concluir reposição.','err')}}

// Inventário
let invSelectedProduct=null,invPendingDuplicate=null;
async function ensureInventario(){
 if(invSessionId)return invSessionId;
 const{data,error}=await db.from('inventarios').select('id,iniciado_em').eq('usuario_id',currentUser.id).eq('status','aberto').order('iniciado_em',{ascending:false}).limit(1).maybeSingle();if(error)throw error;
 if(data?.id){invSessionId=data.id;return invSessionId}
 const r=await db.from('inventarios').insert({usuario_id:currentUser.id,dispositivo_id:deviceRowId,status:'aberto'}).select('id').single();if(r.error)throw r.error;invSessionId=r.data.id;return invSessionId
}
async function newInventario(){try{if(invSessionId)return toast('Já existe um inventário aberto.','err');await ensureInventario();toast('Inventário aberto.','ok');await loadInventario()}catch(e){toast(e.message||'Erro ao abrir inventário.','err')}}
async function inventoryExistingItem(productId){if(!invSessionId)return null;const{data,error}=await db.from('inventario_itens').select('id,quantidade_sistema,quantidade_contada,status').eq('inventario_id',invSessionId).eq('produto_id',productId).maybeSingle();if(error)throw error;return data||null}
async function selectInventoryProduct(p){
 invSelectedProduct=p;$('invCode').value=p.codigo_barras||p.codigo_interno||p.nome;$('invSearchResults').innerHTML='';
 const s=await stockForProduct(p.id),old=await inventoryExistingItem(p.id);
 $('invSelected').classList.remove('hidden');$('invSelected').innerHTML=`<b>${esc(p.nome)}</b><span>Cód. ${esc(p.codigo_interno||'—')} · EAN ${esc(p.codigo_barras||'—')}</span><span>Endereço: ${esc(stockAddress(s))}</span><span>Saldo do sistema: <b>${nf(s.quantidade)}</b></span>${old?`<span class="already-counted">Já contado neste inventário: <b>${nf(old.quantidade_contada)}</b></span>`:''}`;
 $('invQty').focus();$('invQty').select();
}
async function searchInventoryProducts(term=null){
 try{
  const q=String(term??$('invCode').value).trim();if(!q){$('invSearchResults').innerHTML='';return}
  $('invSearchResults').innerHTML='<div class="mini-empty">Buscando...</div>';
  const exact=/^\d+$/.test(q)?await exactProduct(q):null;if(exact){await selectInventoryProduct(exact);return}
  const safe=q.replace(/[,()%\'\"]/g,' ');const{data,error}=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('ativo',true).or(`nome.ilike.%${safe}%,codigo_interno.ilike.%${safe}%,codigo_barras.ilike.%${safe}%`).limit(20);if(error)throw error;
  if(!data?.length){$('invSearchResults').innerHTML='<div class="mini-empty">Nenhum produto encontrado.</div>';return}
  window.__invSearchMap=new Map(data.map(p=>[p.id,p]));$('invSearchResults').innerHTML=data.map(p=>`<button class="search-result" onclick="selectInventoryProduct(window.__invSearchMap.get('${p.id}'))"><b>${esc(p.nome)}</b><span>Cód. ${esc(p.codigo_interno||'—')} · EAN ${esc(p.codigo_barras||'—')}</span></button>`).join('');
 }catch(e){console.error(e);$('invSearchResults').innerHTML='<div class="mini-empty">Erro ao pesquisar.</div>';toast(e.message||'Erro na pesquisa.','err')}
}
async function loadInventario(){
 if(!invSessionId){const{data}=await db.from('inventarios').select('id').eq('usuario_id',currentUser.id).eq('status','aberto').order('iniciado_em',{ascending:false}).limit(1).maybeSingle();invSessionId=data?.id||null}
 $('invFinish').classList.toggle('hidden',!invSessionId);$('invSessionTitle').textContent=invSessionId?'Inventário em andamento':'Nenhum inventário aberto';$('invSessionInfo').textContent=invSessionId?'Conte quantas vezes precisar e revise antes de finalizar.':'Abra um inventário para começar.';
 if(!invSessionId){$('invList').innerHTML='<div class="empty">Nenhum produto contado.</div>';$('invSummary').innerHTML='';return}
 const{data,error}=await db.from('inventario_itens').select('id,produto_id,endereco,quantidade_sistema,quantidade_contada,status,coletado_em,produtos(nome,codigo_interno,codigo_barras)').eq('inventario_id',invSessionId).order('criado_em',{ascending:false});if(error)throw error;
 const rows=data||[],divs=rows.filter(i=>i.status==='divergente').length;$('invSummary').innerHTML=`<div><b>${rows.length}</b><span>produtos conferidos</span></div><div><b>${divs}</b><span>com divergência</span></div>`;
 $('invList').innerHTML=rows.length?rows.map(i=>{const dif=num(i.quantidade_contada)-num(i.quantidade_sistema);return `<article class="op-card inventory-card"><h3>${esc(i.produtos?.nome||'Produto')}</h3><div class="op-meta"><span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b> · EAN <b>${esc(i.produtos?.codigo_barras||'—')}</b></span><span>Endereço <b>${esc(i.endereco||'Sem endereço')}</b></span><span>Sistema <b>${nf(i.quantidade_sistema)}</b></span><span>Contado <b>${nf(i.quantidade_contada)}</b></span><span class="${Math.abs(dif)<0.0001?'op-ok':'op-bad'}">Diferença: ${dif>0?'+':''}${nf(dif)}</span></div><div class="review-edit"><input id="invEdit-${i.id}" type="number" min="0" step="0.001" value="${num(i.quantidade_contada)}"><button class="secondary" onclick="editInventoryCount('${i.produto_id}','${i.id}')">Salvar correção</button></div></article>`}).join(''):'<div class="empty">Nenhum produto contado.</div>'
}
function showInventoryDuplicate(oldQty,newQty,p){invPendingDuplicate={oldQty,newQty,product:p};$('invDupOld').textContent=nf(oldQty);$('invDupNew').textContent=nf(newQty);$('invDuplicateInfo').textContent=`${p.nome} já está na lista. O que deseja fazer com a nova contagem?`;$('invDuplicateOverlay').classList.remove('hidden')}
function closeInventoryDuplicate(){invPendingDuplicate=null;$('invDuplicateOverlay').classList.add('hidden')}
async function saveInventoryCount(p,qty,mode){
 const id=await ensureInventario();const{data,error}=await db.rpc('salvar_contagem_inventario',{p_inventario_id:id,p_produto_id:p.id,p_quantidade:qty,p_modo:mode,p_dispositivo_id:deviceRowId});if(error)throw error;
 toast(mode==='somar'?`Contagem somada. Total: ${nf(data?.quantidade_contada)}`:`Total definido: ${nf(data?.quantidade_contada)}`,'ok');$('invQty').value='0';await selectInventoryProduct(p);await loadInventario();
}
async function addInventoryCount(){
 try{
  if(!invSelectedProduct){const q=$('invCode').value.trim();if(!q)throw Error('Bipe ou pesquise um produto.');const exact=await exactProduct(q);if(exact)await selectInventoryProduct(exact);else{await searchInventoryProducts(q);if(!invSelectedProduct)throw Error('Selecione um produto no resultado da pesquisa.')}}
  const qty=num($('invQty').value);if(qty<0)throw Error('Quantidade inválida.');const old=await inventoryExistingItem(invSelectedProduct.id);
  if(old){showInventoryDuplicate(old.quantidade_contada,qty,invSelectedProduct);return}
  await saveInventoryCount(invSelectedProduct,qty,'definir');
 }catch(e){console.error(e);toast(e.message||'Erro no inventário.','err')}
}
async function editInventoryCount(productId,itemId){try{const qty=num($('invEdit-'+itemId).value);if(qty<0)throw Error('Quantidade inválida.');const p=await db.from('produtos').select('id,nome,codigo_interno,codigo_barras,categoria').eq('id',productId).single();if(p.error)throw p.error;await saveInventoryCount(p.data,qty,'definir')}catch(e){toast(e.message||'Erro ao corrigir contagem.','err')}}
async function finishInventario(){
 if(!invSessionId)return;
 try{
  const{data:items,error:ie}=await db.from('inventario_itens').select('id,status').eq('inventario_id',invSessionId);if(ie)throw ie;if(!items?.length)throw Error('Inventário sem produtos conferidos.');
  const divs=items.filter(i=>i.status==='divergente').length;if(!confirm(`Revisão final:\n\n${items.length} produtos conferidos\n${divs} com divergência\n\nFinalizar e acertar o estoque real agora?`))return;
  const{data,error}=await db.rpc('finalizar_inventario',{p_inventario_id:invSessionId});if(error)throw error;
  const adjusted=data?.divergencias_ajustadas||0;invSessionId=null;invSelectedProduct=null;$('invSelected').classList.add('hidden');$('invCode').value='';$('invSearchResults').innerHTML='';toast(`Inventário finalizado. ${adjusted} ajustes aplicados ao estoque.`,'ok');await loadInventario();
 }catch(e){console.error(e);toast(e.message||'Erro ao finalizar inventário.','err')}
}

// Automatiza a tela após bipagem nos dois módulos.
const repoInvBarcodeHandler=window.onNativeBarcode;
window.onNativeBarcode=code=>{const target=operationalScanTarget;repoInvBarcodeHandler(code);if(target==='repCode')setTimeout(()=>locateReposicaoProduct(code),0);if(target==='invCode')setTimeout(async()=>{try{const p=await exactProduct(code);if(p)await selectInventoryProduct(p);else toast('Produto não encontrado.','err')}catch(e){toast(e.message||'Erro no produto.','err')}},0)};

$('repLocate').onclick=()=>locateReposicaoProduct();
$('repCode').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();locateReposicaoProduct()}});
$('repCode').addEventListener('input',()=>{repCurrentProduct=null;$('repProduct').classList.add('hidden');$('repDates').innerHTML='<div class="empty">Localize o produto para ver as datas.</div>'});
$('invSearchBtn').onclick=()=>searchInventoryProducts();
$('invCode').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();searchInventoryProducts()}});
$('invCode').addEventListener('input',()=>{invSelectedProduct=null;$('invSelected').classList.add('hidden')});
$('invDupAdd').onclick=async()=>{const x=invPendingDuplicate;if(!x)return;closeInventoryDuplicate();try{await saveInventoryCount(x.product,x.newQty,'somar')}catch(e){toast(e.message||'Erro ao somar contagem.','err')}};
$('invDupSet').onclick=async()=>{const x=invPendingDuplicate;if(!x)return;closeInventoryDuplicate();try{await saveInventoryCount(x.product,x.newQty,'definir')}catch(e){toast(e.message||'Erro ao definir total.','err')}};
$('invDupCancel').onclick=closeInventoryDuplicate;

// Conferência'''

logic_pattern = re.compile(r'// Reposição\n.*?// Inventário\n.*?// Conferência', re.S)
js, n = logic_pattern.subn(lambda _: new_logic, js, count=1)
if n != 1:
    raise SystemExit('Blocos antigos de Reposição/Inventário não encontrados.')

# Ajusta o botão da tela inicial para descrever a regra correta.
html = html.replace('<h3>Reposição</h3><p>Separação e coleta para reposição.</p>', '<h3>Reposição</h3><p>Baixa FEFO nas validades sem alterar o estoque físico.</p>')
html = html.replace('<h3>Inventário</h3><p>Contagem, divergência e recontagem.</p>', '<h3>Inventário</h3><p>Contagem real, revisão e acerto final do estoque.</p>')

css_extra = r'''
/* Reposição FEFO + Inventário com revisão */
.fefo-note,.inventory-note{display:grid;gap:4px;border-left:4px solid var(--purple)}.fefo-note b,.inventory-note b{color:#6d28d9}.fefo-note span,.inventory-note span{font-size:12px;color:var(--muted);line-height:1.45}.selected-product{margin-top:13px;padding:13px;border:1px solid #ddd6fe;background:#faf8ff;border-radius:14px;display:grid;gap:4px;font-size:12px}.selected-product>b{font-size:15px}.selected-product span{color:#667085}.selected-product em{font-style:normal;color:var(--ok);font-size:10px}.validity-stack{margin-top:13px}.validity-title{font-size:12px;font-weight:800;margin-bottom:7px}.validity-row{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:11px 12px;margin:6px 0;border-radius:12px;background:#f8f9fc;border:1px solid #eef0f5}.validity-row>div{display:grid;gap:2px}.validity-row span{font-size:9px;color:var(--muted);font-weight:800}.validity-row b{font-size:14px}.validity-row strong{font-size:11px;text-align:right}.validity-row.next{border-color:#c4b5fd;background:#f5f3ff}.validity-row.next span{color:#6d28d9}.rep-qty-grid,.inv-qty-grid{margin-top:13px}.section-title{font-size:14px;margin:18px 2px 8px}.search-results{display:grid;gap:6px;margin-top:9px}.search-result{border:1px solid var(--line);border-radius:12px;background:#fff;padding:10px 11px;text-align:left}.search-result b{display:block;font-size:12px}.search-result span{display:block;font-size:10px;color:var(--muted);margin-top:3px}.mini-empty{font-size:11px;color:var(--muted);padding:10px 2px}.already-counted{color:#7c3aed!important;font-weight:700}.inventory-summary{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:12px 0}.inventory-summary div{background:#fff;border:1px solid var(--line);border-radius:13px;padding:11px}.inventory-summary b{display:block;font-size:20px;color:#6d28d9}.inventory-summary span{font-size:10px;color:var(--muted)}.review-edit{display:grid;grid-template-columns:1fr auto;gap:7px;margin-top:11px}.review-edit input{min-width:0;border:1px solid var(--line);border-radius:11px;padding:9px}.review-edit .secondary{padding:9px 11px;font-size:11px}.duplicate-values{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin:15px 0}.duplicate-values div{border:1px solid var(--line);border-radius:13px;padding:12px;background:#f8f9fc}.duplicate-values small{display:block;color:var(--muted);font-size:10px}.duplicate-values b{display:block;font-size:22px;margin-top:3px}.inventory-dialog .primary{width:100%}@media(min-width:760px){.validity-stack{max-width:650px}.inventory-summary{max-width:420px}}
'''
if 'Reposição FEFO + Inventário com revisão' not in css:
    css += '\n' + css_extra

# Verificações de build para evitar publicar interface parcial.
checks = {
    'reposicao-fefo': 'registrar_reposicao_fefo' in js,
    'inventario-rpc': 'salvar_contagem_inventario' in js and 'finalizar_inventario' in js,
    'duplicidade': 'SOMAR À CONTAGEM' in html and 'DEFINIR COMO TOTAL' in html,
    'pesquisa-inventario': 'invSearchBtn' in html and 'searchInventoryProducts' in js,
    'estoque-preservado': 'Estoque físico não será alterado' in html or 'estoque físico não é alterado' in html,
}
missing=[k for k,v in checks.items() if not v]
if missing:
    raise SystemExit('Reposição/Inventário V2 incompletos: '+', '.join(missing))

index_path.write_text(html, encoding='utf-8')
app_path.write_text(js, encoding='utf-8')
css_path.write_text(css, encoding='utf-8')
print('Reposição FEFO e Inventário com acúmulo/revisão preparados e validados.')
