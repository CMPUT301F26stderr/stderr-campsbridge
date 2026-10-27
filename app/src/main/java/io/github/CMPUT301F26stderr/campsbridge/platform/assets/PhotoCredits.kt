package io.github.CMPUT301F26stderr.campsbridge.platform.assets

import android.content.res.AssetManager
import org.json.JSONObject

/** Named scenery bundled for welcome, gallery and sample campsite content.
 * @param id Shared catalog identity and JPEG filename stem.
 */
enum class SceneryPhoto(
    val id: String,
) {
    JASPER("jasper"),
    BANFF("banff"),
    KANANASKIS("kananaskis"),
    ;

    val assetPath: String get() = "photos/$id.jpg"
    val reference: String get() = "file:///android_asset/$assetPath"
}

/** One photographer's original work; display crops do not change its credit.
 * @param id Shared filename stem.
 * @param title Photographed location.
 * @param photographer Creator's display name.
 * @param photographerUrl Creator's original profile.
 * @param sourceUrl Original photo page.
 */
data class PhotoCredit(
    val id: String,
    val title: String,
    val photographer: String,
    val photographerUrl: String,
    val sourceUrl: String,
)

/** Packaged photography credits shared with the prototype.
 * @param licenseName Published license name.
 * @param licenseUrl Original license terms.
 * @param photos Every bundled scenery photograph.
 */
data class PhotoCredits(
    val licenseName: String,
    val licenseUrl: String,
    val photos: List<PhotoCredit>,
)

/** Reads the current checked-in catalog without network access or legacy field defaults.
 * @param assets Application's merged packaged assets.
 */
fun photoCredits(assets: AssetManager): PhotoCredits {
    val catalog =
        JSONObject(assets.open("photo-credits.json").bufferedReader().use { it.readText() })
    val photos = catalog.getJSONArray("photos")
    return PhotoCredits(
        catalog.getString("licenseName"),
        catalog.getString("licenseUrl"),
        List(photos.length()) { index ->
            val photo = photos.getJSONObject(index)
            PhotoCredit(
                photo.getString("id"),
                photo.getString("title"),
                photo.getString("photographer"),
                photo.getString("photographerUrl"),
                photo.getString("sourceUrl"),
            )
        },
    )
}
