import re
import app as base


def _num(value):
    value = value.strip()
    if not value:
        return None
    # Formato brasileiro: 1.234,56 / 12,00. Também aceita inteiro e decimal com ponto.
    if "," in value:
        cleaned = value.replace(".", "").replace(",", ".")
    else:
        cleaned = value
    try:
        return float(cleaned)
    except ValueError:
        return None


def parse_stock_pdf_flexible(text: str):
    """Parser tolerante a relatórios de estoque com colunas e quebras diferentes."""
    products = {}

    # Código interno no início da linha. Aceita códigos maiores que o parser antigo.
    code_re = re.compile(r"^\s*(\d{1,10})\b")
    # Números avulsos: estoque inteiro/decimal, negativos e EANs.
    number_re = re.compile(r"(?<![A-Za-z])(-?\d+(?:[.,]\d+)?)(?![A-Za-z])")
    pure_number_re = re.compile(r"^\s*(-?\d+(?:[.,]\d+)?)\s*$")
    ean_re = re.compile(r"(?<!\d)(\d{8,14})(?!\d)")

    pending = None

    def header_or_footer(line: str) -> bool:
        lower = line.lower().strip()
        if not lower:
            return True
        blocked = (
            "grupo :", "quantidade de itens", "total do produto", "d.a.m ",
            "produto:", "relatório", "relatorio", "página", "pagina",
        )
        if any(lower.startswith(x) for x in blocked):
            return True
        if ("código" in lower or "codigo" in lower) and any(x in lower for x in ("estoque", "descrição", "descricao", "ean", "barras")):
            return True
        return False

    def choose_ean(line: str, code_span=None):
        matches = list(ean_re.finditer(line))
        if code_span:
            matches = [m for m in matches if not (m.start() == code_span[0] and m.end() == code_span[1])]
        if not matches:
            return None, None
        # Prioriza EAN-13; depois o maior identificador disponível.
        matches.sort(key=lambda m: (len(m.group(1)) == 13, len(m.group(1)), m.start()), reverse=True)
        m = matches[0]
        return m.group(1), (m.start(), m.end())

    def choose_qty(line: str, code_span=None, ean_span=None):
        candidates = []
        for m in number_re.finditer(line):
            span = (m.start(1), m.end(1))
            if code_span and span == code_span:
                continue
            if ean_span and span == ean_span:
                continue
            token = m.group(1)
            # Datas não devem virar estoque.
            around = line[max(0, span[0]-1):min(len(line), span[1]+1)]
            if "/" in around:
                continue
            n = _num(token)
            if n is None:
                continue
            candidates.append((m, n))
        if not candidates:
            return None, None
        # Em relatórios de estoque a quantidade costuma ser o último número que não é EAN.
        m, n = candidates[-1]
        return n, (m.start(1), m.end(1))

    def clean_desc(line: str, spans):
        chars = list(line)
        for start, end in sorted([s for s in spans if s], reverse=True):
            for i in range(start, min(end, len(chars))):
                chars[i] = " "
        desc = "".join(chars)
        desc = re.sub(r"\s+", " ", desc).strip(" -|;:\t")
        return desc

    def save(code, ean, desc, qty):
        if not code:
            return
        desc = re.sub(r"\s+", " ", (desc or "")).strip()
        # Evita lixo de rodapé sem descrição real.
        if not desc or not re.search(r"[A-Za-zÀ-ÿ]", desc):
            return
        products[code] = (code, ean, desc, float(qty or 0.0))

    def flush_pending(default_qty=0.0):
        nonlocal pending
        if pending:
            save(pending["code"], pending.get("ean"), pending.get("desc", ""), pending.get("qty", default_qty))
            pending = None

    for raw in text.splitlines():
        line = re.sub(r"\s+", " ", raw).strip()
        if header_or_footer(line):
            continue

        code_m = code_re.match(line)
        if code_m:
            # Uma nova linha de produto fecha o produto anterior, mesmo que o estoque estivesse em outra formatação.
            flush_pending(0.0)
            code = code_m.group(1)
            code_span = (code_m.start(1), code_m.end(1))
            ean, ean_span = choose_ean(line, code_span)
            qty, qty_span = choose_qty(line, code_span, ean_span)
            desc = clean_desc(line, [code_span, ean_span, qty_span])

            if qty is not None and desc:
                save(code, ean, desc, qty)
            else:
                pending = {"code": code, "ean": ean, "desc": desc, "qty": qty}
            continue

        if pending:
            # Caso clássico: quantidade vem sozinha na linha seguinte.
            only = pure_number_re.match(line)
            if only:
                q = _num(only.group(1))
                if q is not None:
                    pending["qty"] = q
                    flush_pending(q)
                    continue

            # Continuação pode trazer EAN, estoque, descrição ou os três.
            ean, ean_span = choose_ean(line)
            qty, qty_span = choose_qty(line, None, ean_span)
            if ean and not pending.get("ean"):
                pending["ean"] = ean
            extra = clean_desc(line, [ean_span, qty_span])
            if extra:
                pending["desc"] = (pending.get("desc", "") + " " + extra).strip()
            if qty is not None:
                pending["qty"] = qty
                flush_pending(qty)

    flush_pending(0.0)

    # Protege o banco contra EAN duplicado acidentalmente extraído de duas linhas.
    seen_ean = {}
    result = []
    for code, ean, desc, stock in products.values():
        if ean:
            owner = seen_ean.get(ean)
            if owner and owner != code:
                ean = None
            else:
                seen_ean[ean] = code
        result.append((code, ean, desc, stock))
    return result


# Substitui somente o parser de estoque; toda a interface e o banco continuam os mesmos.
base.parse_stock_pdf = parse_stock_pdf_flexible


if __name__ == "__main__":
    base.App().mainloop()
