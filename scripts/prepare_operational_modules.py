from pathlib import Path
import re

index_path = Path('app/src/main/assets/index.html')
app_path = Path('app/src/main/assets/app.js')
css_path = Path('app/src/main/assets/styles.css')

html = index_path.read_text(encoding='utf-8')
js = app_path.read_text(encoding='utf-8')
css = css_path.read_text(encoding='utf-8')

# Ativa os módulos da tela inicial e associa cada um à permissão correspondente.
replacements = {
    '<button class="action" data-soon><div class="icon">📦</div><h3>Reposição</h3><p>Controle de reposição.</p></button>': '<button class="action" data-view-open="reposicao" data-perm="reposicao"><div class="icon">📦</div><h3>Reposição</h3><p>Separação e coleta para reposição.</p></button>',
    '<button class="action" data-soon><div class="icon">🧾</div><h3>Inventário</h3><p>Contagem de estoque.</p></button>': '<button class="action" data-view-open="inventario" data-perm="inventario"><div class="icon">🧾</div><h3>Inventário</h3><p>Contagem, divergência e recontagem.</p></button>',
    '<button class="action" data-soon><div class="icon">✅</div><h3>Conferência</h3><p>Conferir mercadorias.</p></button>': '<button class="action" data-view-open="conferencia" data-perm="conferencia"><div class="icon">✅</div><h3>Conferência</h3><p>Conferir produtos por bipagem.</p></button>',
    '<button class="action" data-soon><div class="icon">⚠️</div><h3>Avaria</h3><p>Controle de avarias.</p></button>': '<button class="action" data-view-open="avaria" data-perm="avaria"><div class="icon">⚠️</div><h3>Avaria</h3><p>Registrar perdas, danos e vencidos.</p></button>',
    '<button class="action" data-soon><div class="icon">🔄</div><h3>Movimentação</h3><p>Entradas, saídas e ajustes.</p></button>': '<button class="action" data-view-open="movimentacoes" data-perm="movimentacoes"><div class="icon">🔄</div><h3>Movimentação</h3><p>Entradas, saídas e ajustes com auditoria.</p></button>',
    '<button class="action" data-soon><div class="icon">🏷️</div><h3>Endereçamento</h3><p>Localização do produto.</p></button>': '<button class="action" data-soon data-perm="enderecamento"><div class="icon">🏷️</div><h3>Endereçamento</h3><p>Localização do produto.</p></button>',
    '<button class="action" data-view-open="imports"><div class="icon">📥</div><h3>Importações</h3><p>PDF de estoque e validades.</p></button>': '<button class="action" data-view-open="imports" data-perm="importacoes"><div class="icon">📥</div><h3>Importações</h3><p>PDF de estoque e validades.</p></button>',
    '<button class="action wide" data-soon><div class="icon">📅</div><h3>Controle de validade</h3><p>Vencidos, próximos do vencimento e todas as datas do produto.</p></button>': '<button class="action wide" data-soon data-perm="validade"><div class="icon">📅</div><h3>Controle de validade</h3><p>Vencidos, próximos do vencimento e todas as datas do produto.</p></button>',
}
for old, new in replacements.items():
    if old in html:
        html = html.replace(old, new)

