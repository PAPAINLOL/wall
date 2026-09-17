package com.example.util

import com.example.data.model.RedditPost
import kotlin.math.abs
import kotlin.random.Random

object HumorHelper {

    private val instituteHumorTags = listOf(
        "🏆 Modo Instituto: 100% Épico",
        "👨‍🏫 El profesor de guardia mirando la tele",
        "⚡ Nivel de arte: Monumental",
        "🐱 Gato detectado en el aula 3B",
        "📻 Portada de disco que pondrían en el recreo",
        "🧪 Experimento de física cuántica",
        "🍕 Cuando la cafetería tiene pizza",
        "💡 Idea millonaria del trabajo en grupo",
        "🕹️ Pantalla de espera en 4K Ultra HD",
        "🔥 Esto no se recorta: Full 100% Pantalla",
        "🛋️ Sofá de sala de profesores activado",
        "🎓 Aprobado general en estética visual",
        "☕ Nivel de cafeína: Desbordado"
    )

    private val funnyReactions = listOf(
        "¡Una obra maestra!",
        "¿Quién aprobó esto?",
        "Fondo legendario para la eternidad",
        "10/10 en el examen de arte",
        "Digno de estar en el pasillo central",
        "Arte contemporáneo puro",
        "Inexplicable pero maravilloso",
        "Esto merece una matrícula de honor",
        "Directo al salón de la fama del instituto"
    )

    private val wallpaperHumorDescriptions = listOf(
        "🖼️ Transforma tu pantalla en una portada de museo sin recortar un solo milímetro",
        "🚀 Tu fondo ahora tiene 1000% más estilo y cero píxeles amputados",
        "🎨 El marco de color aleatorio disimula cualquier pantalla panorámica",
        "🛡️ Blindado contra recortes feos: El arte se respeta tal como nació",
        "🎸 Si esto fuera un disco de vinilo, ya estaría agotado en preventa",
        "🌟 Listo para presumir de fondo ante toda la clase o en el televisor"
    )

    private val topModeHumorDescriptions = listOf(
        "🏆 Top (Votos): Las portadas bendecidas por miles de internautas con buen gusto",
        "🔥 Top: Fotos con más karma positivo que el estudiante de primera fila",
        "⭐ Top: Lo más aclamado de la historia de los subreddits activos",
        "🥇 Top: Obras maestras aprobadas unánimemente por el tribunal de Reddit"
    )

    private val newModeHumorDescriptions = listOf(
        "⏱️ New (Cronológico): Recién salidas del horno de Reddit hace 5 minutos",
        "⚡ New: Sé el primero en ver esta maravilla antes de que sea viral",
        "🥖 New: Más frescas que el pan de la panadería a las 7 de la mañana",
        "📡 New: Cronología en tiempo real sin filtros de popularidad previa"
    )

    private val cacheHumorDescriptions = listOf(
        "💾 Modo Búnker: Guardada en tu dispositivo para cuando se caiga el WiFi del instituto",
        "⚡ Memoria local ultrarrápida: Cambio de fondo a la velocidad de la luz",
        "🛡️ Cero consumo de datos móviles: Esta joya ya duerme en tu memoria",
        "📦 Empaquetada y protegida en el disco local contra cortes de conexión"
    )

    private val subredditHumorTips = listOf(
        "💡 Puedes escribir varios subreddits separados por comas (ej: albumartPorn, vinyl, hmmm)",
        "🙈 ¿Un subreddit se puso raro hoy? Desactiva el interruptor para ocultarlo temporalmente",
        "🗑️ Elimina los que no te gusten y quédate solo con la crème de la crème",
        "🎶 Mezcla portadas de discos con gatos borrosos para una experiencia surrealista"
    )

    fun getHumorousBadge(post: RedditPost): String {
        val index = abs((post.id + post.subreddit).hashCode()) % instituteHumorTags.size
        return instituteHumorTags[index]
    }

    fun getHumorousReaction(post: RedditPost): String {
        val index = abs(post.id.hashCode()) % funnyReactions.size
        return funnyReactions[index]
    }

    fun getHumorousWallpaperDescription(postId: String = ""): String {
        val seed = if (postId.isNotBlank()) abs(postId.hashCode()) else Random.nextInt(1000)
        return wallpaperHumorDescriptions[seed % wallpaperHumorDescriptions.size]
    }

    fun getHumorousSortDescription(isTop: Boolean, seedKey: String = ""): String {
        val list = if (isTop) topModeHumorDescriptions else newModeHumorDescriptions
        val index = if (seedKey.isNotBlank()) abs(seedKey.hashCode()) % list.size else Random.nextInt(list.size)
        return list[index]
    }

    fun getHumorousCacheDescription(seedKey: String = ""): String {
        val index = if (seedKey.isNotBlank()) abs(seedKey.hashCode()) % cacheHumorDescriptions.size else Random.nextInt(cacheHumorDescriptions.size)
        return cacheHumorDescriptions[index]
    }

    fun getHumorousSubredditTip(): String {
        return subredditHumorTips[Random.nextInt(subredditHumorTips.size)]
    }

    fun getHumorousAutoplayCaption(isPlaying: Boolean): String {
        return if (isPlaying) {
            "🎬 Proyector activo: Cambiando fotos automáticamente como en una expo de arte"
        } else {
            "⏸️ Proyector en pausa: Apreciando los detalles con calma infinita"
        }
    }
}
