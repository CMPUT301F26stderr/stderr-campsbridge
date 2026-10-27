package io.github.CMPUT301F26stderr.campsbridge.ui.credits

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.PhotoCredits
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.photoCredits
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Connects packaged credits, ordinary web links and Google's generated license reader.
 * @param onBack Returns to the caller's Settings.
 * @param onLicense Opens a complete bundled font or Java API license.
 */
@Composable
fun CreditsRoute(
    onBack: () -> Unit,
    onLicense: (BundledLicense) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember(context) { photoCredits(context.assets) }
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val librariesTitle = stringResource(R.string.credits_libraries)
    CreditsScreen(
        catalog,
        onBack,
        onLibraries = {
            OssLicensesMenuActivity.setActivityTitle(librariesTitle)
            OssLicensesMenuActivity.setTheme(colors, colors, typography)
            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
        },
        onLicense,
        uriHandler::openUri,
    )
}

/** Shows each asset's creator/source alongside dependency notices and font/service licenses.
 * @param catalog Packaged photography metadata.
 * @param onBack Returns to Settings.
 * @param onLibraries Opens the generated runtime dependency notices.
 * @param onLicense Opens a complete offline font or Java API license.
 * @param onOpenLink Opens the selected creator, source or license URL.
 * @param modifier Caller layout.
 */
@Composable
fun CreditsScreen(
    catalog: PhotoCredits,
    onBack: () -> Unit,
    onLibraries: () -> Unit,
    onLicense: (BundledLicense) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(R.string.credits_title), modifier, onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            NavigationRow(stringResource(R.string.credits_libraries), onLibraries)
            Text(
                stringResource(R.string.credits_additional_libraries),
                style = MaterialTheme.typography.titleMedium,
            )
            for (library in AdditionalLibraryCredits) {
                NavigationRow(
                    library.name,
                    { onOpenLink(library.sourceLicenseUrl) },
                    supportingText = library.license,
                )
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.credits_photos),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.credits_photos_body))
            TextButton({ onOpenLink(catalog.licenseUrl) }) { Text(catalog.licenseName) }
            for (photo in catalog.photos) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(photo.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.credits_photo_by, photo.photographer))
                    TextButton({ onOpenLink(photo.sourceUrl) }) {
                        Text(stringResource(R.string.credits_photo_source, photo.title))
                    }
                    TextButton({ onOpenLink(photo.photographerUrl) }) {
                        Text(stringResource(R.string.credits_photographer))
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.credits_font), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.credits_font_body))
            NavigationRow(
                stringResource(R.string.credits_font_license),
                { onLicense(BundledLicense.INTER) },
            )
            TextButton({ onOpenLink("https://rsms.me/inter/") }) {
                Text(stringResource(R.string.credits_font_source))
            }
            HorizontalDivider()
            Text(stringResource(R.string.credits_java), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.credits_java_body))
            NavigationRow(
                stringResource(R.string.credits_java_license),
                { onLicense(BundledLicense.DESUGAR) },
            )
            TextButton({ onOpenLink("https://github.com/google/desugar_jdk_libs") }) {
                Text(stringResource(R.string.credits_java_source))
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.credits_services),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.credits_services_body))
            TextButton({ onOpenLink("https://developer.android.com/studio/terms") }) {
                Text(stringResource(R.string.credits_sdk_terms))
            }
            TextButton({ onOpenLink("https://cloud.google.com/maps-platform/terms") }) {
                Text(stringResource(R.string.credits_maps_terms))
            }
            TextButton({ onOpenLink("https://firebase.google.com/terms") }) {
                Text(stringResource(R.string.credits_firebase_terms))
            }
        }
    }
}

/** Complete license texts for bundled assets outside the runtime dependency graph.
 * @param routeKey Fixed navigation identity.
 * @param title Reader heading resource.
 * @param assetPath Packaged complete license text.
 */
enum class BundledLicense(
    val routeKey: String,
    @StringRes val title: Int,
    val assetPath: String,
) {
    INTER("inter", R.string.credits_font_license, "licenses/Inter-OFL.txt"),
    DESUGAR("desugar", R.string.credits_java_license, "licenses/desugar-jdk-libs-LICENSE.txt"),
}

/** Displays complete bundled license text without a browser or network dependency.
 * @param license Exact packaged document.
 * @param onBack Returns to credits without changing settings.
 */
@Composable
fun BundledLicenseScreen(
    license: BundledLicense,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val text =
        remember(context, license) {
            context.assets
                .open(license.assetPath)
                .bufferedReader()
                .use { it.readText() }
        }
    BackHandler(onBack = onBack)
    AppScreen(stringResource(license.title), onBack = onBack) {
        Text(text, Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
    }
}

/** Previews the credits hierarchy with the actual bundled catalog and inert callbacks. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun CreditsPreview() {
    val context = LocalContext.current
    val catalog = remember(context) { photoCredits(context.assets) }
    CampsbridgeTheme { CreditsScreen(catalog, {}, {}, {}, {}) }
}
