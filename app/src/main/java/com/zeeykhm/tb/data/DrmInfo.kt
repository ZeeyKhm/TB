package com.zeeykhm.tb.data

enum class DrmScheme {
    WIDEVINE,
    CLEARKEY,
}

/**
 * DRM details read from Kodi-style `#KODIPROP` lines:
 *
 * ```
 * #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
 * #KODIPROP:inputstream.adaptive.license_key=https://license.example/widevine
 * ```
 */
data class DrmInfo(
    val scheme: DrmScheme,
    /** Widevine licence server. */
    val licenseUrl: String? = null,
    /** Extra headers sent with the Widevine licence request. */
    val licenseHeaders: Map<String, String> = emptyMap(),
    /** ClearKey licence JSON (`{"keys":[...]}`) used as the DRM response. */
    val clearKeyJson: String? = null,
)