modules_html = r'''
    <section id="reposicao" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Reposição</h2><small>Bipe, separe e conclua os produtos</small></div></div>
      <div class="panel op-session"><div><b id="repSessionTitle">Nenhuma reposição aberta</b><small id="repSessionInfo">Abra uma reposição para começar.</small></div><div class="actions"><button id="repNew" class="purple">＋ Nova reposição</button><button id="repFinish" class="secondary hidden">✓ Concluir</button></div></div>
      <div class="panel">
        <div class="op-grid"><div><label>Código / EAN</label><div class="scanline"><input id="repCode" inputmode="numeric" placeholder="Bipe ou digite"><button id="repScan" class="secondary">▥</button></div></div><div><label>Quantidade sugerida</label><input id="repQty" type="number" min="0" step="0.001" value="1"></div></div>
        <div class="actions"><button id="repAdd" class="purple">Adicionar à reposição</button></div>
      </div>
      <div id="repList" class="op-list"><div class="empty">Nenhum item nesta reposição.</div></div>
    </section>

    <section id="inventario" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Inventário</h2><small>Contagem com comparação do saldo do sistema</small></div></div>
      <div class="panel op-session"><div><b id="invSessionTitle">Nenhum inventário aberto</b><small id="invSessionInfo">Abra um inventário para começar.</small></div><div class="actions"><button id="invNew" class="purple">＋ Novo inventário</button><button id="invFinish" class="secondary hidden">✓ Concluir</button></div></div>
      <div class="panel">
        <div class="op-grid"><div><label>Código / EAN</label><div class="scanline"><input id="invCode" inputmode="numeric" placeholder="Bipe ou digite"><button id="invScan" class="secondary">▥</button></div></div><div><label>Quantidade contada</label><input id="invQty" type="number" min="0" step="0.001" value="0"></div></div>
        <div class="actions"><button id="invAdd" class="purple">Salvar contagem</button></div>
      </div>
      <div id="invList" class="op-list"><div class="empty">Nenhuma contagem registrada.</div></div>
    </section>

    <section id="conferencia" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Conferência</h2><small>Registro rápido por produto</small></div></div>
      <div class="panel">
        <div class="op-grid"><div><label>Código / EAN</label><div class="scanline"><input id="confCode" inputmode="numeric" placeholder="Bipe ou digite"><button id="confScan" class="secondary">▥</button></div></div><div><label>Quantidade</label><input id="confQty" type="number" min="0" step="0.001" value="1"></div><div><label>Tipo</label><select id="confType"><option value="geral">Geral</option><option value="pedido">Pedido</option><option value="transferencia">Transferência</option><option value="inventario">Inventário</option></select></div><div><label>Referência</label><input id="confRef" placeholder="Pedido / transferência (opcional)"></div></div>
        <label class="op-label">Observação</label><textarea id="confObs" class="op-textarea" placeholder="Observação opcional"></textarea>
        <div class="actions"><button id="confSave" class="purple">✓ Registrar conferência</button></div>
      </div>
      <div id="confList" class="op-list"><div class="empty">Nenhuma conferência recente.</div></div>
    </section>

    <section id="avaria" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Avaria</h2><small>Perdas, danos e produtos vencidos</small></div></div>
      <div class="panel">
        <div class="op-grid"><div><label>Código / EAN</label><div class="scanline"><input id="avCode" inputmode="numeric" placeholder="Bipe ou digite"><button id="avScan" class="secondary">▥</button></div></div><div><label>Quantidade</label><input id="avQty" type="number" min="0.001" step="0.001" value="1"></div><div><label>Tipo</label><select id="avType"><option value="quebrado">Quebrado</option><option value="vazamento">Vazamento</option><option value="amassado">Amassado</option><option value="vencido">Vencido</option><option value="outro">Outro</option></select></div><label class="checkline op-check"><input id="avExpired" type="checkbox"><span><b>Produto vencido</b><br><small>Marca o registro como vencimento/perda.</small></span></label></div>
        <label class="op-label">Observação</label><textarea id="avObs" class="op-textarea" placeholder="Motivo ou detalhe da avaria"></textarea>
        <div class="actions"><button id="avSave" class="purple">⚠ Registrar avaria</button></div>
      </div>
      <div id="avList" class="op-list"><div class="empty">Nenhuma avaria recente.</div></div>
    </section>

    <section id="movimentacoes" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Movimentações</h2><small>Saldo atualizado com auditoria automática</small></div></div>
      <div class="panel">
        <div class="op-grid"><div><label>Código / EAN</label><div class="scanline"><input id="movCode" inputmode="numeric" placeholder="Bipe ou digite"><button id="movScan" class="secondary">▥</button></div></div><div><label>Tipo</label><select id="movType"><option value="entrada">Entrada</option><option value="saida">Saída</option><option value="ajuste">Ajuste (novo saldo)</option></select></div><div><label id="movQtyLabel">Quantidade</label><input id="movQty" type="number" min="0" step="0.001" value="1"></div></div>
        <label class="op-label">Observação</label><textarea id="movObs" class="op-textarea" placeholder="Motivo da movimentação"></textarea>
        <div class="actions"><button id="movSave" class="purple">↻ Aplicar movimentação</button></div>
        <div id="movResult" class="status"></div>
      </div>
      <div id="movList" class="op-list"><div class="empty">Nenhuma movimentação recente.</div></div>
    </section>
'''

if 'id="reposicao"' not in html:
    html = html.replace('    <section id="imports" class="hidden">', modules_html + '\n    <section id="imports" class="hidden">')

