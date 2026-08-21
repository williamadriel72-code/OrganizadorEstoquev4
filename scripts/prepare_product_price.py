from pathlib import Path

path = Path('app/src/main/assets/app.js')
js = path.read_text(encoding='utf-8')

# Invalida o cache antigo, que foi criado sem a coluna preco.
js = js.replace("const PRODUCT_INDEX_KEY='aws_product_index_v2';", "const PRODUCT_INDEX_KEY='aws_product_index_v3';")

# Todas as consultas usadas por busca, bipagem e cache local passam a carregar preco.
js = js.replace(
    ".select('id,nome,codigo_interno,codigo_barras,categoria')",
    ".select('id,nome,codigo_interno,codigo_barras,categoria,preco')"
)

# Formatador monetario brasileiro.
if 'function brMoney(' not in js:
    marker = "function brDate(v){if(!v)return'—';const[y,m,d]=String(v).split('-');return`${d}/${m}/${y}`}\n"
    money = "function brMoney(v){if(v===null||v===undefined||v==='')return'—';const n=Number(v);if(!Number.isFinite(n))return'—';return n.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}\n"
    if marker not in js:
        raise SystemExit('Nao foi possivel localizar brDate para inserir brMoney.')
    js = js.replace(marker, marker + money, 1)

# Exibe o preco no card usado tanto na pesquisa quanto no scanner.
old = "<div><span>EAN:</span> <b>${esc(p.codigo_barras||'—')}</b></div><div><span>Estoque:</span> <b class=\"stock\">${qty}</b></div>"
new = "<div><span>EAN:</span> <b>${esc(p.codigo_barras||'—')}</b></div><div><span>Preço:</span> <b class=\"price\">${brMoney(p.preco)}</b></div><div><span>Estoque:</span> <b class=\"stock\">${qty}</b></div>"
if old not in js:
    raise SystemExit('Nao foi possivel localizar o card do produto para inserir o preco.')
js = js.replace(old, new)

if "categoria,preco" not in js or 'Preço:' not in js or 'brMoney(p.preco)' not in js:
    raise SystemExit('Validacao da exibicao de preco falhou.')

path.write_text(js, encoding='utf-8')
print('Preco preparado: consultas, cache, scanner e card exibem valor em BRL.')
