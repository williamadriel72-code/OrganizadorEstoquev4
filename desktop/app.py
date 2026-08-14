import os
import re
import sqlite3
import sys
from datetime import datetime, timedelta
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

from pypdf import PdfReader

APP_NAME = "OrganizadorEstoquePC"
BG = "#061321"
PANEL = "#10243A"
PANEL2 = "#0D1E31"
BLUE = "#1677FF"
TEXT = "#F5F7FA"
MUTED = "#9FB0C4"
GREEN = "#20C983"
YELLOW = "#FFB938"
RED = "#FF5368"
ORANGE = "#FF7A59"
PURPLE = "#8D63F6"


def app_data_dir() -> Path:
    base = os.environ.get("APPDATA") or str(Path.home())
    p = Path(base) / APP_NAME
    p.mkdir(parents=True, exist_ok=True)
    return p


DB_PATH = app_data_dir() / "estoque.db"


def parse_number(value: str):
    try:
        return float(value.strip().replace(".", "").replace(",", ".")) if "," in value else float(value.strip())
    except Exception:
        return None


def format_qty(v):
    try:
        n = float(v)
        if abs(n - round(n)) < 1e-9:
            return f"{int(round(n)):,}".replace(",", ".")
        return f"{n:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")
    except Exception:
        return str(v)