# Perfil passa a carregar permissões.
js = js.replace(".select('nome,papel,filial_id,ativo')", ".select('nome,papel,filial_id,ativo,permissoes')")
js = js.replace("profile=data||{nome:session.user.email.split('@')[0],papel:'funcionario',filial_id:null,ativo:true};", "profile=data||{nome:session.user.email.split('@')[0],papel:'funcionario',filial_id:null,ativo:true,permissoes:{busca_produto:true}};")

# Expande o roteamento das telas.
js = re.sub(
    r"function show\(v\)\{for\(const id of\['home','search','imports','updates'\]\)\$\(id\)\.classList\.toggle\('hidden',id!==v\);if\(v==='search'\)setTimeout\(\(\)=>\$\('q'\)\.focus\(\),50\);if\(v==='updates'\)loadLatestVersion\(\)\}",
    "function show(v){const operational=['reposicao','inventario','conferencia','avaria','movimentacoes'];if(operational.includes(v)&&!hasPerm(v==='movimentacoes'?'movimentacoes':v)){toast('Você não tem permissão para este módulo.','err');v='home'}for(const id of ['home','search','reposicao','inventario','conferencia','avaria','movimentacoes','imports','updates'])$(id).classList.toggle('hidden',id!==v);if(v==='search')setTimeout(()=>$('q').focus(),50);if(v==='updates')loadLatestVersion();if(operational.includes(v))loadOperationalView(v)}",
    js,
    count=1
)

# Após login, aplica as permissões e registra o aparelho.
js = js.replace("$('auth').classList.add('hidden');$('app').classList.remove('hidden');show('home');refreshProductIndex();await checkUpdate();", "$('auth').classList.add('hidden');$('app').classList.remove('hidden');applyPermissions();registerDevice();show('home');refreshProductIndex();await checkUpdate();")

