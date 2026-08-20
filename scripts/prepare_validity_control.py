from pathlib import Path
import re

index_path = Path('app/src/main/assets/index.html')
app_path = Path('app/src/main/assets/app.js')
css_path = Path('app/src/main/assets/styles.css')
kotlin_path = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')

html = index_path.read_text(encoding='utf-8')
js = app_path.read_text(encoding='utf-8')
css = css_path.read_text(encoding='utf-8')
kt = kotlin_path.read_text(encoding='utf-8')

# Ativa o card existente do Controle de validade.
html = re.sub(
    r'<button class="action wide" data-soon(?: data-perm="validade")?><div class="icon">📅</div><h3>Controle de validade</h3><p>.*?</p></button>',
    '<button class="action wide" data-view-open="validade" data-perm="validade"><div class="icon">📅</div><h3>Controle de validade</h3><p>Vencidos, próximos do vencimento e relatório PDF A4.</p></button>',
    html,
    count=1,
    flags=re.S,
)

validity_html = r'''
    <section id="validade" class="hidden">
      <div class="head"><button class="back" data-home>←</button><div><h2>Controle de validade</h2><small>Produtos vencidos e próximos do vencimento</small></div></div>
      <div class="panel validity-toolbar">
        <div class="validity-filters" id="validityFilters">
          <button class="validity-filter" data-validity-filter="vencidos">Vencidos <b id="countExpired">0</b></button>
          <button class="validity-filter" data-validity-filter="hoje">Hoje <b id="countToday">0</b></button>
          <button class="validity-filter" data-validity-filter="7">7 dias <b id="count7">0</b></button>
          <button class="validity-filter" data-validity-filter="15">15 dias <b id="count15">0</b></button>
          <button class="validity-filter active" data-validity-filter="30">30 dias <b id="count30">0</b></button>
          <button class="validity-filter" data-validity-filter="todos">Todos</button>
        </div>
        <div class="validity-actions-row">
          <input id="validitySearch" placeholder="Produto, código, EAN ou endereço">
          <button id="validityPdf" class="purple">📄 Salvar / Imprimir PDF A4</button>
        </div>
        <div id="validitySummary" class="status">Carregando...</div>
      </div>
      <div id="validityList" class="validity-list"><div class="empty">Carregando validades...</div></div>
      <div class="actions"><button id="validityMore" class="secondary hidden">Mostrar mais</button></div>
    </section>
'''

if 'id="validade"' not in html:
    html = html.replace('    <section id="imports" class="hidden">', validity_html + '\n    <section id="imports" class="hidden">', 1)

# Roteamento e permissão.
js = js.replace("['reposicao','inventario','conferencia','avaria','movimentacoes']", "['reposicao','inventario','conferencia','avaria','movimentacoes','validade']")
js = js.replace("['home','search','reposicao','inventario','conferencia','avaria','movimentacoes','imports','updates']", "['home','search','reposicao','inventario','conferencia','avaria','movimentacoes','validade','imports','updates']")
js = js.replace("if(v==='movimentacoes')await loadMovimentacoes()", "if(v==='movimentacoes')await loadMovimentacoes();if(v==='validade')await loadValidityControl()")
js = js.replace("['search','reposicao','inventario','conferencia','avaria','movimentacoes','imports','updates']", "['search','reposicao','inventario','conferencia','avaria','movimentacoes','validade','imports','updates']")

