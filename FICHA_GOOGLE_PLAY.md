# Ficha de Google Play · Serafiel

> Los límites de caracteres son los de Play Console. Textos listos para pegar.

## Datos básicos
- **Nombre de la app** (máx. 30): `Serafiel: radar y chat`
- **Categoría:** Social
- **Etiquetas sugeridas:** Redes sociales, Mensajería, Encontrar gente cercana
- **Correo de contacto / sitio web / política de privacidad:** pendientes de definir (la política es obligatoria)

## Descripción breve (máx. 80)
`Encuentra gente cerca en un radar GPS y chatea con privacidad y control total.`

## Descripción completa (máx. 4000)
Serafiel une dos cosas en una sola app: un radar social por GPS y un chat completo.

**📡 CONOCE GENTE CERCA**
Enciende tu baliza con tu propósito (amistad, networking, turismo, ayuda o charla) y aparece en el radar de quienes estén dentro del radio que elijas, desde 1 km hasta todo el mundo. Filtra por propósito, por idioma o por quién está disponible ahora.

✈️ **Modo viaje:** ¿vas a otra ciudad? Mira quién hay allá antes de llegar, sin cambiar tu ubicación.

🏘️ **Salas de zona:** un chat abierto con la gente de tu zona.

**💬 CHATS Y GRUPOS COMPLETOS**
• Chats privados y grupos de hasta 50 personas
• Fotos, videos, notas de voz, audios y ubicación
• Ubicación en tiempo real (15 min, 1 h u 8 h)
• Responder, reaccionar con emojis, editar, borrar, reenviar y fijar mensajes
• Menciones con @ y encuestas en grupos
• "Escribiendo…" y confirmaciones de entrega y lectura, también en grupos
• Búsqueda dentro de cada conversación
• Sin internet, tus mensajes de texto quedan en cola y se envían solos al reconectar

**🔒 TÚ MANDAS SOBRE TU PRIVACIDAD**
• Elige qué tan precisa es tu ubicación: exacta, ~500 m o ~2 km
• Modo fantasma: desaparece del radar por 1, 8 o 24 horas
• Bloqueo de la app con huella o PIN
• Inicio de sesión con cuenta de Google verificada
• Reporta y bloquea a quien quieras
• Tu ubicación exacta solo se comparte cuando tú la envías en un chat

**🎨 HAZLA TUYA**
• Tema claro u oscuro
• 5 colores de acento
• Fondos de chat: degradados, un degradado propio o tu foto
• Disponible en español, inglés, portugués, francés, alemán, italiano y ruso

**🌍 CÓDIGO ABIERTO**
Serafiel es software libre (GPL v3). Mapas con OpenStreetMap y OpenFreeMap, sin rastreadores publicitarios.

Descárgala y enciende tu baliza.

## Novedades de esta versión (máx. 500)
Primera versión pública: radar GPS, modo viaje, chats y grupos con encuestas, ubicación en tiempo real, modo fantasma, bloqueo con huella/PIN, temas y fondos personalizados, 8 idiomas y envío de mensajes sin conexión.

## Recursos gráficos que debes preparar
| Recurso | Requisito |
|---|---|
| Icono | 512 × 512 px, PNG |
| Gráfico de funciones | 1024 × 500 px, PNG o JPG |
| Capturas de teléfono | Mínimo 2, hasta 8 (16:9 o 9:16, lado entre 320 y 3840 px) |
| Sugerencia de capturas | Radar · Modo viaje · Chat con fondo · Grupo con encuesta · Privacidad (modo fantasma) · Apariencia |

## Cuestionarios obligatorios de Play Console
- **Seguridad de los datos:** declarar ubicación (aproximada y precisa), nombre, correo (cuenta Google), fotos/videos/audio y mensajes. Los datos se procesan en Firebase. Indicar que el usuario puede borrar su cuenta si se implementa esa opción.
- **Clasificación de contenido:** apps con contenido generado por usuarios y ubicación; requiere moderación (ya existe reportar y bloquear).
- **Permisos sensibles:** justificar ubicación (radar), cámara y micrófono (fotos, videos, notas de voz) y notificaciones.
- **Cuenta de desarrollador:** pago único de US$25; las cuentas personales nuevas deben hacer una prueba cerrada con 12 testers durante 14 días antes de publicar. Verificar los requisitos vigentes en la ayuda de Play Console.

## Antes de publicar (pendiente técnico)
1. Compilar un **AAB firmado de release** (hoy el CI publica solo APK de debug).
2. Publicar una **política de privacidad** en una URL pública.
3. Ofrecer **eliminar cuenta y datos** desde la app (Play lo exige para apps con cuentas).
4. Revisar el aviso de "Instalar sin analizar" de Play Protect: desaparece al publicar en Play.
