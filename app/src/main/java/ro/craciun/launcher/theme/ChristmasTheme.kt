package ro.craciun.launcher.theme

/**
 * O variantă de temă de Crăciun. Toate culorile sunt ARGB.
 * Scena (cer, dealuri, brazi, case, efecte) se construiește în ChristmasRenderer pe baza acestor valori.
 */
class ChristmasTheme(
    val id: String,
    val name: String,
    val tagline: String,
    val skyTop: Int,
    val skyMid: Int,
    val skyBottom: Int,
    val showMoon: Boolean,
    val moonColor: Int,
    val moonScale: Float,
    val aurora: Boolean,
    val auroraColors: IntArray,
    val farHill: Int,
    val midHill: Int,
    val ground: Int,
    val groundShadow: Int,
    val treeDark: Int,
    val treeLight: Int,
    val pineCount: Int,
    val houses: Int,
    val church: Boolean,
    val snowman: Boolean,
    val bokeh: Boolean,
    val lightColors: IntArray,
    val ornamentColors: IntArray,
    val windowColor: Int,
    val accent: Int,
    val panel: Int,
    /** La câte secunde (aprox.) trece sania pe cer. 0 = niciodată. */
    val sleighEverySec: Float
)

private fun c(hex: Long): Int = hex.toInt()
private fun colors(vararg hex: Long): IntArray = IntArray(hex.size) { hex[it].toInt() }

object ThemeCatalog {

    val NOAPTE_SFANTA = ChristmasTheme(
        id = "noapte_sfanta",
        name = "Noapte Sfântă",
        tagline = "Lună plină, brad împodobit și om de zăpadă",
        skyTop = c(0xFF040818), skyMid = c(0xFF101C46), skyBottom = c(0xFF2D3F74),
        showMoon = true, moonColor = c(0xFFFFF3D1), moonScale = 1.2f,
        aurora = false, auroraColors = IntArray(0),
        farHill = c(0xFF8EA3CC), midHill = c(0xFFB8C7E6), ground = c(0xFFEAF1FB), groundShadow = c(0xFFAFC1DF),
        treeDark = c(0xFF0B3524), treeLight = c(0xFF1E6E45),
        pineCount = 10, houses = 3, church = false, snowman = true, bokeh = false,
        lightColors = colors(0xFFFF3B3B, 0xFFFFC93C, 0xFF39E07A, 0xFF4FA8FF, 0xFFFFF4DA),
        ornamentColors = colors(0xFFC8102E, 0xFFF2C14E, 0xFF1F5FB0, 0xFFDDE3EE, 0xFF2E8B57),
        windowColor = c(0xFFFFC46B), accent = c(0xFFE63946), panel = c(0xE00A1230),
        sleighEverySec = 28f
    )

    val PADUREA_DE_ARGINT = ChristmasTheme(
        id = "padurea_de_argint",
        name = "Pădurea de Argint",
        tagline = "Aurora boreală peste brazii înzăpeziți",
        skyTop = c(0xFF010D16), skyMid = c(0xFF07303F), skyBottom = c(0xFF2A6577),
        showMoon = true, moonColor = c(0xFFE6F6FF), moonScale = 0.9f,
        aurora = true, auroraColors = colors(0x9932F5A0, 0x7730C8FF, 0x55A86BFF),
        farHill = c(0xFF7FA6B6), midHill = c(0xFFA9CAD6), ground = c(0xFFEAF6FA), groundShadow = c(0xFFA2C2D2),
        treeDark = c(0xFF06281F), treeLight = c(0xFF15523F),
        pineCount = 24, houses = 1, church = false, snowman = false, bokeh = false,
        lightColors = colors(0xFFE9FBFF, 0xFF8EDCFF, 0xFFB9F3FF, 0xFFFFFFFF),
        ornamentColors = colors(0xFFC9D6E3, 0xFF5AA9D6, 0xFF2D5F8A, 0xFFE8F4FF),
        windowColor = c(0xFFFFD08A), accent = c(0xFF6FD3FF), panel = c(0xE0032029),
        sleighEverySec = 45f
    )