validity_js = r'''

// ===== Controle de Validade =====
let validityFilter='30',validityRows=[],validityRenderLimit=200,validityBusy=false,validityTimer=null;
function localIsoDate(d=new Date()){return`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
function plusDaysIso(days){const d=new Date();d.setHours(12,0,0,0);d.setDate(d.getDate()+Number(days||0));return localIsoDate(d)}
function validityStatus(days){days=Number(days);if(days<0)return{label:'VENCIDO',cls:'expired'};if(days===0)return{label:'VENCE HOJE',cls:'today'};if(days<=7)return{label:'URGENTE',cls:'urgent'};if(days<=15)return{label:'ATENÇÃO',cls:'attention'};if(days<=30)return{label:'PRÓXIMO',cls:'near'};return{label:'OK',cls:'ok'}}
function validityFilterLabel(){return({vencidos:'Vencidos',hoje:'Vencem hoje','7':'Vencem em até 7 dias','15':'Vencem em até 15 dias','30':'Vencem em até 30 dias',todos:'Todas as validades'})[validityFilter]||'Controle de validade'}
function applyValidityRange(q,filter){const today=localIsoDate();if(filter==='vencidos')return q.lt('validade',today);if(filter==='hoje')return q.eq('validade',today);if(['7','15','30'].includes(filter))return q.gt('validade',today).lte('validade',plusDaysIso(Number(filter)));return q}
function validityQuery(filter,search,from=0,to=999,withCount=false){let q=db.from('controle_validade_lista').select('*',withCount?{count:'exact'}:undefined).order('validade',{ascending:true}).order('nome',{ascending:true}).range(from,to);q=applyValidityRange(q,filter);const s=String(search||'').trim().replace(/[,()%\\'\"]/g,' ');if(s)q=q.or(`nome.ilike.%${s}%,codigo_interno.ilike.%${s}%,ean.ilike.%${s}%,endereco.ilike.%${s}%`);return q}
async function fetchValidityRows(filter,search){const first=await validityQuery(filter,search,0,999,true);if(first.error)throw first.error;const count=Number(first.count||0),rows=[...(first.data||[])];if(count>1000){const jobs=[];for(let from=1000;from<count;from+=1000)jobs.push(validityQuery(filter,search,from,Math.min(from+999,count-1),false));const rest=await Promise.all(jobs);for(const r of rest){if(r.error)throw r.error;rows.push(...(r.data||[]))}}return rows}
async function validityCount(filter){let q=db.from('controle_validade_lista').select('validade_id',{count:'exact',head:true});q=applyValidityRange(q,filter);const r=await q;if(r.error)throw r.error;return Number(r.count||0)}
async function loadValidityCounts(){try{const [a,b,c,d,e]=await Promise.all(['vencidos','hoje','7','15','30'].map(validityCount));$('countExpired').textContent=a;$('countToday').textContent=b;$('count7').textContent=c;$('count15').textContent=d;$('count30').textContent=e}catch(err){console.warn('Contagens de validade indisponíveis',err)}}
function renderValidityRows(){const rows=validityRows.slice(0,validityRenderLimit);if(!rows.length){$('validityList').innerHTML='<div class="empty">Nenhum produto encontrado neste filtro.</div>';$('validityMore').classList.add('hidden');return}$('validityList').innerHTML=rows.map(r=>{const st=validityStatus(r.dias_para_vencer),qty=r.quantidade==null?'Não informada':nf(r.quantidade),days=Number(r.dias_para_vencer),daysTxt=days<0?`${Math.abs(days)} dia(s) vencido`:days===0?'Vence hoje':`${days} dia(s)`;return`<article class="validity-card ${st.cls}"><div class="validity-card-top"><div><h3>${esc(r.nome||'Produto')}</h3><span>Cód. ${esc(r.codigo_interno||'—')} · EAN ${esc(r.ean||'—')}</span></div><b class="validity-badge">${st.label}</b></div><div class="validity-grid"><div><small>Validade</small><b>${brDate(r.validade)}</b></div><div><small>Dias</small><b>${daysTxt}</b></div><div><small>Quantidade</small><b>${qty}</b></div><div><small>Lote</small><b>${esc(r.lote||'—')}</b></div><div class="full"><small>Endereço</small><b>${esc(r.endereco||'Sem endereço')}</b></div></div></article>`}).join('');$('validityMore').classList.toggle('hidden',validityRows.length<=validityRenderLimit);$('validityMore').textContent=`Mostrar mais (${Math.min(200,validityRows.length-validityRenderLimit)} restantes)`}
async function loadValidityControl(){if(validityBusy)return;validityBusy=true;validityRenderLimit=200;$('validityList').innerHTML='<div class="empty">Carregando validades...</div>';$('validitySummary').textContent='Consultando produtos...';document.querySelectorAll('[data-validity-filter]').forEach(b=>b.classList.toggle('active',b.dataset.validityFilter===validityFilter));try{const search=$('validitySearch')?.value||'';const [rows]=await Promise.all([fetchValidityRows(validityFilter,search),loadValidityCounts()]);validityRows=rows;$('validitySummary').textContent=`${validityFilterLabel()} · ${rows.length.toLocaleString('pt-BR')} registro(s)`;renderValidityRows()}catch(e){console.error(e);$('validityList').innerHTML='<div class="empty">Erro ao carregar o controle de validade.</div>';$('validitySummary').textContent=e.message||'Falha na consulta.';toast('Erro ao carregar validades.','err')}finally{validityBusy=false}}
function validityReportHtml(){const title=validityFilterLabel(),emitted=new Date().toLocaleString('pt-BR'),known=validityRows.filter(r=>r.quantidade!=null).reduce((a,r)=>a+num(r.quantidade),0);const rows=validityRows.map((r,i)=>{const st=validityStatus(r.dias_para_vencer),days=Number(r.dias_para_vencer);return`<tr><td>${i+1}</td><td>${esc(r.nome||'')}</td><td>${esc(r.codigo_interno||'—')}</td><td>${esc(r.ean||'—')}</td><td>${esc(r.endereco||'Sem endereço')}</td><td>${esc(r.lote||'—')}</td><td>${brDate(r.validade)}</td><td>${r.quantidade==null?'—':nf(r.quantidade)}</td><td>${days}</td><td>${st.label}</td></tr>`}).join('');return`<!doctype html><html><head><meta charset="utf-8"><style>@page{size:A4 portrait;margin:10mm}*{box-sizing:border-box}body{font-family:Arial,sans-serif;color:#111;margin:0;font-size:8.5pt}h1{font-size:16pt;margin:0 0 3mm}h2{font-size:10pt;margin:0 0 2mm;font-weight:normal}.meta{display:flex;gap:8mm;margin:0 0 4mm;font-size:8pt}.meta b{font-size:9pt}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border:0.25mm solid #aaa;padding:1.4mm;vertical-align:top;word-wrap:break-word}th{background:#eee;font-size:7.5pt}td{font-size:7.2pt}th:nth-child(1),td:nth-child(1){width:5%}th:nth-child(2),td:nth-child(2){width:20%}th:nth-child(3),td:nth-child(3){width:8%}th:nth-child(4),td:nth-child(4){width:11%}th:nth-child(5),td:nth-child(5){width:16%}th:nth-child(6),td:nth-child(6){width:8%}th:nth-child(7),td:nth-child(7){width:9%}th:nth-child(8),td:nth-child(8){width:7%}th:nth-child(9),td:nth-child(9){width:6%}th:nth-child(10),td:nth-child(10){width:10%}thead{display:table-header-group}tr{page-break-inside:avoid}.footer{margin-top:4mm;font-size:7pt;color:#555}</style></head><body><h1>AWS Gestão de Estoque</h1><h2>Relatório de Controle de Validade · ${esc(title)}</h2><div class="meta"><span>Emitido: <b>${esc(emitted)}</b></span><span>Registros: <b>${validityRows.length}</b></span><span>Qtd. informada: <b>${nf(known)}</b></span></div><table><thead><tr><th>#</th><th>Produto</th><th>Cód.</th><th>EAN</th><th>Endereço</th><th>Lote</th><th>Validade</th><th>Qtd.</th><th>Dias</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table><div class="footer">Relatório gerado pelo AWS Gestão de Estoque.</div></body></html>`}
function printValidityPdf(){if(!validityRows.length)return toast('Não há produtos neste filtro para gerar o PDF.','err');if(!window.AndroidApp?.printValidityReport)return toast('Atualize o APK para gerar PDF A4.','err');const title=`Controle-de-Validade-${localIsoDate()}`;AndroidApp.printValidityReport(validityReportHtml(),title)}
document.querySelectorAll('[data-validity-filter]').forEach(b=>b.onclick=()=>{validityFilter=b.dataset.validityFilter;loadValidityControl()});
$('validitySearch').oninput=()=>{clearTimeout(validityTimer);validityTimer=setTimeout(loadValidityControl,300)};
$('validityPdf').onclick=printValidityPdf;
$('validityMore').onclick=()=>{validityRenderLimit+=200;renderValidityRows()};
'''

