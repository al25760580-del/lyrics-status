# LyricsStatus v0.1 (Android / Kotlin / Material Design 3) 🎵

LyricsStatus es una aplicación nativa para Android escrita en **Kotlin** con **Jetpack Compose** y **Material Design 3**, con integración del motor de autenticación y presencia de **Starlingscord**.

Funciona de forma **100% autónoma en el dispositivo**, ejecutándose en segundo plano para sincronizar la letra de la música que estás escuchando en tiempo real, actualizar tu estado de Discord (`PATCH /api/v9/users/@me/settings`), y mostrarla tanto en la interfaz de la aplicación como en una **notificación interactiva enriquecida con letra sincronizada y traducción por IA**.

---

## 🌟 Características Principales

1. **Detección Dual de Música & Conmutador de Modos:**
   - **Modo 📱 Android Player:** Detecta Spotify, Apple Music, YouTube Music, Deezer, Tidal, Poweramp, VLC, etc. en tu teléfono mediante `MediaPlaybackListenerService` (`NotificationListenerService` / `MediaSessionManager`).
   - **Modo 🎮 Discord Rich Presence:** Conexión en tiempo real a Discord Gateway WebSocket (`wss://gateway.discord.gg/?v=10&encoding=json`) para detectar canciones reproducidas en cualquier dispositivo vinculado (Spotify en PC/Mac/Web, Cider, foobar2000, etc.).
   - **Modo 🔄 Auto Sync:** Prioriza la reproducción local de Android si está activa; si no, conmuta fluidamente a Discord Rich Presence.
   - **Conmutador Rápido:** Píldoras de cambio de modo de 1 toque integradas en la barra superior de reproducción.

2. **Gestor & Autenticación de Discord (Estilo Starlingscord):**
   - **Inicio de sesión directo:** Ingresa tu correo/teléfono y contraseña con soporte para autenticación en dos pasos (MFA / 2FA TOTP).
   - **Entrada Manual de Token:** Pega directamente tu token de usuario de Discord.
   - **Script de Consola Web:** Snippet de 1 clic para extraer el token desde la consola del navegador en Discord Web.
   - **Extractor de Conexiones de Spotify:** Obtiene automáticamente el `access_token` de Spotify vinculado a tu cuenta de Discord.

3. **Notificación en Segundo Plano con Letras en Vivo (`LyricsForegroundService`):**
   - Muestra el título, artista y la **línea activa de la letra sincronizada en tiempo real**.
   - Muestra la **traducción por IA justo debajo** (en modo bilingüe).
   - Acciones multimedia en la notificación: *Anterior*, *Reproducir/Pausar*, *Siguiente*, *Activar/Desactivar Traducción*.

4. **Menú de IA Multimodelo para Traducción de Letras:**
   - **Google Gemini:** `gemini-3.5-flash-lite` *(predeterminado de ultra-alta velocidad)*, `gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`, `gemini-2.0-flash`.
   - **OpenAI (ChatGPT):** `gpt-4.5-preview`, `gpt-4o`, `gpt-4o-mini`, `o3-mini`.
   - **Anthropic Claude:** `claude-3-7-sonnet-20250219`, `claude-3-5-haiku-20241022`, `claude-3-5-sonnet-20241022`.
   - **xAI Grok:** `grok-3`, `grok-2-latest`, `grok-beta`.
   - **Custom Endpoint & Entrada Libre:** Posibilidad de escribir manualmente cualquier modelo o conectar servidores locales (Ollama, vLLM, LMStudio, LocalAI).
   - Traducción de la canción completa en una sola llamada (batch 1:1) optimizada para ritmo y rima cantable.
   - Selector de idioma destino (Español mexicano, Español castellano, Inglés, Portugués, Francés, Alemán, Japonés, etc.).
   - Probador en vivo (*Live AI Tester*) con medidor de latencia en milisegundos.
   - Caché persistente en disco (`cache/lyrics_translations/`) y en memoria.

5. **Fuentes de Letras Sincronizadas:**
   - **Custom Lyrics:** Editor visual, importador de archivos `.lrc` o texto plano (con temporizador automático de 3 segundos por línea) y reemplazo prioritario.
   - **Caché local en disco:** Para evitar llamadas de red redundantes.
   - **LrcLib:** `https://lrclib.net/`
   - **NetEase Cloud Music:** `https://music.163.com/`
   - **QQ Music:** `https://y.qq.com/` (con decodificación Base64 y entidades HTML).

6. **Reproductor y Visor de Letras Interactivo:**
   - Desplazamiento automático fluido (*auto-scroll*) con efecto karaoke y resplandor en la línea activa.
   - Toque en cualquier línea para saltar (*seek*) inmediatamente a ese segundo de la canción.
   - Ajuste rápido de desfase milimétrico (*Offset* `+/- 100ms`) y compensación automática de latencia (*Auto-Offset*).
   - Barra de canciones demo para probar inmediatamente letras y traducción sin necesidad de abrir Spotify.

