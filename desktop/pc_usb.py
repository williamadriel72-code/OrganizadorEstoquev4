import json
import os
import queue
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

# Carrega primeiro o parser flexível de PDF usado pela versão Windows atual.
import app_windows  # noqa: F401
from app import App, PANEL2, MUTED, GREEN, YELLOW, RED

USB_PORT = 8765


def bundled_adb_path():
    candidates = []
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        base = Path(sys._MEIPASS)
        candidates += [base / "adb.exe", base / "platform-tools" / "adb.exe"]
    here = Path(__file__).resolve().parent
    candidates += [here / "adb.exe", here / "platform-tools" / "adb.exe"]
    found = shutil.which("adb")
    if found:
        candidates.append(Path(found))
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return str(candidate)
    return None


def hidden_run(args, timeout=6):
    kwargs = {
        "capture_output": True,
        "text": True,
        "timeout": timeout,
        "check": False,
    }
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.CREATE_NO_WINDOW
    return subprocess.run(args, **kwargs)


class ScannerRequestHandler(BaseHTTPRequestHandler):
    scan_queue = None

    def _reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/ping":
            self._reply(200, {"ok": True, "service": "OrganizadorEstoquePC"})
        else:
            self._reply(404, {"ok": False})

    def do_POST(self):
        if self.path != "/scan":
            self._reply(404, {"ok": False})
            return
        try:
            length = min(int(self.headers.get("Content-Length", "0")), 256)
            code = self.rfile.read(length).decode("utf-8", errors="ignore").strip()
            if not code or len(code) > 64 or any(ch in code for ch in "\r\n\t"):
                self._reply(400, {"ok": False, "error": "codigo_invalido"})
                return
            if ScannerRequestHandler.scan_queue is None:
                self._reply(503, {"ok": False, "error": "receptor_indisponivel"})
                return
            ScannerRequestHandler.scan_queue.put(code)
            self._reply(200, {"ok": True, "code": code})
        except Exception:
            self._reply(500, {"ok": False, "error": "falha_interna"})

    def log_message(self, format, *args):
        return


class USBScannerApp(App):
    def __init__(self):
        self.scan_queue = queue.Queue()
        self.usb_server = None
        self.adb_path = bundled_adb_path()
        self._adb_checking = False
        super().__init__()
        self.title("Organizador Geral de Estoque - PC + Scanner USB")

        self.usb_status = tk.Label(
            self.sidebar,
            text="Scanner USB: iniciando...",
            bg=PANEL2,
            fg=YELLOW,
            font=("Segoe UI Semibold", 9),
            justify="left"
        )
        self.usb_status.pack(side="bottom", anchor="w", padx=18, pady=(0, 8))

        self.start_usb_server()
        self.after(250, self.poll_scanner_queue)
        self.after(400, self.schedule_adb_check)

    def start_usb_server(self):
        try:
            ScannerRequestHandler.scan_queue = self.scan_queue
            self.usb_server = ThreadingHTTPServer(("127.0.0.1", USB_PORT), ScannerRequestHandler)
            self.usb_server.daemon_threads = True
            thread = threading.Thread(target=self.usb_server.serve_forever, daemon=True)
            thread.start()
        except Exception:
            self.usb_server = None
            self.usb_status.config(text=f"Scanner USB: porta {USB_PORT} ocupada", fg=RED)

    def set_usb_status(self, text, color):
        try:
            self.usb_status.config(text=text, fg=color)
        except Exception:
            pass

    def schedule_adb_check(self):
        if self._adb_checking:
            self.after(4000, self.schedule_adb_check)
            return
        self._adb_checking = True
        threading.Thread(target=self.check_adb_bridge, daemon=True).start()
        self.after(4000, self.schedule_adb_check)

    def check_adb_bridge(self):
        try:
            if not self.adb_path:
                self.after(0, lambda: self.set_usb_status("Scanner USB: ADB não encontrado", RED))
                return

            result = hidden_run([self.adb_path, "devices"], timeout=7)
            output = (result.stdout or "") + "\n" + (result.stderr or "")
            lines = [line.strip() for line in output.splitlines() if line.strip()]
            authorized = [line for line in lines if "\tdevice" in line]
            unauthorized = [line for line in lines if "\tunauthorized" in line]

            if unauthorized and not authorized:
                self.after(0, lambda: self.set_usb_status("Scanner USB: autorize no celular", YELLOW))
                return
            if not authorized:
                self.after(0, lambda: self.set_usb_status("Scanner USB: conecte o celular", MUTED))
                return

            reverse = hidden_run(
                [self.adb_path, "reverse", f"tcp:{USB_PORT}", f"tcp:{USB_PORT}"],
                timeout=6
            )
            if reverse.returncode == 0:
                self.after(0, lambda: self.set_usb_status("Scanner USB: CONECTADO ✓", GREEN))
            else:
                self.after(0, lambda: self.set_usb_status("Scanner USB: falha na ponte USB", RED))
        except subprocess.TimeoutExpired:
            self.after(0, lambda: self.set_usb_status("Scanner USB: ADB sem resposta", RED))
        except Exception:
            self.after(0, lambda: self.set_usb_status("Scanner USB: desconectado", MUTED))
        finally:
            self._adb_checking = False

    def poll_scanner_queue(self):
        latest = None
        try:
            while True:
                latest = self.scan_queue.get_nowait()
        except queue.Empty:
            pass

        if latest:
            try:
                self.show_products()
                self.product_query.set(latest)
                self.scan_product()
                self.deiconify()
                self.lift()
            except Exception:
                pass
        try:
            self.after(180, self.poll_scanner_queue)
        except Exception:
            pass

    def destroy(self):
        try:
            if self.usb_server:
                self.usb_server.shutdown()
                self.usb_server.server_close()
        except Exception:
            pass
        super().destroy()


if __name__ == "__main__":
    USBScannerApp().mainloop()
