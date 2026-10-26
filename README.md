# ShadowPhoto

ShadowPhoto is an Android app that hides a private photograph inside an ordinary PNG cover. A normal image viewer shows only the cover. With the right passphrase, ShadowPhoto returns the original bytes.

ShadowPhoto es una app de Android que esconde una fotografía privada dentro de un PNG normal. Un visor de imágenes cualquiera muestra solo la portada. Con la clave correcta, ShadowPhoto devuelve los bytes originales.

There is no server, no account, and no network permission. The sealed file is the whole secret.

No hay servidor, ni cuenta, ni permiso de red. El archivo sellado es el secreto completo.

## English

### What it does

1. You take a photo or pick one. The original is not written to the public gallery.
2. You choose a different cover, or the app generates a neutral one on the device.
3. You set a passphrase of at least 8 characters.
4. The app derives a key with **Argon2id**, encrypts the original with **AES-256-GCM**, and inserts that ciphertext into the PNG.
5. The result lives only in the app’s private storage and is listed in the vault.
6. Opening it again requires the same passphrase. A wrong passphrase returns nothing, not a partial image.
7. You can export the protected PNG or a `.shp` backup and open it on another Android phone with the same passphrase.
8. From the detail screen, or right after protecting, you can share that sealed PNG or copy it into the **ShadowPhoto** album under Pictures. Google Photos can back that album up when backup is on. The private photo is not written there.

The vault asks for biometrics or the device lock when the phone has one enrolled. It also creates an AES key in the Android Keystore that never leaves the device. Portable decryption is still the passphrase.

### Cover PNG and the `shPd` chunk

The container is a valid PNG:

- signature and `IHDR`
- `IDAT` with the cover pixels, left unchanged
- a private ancillary chunk named `shPd` (lowercase first and second letters: not critical, not public)
- `IEND`

`shPd` is safe to copy. A standards-compliant viewer ignores it and paints the cover. The disguise is not undetectable: the chunk is visible to anyone who inspects the file, and the file is larger than the cover alone. What an inspector cannot do is recover the photograph without the passphrase, or reveal it by raising contrast. This is not LSB steganography and the secret is not stored in Exif.

Inside `shPd` (format **SHP-1**):

```
SHP1 | version u8 | flags u8 | salt 16 | KEK nonce 12 | DEK nonce 12 |
wrapped DEK length u16 | wrapped DEK | ciphertext length u32 | ciphertext
```

Version must be 1. The `.shp` backup starts with `SHPB`, version 1, three reserved bytes, a u32 length, and the full protected PNG. It exists so another app does not treat the container as a photo and recompress it.

JPEG covers are re-encoded to PNG. Text and Exif chunks (`eXIf`, `tEXt`, `iTXt`, `zTXt`, `tIME`) are stripped from the cover. The private original is kept as the exact file bytes, then encrypted.

### Cryptography

No keys are embedded in the app.

1. A random 16-byte salt is stored in the container.
2. KEK = Argon2id(passphrase, salt), Argon2 version 1.3, 19 MiB, t=2, p=1, 32-byte output. Bouncy Castle supplies Argon2id only. AES-GCM uses `javax.crypto`, not a Bouncy Castle provider.
3. A random 256-bit DEK encrypts the original with AES-256-GCM. The authentication tag stays with the ciphertext.
4. The DEK is wrapped with the KEK under a separate nonce.
5. Version, flags, and salt are additional authenticated data. If they change, GCM fails and the app returns nothing.

`protect()` checks a round trip before it stores the file. A tampered ciphertext or a damaged chunk CRC fails closed.

The passphrase in the Compose UI is a `String` and cannot be wiped. On the way into the engine it is copied to a `CharArray` and to UTF-8 bytes, and those copies are zeroed.

Parameters live in `Argon2Params.PRODUCTION`.

### Local-only design

