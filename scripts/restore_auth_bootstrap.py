from pathlib import Path
import re

path=Path('app/src/main/assets/app.js')
js=path.read_text(encoding='utf-8')

# O transformador legado dos módulos substituía window.androidBack até o fim do arquivo.
# Recoloca somente o bootstrap de autenticação que precisa existir depois dele.
if 'db.auth.onAuthStateChange' not in js:
    js += "\ndb.auth.onAuthStateChange(e=>{if(e==='SIGNED_OUT'){$('app').classList.add('hidden');$('auth').classList.remove('hidden')}});\n"

if not re.search(r'(^|\n)start\(\);\s*$',js):
    js += 'start();\n'

path.write_text(js,encoding='utf-8')
print('Bootstrap de autenticação restaurado: sessão será verificada ao abrir o APK.')