if '// ===== Controle de Validade =====' not in js:
    pos = js.find('// Conferência')
    if pos < 0:
        pos = js.find('window.androidBack=')
    if pos < 0:
        js += validity_js
    else:
        js = js[:pos] + validity_js + '\n' + js[pos:]

css_extra = r'''
/* Controle de validade */
.validity-toolbar{display:grid;gap:12px}.validity-filters{display:flex;gap:7px;overflow-x:auto;padding-bottom:2px}.validity-filter{white-space:nowrap;border:1px solid var(--line);background:#fff;border-radius:999px;padding:9px 11px;font-size:11px;font-weight:800}.validity-filter b{display:inline-flex;min-width:20px;justify-content:center;margin-left:4px;padding:2px 5px;border-radius:999px;background:#eef2ff}.validity-filter.active{border-color:#8b5cf6;background:#f5f3ff;color:#6d28d9}.validity-actions-row{display:grid;grid-template-columns:1fr;gap:8px}.validity-actions-row input{width:100%;border:1px solid var(--line);border-radius:13px;padding:13px;background:#fff}.validity-list{display:grid;gap:9px}.validity-card{background:#fff;border:1px solid var(--line);border-left:5px solid #94a3b8;border-radius:15px;padding:13px}.validity-card.expired{border-left-color:#dc2626}.validity-card.today{border-left-color:#ea580c}.validity-card.urgent{border-left-color:#f97316}.validity-card.attention{border-left-color:#eab308}.validity-card.near{border-left-color:#8b5cf6}.validity-card.ok{border-left-color:#16a34a}.validity-card-top{display:flex;justify-content:space-between;gap:9px;align-items:flex-start}.validity-card h3{font-size:13px;margin:0 0 3px}.validity-card-top span{display:block;font-size:10px;color:var(--muted)}.validity-badge{font-size:9px;border-radius:999px;padding:5px 7px;background:#f3f4f6;white-space:nowrap}.validity-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:11px}.validity-grid div{background:#f8fafc;border-radius:10px;padding:8px}.validity-grid .full{grid-column:1/-1}.validity-grid small{display:block;color:var(--muted);font-size:9px;margin-bottom:2px}.validity-grid b{font-size:11px}@media(min-width:760px){.validity-actions-row{grid-template-columns:1fr auto}.validity-list{grid-template-columns:repeat(2,minmax(0,1fr))}}
'''
if '/* Controle de validade */' not in css:
    css += '\n' + css_extra

