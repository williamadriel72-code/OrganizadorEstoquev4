from pathlib import Path
import re

path = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')
kt = path.read_text(encoding='utf-8')

if 'import android.app.PendingIntent' not in kt:
    kt = kt.replace('import android.annotation.SuppressLint\n', 'import android.annotation.SuppressLint\nimport android.app.PendingIntent\n', 1)
if 'import android.content.pm.PackageInstaller' not in kt:
    kt = kt.replace('import android.content.pm.PackageInfo\n', 'import android.content.pm.PackageInfo\nimport android.content.pm.PackageInstaller\n', 1)

pattern = re.compile(r'''    private fun launchInstaller\(file: File\) \{\n.*?\n    \}\n\n    private fun notifyUpdateProgress''', re.S)
replacement = '''    private fun launchInstaller(file: File) {
        awaitingInstallPermission = false
        pendingInstallFile = null
        val installer = packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val statusIntent = Intent(this, UpdateInstallReceiver::class.java).apply {
                    action = "$packageName.UPDATE_INSTALL_STATUS"
                }
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusReceiver = PendingIntent.getBroadcast(this, sessionId, statusIntent, pendingFlags)
                session.commit(statusReceiver.intentSender)
            }
        } catch (sessionError: Throwable) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                notifyUpdateProgress(100, file.length(), file.length(), "Abrindo instalador do Android...")
                startActivity(fallback)
            } catch (fallbackError: Throwable) {
                notifyUpdateError(
                    "APK validado, mas o Android não conseguiu abrir a instalação: ${fallbackError.message ?: sessionError.message ?: "erro desconhecido"}"
                )
            }
        }
    }

    private fun notifyUpdateProgress'''

kt, count = pattern.subn(replacement, kt, count=1)
if count != 1:
    raise SystemExit('Método launchInstaller não encontrado para aplicar fallback.')

if 'PackageInstaller.SessionParams' not in kt or 'FileProvider.getUriForFile' not in kt:
    raise SystemExit('Fallback do instalador não foi aplicado corretamente.')

path.write_text(kt, encoding='utf-8')
print('Instalador preparado: PackageInstaller.Session com fallback FileProvider sem novo download.')
