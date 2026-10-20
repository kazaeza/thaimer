package com.thaimer.bfour.core

/** Ready-made targets. Temperatures are final core temperatures in °C. */
data class Preset(val label: String, val celsius: Int)

data class PresetGroup(val title: String, val presets: List<Preset>)

object Presets {
    val groups = listOf(
        PresetGroup(
            "Говядина, стейк",
            listOf(
                Preset("Rare", 52),
                Preset("Medium rare", 57),
                Preset("Medium", 63),
                Preset("Medium well", 68),
                Preset("Well done", 71),
            )
        ),
        PresetGroup(
            "Баранина",
            listOf(
                Preset("Medium rare", 57),
                Preset("Medium", 63),
                Preset("Well done", 71),
            )
        ),
        PresetGroup(
            "Свинина",
            listOf(
                Preset("Сочная", 63),
                Preset("Прожаренная", 71),
            )
        ),
        PresetGroup(
            "Птица",
            listOf(
                Preset("Курица / индейка", 74),
                Preset("Бедро", 80),
                Preset("Утиная грудка", 57),
            )
        ),
        PresetGroup(
            "Рыба",
            listOf(
                Preset("Лосось", 50),
                Preset("Рыба", 60),
            )
        ),
        PresetGroup(
            "Долгое копчение",
            listOf(
                Preset("Фарш / бургер", 71),
                Preset("Брискет", 95),
                Preset("Pulled pork", 95),
            )
        ),
    )

    const val MIN_C = 20
    const val MAX_C = 100
}