7. **Plantillas de Estado de Discord (`DiscordStatusPusher`):**
   - La línea activa se envía con `PATCH https://discord.com/api/v9/users/@me/settings` (campo `custom_status`), igual que el proyecto de referencia [aldair402/lyrics-status](https://github.com/aldair402/lyrics-status).
   - Campos disponibles: `{lyrics}`, `{timestamp}`, `{song_name}`, `{song_author}`, `{song_album}`.
   - Transformaciones con sintaxis `{campo:transformación}`: `uppercase`, `lowercase`, `cropped` (recorta a 40 caracteres con `...`) y `letters_only`. Se pueden combinar: `{lyrics:uppercase:letters_only}`.
   - Placeholders legacy del proyecto de referencia mantenidos: `{lyrics_upper}`, `{song_name_cropped}`, `{song_author_lower}`, etc.
   - Emoji unicode (`🎶`) o personalizado de Discord (`<:nombre:id>`, `<a:nombre:id>` animado) en el estado, con expiración automática de 60 s.
   - *Auto-Offset* EWMA: media móvil exponencial (70/30) de la latencia real de cada PATCH para enviar la línea con anticipación y que aparezca justo a tiempo (`updateAutoOffset` / `autoOffsetMs`).
   - Deduplicación por canción+línea y limpieza del estado (`custom_status = null`) al pausar/detener la reproducción.

---

## 📁 Estructura del Proyecto

```
LyricsStatusKotlin/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── scripts/
│   └── fetch_token.py           # Script CLI para validar tokens y conexiones de Spotify
└── app/
    ├── build.gradle.kts
    ├── src/
    │   ├── main/
    │   │   ├── AndroidManifest.xml
    │   │   ├── java/com/lyricsstatus/app/
    │   │   │   ├── LyricsApp.kt
    │   │   │   ├── MainActivity.kt
    │   │   │   ├── data/
    │   │   │   │   ├── discord/ # Integración Discord Gateway, Auth & RPC (Starlingscord)
    │   │   │   │   │   ├── DiscordAuth.kt
    │   │   │   │   │   └── DiscordGatewayPresence.kt
    │   │   │   │   ├── token/
    │   │   │   │   │   └── TokenFetcher.kt
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── AiProvider.kt
    │   │   │   │   │   ├── CustomLyricsMeta.kt
    │   │   │   │   │   ├── LyricsLine.kt
    │   │   │   │   │   ├── PlaybackState.kt
    │   │   │   │   │   ├── SettingsModel.kt
    │   │   │   │   │   └── SongLyrics.kt
    │   │   │   │   ├── parser/
    │   │   │   │   │   └── LrcParser.kt
    │   │   │   │   ├── sources/
    │   │   │   │   │   ├── CustomLyricsSource.kt
    │   │   │   │   │   ├── LrcLibSource.kt
    │   │   │   │   │   ├── LyricsSource.kt
    │   │   │   │   │   ├── NetEaseSource.kt
    │   │   │   │   │   └── QqMusicSource.kt
    │   │   │   │   ├── ai/
    │   │   │   │   │   ├── AiTranslator.kt
    │   │   │   │   │   ├── ClaudeTranslator.kt
    │   │   │   │   │   ├── CustomEndpointTranslator.kt
    │   │   │   │   │   ├── GeminiTranslator.kt
    │   │   │   │   │   ├── GrokTranslator.kt
    │   │   │   │   │   ├── OpenAiTranslator.kt
    │   │   │   │   │   ├── TranslationManager.kt
    │   │   │   │   └── repository/
    │   │   │   │       ├── LyricsRepository.kt
    │   │   │   │       └── SettingsRepository.kt
    │   │   │   ├── service/
    │   │   │   │   ├── LyricsForegroundService.kt
    │   │   │   │   ├── MediaPlaybackListenerService.kt
    │   │   │   │   ├── NotificationHelper.kt
    │   │   │   │   └── PlaybackStateManager.kt
    │   │   │   └── ui/
    │   │   │       ├── components/
    │   │   │       │   ├── AiProviderSelector.kt
    │   │   │       │   ├── CustomEndpointDialog.kt
    │   │   │       │   └── LyricsDisplay.kt
    │   │   │       ├── screens/
    │   │   │       │   ├── AiSettingsScreen.kt
    │   │   │       │   ├── CustomLyricsScreen.kt
    │   │   │       │   ├── GeneralSettingsScreen.kt
    │   │   │       │   ├── NowPlayingScreen.kt
    │   │   │       │   └── TokenFetcherScreen.kt
    │   │   │       ├── theme/
    │   │   │       │   ├── Color.kt
    │   │   │       │   ├── Theme.kt
    │   │   │       │   └── Type.kt
    │   │   │       └── viewmodel/
    │   │   │           ├── AiSettingsViewModel.kt
    │   │   │           ├── CustomLyricsViewModel.kt
    │   │   │           └── PlayerViewModel.kt
    │   │   └── res/
    │   │       ├── values/
    │   │       │   ├── strings.xml
    │   │       │   └── themes.xml
    │   │       ├── values-es/
    │   │       │   └── strings.xml
    │   │       └── xml/
    │   └── test/java/com/lyricsstatus/app/
    │       ├── DiscordPresenceTest.kt
    │       ├── LrcParserTest.kt
    │       ├── TokenFetcherTest.kt
    │       └── TranslationUtilsTest.kt
```
