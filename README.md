<div align="center">

# 🛰️ Serafiel

### Radar social por GPS + mensajería estilo WhatsApp

Descubre personas activas cerca de ti, conversa en salas locales, chatea en privado y en grupos, y comparte tu ubicación en tiempo real. Todo en una app Android nativa.

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)
![MapLibre](https://img.shields.io/badge/Mapas-MapLibre%20%2B%20OpenFreeMap-396CB2)
![Build](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)
![License](https://img.shields.io/badge/Licencia-GPL%20v3-blue)
[![Release](https://img.shields.io/github/v/release/gbrss/Serafiel?label=Release&color=FFC928)](https://github.com/gbrss/Serafiel/releases/latest)

### 📲 [Descargar el APK (Release)](https://github.com/gbrss/Serafiel/releases/download/latest/Serafiel-debug.apk)

<sub>Se actualiza solo con cada cambio en `main` · [Ver todas las releases](https://github.com/gbrss/Serafiel/releases)</sub>

</div>

---

## 📑 Contenido

- [Qué es Serafiel](#-qué-es-serafiel)
- [Funcionalidades](#-funcionalidades)
- [Stack tecnológico](#-stack-tecnológico)
- [Arquitectura](#-arquitectura)
- [Modelo de datos en Firestore](#-modelo-de-datos-en-firestore)
- [Medios grandes en Firestore (trozos)](#-medios-grandes-en-firestore-trozos)
- [Seguridad](#-seguridad)
- [Puesta en marcha](#-puesta-en-marcha)
- [Compilar sin Android Studio (GitHub Actions)](#-compilar-sin-android-studio-github-actions)
- [Pruebas de reglas](#-pruebas-de-reglas)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [Límites conocidos y hoja de ruta](#-límites-conocidos-y-hoja-de-ruta)
- [Contribuir](#-contribuir)
- [Licencia](#-licencia)

---

## 💡 Qué es Serafiel

Serafiel une dos mundos en una sola app:

1. **Un radar social sin filtros:** cada persona enciende una *baliza* con su propósito (amistad, networking, turismo, ayuda o charla) y aparece para quienes estén dentro del radio elegido, desde 1 km hasta todo el mundo.
2. **Mensajería completa al estilo WhatsApp:** chats 1:1 y grupos con estados de entrega, reacciones, notas de voz, fotos y videos en HD, ubicación en tiempo real y respuesta rápida desde las notificaciones.

El inicio de sesión es con cuenta de Google y toda la información vive en Cloud Firestore, protegida por reglas de seguridad estrictas.

---

## ✨ Funcionalidades

### 📡 Radar y Sala Local
| Función | Detalle |
|---|---|
| Baliza de proximidad | Nombre, propósito, estado, idiomas y foto; se enciende y apaga con un toque |
| Radio ajustable | De 1 a 1000 km, o modo global (*+1000 km · Todo el mundo*) |
| Filtro por propósito | Amistad, Networking, Turismo, Ayuda, Charla |
| Presencia | Indicador verde "en línea" (desactivable por privacidad) |
| Sala Local | Chat público de la zona con respuestas, edición y moderación |
| Seguridad | Reportar perfiles o mensajes y bloquear personas |

### 💬 Chats privados y grupos
| Función | Chats 1:1 | Grupos |
|---|:---:|:---:|
| Texto, emojis, responder deslizando, editar y eliminar | ✅ | ✅ |
| Menciones con `@` | — | ✅ |
| **Escribiendo…** en la cabecera | ✅ | — |
| **✓ enviado · ✓✓ entregado · ✓✓ azul visto** | ✅ | — |
| **Reacciones con emoji** (rápidas o cualquier emoji) | ✅ | ✅ |
| **Notas de voz de hasta 5 min** | ✅ | ✅ |
| Fotos (cámara o galería) con **versión HD opcional** | ✅ | ✅ |
| **Videos** comprimidos en el teléfono (480p / 720p HD) | ✅ | ✅ |
| Archivos de audio (hasta 24 MB) | ✅ | ✅ |
| Ubicación actual y **ubicación en tiempo real** (15 min, 1 h, 8 h) | ✅ | ✅ |

### 🎤 Notas de voz estilo WhatsApp
- **Mantener pulsado** el micrófono para grabar y **soltar** para enviar.
- **Deslizar hacia arriba** 🔒 bloquea la grabación, que sigue sola hasta pulsar enviar.
- **Deslizar hacia la izquierda** cancela.
- Máximo de **5 minutos**; al llegar al límite se envía automáticamente.

### 🖼️ Calidad de fotos y videos (Ajustes)
| Ajuste | Qué hace |
|---|---|
| **Enviar en HD** | Fotos hasta 2048 px y videos 720p a ~2,5 Mbps (máx. 1:00). Apagado: 480p a ~1 Mbps (máx. 2:30) |
| **Recibir en HD automáticamente** | Descarga sola la versión HD de las fotos recibidas. Apagado: se ve la versión ligera y la HD se baja al abrir la foto |

### 🔔 Notificaciones
- Mensajes con estilo de conversación (MessagingStyle).
- **Responder** desde la notificación sin abrir la app.
- **Marcar como leído** en chats 1:1.

### 🗺️ Mapas
- **MapLibre Native** con estilos vectoriales de **OpenFreeMap**: sin API key, sin costo y con datos de OpenStreetMap.
- Marcadores en Compose (avatar, punto GPS y pin), círculo de precisión en metros reales y miniaturas estáticas cacheadas para las burbujas del chat.

---

## 🧰 Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Arquitectura | MVVM (ViewModel + StateFlow), repositorio único |
| Backend | Firebase Authentication (Google / Credential Manager) + Cloud Firestore |
| Mapas | MapLibre Android SDK 13 (OpenGL ES) + OpenFreeMap |
| Ubicación | Google Play Services Location (FusedLocationProvider) |
| Lugares cercanos | Overpass API (OpenStreetMap), Geocoder de Android y Google Places opcional |
| Video | AndroidX Media3 Transformer (H.264 + AAC) |
| Imágenes | Coil |
| Build | Gradle 9.3 (Kotlin DSL + version catalog), AGP 9.1 |
| CI | GitHub Actions (APK de debug + pruebas de reglas en el emulador) |

`minSdk 24` · `targetSdk 36`

---

## 🏗️ Arquitectura

```
┌──────────────────────────── UI (Jetpack Compose) ────────────────────────────┐
│ RadarScreen · ZoneRoomScreen · ChatsScreen · GroupsScreen · ProfileSafetySheet │
│ WhatsAppLocationComponents · VoiceNoteComposer · ChatExtras · MapLibreMap      │
└──────────────────────────────────────┬────────────────────────────────────────┘
                                       │ StateFlow / eventos
                         ┌─────────────▼─────────────┐
                         │      SerafielViewModel     │
                         └─────────────┬─────────────┘
          ┌────────────────────────────┼─────────────────────────────┐
┌─────────▼─────────┐    ┌─────────────▼────────────┐    ┌───────────▼──────────┐
│ SerafielRepository │    │ ChatMediaHelper           │    │ LocationHelper        │
│ (Firestore + Auth) │    │ ChatVideoHelper           │    │ (GPS, zonas, lugares) │
└─────────┬─────────┘    │ ChunkedMediaStore         │    └──────────────────────┘
          │              └─────────────┬────────────┘
          └────────────────┬───────────┘
                    ┌──────▼──────┐        ┌────────────────────────────────┐
                    │  Firestore  │◄───────┤ MessageListenerService           │
                    └─────────────┘        │ (notificaciones + "entregado")   │
                                           │ NotificationReplyReceiver        │
                                           └────────────────────────────────┘
```

---

## 🗄️ Modelo de datos en Firestore

```
users/{uid}                          perfil privado, bloqueos, presencia
beacons/{uid}                        baliza pública del radar
zone_rooms/{zoneKey}/messages/{id}   Sala Local
chats/{uidA_uidB}                    chat 1:1
  ├─ lastReadAt / lastDeliveredAt    mapas uid → hora del servidor (✓✓ / visto)
  ├─ typingAt                        mapa uid → último pulso de "escribiendo"
  └─ messages/{id}
       ├─ reactions                  mapa uid → emoji
       └─ chunks/{index}             trozos de medios grandes
groups/{groupId}
  └─ messages/{id}
       ├─ reactions
       └─ chunks/{index}
reports/{id}                         reportes de seguridad
```

**Tipos de mensaje:** `text`, `image`, `voice`, `audio`, `video`, `location`, `live_location`.

**Cómo se calculan los estados:** cada mensaje propio compara su `createdAt` con `lastDeliveredAt` y `lastReadAt` del otro participante:

| Condición | Estado |
|---|---|
| `lastReadAt[otro] ≥ createdAt` | ✓✓ azul **visto** |
| `lastDeliveredAt[otro] ≥ createdAt` | ✓✓ **entregado** |
| Ninguna de las anteriores | ✓ **enviado** |

Así basta **una escritura por conversación** para marcar todo como leído, en vez de una por mensaje.

---

## 📦 Medios grandes en Firestore (trozos)

Firestore limita cada documento a 1 MiB. Para notas de voz largas, fotos HD y videos, `ChunkedMediaStore`:

1. Divide el archivo en partes de **512 KB** (~683 KB en base64).
2. Sube cada parte a `.../messages/{id}/chunks/{index}` **antes** de crear el mensaje, para que el receptor nunca vea un mensaje incompleto.
3. Guarda en el mensaje `chunkCount` y `mediaSizeBytes`. `mediaData` queda para la versión ligera: la foto estándar, la miniatura del video o el audio corto.
4. Al reproducir o abrir, descarga los trozos uno por uno con progreso, los une y los cachea en `cacheDir/chat_media`.

| Contenido | En línea (`mediaData`) | En trozos |
|---|---|---|
| Foto estándar | ✅ 640 px | — |
| Foto HD | ✅ (versión ligera) | ✅ hasta 2048 px |
| Nota de voz ≤ ~480 KB | ✅ | — |
| Nota de voz larga (hasta 5 min) | — | ✅ |
| Video | ✅ miniatura | ✅ MP4 (máx. 24 MB) |

---

## 🔐 Seguridad

Las reglas de [`firestore.rules`](firestore.rules) aplican **denegación por defecto** y validan forma, tamaño y autoría de cada escritura:

- Solo los participantes o miembros leen y escriben en sus chats y grupos.
- Cada usuario **solo puede modificar su propia entrada** en `lastReadAt`, `lastDeliveredAt`, `typingAt` y `reactions`, y los recibos deben llevar la **hora del servidor** (no se puede falsificar un "visto").
- Una reacción no puede tocar ningún otro campo del mensaje.
- Los trozos de medios exigen `senderId` propio, índice válido y como máximo 700 000 caracteres por documento.
- Bloqueos, reportes y moderación de la Sala Local.

---

## 🚀 Puesta en marcha

### Requisitos
- JDK 21 y Android SDK (o solo una cuenta de GitHub; ver la sección siguiente).
- Un proyecto de Firebase con **Authentication (Google)** y **Cloud Firestore**.

### 1. Firebase
1. Registra la app Android con el paquete `com.aistudio.serafiel.rmqvkp`.
2. Agrega las huellas **SHA-1 y SHA-256** del keystore con que firmas. Sin ellas, el inicio de sesión con Google falla.
3. Descarga `google-services.json` y colócalo en `app/`.
4. Publica [`firestore.rules`](firestore.rules) en la base de datos indicada en [`firebase.json`](firebase.json).

### 2. Variables (opcional)
Copia `.env.example` a `.env`:

```properties
# Solo para sugerencias de Google Places. Los mapas no la necesitan.
MAPS_API_KEY=tu_api_key
```

### 3. Compilar
```bash
gradle :app:assembleDebug
# APK en app/build/outputs/apk/debug/
```

---

## ☁️ Compilar sin Android Studio (GitHub Actions)

El workflow [`build-apk.yml`](.github/workflows/build-apk.yml) compila un APK de debug **en cada push**, en cualquier rama.

**Secrets del repositorio** (Settings → Secrets and variables → Actions):

| Secret | Obligatorio | Uso |
|---|:---:|---|
| `GOOGLE_SERVICES_JSON` | ✅ | Contenido completo de `google-services.json` |
| `DEBUG_KEYSTORE_BASE64` | Recomendado | Keystore de debug en base64. Mantiene la firma fija para actualizar sin desinstalar y para que el SHA-1 registrado en Firebase siga siendo válido |
| `MAPS_API_KEY` | — | Google Places (opcional) |

**Descargar el APK:** cada compilación de `main` se publica en **Releases**, con un enlace fijo que funciona directo desde el celular:

👉 **[Descargar Serafiel-debug.apk](https://github.com/gbrss/Serafiel/releases/latest/download/Serafiel-debug.apk)**

(También queda como *artifact* en cada corrida de Actions.)

> El log de cada compilación imprime el SHA-1 y SHA-256 del keystore usado, listos para pegar en Firebase.

---

## 🧪 Pruebas de reglas

[`firestore.test.js`](firestore.test.js) cubre aislamiento entre usuarios, mensajes enriquecidos, grupos, reportes, recibos de lectura, reacciones y trozos de medios. El workflow [`firestore-rules.yml`](.github/workflows/firestore-rules.yml) las ejecuta en el **emulador de Firestore** cada vez que cambian las reglas o las pruebas.

En local:
```bash
npm install --no-save @firebase/rules-unit-testing firebase firebase-tools
npx firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"
```

---

## 📁 Estructura del proyecto

```
app/src/main/java/com/example/
├── MainActivity.kt                    navegación, permisos y ciclo de vida
├── auth/GoogleAuthHelper.kt           login con Google (Credential Manager)
├── data/
│   ├── SerafielRepository.kt          acceso a Firestore
│   ├── LocationHelper.kt              GPS, zonas y lugares cercanos
│   ├── ChatMediaHelper.kt             fotos, audio, grabadora y reproductor
│   ├── ChatVideoHelper.kt             compresión de video (Media3)
│   └── ChunkedMediaStore.kt           medios troceados + ajustes HD
├── model/SerafielModels.kt            modelos y estados de entrega
├── notifications/
│   ├── MessageListenerService.kt      escucha de mensajes y recibo "entregado"
│   ├── SerafielNotificationHelper.kt  canales y notificaciones
│   └── NotificationReplyReceiver.kt   responder / marcar leído
└── ui/
    ├── map/MapLibreMap.kt             mapa, marcadores y miniaturas
    ├── screens/                       Radar, Sala Local, Chats, Grupos, Perfil,
    │                                  ubicación, notas de voz, reacciones, video
    ├── theme/                         colores y tipografía
    └── viewmodel/SerafielViewModel.kt
```

---

## 🧭 Límites conocidos y hoja de ruta

| Tema | Estado actual | Próximo paso |
|---|---|---|
| Notificaciones con la app cerrada | Llegan mientras el proceso de la app sigue vivo | Push con **Firebase Cloud Messaging** |
| "Entregado" | Se marca cuando la app recibe el mensaje | Marcarlo también desde el push de FCM |
| Estados en grupos | Solo reacciones | "Escribiendo" y "visto por" en grupos |
| Almacenamiento de medios | Firestore troceado (cuenta en la cuota) | Migrar a Cloud Storage si crece el volumen |
| Videos | Máx. 24 MB tras comprimir | Subida en segundo plano con WorkManager |

---

## 🤝 Contribuir

¡Las contribuciones son bienvenidas!

1. Haz un *fork* y crea una rama: `git checkout -b feat/mi-mejora`.
2. Usa **tu propio proyecto de Firebase** para probar: registra el paquete, agrega tus SHA-1/SHA-256, publica `firestore.rules` y coloca tu `google-services.json` en `app/` (está en `.gitignore`).
3. Si tocas `firestore.rules`, agrega casos en `firestore.test.js`; el CI los corre en el emulador.
4. Usa mensajes de commit claros (`feat:`, `fix:`, `docs:`, `ci:`…) y abre un *Pull Request* describiendo el cambio.

**Nunca subas** `google-services.json`, keystores, archivos `.env` ni APKs al repositorio.

---

## 📜 Licencia

Serafiel es software libre bajo la **[GNU General Public License v3.0](LICENSE)**: puedes usarlo, estudiarlo, modificarlo y redistribuirlo, siempre que las versiones derivadas se publiquen con la misma licencia y su código fuente.

---

<div align="center">

**Serafiel** · Hecho con Kotlin, Jetpack Compose y Firebase

Mapas © [OpenFreeMap](https://openfreemap.org) · Datos © [colaboradores de OpenStreetMap](https://www.openstreetmap.org/copyright)

</div>