operational_js = r'''

// ===== Módulos operacionais V5.0.7 =====
let repSessionId=null,invSessionId=null,operationalScanTarget=null,deviceRowId=null;
const opNativeBarcode=window.onNativeBarcode;
function num(v){const n=Number(v);return Number.isFinite(n)?n:0}
function nf(v){return num(v).toLocaleString('pt-BR',{maximumFractionDigits:3})}
function dt(v){if(!v)return'—';try{return new Date(v).toLocaleString('pt-BR')}catch{return String(v)}}
function hasPerm(k){if(!profile||profile.ativo===false)return false;if(profile.papel==='administrador'||profile.papel==='gerente')return true;return profile.permissoes?.[k]===true}
function applyPermissions(){document.querySelectorAll('[data-perm]').forEach(el=>el.classList.toggle('hidden',!hasPerm(el.dataset.perm)));if($('updatesCard'))$('updatesCard').classList.toggle('hidden',profile?.papel!=='administrador')}
function startModuleScan(inputId){operationalScanTarget=inputId;if(window.AndroidApp?.scanBarcode)AndroidApp.scanBarcode();else{operationalScanTarget=null;toast('Use o APK para bipar.','err')}}
window.onNativeBarcode=code=>{const target=operationalScanTarget;operationalScanTarget=null;if(target&&$(target)){ $(target).value=String(code||'');$(target).focus();toast('Código lido: '+code,'ok');return }if(opNativeBarcode)opNativeBarcode(code)};

async function registerDevice(){
 try{
  let did=localStorage.getItem('aws_device_id');if(!did){did=(crypto?.randomUUID?.()||('dev-'+Date.now()+'-'+Math.random().toString(16).slice(2)));localStorage.setItem('aws_device_id',did)}
  const vc=window.AndroidApp?.getAppVersionCode?Number(AndroidApp.getAppVersionCode())||null:null;
  const vn=window.AndroidApp?.getAppVersionName?String(AndroidApp.getAppVersionName()||'')||null:null;
  const row={device_id:did,usuario_id:currentUser.id,nome:'Aparelho AWS',plataforma:'android',app_version_code:vc,app_version_name:vn,ativo:true,ultimo_acesso:new Date().toISOString(),atualizado_em:new Date().toISOString()};
  const{data,error}=await db.from('dispositivos').upsert(row,{onConflict:'device_id'}).select('id').single();if(error)throw error;deviceRowId=data?.id||null;
 }catch(e){console.warn('Registro do dispositivo não concluído',e)}
}
async function productForOperation(code){const s=String(code||'').trim();if(!s)throw Error('Informe ou bipe o código do produto.');const p=await exactProduct(s);if(!p)throw Error('Produto não encontrado.');return p}
async function stockForProduct(id){const{data,error}=await db.from('estoques').select('quantidade,endereco,rua,prateleira,nivel,posicao').is('filial_id',null).eq('produto_id',id).maybeSingle();if(error)throw error;return data||{quantidade:0}}
function stockAddress(s){return s?.endereco||[s?.rua,s?.prateleira,s?.nivel,s?.posicao].filter(Boolean).join(' - ')||'Sem endereço'}
function opCard(title,meta,actions=''){return`<article class="op-card"><h3>${esc(title)}</h3><div class="op-meta">${meta}</div>${actions?`<div class="actions">${actions}</div>`:''}</article>`}

async function loadOperationalView(v){try{if(v==='reposicao')await loadReposicao();if(v==='inventario')await loadInventario();if(v==='conferencia')await loadConferencias();if(v==='avaria')await loadAvarias();if(v==='movimentacoes')await loadMovimentacoes()}catch(e){console.error(e);toast(e.message||'Erro ao carregar módulo.','err')}}

// Reposição
async function ensureReposicao(){if(repSessionId)return repSessionId;const{data,error}=await db.from('reposicoes').select('id,criado_em').eq('criado_por',currentUser.id).eq('status','aberta').order('criado_em',{ascending:false}).limit(1).maybeSingle();if(error)throw error;if(data?.id){repSessionId=data.id;return repSessionId}const r=await db.from('reposicoes').insert({criado_por:currentUser.id,dispositivo_id:deviceRowId,status:'aberta'}).select('id').single();if(r.error)throw r.error;repSessionId=r.data.id;return repSessionId}
async function newReposicao(){if(repSessionId)return toast('Já existe uma reposição aberta.','err');await ensureReposicao();toast('Reposição aberta.','ok');await loadReposicao()}
async function loadReposicao(){
 if(!repSessionId){const{data}=await db.from('reposicoes').select('id,criado_em').eq('criado_por',currentUser.id).eq('status','aberta').order('criado_em',{ascending:false}).limit(1).maybeSingle();repSessionId=data?.id||null}
 $('repFinish').classList.toggle('hidden',!repSessionId);$('repSessionTitle').textContent=repSessionId?'Reposição em andamento':'Nenhuma reposição aberta';$('repSessionInfo').textContent=repSessionId?'Bipe os itens e marque a coleta.':'Abra uma reposição para começar.';
 if(!repSessionId){$('repList').innerHTML='<div class="empty">Nenhum item nesta reposição.</div>';return}
 const{data,error}=await db.from('reposicao_itens').select('id,produto_id,quantidade_sugerida,quantidade_coletada,status,endereco_resolvido,produtos(nome,codigo_interno,codigo_barras)').eq('reposicao_id',repSessionId).order('criado_em',{ascending:false});if(error)throw error;
 $('repList').innerHTML=(data||[]).length?(data||[]).map(i=>opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Endereço <b>${esc(i.endereco_resolvido||'Sem endereço')}</b></span><span>Sugerido <b>${nf(i.quantidade_sugerida)}</b></span><span>Coletado <b>${i.quantidade_coletada==null?'—':nf(i.quantidade_coletada)}</b></span><span>Status <b>${esc(i.status)}</b></span>`,i.status==='concluido'?'':`<button class="purple" onclick="completeRepItem('${i.id}',${num(i.quantidade_sugerida)})">✓ Coletado</button>`)).join(''):'<div class="empty">Nenhum item nesta reposição.</div>'
}
async function addReposicaoItem(){try{const id=await ensureReposicao(),p=await productForOperation($('repCode').value),q=num($('repQty').value);if(q<=0)throw Error('Quantidade precisa ser maior que zero.');const s=await stockForProduct(p.id);const{data:old,error:oe}=await db.from('reposicao_itens').select('id,quantidade_sugerida').eq('reposicao_id',id).eq('produto_id',p.id).eq('status','pendente').limit(1).maybeSingle();if(oe)throw oe;if(old?.id){const r=await db.from('reposicao_itens').update({quantidade_sugerida:num(old.quantidade_sugerida)+q,endereco_resolvido:stockAddress(s),origem_localizacao:'estoque'}).eq('id',old.id);if(r.error)throw r.error}else{const r=await db.from('reposicao_itens').insert({reposicao_id:id,produto_id:p.id,quantidade_sugerida:q,status:'pendente',endereco_resolvido:stockAddress(s),origem_localizacao:'estoque',usuario_id:currentUser.id});if(r.error)throw r.error}$('repCode').value='';$('repCode').focus();toast('Item adicionado à reposição.','ok');await loadReposicao()}catch(e){console.error(e);toast(e.message||'Erro na reposição.','err')}}
async function completeRepItem(id,q){try{const{error}=await db.from('reposicao_itens').update({quantidade_coletada:q,status:'concluido',usuario_id:currentUser.id,coletado_em:new Date().toISOString()}).eq('id',id);if(error)throw error;await loadReposicao()}catch(e){toast(e.message||'Erro ao concluir item.','err')}}
async function finishReposicao(){if(!repSessionId)return;try{const{error}=await db.from('reposicoes').update({status:'concluida',concluido_em:new Date().toISOString()}).eq('id',repSessionId);if(error)throw error;repSessionId=null;toast('Reposição concluída.','ok');await loadReposicao()}catch(e){toast(e.message||'Erro ao concluir reposição.','err')}}

// Inventário
async function ensureInventario(){if(invSessionId)return invSessionId;const{data,error}=await db.from('inventarios').select('id,iniciado_em').eq('usuario_id',currentUser.id).eq('status','aberto').order('iniciado_em',{ascending:false}).limit(1).maybeSingle();if(error)throw error;if(data?.id){invSessionId=data.id;return invSessionId}const r=await db.from('inventarios').insert({usuario_id:currentUser.id,dispositivo_id:deviceRowId,status:'aberto'}).select('id').single();if(r.error)throw r.error;invSessionId=r.data.id;return invSessionId}
async function newInventario(){if(invSessionId)return toast('Já existe um inventário aberto.','err');await ensureInventario();toast('Inventário aberto.','ok');await loadInventario()}
async function loadInventario(){if(!invSessionId){const{data}=await db.from('inventarios').select('id').eq('usuario_id',currentUser.id).eq('status','aberto').order('iniciado_em',{ascending:false}).limit(1).maybeSingle();invSessionId=data?.id||null}$('invFinish').classList.toggle('hidden',!invSessionId);$('invSessionTitle').textContent=invSessionId?'Inventário em andamento':'Nenhum inventário aberto';$('invSessionInfo').textContent=invSessionId?'Cada nova leitura pode corrigir a contagem do mesmo produto.':'Abra um inventário para começar.';if(!invSessionId){$('invList').innerHTML='<div class="empty">Nenhuma contagem registrada.</div>';return}const{data,error}=await db.from('inventario_itens').select('id,produto_id,endereco,quantidade_sistema,quantidade_contada,status,coletado_em,produtos(nome,codigo_interno)').eq('inventario_id',invSessionId).order('criado_em',{ascending:false});if(error)throw error;$('invList').innerHTML=(data||[]).length?(data||[]).map(i=>opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Endereço <b>${esc(i.endereco||'Sem endereço')}</b></span><span>Sistema <b>${nf(i.quantidade_sistema)}</b></span><span>Contado <b>${nf(i.quantidade_contada)}</b></span><span class="${i.status==='divergente'?'op-bad':'op-ok'}">${i.status==='divergente'?'Divergência':'Correto'}</span>`)).join(''):'<div class="empty">Nenhuma contagem registrada.</div>'}
async function addInventoryCount(){try{const id=await ensureInventario(),p=await productForOperation($('invCode').value),count=num($('invQty').value);if(count<0)throw Error('Quantidade inválida.');const s=await stockForProduct(p.id),sys=num(s.quantidade),status=Math.abs(sys-count)<0.0001?'correto':'divergente';const{data:old,error:oe}=await db.from('inventario_itens').select('id').eq('inventario_id',id).eq('produto_id',p.id).limit(1).maybeSingle();if(oe)throw oe;const row={endereco:stockAddress(s),quantidade_sistema:sys,quantidade_contada:count,status,usuario_id:currentUser.id,dispositivo_id:deviceRowId,coletado_em:new Date().toISOString()};const r=old?.id?await db.from('inventario_itens').update(row).eq('id',old.id):await db.from('inventario_itens').insert({...row,inventario_id:id,produto_id:p.id});if(r.error)throw r.error;$('invCode').value='';$('invCode').focus();toast(status==='correto'?'Contagem correta.':'Divergência registrada.','ok');await loadInventario()}catch(e){console.error(e);toast(e.message||'Erro no inventário.','err')}}
async function finishInventario(){if(!invSessionId)return;try{const{error}=await db.from('inventarios').update({status:'concluido',concluido_em:new Date().toISOString()}).eq('id',invSessionId);if(error)throw error;invSessionId=null;toast('Inventário concluído.','ok');await loadInventario()}catch(e){toast(e.message||'Erro ao concluir inventário.','err')}}

// Conferência
async function saveConferencia(){try{const p=await productForOperation($('confCode').value),q=num($('confQty').value),tipo=$('confType').value,ref=$('confRef').value.trim();if(q<=0)throw Error('Quantidade precisa ser maior que zero.');const row={produto_id:p.id,quantidade:q,status:'concluido',tipo,usuario_id:currentUser.id,dispositivo_id:deviceRowId,observacao:$('confObs').value.trim()||null,concluido_em:new Date().toISOString()};if(tipo==='pedido')row.pedido_referencia=ref||null;if(tipo==='transferencia')row.transferencia_referencia=ref||null;const{error}=await db.from('conferencias').insert(row);if(error)throw error;$('confCode').value='';$('confObs').value='';$('confCode').focus();toast('Conferência registrada.','ok');await loadConferencias()}catch(e){console.error(e);toast(e.message||'Erro na conferência.','err')}}
async function loadConferencias(){const{data,error}=await db.from('conferencias').select('id,quantidade,tipo,status,pedido_referencia,transferencia_referencia,criado_em,produtos(nome,codigo_interno)').order('criado_em',{ascending:false}).limit(30);if(error)throw error;$('confList').innerHTML=(data||[]).length?(data||[]).map(i=>opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Qtd. <b>${nf(i.quantidade)}</b></span><span>Tipo <b>${esc(i.tipo)}</b></span><span>${dt(i.criado_em)}</span>`)).join(''):'<div class="empty">Nenhuma conferência recente.</div>'}

// Avaria
async function saveAvaria(){try{const p=await productForOperation($('avCode').value),q=num($('avQty').value),tipo=$('avType').value;if(q<=0)throw Error('Quantidade precisa ser maior que zero.');const{error}=await db.from('avarias').insert({produto_id:p.id,quantidade:q,vencido:$('avExpired').checked||tipo==='vencido',tipo,status:'aberta',usuario_id:currentUser.id,dispositivo_id:deviceRowId,observacao:$('avObs').value.trim()||null});if(error)throw error;$('avCode').value='';$('avObs').value='';$('avExpired').checked=false;$('avCode').focus();toast('Avaria registrada.','ok');await loadAvarias()}catch(e){console.error(e);toast(e.message||'Erro ao registrar avaria.','err')}}
async function loadAvarias(){const{data,error}=await db.from('avarias').select('id,quantidade,vencido,tipo,status,criado_em,produtos(nome,codigo_interno)').order('criado_em',{ascending:false}).limit(30);if(error)throw error;$('avList').innerHTML=(data||[]).length?(data||[]).map(i=>opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Qtd. <b>${nf(i.quantidade)}</b></span><span>Tipo <b>${esc(i.tipo||'—')}</b></span><span>${i.vencido?'⚠ Vencido · ':''}${dt(i.criado_em)}</span>`)).join(''):'<div class="empty">Nenhuma avaria recente.</div>'}

// Movimentações
async function saveMovimentacao(){try{const p=await productForOperation($('movCode').value),q=num($('movQty').value),tipo=$('movType').value;if(q<0)throw Error('Quantidade inválida.');if((tipo==='entrada'||tipo==='saida')&&q<=0)throw Error('Quantidade precisa ser maior que zero.');const{data,error}=await db.rpc('registrar_movimentacao_estoque',{p_produto_id:p.id,p_tipo:tipo,p_quantidade:q,p_observacao:$('movObs').value.trim()||null});if(error)throw error;$('movResult').textContent=`${p.nome}: saldo ${nf(data?.saldo_anterior)} → ${nf(data?.saldo_posterior)}`;$('movCode').value='';$('movObs').value='';$('movCode').focus();toast('Estoque atualizado e auditado.','ok');await loadMovimentacoes()}catch(e){console.error(e);toast(e.message||'Erro na movimentação.','err')}}
async function loadMovimentacoes(){const{data,error}=await db.from('movimentacoes').select('id,tipo,quantidade,saldo_anterior,saldo_posterior,observacao,criado_em,produtos(nome,codigo_interno)').order('criado_em',{ascending:false}).limit(40);if(error)throw error;$('movList').innerHTML=(data||[]).length?(data||[]).map(i=>opCard(i.produtos?.nome||'Produto',`<span>Cód. <b>${esc(i.produtos?.codigo_interno||'—')}</b></span><span>Tipo <b>${esc(i.tipo)}</b></span><span>Mov. <b>${nf(i.quantidade)}</b></span><span>Saldo <b>${nf(i.saldo_anterior)} → ${nf(i.saldo_posterior)}</b></span><span>${dt(i.criado_em)}</span>`)).join(''):'<div class="empty">Nenhuma movimentação recente.</div>'}

// Eventos de UI.
$('repNew').onclick=newReposicao;$('repFinish').onclick=finishReposicao;$('repAdd').onclick=addReposicaoItem;$('repScan').onclick=()=>startModuleScan('repCode');
$('invNew').onclick=newInventario;$('invFinish').onclick=finishInventario;$('invAdd').onclick=addInventoryCount;$('invScan').onclick=()=>startModuleScan('invCode');
$('confSave').onclick=saveConferencia;$('confScan').onclick=()=>startModuleScan('confCode');
$('avSave').onclick=saveAvaria;$('avScan').onclick=()=>startModuleScan('avCode');
$('movSave').onclick=saveMovimentacao;$('movScan').onclick=()=>startModuleScan('movCode');$('movType').onchange=()=>$('movQtyLabel').textContent=$('movType').value==='ajuste'?'Novo saldo':'Quantidade';
'''