- `applicationId` / namespace: `com.shadowphoto`
- `minSdk` 30 (Android 11), `targetSdk` / `compileSdk` 35
- No `INTERNET` permission and no backend
- Room database `shadowphoto.db`, table `protected_images`: `id`, `path`, `backupPath`, `contentHash` (SHA-256 of the protected PNG), `createdAt`, `flags`, `originalSize`
- Files under `filesDir/vault/{id}.png` and `{id}.shp`, written atomically. Delete overwrites the bytes first. That is best-effort; flash storage does not guarantee physical erasure.
- `allowBackup=false`. Extraction rules exclude the vault, the database, and the keystore prefs.
- Private screens set `FLAG_SECURE`.
- The in-app lock badge is UI only. The exported PNG has no visible watermark.

### Protect, unlock, export, and share

**Protect.** Home → Nueva fotografía. Private photo from the camera (cache file, overwritten and deleted) or the photo picker. Cover from the picker or a generated neutral PNG. Passphrase twice, then Cifrar fotografía. Success copy: “Imagen protegida correctamente”.

**Unlock.** Vault → detail → Descifrar. After a correct passphrase, BiometricPrompt (`BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`) runs before the viewer. Cancel wipes the unlocked bytes. If the device has no biometric or credential enrolled, the passphrase alone continues. The viewer does not save the original to the gallery.

**Export.** Detail can write the sealed PNG or the `.shp` backup through the system document picker (Storage Access Framework). Import an external PNG or `.shp` with Abrir archivo, then optionally save it into the vault.

**Share.** Compartir opens the Android share sheet with the sealed PNG (`image/png`) via a FileProvider limited to `filesDir/vault/`. WhatsApp appears there when it is installed. **Enviar por WhatsApp** is shown only if `com.whatsapp` or `com.whatsapp.w4b` is installed. That shortcut sends the PNG as a document (`application/octet-stream`) so WhatsApp is less likely to recompress it and drop `shPd`. If WhatsApp cannot be opened, the sheet is used. Other apps may still recompress an image and destroy the chunk.

**Album.** Guardar en el álbum ShadowPhoto inserts the same sealed bytes with MediaStore into `Pictures/ShadowPhoto/`. That is a normal PNG the Photos app can sync when backup is on. There is no private Google Photos API. **Enviar a Google Fotos** appears only if `com.google.android.apps.photos` is installed, and it is a normal send intent. If Fotos re-saves the image, the sealed block can be lost. The album is the reliable path.

On Android 11+ the app does not request `WRITE_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` for this save. Package visibility uses a `<queries>` block for WhatsApp, WhatsApp Business, Google Photos, and `SEND` of `image/png`. It does not request `QUERY_ALL_PACKAGES`.

### Requirements

- Android 11 (API 30) or newer on the device
- JDK 17 (the project compiles to JVM 17; a JDK 21 toolchain is fine)
- Android SDK 35 and Build-Tools 35.0.0
- Android Studio, or the Gradle wrapper below

Gradle wrapper **8.11.1**, Android Gradle Plugin **8.7.3**, Kotlin **2.0.21**, Compose BOM **2024.12.01**.

### Build and run