    val LUMINI_DE_AUR = ChristmasTheme(
        id = "lumini_de_aur",
        name = "Lumini de Aur",
        tagline = "Elegant: aur, șampanie și lumini calde",
        skyTop = c(0xFF080305), skyMid = c(0xFF24090F), skyBottom = c(0xFF4E1A20),
        showMoon = false, moonColor = c(0xFFFFE9B0), moonScale = 1f,
        aurora = false, auroraColors = IntArray(0),
        farHill = c(0xFFB08A86), midHill = c(0xFFD8BFB4), ground = c(0xFFF6EBE2), groundShadow = c(0xFFCDB3A8),
        treeDark = c(0xFF0D2E22), treeLight = c(0xFF235E43),
        pineCount = 6, houses = 0, church = false, snowman = false, bokeh = true,
        lightColors = colors(0xFFFFD27A, 0xFFFFB347, 0xFFFFF1C1, 0xFFFFE08A),
        ornamentColors = colors(0xFFD4A537, 0xFFF3E0B0, 0xFF8E1B2B, 0xFFB8860B),
        windowColor = c(0xFFFFC46B), accent = c(0xFFFFC857), panel = c(0xE0180609),
        sleighEverySec = 0f
    )

    val SATUL_DE_POVESTE = ChristmasTheme(
        id = "satul_de_poveste",
        name = "Satul de Poveste",
        tagline = "Amurg de iarnă, case luminate și biserica satului",
        skyTop = c(0xFF160E2E), skyMid = c(0xFF45275E), skyBottom = c(0xFFC97A78),
        showMoon = true, moonColor = c(0xFFFFF0DC), moonScale = 0.8f,
        aurora = false, auroraColors = IntArray(0),
        farHill = c(0xFF9C88B0), midHill = c(0xFFC7B6D3), ground = c(0xFFF3EDF6), groundShadow = c(0xFFC2B3D1),
        treeDark = c(0xFF0E3326), treeLight = c(0xFF22694A),
        pineCount = 9, houses = 6, church = true, snowman = true, bokeh = false,
        lightColors = colors(0xFFFFB04D, 0xFFFF5E5E, 0xFF6BE08A, 0xFFFFE47A, 0xFF7FB2FF),
        ornamentColors = colors(0xFFB3122E, 0xFFF0B429, 0xFF2F6E3B, 0xFFE6E6EA),
        windowColor = c(0xFFFFB04D), accent = c(0xFFFFB04D), panel = c(0xE01B1030),
        sleighEverySec = 32f
    )

    val SANIA_LUI_MOS_CRACIUN = ChristmasTheme(
        id = "sania_mos_craciun",
        name = "Sania lui Moș Crăciun",
        tagline = "Lună uriașă, sania zboară des, lumini roșu-alb",
        skyTop = c(0xFF070B26), skyMid = c(0xFF18256A), skyBottom = c(0xFF4C64A8),
        showMoon = true, moonColor = c(0xFFFFF8E6), moonScale = 2.0f,
        aurora = false, auroraColors = IntArray(0),
        farHill = c(0xFF90A4D2), midHill = c(0xFFBCCAEA), ground = c(0xFFEFF4FD), groundShadow = c(0xFFB4C4E4),
        treeDark = c(0xFF0B3524), treeLight = c(0xFF1E6E45),
        pineCount = 12, houses = 2, church = false, snowman = true, bokeh = false,
        lightColors = colors(0xFFFF2E3F, 0xFFFFFFFF, 0xFFFF2E3F, 0xFFFFE07A),
        ornamentColors = colors(0xFFD0102A, 0xFFF4F4F8, 0xFFE8B931, 0xFF1A7A45),
        windowColor = c(0xFFFFC46B), accent = c(0xFFFF4D5E), panel = c(0xE00C1240),
        sleighEverySec = 10f
    )

    val ALL: List<ChristmasTheme> = listOf(
        NOAPTE_SFANTA, PADUREA_DE_ARGINT, LUMINI_DE_AUR, SATUL_DE_POVESTE, SANIA_LUI_MOS_CRACIUN
    )

    val DEFAULT: ChristmasTheme get() = NOAPTE_SFANTA

    fun byId(id: String?): ChristmasTheme = ALL.firstOrNull { it.id == id } ?: NOAPTE_SFANTA
}
