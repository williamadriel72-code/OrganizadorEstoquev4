from pathlib import Path

path = Path('app/src/main/assets/app.js')
js = path.read_text(encoding='utf-8')

old_scan = "const opNativeBarcode=window.onNativeBarcode;\n"
new_scan = "const opNativeBarcode=window.onNativeBarcode;\nconst opNativeScannerError=window.onNativeScannerError;\n"
if old_scan in js and 'const opNativeScannerError=' not in js:
    js = js.replace(old_scan, new_scan, 1)

old_handler = "window.onNativeBarcode=code=>{const target=operationalScanTarget;operationalScanTarget=null;if(target&&$(target)){ $(target).value=String(code||'');$(target).focus();toast('Código lido: '+code,'ok');return }if(opNativeBarcode)opNativeBarcode(code)};"
new_handler = old_handler + "\nwindow.onNativeScannerError=m=>{operationalScanTarget=null;if(opNativeScannerError)opNativeScannerError(m);else toast(m||'Erro no leitor.','err')};"
if old_handler in js and 'operationalScanTarget=null;if(opNativeScannerError)' not in js:
    js = js.replace(old_handler, new_handler, 1)

old_device = "let did=localStorage.getItem('aws_device_id');if(!did){did=(crypto?.randomUUID?.()||('dev-'+Date.now()+'-'+Math.random().toString(16).slice(2)));localStorage.setItem('aws_device_id',did)}"
new_device = "let base=localStorage.getItem('aws_device_base');if(!base){base=(window.crypto?.randomUUID?.()||('dev-'+Date.now()+'-'+Math.random().toString(16).slice(2)));localStorage.setItem('aws_device_base',base)}const did=base+'-'+currentUser.id"
if old_device not in js:
    raise SystemExit('Bloco de identificação do dispositivo não encontrado.')
js = js.replace(old_device, new_device, 1)

path.write_text(js, encoding='utf-8')
print('Refinamentos operacionais aplicados: dispositivo por usuário e cancelamento seguro do scanner.')
