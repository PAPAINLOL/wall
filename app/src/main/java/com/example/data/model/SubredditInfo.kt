package com.example.data.model

data class SubredditInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val iconEmoji: String,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val category: String = "Popular"
) {
    companion object {
        val DEFAULT_SUBREDDITS = listOf(
            SubredditInfo(
                name = "albumartPorn",
                displayName = "Album Covers",
                description = "Portadas de álbumes icónicas, alta resolución y diseño",
                iconEmoji = "🎵",
                isEnabled = true,
                category = "Música & Arte"
            ),
            SubredditInfo(
                name = "blurrypicturesofcats",
                displayName = "Gatos Borrosos",
                description = "Fotos borrosas, absurdas y cómicas de felinos en movimiento",
                iconEmoji = "🐱",
                isEnabled = true,
                category = "Humor & Animales"
            ),
            SubredditInfo(
                name = "hmmm",
                displayName = "Hmmm...",
                description = "Imágenes extrañas, desconcertantes y humor sin contexto",
                iconEmoji = "🤔",
                isEnabled = true,
                category = "Humor & Extraño"
            ),
            SubredditInfo(
                name = "mildlyinteresting",
                displayName = "Curiosidades",
                description = "Fotografías de cosas ligeramente interesantes de la vida real",
                iconEmoji = "✨",
                isEnabled = true,
                category = "Descubrimiento"
            ),
            SubredditInfo(
                name = "wallpapers",
                displayName = "Fondos Clásicos",
                description = "Colección general de fondos de pantalla en alta resolución",
                iconEmoji = "🖼️",
                isEnabled = true,
                category = "Paisajes & Fondos"
            ),
            SubredditInfo(
                name = "EarthPorn",
                displayName = "Naturaleza Épica",
                description = "Fotografía de paisajes naturales y vistas majestuosas",
                iconEmoji = "🌄",
                isEnabled = false,
                category = "Paisajes & Fondos"
            ),
            SubredditInfo(
                name = "Art",
                displayName = "Arte Digital & Tradicional",
                description = "Ilustraciones, pinturas y creaciones artísticas",
                iconEmoji = "🎨",
                isEnabled = false,
                category = "Música & Arte"
            ),
            SubredditInfo(
                name = "fakealbumcovers",
                displayName = "Portadas Falsas",
                description = "Diseños de carátulas ficticias ingeniosas y divertidas",
                iconEmoji = "💿",
                isEnabled = false,
                category = "Música & Arte"
            ),
            SubredditInfo(
                name = "Cyberpunk",
                displayName = "Cyberpunk",
                description = "Luces de neón, estética futurista distópica y sci-fi",
                iconEmoji = "🌆",
                isEnabled = false,
                category = "Estilos"
            ),
            SubredditInfo(
                name = "spaceporn",
                displayName = "Espacio Profundo",
                description = "Galaxias, nebulosas y misiones de la NASA / ESA",
                iconEmoji = "🚀",
                isEnabled = false,
                category = "Descubrimiento"
            )
        )
    }
}