```bash
# optional, if the SDK is not in the default location
echo "sdk.dir=/absolute/path/Android/Sdk" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Open the repository root in Android Studio (not the `app/` folder), pick the `app` module, and run on a device or emulator with API 30+. `local.properties` is gitignored. No API key is required.

The debug APK is written to `app/build/outputs/apk/debug/`. A sideload build is also published at https://joseareche.com/downloads/ShadowPhoto-debug.apk. That file is a debug build, not a store release, and it is not committed in this repository.

Unit tests cover the Argon2id parameters with AES-256-GCM, a byte-exact protect/unlock round trip, an unchanged cover `IDAT`, and failure on a wrong passphrase, tampered ciphertext, tampered authenticated metadata, or a damaged chunk CRC.

### Security notes

The debug APK is not a production build. `assembleDebug` signs with the Android debug keystore. Do not ship that APK, and do not treat a debug signature as an update path for a release install. Release minification is currently off; turning it on needs a proper release keystore that is not in this repository.

A lost passphrase cannot be recovered. The device-bound Keystore key is not a backup of the passphrase. Sharing or saving always copies the sealed PNG, never the decrypted private bytes. Apps that recompress photos can strip `shPd`. Prefer the WhatsApp document shortcut, the `.shp` backup, or the `Pictures/ShadowPhoto` album.

### Layout

```
app/src/main/java/com/shadowphoto/
  ui/          Jetpack Compose, Material 3
  share/       Share sheet, WhatsApp shortcut, MediaStore album
  domain/      ProtectionService and the in-memory session
  data/        Room and private files
  crypto/      Argon2id, AES-256-GCM, Android Keystore
  container/   PNG, shPd chunk, .shp backup
  camera/      CameraX into the app cache