def normalize_date(value: str):
    value = value.strip()
    for fmt in ("%d/%m/%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(value, fmt).strftime("%Y-%m-%d")
        except ValueError:
            pass
    return None


def date_br(value: str):
    try:
        return datetime.strptime(value, "%Y-%m-%d").strftime("%d/%m/%Y")
    except Exception:
        return value


def read_pdf_text(path):
    reader = PdfReader(path)
    return "\n".join((page.extract_text() or "") for page in reader.pages)


def parse_stock_pdf(text: str):
    start_with_ean = re.compile(r"(\d{1,6})\s+(\d{8,14})\s+(.+)")
    qty_at_end = re.compile(r"(-?\d+[.,]\d{2,3})$")
    qty_only = re.compile(r"^-?\d+[.,]\d{2,3}$")
    ean_at_end = re.compile(r"(\d{8,14})$")

    products = {}
    code = None
    ean = None
    description = []

    def save(quantity):
        nonlocal code, ean, description
        if not code:
            return
        desc = " ".join(description).strip()
        if desc:
            products[code] = (code, ean, desc, quantity)
        code = None
        ean = None
        description = []

    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        lower = line.lower()
        if lower.startswith("grupo :") or lower.startswith("quantidade de itens") or lower.startswith("total do produto"):
            continue
        if (("código" in lower or "codigo" in lower) and ("estoque" in lower or "descrição" in lower or "descricao" in lower)):
            continue
        if lower.startswith("d.a.m ") or lower.startswith("produto:"):
            continue

        if code is not None and qty_only.match(line):
            save(parse_number(line) or 0.0)
            continue

        found = start_with_ean.search(line)
        if found:
            code, ean = found.group(1), found.group(2)
            description = []
            rest = found.group(3).strip()
            qty = qty_at_end.search(rest)
            if qty:
                description.append(rest[:qty.start()].strip())
                save(parse_number(qty.group(1)) or 0.0)
            else:
                description.append(rest)
            continue

        if code is not None:
            qty = qty_at_end.search(line)
            if qty:
                before = line[:qty.start()].strip()
                if before:
                    description.append(before)
                save(parse_number(qty.group(1)) or 0.0)
            else:
                description.append(line)
            continue

        first = line.split(" ", 1)[0]
        if first.isdigit() and 1 <= len(first) <= 6:
            possible_ean = ean_at_end.search(line)
            possible_qty = next((t for t in line.split() if "," in t and parse_number(t) is not None), None)
            if possible_qty:
                after = line[len(first):].strip()
                desc = after.replace(possible_qty, "").strip()
                pean = possible_ean.group(1) if possible_ean else None
                if pean and desc.endswith(pean):
                    desc = desc[:-len(pean)].strip()
                products[first] = (first, pean, desc, parse_number(possible_qty) or 0.0)

    return list(products.values())


def parse_expiry_pdf(text: str):
    product_start = re.compile(r"(\d{1,6})\s+(\d{8,14})\s*")
    date_pattern = re.compile(r"\d{2}/\d{2}/\d{4}|\d{4}-\d{2}-\d{2}")
    qty_pattern = re.compile(r"-?\d+[.,]\d{2,3}")
    rows = []
    current_ref = None

    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        lower = line.lower()
        if (("código" in lower or "codigo" in lower) and "validade" in lower):
            continue
        if lower.startswith("grupo :") or lower.startswith("quantidade de itens agrupados") or lower.startswith("d.a.m "):
            continue

        product = product_start.search(line)
        if product:
            # Preferir EAN: é a referência lida pelo bip.
            current_ref = product.group(2) or product.group(1)

        date_match = date_pattern.search(line)
        if not date_match:
            continue
        if lower.startswith("data validade") and current_ref is None:
            continue

        before = line[:date_match.start()]
        quantities = list(qty_pattern.finditer(before))
        qty = parse_number(quantities[-1].group(0)) if quantities else 0.0
        normalized = normalize_date(date_match.group(0))
        ref = (product.group(2) if product else None) or current_ref
        if ref and normalized:
            rows.append((ref.strip(), normalized, max(qty or 0.0, 0.0)))
        # Não limpar current_ref: o mesmo produto pode ter várias datas em linhas seguintes.

    return rows


class Database:
    def __init__(self, path=DB_PATH):
        self.path = str(path)
        self.conn = sqlite3.connect(self.path)
        self.conn.row_factory = sqlite3.Row
        self.init_schema()

    def init_schema(self):
        c = self.conn.cursor()
        c.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS products(
            code TEXT PRIMARY KEY,
            ean TEXT UNIQUE,
            description TEXT NOT NULL,
            group_code TEXT,
            category TEXT,
            stock REAL NOT NULL DEFAULT 0,
            active INTEGER NOT NULL DEFAULT 1,
            updated_at INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS expiry_batches(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_code TEXT NOT NULL,
            expiry_date TEXT NOT NULL,
            quantity REAL NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            UNIQUE(product_code, expiry_date),
            FOREIGN KEY(product_code) REFERENCES products(code)
        );
        CREATE INDEX IF NOT EXISTS idx_products_ean ON products(ean);
        CREATE INDEX IF NOT EXISTS idx_products_description ON products(description);
        CREATE INDEX IF NOT EXISTS idx_expiry_product ON expiry_batches(product_code, expiry_date);
        CREATE TABLE IF NOT EXISTS stock_movements(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_code TEXT NOT NULL,
            movement_type TEXT NOT NULL,
            quantity REAL NOT NULL,
            before_stock REAL NOT NULL,
            after_stock REAL NOT NULL,
            reason TEXT,
            created_at INTEGER NOT NULL
        );
        """)
        self.conn.commit()

    def search_products(self, query="", filter_name="all", limit=500):
        q = query.strip()
        where = ["active=1"]
        args = []
        if filter_name == "low":
            where.append("stock > 0 AND stock <= 5")
        elif filter_name == "zero":
            where.append("stock = 0")
        elif filter_name == "negative":
            where.append("stock < 0")
        if q:
            where.append("(code=? OR ean=? OR description LIKE ?)")
            args += [q, q, f"%{q}%"]
        sql = "SELECT * FROM products WHERE " + " AND ".join(where) + " ORDER BY description LIMIT ?"
        args.append(limit)
        return self.conn.execute(sql, args).fetchall()

    def find_exact(self, ref):
        ref = ref.strip()
        return self.conn.execute("SELECT * FROM products WHERE active=1 AND (code=? OR ean=?) LIMIT 1", (ref, ref)).fetchone()

    def expiries(self, code):
        return self.conn.execute("SELECT * FROM expiry_batches WHERE product_code=? ORDER BY expiry_date", (code,)).fetchall()

    def all_expiries(self):
        return self.conn.execute("""
            SELECT e.id,e.product_code,e.expiry_date,e.quantity,p.description,p.ean,p.stock
            FROM expiry_batches e JOIN products p ON p.code=e.product_code
            ORDER BY e.expiry_date,p.description
        """).fetchall()

    def dashboard(self):
        today = datetime.now().strftime("%Y-%m-%d")
        d7 = (datetime.now()+timedelta(days=7)).strftime("%Y-%m-%d")
        d30 = (datetime.now()+timedelta(days=30)).strftime("%Y-%m-%d")
        d60 = (datetime.now()+timedelta(days=60)).strftime("%Y-%m-%d")
        base = self.conn.execute("""
            SELECT COUNT(*) products,
                   SUM(CASE WHEN stock>0 AND stock<=5 THEN 1 ELSE 0 END) low_stock,
                   SUM(CASE WHEN stock=0 THEN 1 ELSE 0 END) zero_stock,
                   SUM(CASE WHEN stock<0 THEN 1 ELSE 0 END) negative_stock
            FROM products WHERE active=1
        """).fetchone()
        def cnt(sql, args):
            return self.conn.execute(sql, args).fetchone()[0]
        return {
            "products": base["products"] or 0,
            "low": base["low_stock"] or 0,
            "zero": base["zero_stock"] or 0,
            "negative": base["negative_stock"] or 0,
            "expired": cnt("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE expiry_date < ?", (today,)),
            "d7": cnt("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE expiry_date BETWEEN ? AND ?", (today, d7)),
            "d30": cnt("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE expiry_date BETWEEN ? AND ?", (today, d30)),
            "d60": cnt("SELECT COUNT(DISTINCT product_code) FROM expiry_batches WHERE expiry_date BETWEEN ? AND ?", (today, d60)),
        }

    def import_stock(self, rows):
        now = int(datetime.now().timestamp()*1000)
        with self.conn:
            for code, ean, desc, stock in rows:
                existing = self.conn.execute("SELECT group_code,category FROM products WHERE code=?", (code,)).fetchone()
                group_code = existing[0] if existing else None
                category = existing[1] if existing else None
                self.conn.execute("""
                    INSERT INTO products(code,ean,description,group_code,category,stock,active,updated_at)
                    VALUES(?,?,?,?,?,?,1,?)
                    ON CONFLICT(code) DO UPDATE SET
                        ean=excluded.ean,description=excluded.description,stock=excluded.stock,active=1,updated_at=excluded.updated_at
                """, (code, ean, desc, group_code, category, stock, now))
        return len(rows)

    def import_expiries(self, rows):
        refs = {}
        for p in self.conn.execute("SELECT code,ean FROM products WHERE active=1"):
            refs[p["code"]] = p["code"]
            refs[p["code"].lstrip("0") or "0"] = p["code"]
            if p["ean"]:
                refs[p["ean"]] = p["code"]
                refs[p["ean"].lstrip("0") or "0"] = p["code"]

        consolidated = {}
        skipped = 0
        for ref, date, qty in rows:
            keyref = ref.strip()
            code = refs.get(keyref) or refs.get(keyref.lstrip("0") or "0")
            if not code:
                skipped += 1
                continue
            key = (code, date)
            consolidated[key] = consolidated.get(key, 0.0) + qty

        if not consolidated:
            raise ValueError("Nenhuma validade foi vinculada aos produtos. Importe primeiro o PDF de estoque e confira código/EAN.")

        now = int(datetime.now().timestamp()*1000)
        with self.conn:
            self.conn.execute("DELETE FROM expiry_batches")
            for (code, date), qty in consolidated.items():
                self.conn.execute("INSERT INTO expiry_batches(product_code,expiry_date,quantity,updated_at) VALUES(?,?,?,?)", (code,date,qty,now))
        return len(consolidated), skipped

    def stock_in(self, ref, qty, expiry=None):
        p = self.find_exact(ref)
        if not p:
            raise ValueError("Produto não encontrado")
        before = float(p["stock"])
        after = before + qty
        now = int(datetime.now().timestamp()*1000)
        with self.conn:
            self.conn.execute("UPDATE products SET stock=?,updated_at=? WHERE code=?", (after,now,p["code"]))
            if expiry:
                exp = normalize_date(expiry)
                if not exp:
                    raise ValueError("Validade inválida. Use DD/MM/AAAA ou AAAA-MM-DD")
                self.conn.execute("""
                    INSERT INTO expiry_batches(product_code,expiry_date,quantity,updated_at) VALUES(?,?,?,?)
                    ON CONFLICT(product_code,expiry_date) DO UPDATE SET quantity=quantity+excluded.quantity,updated_at=excluded.updated_at
                """, (p["code"],exp,qty,now))
            self.conn.execute("INSERT INTO stock_movements(product_code,movement_type,quantity,before_stock,after_stock,reason,created_at) VALUES(?,?,?,?,?,?,?)",
                              (p["code"],"IN",qty,before,after,"Entrada manual",now))
        return self.find_exact(p["code"])

    def stock_out(self, ref, qty):
        p = self.find_exact(ref)
        if not p:
            raise ValueError("Produto não encontrado")
        before = float(p["stock"])
        if before < qty:
            raise ValueError("Estoque insuficiente")
        now = int(datetime.now().timestamp()*1000)
        remaining = qty
        with self.conn:
            batches = self.conn.execute("SELECT id,quantity FROM expiry_batches WHERE product_code=? AND quantity>0 ORDER BY expiry_date", (p["code"],)).fetchall()
            for b in batches:
                if remaining <= 0:
                    break
                used = min(float(b["quantity"]), remaining)
                self.conn.execute("UPDATE expiry_batches SET quantity=quantity-?,updated_at=? WHERE id=?", (used,now,b["id"]))
                remaining -= used
            after = before - qty
            self.conn.execute("UPDATE products SET stock=?,updated_at=? WHERE code=?", (after,now,p["code"]))
            self.conn.execute("INSERT INTO stock_movements(product_code,movement_type,quantity,before_stock,after_stock,reason,created_at) VALUES(?,?,?,?,?,?,?)",
                              (p["code"],"OUT",qty,before,after,"Saída FEFO/manual",now))
        return self.find_exact(p["code"])


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Organizador Geral de Estoque - PC")
        self.geometry("1280x760")
        self.minsize(980, 620)
        self.configure(bg=BG)
        self.db = Database()
        self.current = None
        self.style_setup()
        self.build_shell()
        self.show_dashboard()

    def style_setup(self):
        s = ttk.Style(self)
        try:
            s.theme_use("clam")
        except Exception:
            pass
        s.configure("TFrame", background=BG)
        s.configure("Panel.TFrame", background=PANEL)
        s.configure("TLabel", background=BG, foreground=TEXT, font=("Segoe UI", 10))
        s.configure("Muted.TLabel", background=BG, foreground=MUTED, font=("Segoe UI", 10))
        s.configure("Title.TLabel", background=BG, foreground=TEXT, font=("Segoe UI Semibold", 22))
        s.configure("CardTitle.TLabel", background=PANEL, foreground=MUTED, font=("Segoe UI", 10))
        s.configure("CardValue.TLabel", background=PANEL, foreground=TEXT, font=("Segoe UI Semibold", 24))
        s.configure("TButton", font=("Segoe UI Semibold", 10), padding=(12,9), background=BLUE, foreground="white")
        s.map("TButton", background=[("active", "#2A86FF")])
        s.configure("Side.TButton", font=("Segoe UI Semibold", 11), padding=(14,12), anchor="w", background=PANEL2, foreground=TEXT)
        s.configure("TEntry", fieldbackground="#0B1A2B", foreground=TEXT, insertcolor=TEXT, padding=8)
        s.configure("Treeview", background=PANEL, fieldbackground=PANEL, foreground=TEXT, rowheight=32, borderwidth=0, font=("Segoe UI", 10))
        s.configure("Treeview.Heading", background=PANEL2, foreground=TEXT, font=("Segoe UI Semibold", 10), relief="flat")
        s.map("Treeview", background=[("selected", BLUE)])

    def build_shell(self):
        self.sidebar = tk.Frame(self, bg=PANEL2, width=210)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)
        tk.Label(self.sidebar, text="ORGANIZADOR\nDE ESTOQUE", bg=PANEL2, fg=TEXT, font=("Segoe UI Semibold", 16), justify="left").pack(anchor="w", padx=18, pady=(22,20))
        for text, cmd in [
            ("Início", self.show_dashboard),
            ("Produtos", self.show_products),
            ("Validades", self.show_expiries),
            ("Entrada", lambda: self.show_movement(True)),
            ("Saída", lambda: self.show_movement(False)),
        ]:
            ttk.Button(self.sidebar, text=text, style="Side.TButton", command=cmd).pack(fill="x", padx=12, pady=4)
        tk.Label(self.sidebar, text="Windows • banco local", bg=PANEL2, fg=MUTED, font=("Segoe UI", 9)).pack(side="bottom", anchor="w", padx=18, pady=18)

        self.content = tk.Frame(self, bg=BG)
        self.content.pack(side="left", fill="both", expand=True)

    def clear(self):
        for w in self.content.winfo_children():
            w.destroy()

    def header(self, title, subtitle=None):
        top = tk.Frame(self.content, bg=BG)
        top.pack(fill="x", padx=24, pady=(22,14))
        tk.Label(top, text=title, bg=BG, fg=TEXT, font=("Segoe UI Semibold", 23)).pack(anchor="w")
        if subtitle:
            tk.Label(top, text=subtitle, bg=BG, fg=MUTED, font=("Segoe UI", 10)).pack(anchor="w", pady=(3,0))

    def card(self, parent, title, value, color=TEXT):
        f = tk.Frame(parent, bg=PANEL, padx=16, pady=14)
        tk.Label(f, text=title, bg=PANEL, fg=MUTED, font=("Segoe UI", 10)).pack(anchor="w")
        tk.Label(f, text=str(value), bg=PANEL, fg=color, font=("Segoe UI Semibold", 24)).pack(anchor="w", pady=(10,0))
        return f

    def show_dashboard(self):
        self.clear()
        self.header("Organizador de Estoque", "Versão Windows • uso com mouse, teclado e leitor de código de barras")
        s = self.db.dashboard()
        grid = tk.Frame(self.content, bg=BG)
        grid.pack(fill="x", padx=24)
        for i in range(4):
            grid.columnconfigure(i, weight=1)
        cards = [
            ("Produtos", s["products"], BLUE), ("Estoque baixo", s["low"], YELLOW),
            ("Zerados", s["zero"], RED), ("Negativos", s["negative"], ORANGE)
        ]
        for i,(t,v,c) in enumerate(cards):
            self.card(grid,t,v,c).grid(row=0,column=i,sticky="nsew",padx=(0 if i==0 else 6,0 if i==3 else 6))

        lower = tk.Frame(self.content, bg=BG)
        lower.pack(fill="both", expand=True, padx=24, pady=18)
        lower.columnconfigure(0, weight=1)
        lower.columnconfigure(1, weight=1)

        exp = tk.Frame(lower, bg=PANEL, padx=18, pady=16)
        exp.grid(row=0,column=0,sticky="nsew",padx=(0,8))
        tk.Label(exp,text="Validades",bg=PANEL,fg=TEXT,font=("Segoe UI Semibold",16)).pack(anchor="w")
        for t,v,c in [("Vencidos",s["expired"],RED),("Vencem em 7 dias",s["d7"],YELLOW),("Vencem em 30 dias",s["d30"],BLUE),("Vencem em 60 dias",s["d60"],PURPLE)]:
            row=tk.Frame(exp,bg=PANEL); row.pack(fill="x",pady=5)
            tk.Label(row,text=t,bg=PANEL,fg=MUTED,font=("Segoe UI",10)).pack(side="left")
            tk.Label(row,text=str(v),bg=PANEL,fg=c,font=("Segoe UI Semibold",12)).pack(side="right")
        ttk.Button(exp,text="ABRIR VALIDADES",command=self.show_expiries).pack(anchor="w",pady=(14,0))

        imp = tk.Frame(lower,bg=PANEL,padx=18,pady=16)
        imp.grid(row=0,column=1,sticky="nsew",padx=(8,0))
        tk.Label(imp,text="Importar dados",bg=PANEL,fg=TEXT,font=("Segoe UI Semibold",16)).pack(anchor="w")
        tk.Label(imp,text="Importe primeiro o PDF de estoque e depois o PDF de validades.",bg=PANEL,fg=MUTED,font=("Segoe UI",10),wraplength=420,justify="left").pack(anchor="w",pady=(4,14))
        ttk.Button(imp,text="IMPORTAR PDF DE ESTOQUE",command=self.import_stock_pdf).pack(fill="x",pady=5)
        ttk.Button(imp,text="IMPORTAR PDF DE VALIDADES",command=self.import_expiry_pdf).pack(fill="x",pady=5)
        ttk.Button(imp,text="PESQUISAR PRODUTO",command=self.show_products).pack(fill="x",pady=(18,5))

    def import_stock_pdf(self):
        path = filedialog.askopenfilename(title="Selecionar PDF de estoque", filetypes=[("PDF", "*.pdf")])
        if not path:
            return
        try:
            self.config(cursor="watch"); self.update_idletasks()
            rows = parse_stock_pdf(read_pdf_text(path))
            if not rows:
                raise ValueError("PDF de estoque sem produtos reconhecidos")
            count = self.db.import_stock(rows)
            messagebox.showinfo("Importação concluída", f"{count} produto(s) importado(s).")
            self.show_dashboard()
        except Exception as e:
            messagebox.showerror("Falha na importação", str(e))
        finally:
            self.config(cursor="")

    def import_expiry_pdf(self):
        path = filedialog.askopenfilename(title="Selecionar PDF de validades", filetypes=[("PDF", "*.pdf")])
        if not path:
            return
        try:
            self.config(cursor="watch"); self.update_idletasks()
            rows = parse_expiry_pdf(read_pdf_text(path))
            if not rows:
                raise ValueError("PDF de validades sem linhas reconhecidas")
            imported, skipped = self.db.import_expiries(rows)
            messagebox.showinfo("Importação concluída", f"{imported} validade(s) importada(s).\n{skipped} linha(s) ignorada(s).")
            self.show_dashboard()
        except Exception as e:
            messagebox.showerror("Falha na importação", str(e))
        finally:
            self.config(cursor="")

    def show_products(self):
        self.clear()
        self.header("Produtos", "Pesquise ou bipe com leitor USB. Pressione Enter após o código.")
        controls = tk.Frame(self.content,bg=BG)
        controls.pack(fill="x",padx=24,pady=(0,10))
        self.product_query = tk.StringVar()
        ent = ttk.Entry(controls,textvariable=self.product_query,font=("Segoe UI",12))
        ent.pack(side="left",fill="x",expand=True)
        ent.bind("<Return>", lambda e:self.scan_product())
        ttk.Button(controls,text="LOCALIZAR / BIPAR",command=self.scan_product).pack(side="left",padx=(10,0))
        ttk.Button(controls,text="LIMPAR",command=lambda:(self.product_query.set(""),self.load_product_table())).pack(side="left",padx=(8,0))
        ent.focus_set()

        filterbar=tk.Frame(self.content,bg=BG); filterbar.pack(fill="x",padx=24,pady=(0,10))
        self.filter_var=tk.StringVar(value="all")
        for txt,val in [("Todos","all"),("Baixos","low"),("Zerados","zero"),("Negativos","negative")]:
            tk.Radiobutton(filterbar,text=txt,value=val,variable=self.filter_var,command=self.load_product_table,bg=BG,fg=TEXT,selectcolor=PANEL2,activebackground=BG,activeforeground=TEXT,font=("Segoe UI",10)).pack(side="left",padx=(0,12))

        self.scan_info=tk.Label(self.content,text="",bg=BG,fg=YELLOW,font=("Segoe UI Semibold",11),justify="left",anchor="w")
        self.scan_info.pack(fill="x",padx=24,pady=(0,8))

        cols=("description","code","ean","group","expiry","stock")
        self.product_tree=ttk.Treeview(self.content,columns=cols,show="headings")
        for c,t,w in [("description","Descrição",360),("code","Código",90),("ean","EAN",150),("group","Grupo",90),("expiry","Validade(s)",260),("stock","Estoque",100)]:
            self.product_tree.heading(c,text=t); self.product_tree.column(c,width=w,anchor="w")
        self.product_tree.pack(fill="both",expand=True,padx=24,pady=(0,22))
        self.product_query.trace_add("write", lambda *_: self.load_product_table())
        self.load_product_table()

    def load_product_table(self, scanned_code=None):
        if not hasattr(self,"product_tree"):
            return
        for i in self.product_tree.get_children(): self.product_tree.delete(i)
        q=self.product_query.get() if hasattr(self,"product_query") else ""
        f=self.filter_var.get() if hasattr(self,"filter_var") else "all"
        rows=self.db.search_products(q,f,500)
        for p in rows:
            exps=self.db.expiries(p["code"])
            exp_text=" | ".join(f"{date_br(x['expiry_date'])} ({format_qty(x['quantity'])})" for x in exps) or "Sem validade"
            self.product_tree.insert("", "end", iid=p["code"], values=(p["description"],p["code"],p["ean"] or "-",p["group_code"] or "-",exp_text,format_qty(p["stock"])))
        if scanned_code and self.product_tree.exists(scanned_code):
            self.product_tree.selection_set(scanned_code); self.product_tree.focus(scanned_code); self.product_tree.see(scanned_code)

    def scan_product(self):
        ref=self.product_query.get().strip()
        if not ref:
            return
        p=self.db.find_exact(ref)
        if not p:
            self.scan_info.config(text="Produto não encontrado",fg=RED)
            self.load_product_table()
            return
        exps=self.db.expiries(p["code"])
        if exps:
            dates=" • ".join(f"{date_br(x['expiry_date'])} (Qtd. {format_qty(x['quantity'])})" for x in exps)
            self.scan_info.config(text=f"{p['description']}  |  Validade: {dates}",fg=YELLOW)
        else:
            self.scan_info.config(text=f"{p['description']}  |  Validade: Sem validade cadastrada",fg=YELLOW)
        self.load_product_table(p["code"])

    def show_expiries(self):
        self.clear(); self.header("Validades","Produtos e lotes ordenados da validade mais próxima para a mais distante (FEFO)")
        cols=("description","code","ean","expiry","qty","stock")
        tree=ttk.Treeview(self.content,columns=cols,show="headings")
        for c,t,w in [("description","Descrição",400),("code","Código",90),("ean","EAN",150),("expiry","Validade",120),("qty","Qtd. validade",120),("stock","Estoque",100)]:
            tree.heading(c,text=t); tree.column(c,width=w,anchor="w")
        for x in self.db.all_expiries():
            tree.insert("","end",values=(x["description"],x["product_code"],x["ean"] or "-",date_br(x["expiry_date"]),format_qty(x["quantity"]),format_qty(x["stock"])))
        tree.pack(fill="both",expand=True,padx=24,pady=(0,22))

    def show_movement(self, entry):
        self.clear(); self.header("Entrada de estoque" if entry else "Saída de estoque", "Localize pelo código ou EAN e confirme a movimentação")
        wrap=tk.Frame(self.content,bg=BG); wrap.pack(fill="both",expand=True,padx=24,pady=(0,22)); wrap.columnconfigure(0,weight=1); wrap.columnconfigure(1,weight=1)
        left=tk.Frame(wrap,bg=PANEL,padx=18,pady=18); left.grid(row=0,column=0,sticky="nsew",padx=(0,8))
        right=tk.Frame(wrap,bg=PANEL,padx=18,pady=18); right.grid(row=0,column=1,sticky="nsew",padx=(8,0))
        tk.Label(left,text="Localizar produto",bg=PANEL,fg=TEXT,font=("Segoe UI Semibold",16)).pack(anchor="w")
        ref=tk.StringVar(); e=ttk.Entry(left,textvariable=ref,font=("Segoe UI",12)); e.pack(fill="x",pady=(14,8)); e.focus_set()
        info=tk.Label(left,text="Bipe ou digite o código/EAN",bg=PANEL,fg=MUTED,font=("Segoe UI",10),justify="left",anchor="w",wraplength=420); info.pack(fill="x",pady=(8,0))
        found={"p":None}
        def locate(*_):
            p=self.db.find_exact(ref.get()); found["p"]=p
            if not p: info.config(text="Produto não encontrado",fg=RED); return
            exps=self.db.expiries(p["code"])
            exptext="\n".join(f"Validade {date_br(x['expiry_date'])} • Qtd. {format_qty(x['quantity'])}" for x in exps) or "Sem validade cadastrada"
            info.config(text=f"{p['description']}\nCódigo {p['code']} • EAN {p['ean'] or '-'}\nEstoque atual: {format_qty(p['stock'])}\n{exptext}",fg=TEXT)
        e.bind("<Return>",locate); ttk.Button(left,text="LOCALIZAR PRODUTO",command=locate).pack(fill="x",pady=8)

        tk.Label(right,text="Confirmar movimentação",bg=PANEL,fg=TEXT,font=("Segoe UI Semibold",16)).pack(anchor="w")
        tk.Label(right,text="Quantidade",bg=PANEL,fg=MUTED).pack(anchor="w",pady=(16,4)); qty=tk.StringVar(value="1,00"); ttk.Entry(right,textvariable=qty).pack(fill="x")
        expiry=tk.StringVar()
        if entry:
            tk.Label(right,text="Validade opcional (DD/MM/AAAA)",bg=PANEL,fg=MUTED).pack(anchor="w",pady=(14,4)); ttk.Entry(right,textvariable=expiry).pack(fill="x")
        status=tk.Label(right,text="",bg=PANEL,fg=MUTED,font=("Segoe UI",10)); status.pack(anchor="w",pady=(14,8))
        def confirm():
            p=found["p"]
            n=parse_number(qty.get())
            if not p or n is None or n<=0:
                status.config(text="Localize um produto e informe quantidade válida.",fg=RED); return
            try:
                updated=self.db.stock_in(ref.get(),n,expiry.get().strip() or None) if entry else self.db.stock_out(ref.get(),n)
                found["p"]=updated; status.config(text="Operação concluída.",fg=GREEN); locate()
            except Exception as ex:
                status.config(text=str(ex),fg=RED)
        ttk.Button(right,text="CONFIRMAR ENTRADA" if entry else "CONFIRMAR SAÍDA",command=confirm).pack(fill="x",pady=(8,0))


if __name__ == "__main__":
    App().mainloop()
