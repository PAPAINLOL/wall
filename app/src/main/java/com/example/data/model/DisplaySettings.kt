package com.example.data.model

enum class BorderStyle(val title: String, val description: String) {
    RANDOM_VIBRANT("Color Aleatorio", "Bordes de color vivo y armónico elegido al azar"),
    DOMINANT_PALETTE("Color Dominante", "Borde adaptativo extraído automáticamente de la foto"),
    AMBIENT_BLUR("Desenfoque Ambiental", "Fondo difuminado de la misma imagen para TV/pantallas"),
    OLED_BLACK("Negro Puro OLED", "Bordes negros absolutos para ahorro de energía y contraste"),
    SLATE_DARK("Gris Cinematográfico", "Tono oscuro suave de sala de cine")
}

enum class FeedSort(
    val param: String,
    val displayName: String,
    val icon: String,
    val description: String
) {
    TOP("top?t=all", "Top", "🏆", "Basado en votos de los usuarios (mayor puntuación)"),
    NEW("new", "New", "⏱️", "Orden cronológico reciente (publicaciones más nuevas)");

    companion object {
        val TOP_MONTH = TOP
        val TOP_ALL = TOP
    }
}

enum class WallpaperTarget(val displayName: String) {
    HOME_AND_LOCK("Pantalla de Inicio y Bloqueo"),
    HOME_ONLY("Solo Pantalla de Inicio"),
    LOCK_ONLY("Solo Pantalla de Bloqueo")
}

data class DisplaySettings(
    val intervalSeconds: Int = 10,
    val borderStyle: BorderStyle = BorderStyle.RANDOM_VIBRANT,
    val feedSort: FeedSort = FeedSort.TOP,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.HOME_AND_LOCK,
    val autoSetWallpaperOnSlide: Boolean = false,
    val showTvClock: Boolean = true,
    val showPostInfo: Boolean = true,
    val showHumorousBadges: Boolean = true,
    val preventDuplicates: Boolean = true, // "No repetir"
    val isAutoPlay: Boolean = true
) {
    companion object {
        val AVAILABLE_INTERVALS = listOf(
            5 to "5 segundos (Rápido)",
            10 to "10 segundos (Recomendado)",
            15 to "15 segundos",
            30 to "30 segundos",
            60 to "1 minuto",
            300 to "5 minutos",
            900 to "15 minutos",
            1800 to "30 minutos",
            3600 to "1 hora"
        )
    }
}