```

### Left out on purpose

- Accounts, a server, and any network client
- LSB steganography and storing the secret in Exif
- A visible mark on the exported cover
- Copying the decrypted photo into the gallery
- A guarantee of physical deletion on flash

## Español

### Qué hace

1. Tomas una foto o la eliges. El original no se escribe en la galería pública.
2. Eliges una portada distinta, o la app genera una portada neutra en el dispositivo.
3. Escribes una clave de al menos 8 caracteres.
4. La app deriva una clave con **Argon2id**, cifra el original con **AES-256-GCM** y lo inserta en el PNG.
5. El resultado vive solo en el almacenamiento privado de la app y aparece en la bóveda.
6. Para verlo de nuevo hace falta la misma clave. Una clave incorrecta no devuelve una imagen parcial.
7. Puedes exportar el PNG protegido o un respaldo `.shp` y abrirlo en otro Android con la misma clave.
8. Desde el detalle, o justo después de proteger, puedes compartir ese PNG sellado o copiarlo al álbum **ShadowPhoto** dentro de Imágenes. Google Fotos puede subirlo si la copia de seguridad está activada. La foto privada no se escribe ahí.

La bóveda pide biometría o el bloqueo del dispositivo cuando el teléfono lo tiene configurado. También crea una clave AES en Android Keystore que no sale del aparato. El descifrado portátil sigue siendo la contraseña.

### PNG de portada y el chunk `shPd`

El contenedor es un PNG válido:

- firma e `IHDR`
- `IDAT` con los píxeles de la portada, sin modificar
- un chunk ancilar privado llamado `shPd` (minúsculas en el primer y segundo carácter: no es crítico y no es público)
- `IEND`

`shPd` se puede copiar. Un visor que respeta el formato lo ignora y pinta la portada. El camuflaje no es indetectable: quien inspeccione el archivo ve el chunk y un tamaño mayor que la portada. Lo que no puede hacer es recuperar la foto sin la clave, ni verla subiendo el contraste. No es esteganografía LSB y el secreto no va en Exif.

Dentro de `shPd` (formato **SHP-1**):

```
SHP1 | version u8 | flags u8 | salt 16 | nonce KEK 12 | nonce DEK 12 |
longitud DEK envuelta u16 | DEK envuelta | longitud u32 | ciphertext
```

La versión tiene que ser 1. El respaldo `.shp` empieza por `SHPB`, versión 1, tres bytes reservados, una longitud u32 y el PNG protegido completo. Sirve para copiar el contenedor sin que otra app lo trate como foto y lo recomprima.

Si la portada es JPEG, se vuelve a codificar como PNG. De la portada se quitan los chunks de texto y Exif (`eXIf`, `tEXt`, `iTXt`, `zTXt`, `tIME`). El original privado se conserva como bytes exactos del archivo y después se cifra.

### Criptografía

No hay claves embebidas.

1. Un salt aleatorio de 16 bytes se guarda en el contenedor.
2. KEK = Argon2id(contraseña, salt), Argon2 versión 1.3, 19 MiB, t=2, p=1, salida de 32 bytes. Bouncy Castle solo aporta Argon2id. AES-GCM usa `javax.crypto`, no un proveedor de Bouncy Castle.
3. Una DEK aleatoria de 256 bits cifra el original con AES-256-GCM. La etiqueta de autenticación va con el ciphertext.
4. La DEK se envuelve con la KEK y otro nonce.
5. Versión, flags y salt son datos asociados (AAD). Si cambian, GCM falla y la app no devuelve nada.

`protect()` comprueba la ida y vuelta antes de guardar. Un ciphertext alterado o un CRC de chunk dañado falla cerrado.

La contraseña de la interfaz Compose es un `String` y no se puede borrar de la memoria. Al entrar al motor se copia a `CharArray` y a bytes UTF-8, y esas copias sí se ponen a cero.

Los parámetros están en `Argon2Params.PRODUCTION`.

### Diseño solo local

- `applicationId` / namespace: `com.shadowphoto`
- `minSdk` 30 (Android 11), `targetSdk` / `compileSdk` 35
- Sin permiso `INTERNET` y sin backend
- Base Room `shadowphoto.db`, tabla `protected_images`: `id`, `path`, `backupPath`, `contentHash` (SHA-256 del PNG protegido), `createdAt`, `flags`, `originalSize`
- Archivos en `filesDir/vault/{id}.png` y `{id}.shp`, escritos de forma atómica. Al borrar se sobrescriben antes. Es un intento razonable; la memoria flash no garantiza el borrado físico.
- `allowBackup=false`. Las reglas de extracción excluyen la bóveda, la base de datos y las preferencias del Keystore.
- Las pantallas privadas usan `FLAG_SECURE`.
- El candado dentro de la app es solo interfaz. El PNG exportado no lleva una marca visible.

### Proteger, abrir, exportar y compartir

**Proteger.** Inicio → Nueva fotografía. La foto privada sale de la cámara (archivo de caché, sobrescrito y borrado) o del selector. La portada sale del selector o de un PNG neutro generado. La clave se escribe dos veces y se pulsa Cifrar fotografía. El texto de éxito es «Imagen protegida correctamente».

**Abrir.** Bóveda → detalle → Descifrar. Tras una clave correcta, BiometricPrompt (`BIOMETRIC_STRONG` o `DEVICE_CREDENTIAL`) corre antes del visor. Si cancelas, se borran los bytes descifrados. Si el dispositivo no tiene biometría ni bloqueo, basta la clave. El visor no guarda el original en la galería.

**Exportar.** El detalle puede escribir el PNG sellado o el respaldo `.shp` con el selector de documentos del sistema. Con Abrir archivo se importa un PNG o `.shp` externo y, si quieres, se guarda en la bóveda.

**Compartir.** Compartir abre la hoja del sistema con el PNG sellado (`image/png`) mediante un FileProvider limitado a `filesDir/vault/`. WhatsApp aparece ahí si está instalado. **Enviar por WhatsApp** solo se muestra si está `com.whatsapp` o `com.whatsapp.w4b`. Ese atajo envía el PNG como documento (`application/octet-stream`) para que WhatsApp sea menos propenso a recomprimirlo y perder `shPd`. Si WhatsApp no se puede abrir, se usa la hoja del sistema. Otras apps pueden recomprimir la imagen y destruir el chunk.

**Álbum.** Guardar en el álbum ShadowPhoto inserta los mismos bytes sellados con MediaStore en `Pictures/ShadowPhoto/`. Es un PNG normal que Fotos puede sincronizar si la copia de seguridad está activada. No hay una API privada de Google Fotos. **Enviar a Google Fotos** solo aparece si está `com.google.android.apps.photos`, y es un envío normal. Si Fotos vuelve a guardar la imagen, el bloque sellado puede perderse. El álbum es el camino fiable.

En Android 11+ la app no pide `WRITE_EXTERNAL_STORAGE` ni `READ_MEDIA_IMAGES` para este guardado. La visibilidad de paquetes usa un bloque `<queries>` para WhatsApp, WhatsApp Business, Google Fotos y `SEND` de `image/png`. No pide `QUERY_ALL_PACKAGES`.

### Requisitos

- Android 11 (API 30) o superior en el dispositivo
- JDK 17 (el proyecto compila a JVM 17; un JDK 21 también sirve)
- Android SDK 35 y Build-Tools 35.0.0
- Android Studio, o el wrapper de Gradle de abajo

Wrapper de Gradle **8.11.1**, Android Gradle Plugin **8.7.3**, Kotlin **2.0.21**, Compose BOM **2024.12.01**.

### Compilar y ejecutar

```bash
# opcional, si el SDK no está en la ruta por defecto
echo "sdk.dir=/ruta/absoluta/Android/Sdk" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Abre la raíz del repositorio en Android Studio (no la carpeta `app/`), elige el módulo `app` y ejecútalo en un dispositivo o emulador con API 30 o superior. `local.properties` no se versiona. No hace falta ninguna clave de API.