if '// ===== Módulos operacionais V5.0.7 =====' not in js:
    # Coloca antes do tratamento do botão Voltar, que será substituído abaixo.
    pos = js.find('window.androidBack=')
    if pos >= 0:
        js = js[:pos] + operational_js + '\n' + js[pos:]
    else:
        js += operational_js

# Voltar físico funciona em qualquer módulo novo.
js = re.sub(
    r"window\.androidBack=.*$",
    "window.androidBack=()=>{if(!$('updateOverlay').classList.contains('hidden')){if(latestUpdate?.obrigatoria)return;$('updateOverlay').classList.add('hidden');return}const views=['search','reposicao','inventario','conferencia','avaria','movimentacoes','imports','updates'];if(views.some(id=>!$(id).classList.contains('hidden')))return show('home');AndroidApp?.exitApp?.()};",
    js,
    flags=re.S
)

css_extra = r'''
/* módulos operacionais V5.0.7 */
.op-session{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}.op-session small{display:block;color:var(--muted);margin-top:4px}.op-grid{display:grid;grid-template-columns:1fr;gap:12px}.op-grid label,.op-label{display:block;font-size:12px;font-weight:750;margin:0 0 6px 2px}.op-grid input,.op-grid select,.op-textarea{width:100%;border:1px solid var(--line);border-radius:13px;padding:13px;background:#fff;color:var(--text);outline:0}.op-textarea{min-height:76px;resize:vertical;margin-bottom:4px}.scanline{display:grid;grid-template-columns:1fr auto;gap:7px}.scanline .secondary{padding-inline:16px}.op-list{margin-top:12px}.op-card{background:#fff;border:1px solid var(--line);border-radius:16px;padding:14px;margin-bottom:9px;box-shadow:0 7px 18px rgba(31,41,55,.045)}.op-card h3{font-size:14px;margin:0 0 8px}.op-meta{display:grid;gap:4px;font-size:11px;color:var(--muted)}.op-meta b{color:var(--text)}.op-ok{color:var(--ok)!important;font-weight:800}.op-bad{color:var(--bad)!important;font-weight:800}.op-check{margin:0}.op-card .actions{margin-top:10px}.op-card .purple{padding:9px 12px;font-size:12px}@media(min-width:760px){.op-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
'''
if 'módulos operacionais V5.0.7' not in css:
    css += '\n' + css_extra

index_path.write_text(html, encoding='utf-8')
app_path.write_text(js, encoding='utf-8')
css_path.write_text(css, encoding='utf-8')
print('Módulos operacionais V5.0.7 preparados: reposição, inventário, conferência, avaria, movimentações e permissões.')
