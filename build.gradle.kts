plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.android.gms.oss-licenses-plugin") version "0.10.10" apply false
}

// ---------------------------------------------------------------------------------------------
// AdMob ID: поділ тестових і бойових (docs/release/ADMOB_SETUP.md, DECISIONS.md 2026-11-18).
//  * debug завжди з тестовими ID Google (ca-app-pub-3940256099942544/...).
//  * release бере бойові ID ЛИШЕ з локального admob.properties (у .gitignore, не в репозиторії).
//  * Немає admob.properties: release збирається з ТЕСТОВИМИ ID і голосним попередженням.
//  * -Pproduction=true: release без повних бойових ID (або з тестовими) ПАДАЄ.
//  * admob.properties є, але неповний чи з хибним форматом: збірка падає завжди.
// Результат кладемо в rootProject.extra["admob"] (Map<String,String>), читають :core-ads і :app.
// ---------------------------------------------------------------------------------------------
run {
    val testPublisher = "ca-app-pub-3940256099942544"
    val test = mapOf(
        "appId" to "$testPublisher~3347511713",
        "banner" to "$testPublisher/6300978111",
        "interstitial" to "$testPublisher/1033173712"
    )
    val production = (findProperty("production") as String?)?.equals("true", ignoreCase = true) == true
    val file = rootProject.file("admob.properties")
    val appRe = Regex("""^ca-app-pub-\d{16}~\d{10}$""")
    val unitRe = Regex("""^ca-app-pub-\d{16}/\d{10}$""")

    fun fail(msg: String): Nothing = throw GradleException("AdMob: $msg")

    val release: Map<String, String>
    val releaseIsTest: Boolean
    if (!file.isFile) {
        if (production) {
            fail("-Pproduction=true, але немає admob.properties у корені проєкту. " +
                "Скопіюй admob.properties.example у admob.properties і впиши свої ID (docs/release/ADMOB_SETUP.md).")
        }
        release = test
        releaseIsTest = true
        // Попередження лише коли запитано збірку релізу/пакета (не заважає debug і синхронізації IDE)
        if (gradle.startParameter.taskNames.any { Regex("(?i)release|bundle|^build$|assemble$").containsMatchIn(it.substringAfterLast(':')) }) logger.warn(
            "\n############################################################################\n" +
            "# УВАГА (AdMob): admob.properties не знайдено. RELEASE збирається з ТЕСТОВИМИ\n" +
            "# ID Google, реклама не приноситиме доходу. Для бойової збірки: див.\n" +
            "# docs/release/ADMOB_SETUP.md та -Pproduction=true.\n" +
            "############################################################################"
        )
    } else {
        val p = java.util.Properties().apply { file.inputStream().use { load(it) } }
        fun v(k: String) = p.getProperty(k)?.trim().orEmpty()
        val app = v("admob.appId")
        val banner = v("admob.bannerAdUnitId")
        val inter = v("admob.interstitialAdUnitId")
        if (app.isEmpty() || banner.isEmpty() || inter.isEmpty()) {
            fail("admob.properties неповний: потрібні admob.appId, admob.bannerAdUnitId, admob.interstitialAdUnitId.")
        }
        if (!appRe.matches(app)) fail("admob.appId має вигляд ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY (з тильдою ~).")
        if (!unitRe.matches(banner)) fail("admob.bannerAdUnitId має вигляд ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY (зі слешем /).")
        if (!unitRe.matches(inter)) fail("admob.interstitialAdUnitId має вигляд ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY (зі слешем /).")
        val pub = app.substringBefore('~')
        if (banner.substringBefore('/') != pub || inter.substringBefore('/') != pub) {
            fail("ID застосунку і блоків оголошень належать різним видавцям (частина до ~ і / має збігатися).")
        }
        releaseIsTest = pub == testPublisher
        if (releaseIsTest && production) fail("-Pproduction=true, але в admob.properties тестові ID Google.")
        release = mapOf("appId" to app, "banner" to banner, "interstitial" to inter)
    }
    rootProject.extra["admob"] = mapOf(
        "testAppId" to test.getValue("appId"),
        "testBanner" to test.getValue("banner"),
        "testInterstitial" to test.getValue("interstitial"),
        "releaseAppId" to release.getValue("appId"),
        "releaseBanner" to release.getValue("banner"),
        "releaseInterstitial" to release.getValue("interstitial"),
        "releaseIsTest" to releaseIsTest.toString(),
        "testPublisher" to testPublisher
    )
}