El APK de depuración queda en `app/build/outputs/apk/debug/`. También hay una copia para instalar a mano en https://joseareche.com/downloads/ShadowPhoto-debug.apk. Es una build de depuración, no una versión de tienda, y no está en este repositorio.

Las pruebas unitarias cubren los parámetros Argon2id con AES-256-GCM, una ida y vuelta exacta, un `IDAT` de portada sin cambios, y el fallo con clave incorrecta, ciphertext alterado, metadatos autenticados tocados o CRC de chunk dañado.

### Notas de seguridad

El APK de depuración no es una build de producción. `assembleDebug` firma con el keystore de depuración de Android. No distribuyas ese APK y no lo uses como ruta de actualización de una instalación release. La ofuscación de release está apagada; activarla exige un keystore de release que no está en este repositorio.

Si olvidas la clave, no se puede recuperar. La clave del Keystore ligada al aparato no es una copia de la contraseña. Compartir o guardar siempre copia el PNG sellado, nunca los bytes privados descifrados. Las apps que recomprimen fotos pueden quitar `shPd`. Conviene el atajo de WhatsApp como documento, el respaldo `.shp` o el álbum `Pictures/ShadowPhoto`.

### Estructura

```
app/src/main/java/com/shadowphoto/
  ui/          Jetpack Compose, Material 3
  share/       Hoja de compartir, atajo de WhatsApp, álbum MediaStore
  domain/      ProtectionService y la sesión en memoria
  data/        Room y archivos privados
  crypto/      Argon2id, AES-256-GCM, Android Keystore
  container/   PNG, chunk shPd, respaldo .shp
  camera/      CameraX hacia la caché de la app
```

### Dejado fuera a propósito

- Cuentas, servidor y cualquier cliente de red
- Esteganografía LSB y guardar el secreto en Exif
- Una marca visible en la portada exportada
- Copiar la foto descifrada a la galería
- Garantía de borrado físico en flash

## Contact

- Jose Areche
- Website: https://joseareche.com
- Email: jose.areche@gmail.com
- Blog post: https://joseareche.com/blog/shadowphoto-foto-privada-en-png/
- Debug APK: https://joseareche.com/downloads/ShadowPhoto-debug.apk

## Contacto

- Jose Areche
- Sitio web: https://joseareche.com
- Correo: jose.areche@gmail.com
- Artículo: https://joseareche.com/blog/shadowphoto-foto-privada-en-png/
- APK de depuración: https://joseareche.com/downloads/ShadowPhoto-debug.apk

## License

MIT. See [LICENSE](LICENSE).