# Ponte nativa para imprimir/salvar em PDF A4 usando o diálogo oficial do Android.
if 'import android.print.PrintAttributes' not in kt:
    kt = kt.replace('import android.provider.Settings\n', 'import android.provider.Settings\nimport android.print.PrintAttributes\nimport android.print.PrintManager\n', 1)
if 'private var reportPrintWebView' not in kt:
    kt = kt.replace('    private lateinit var webView: WebView\n', '    private lateinit var webView: WebView\n    private var reportPrintWebView: WebView? = null\n', 1)
if 'fun printValidityReport(' not in kt:
    native = r'''        @JavascriptInterface
        fun printValidityReport(html: String, title: String) {
            runOnUiThread {
                try {
                    reportPrintWebView?.destroy()
                    val report = WebView(this@LauncherActivity)
                    reportPrintWebView = report
                    report.settings.javaScriptEnabled = false
                    report.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val manager = getSystemService(PRINT_SERVICE) as PrintManager
                            val jobName = title.ifBlank { "Controle de Validade" }
                            val adapter = report.createPrintDocumentAdapter(jobName)
                            val attrs = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build()
                            manager.print(jobName, adapter, attrs)
                        }
                    }
                    report.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                } catch (t: Throwable) {
                    notifyUpdateError("Não foi possível abrir o relatório A4: ${t.message ?: "erro desconhecido"}")
                }
            }
        }

'''
    marker = '        @JavascriptInterface\n        fun exitApp()'
    if marker not in kt:
        raise SystemExit('Ponto de inserção do gerador PDF não encontrado no AndroidBridge.')
    kt = kt.replace(marker, native + marker, 1)
if 'reportPrintWebView?.destroy()' not in kt.split('override fun onDestroy()',1)[-1]:
    kt = kt.replace('        webView.destroy()\n', '        webView.destroy()\n        reportPrintWebView?.destroy()\n        reportPrintWebView = null\n', 1)

checks={
 'tela':'id="validade"' in html,
 'card':'data-view-open="validade"' in html,
 'consulta':'controle_validade_lista' in js,
 'filtros':'data-validity-filter="30"' in html,
 'pdf':'printValidityReport' in js and 'fun printValidityReport' in kt,
 'a4':'PrintAttributes.MediaSize.ISO_A4' in kt,
}
missing=[k for k,v in checks.items() if not v]
if missing: raise SystemExit('Controle de validade incompleto: '+', '.join(missing))

index_path.write_text(html,encoding='utf-8')
app_path.write_text(js,encoding='utf-8')
css_path.write_text(css,encoding='utf-8')
kotlin_path.write_text(kt,encoding='utf-8')
print('Controle de validade preparado: filtros, pesquisa, lista e PDF A4.')
