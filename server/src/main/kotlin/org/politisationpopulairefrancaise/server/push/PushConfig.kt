package org.politisationpopulairefrancaise.server.push

import java.net.URI
import java.nio.file.Path

/**
 * Settings of the push wake-ups (M31, docs/integration/notifications.md, sections 3 and 4), read
 * from the environment only:
 *
 * - PPF_VAPID_PRIVATE_KEY_FILE: the VAPID private key (EC P-256, PKCS#8 PEM), outside the repository,
 *   one per environment: `/run/secrets/vapid_private_key` on the server. Without it, push is off:
 *   `GET /api/v1/push/vapid` and `POST /api/v1/push/subscriptions` answer `503 push_disabled`.
 * - PPF_PUSH_ALLOWED_HOSTS: comma-separated host names a push endpoint may use (default: `push.`
 *   followed by the host of PPF_PUBLIC_URL, that is our ntfy only): `push.politisation-populaire-francaise.org`
 *   in production, `push.preprod.politisation-populaire-francaise.org` in preproduction. Any other host,
 *   and any path of an allowed host other than a UnifiedPush topic (`/up<random>`, the only topics of our
 *   ntfy), is refused with `422 push_host_not_allowed`, so that the server cannot be made to contact
 *   arbitrary addresses.
 * - PPF_PUSH_INTERNAL_URL: base URL through which the server reaches our ntfy on the internal network
 *   of the containers: `http://ntfy:8080`. When set, a push to an allowed host goes to this
 *   base, keeping the path and query of the endpoint, never through the public name.
 * - PPF_PUBLIC_URL: the https address of the site (default https://politisation-populaire-francaise.org),
 *   the `sub` claim of the VAPID token (never an e-mail address) and the base of the URL that the
 *   NIP-98 authorization of a mailbox subscription must name.
 * - PPF_ENVIRONMENT: in "development" only (the default), http endpoints and an http public URL are accepted.
 */
data class PushConfig(
    val vapid: VapidKeys?,
    val allowedHosts: Set<String>,
    val internalUrl: URI? = null,
    val publicUrl: String = DEFAULT_PUBLIC_URL,
    val allowHttpEndpoints: Boolean = false,
) {
    val enabled: Boolean get() = vapid != null

    /** The absolute URL a NIP-98 authorization must name in its `u` tag. */
    val subscriptionsUrl: String get() = publicUrl.trimEnd('/') + SUBSCRIPTIONS_PATH

    companion object {
        const val VAPID_KEY_FILE_VARIABLE: String = "PPF_VAPID_PRIVATE_KEY_FILE"
        const val ALLOWED_HOSTS_VARIABLE: String = "PPF_PUSH_ALLOWED_HOSTS"
        const val INTERNAL_URL_VARIABLE: String = "PPF_PUSH_INTERNAL_URL"
        const val PUBLIC_URL_VARIABLE: String = "PPF_PUBLIC_URL"
        const val DEFAULT_PUBLIC_URL: String = "https://politisation-populaire-francaise.org"
        const val SUBSCRIPTIONS_PATH: String = "/api/v1/push/subscriptions"

        /** Push off; used when nothing is configured and by the tests that do not need it. */
        val DISABLED: PushConfig = PushConfig(vapid = null, allowedHosts = emptySet())

        fun fromEnvironment(env: (String) -> String? = System::getenv): PushConfig {
            val development = (env("PPF_ENVIRONMENT") ?: "development") == "development"
            val publicUrl = env(PUBLIC_URL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/') ?: DEFAULT_PUBLIC_URL
            val publicUri = runCatching { URI(publicUrl) }.getOrNull()
            require(publicUri != null && publicUri.host != null && publicUri.rawUserInfo == null && publicUri.rawQuery == null) {
                "$PUBLIC_URL_VARIABLE is not a valid URL"
            }
            require(publicUri.scheme == "https" || (development && publicUri.scheme == "http")) {
                "$PUBLIC_URL_VARIABLE must use https"
            }
            val hosts = env(ALLOWED_HOSTS_VARIABLE)?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf("push." + publicUri.host.lowercase())
            val internal = env(INTERNAL_URL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                val uri = runCatching { URI(raw.trimEnd('/')) }.getOrNull()
                require(uri != null && (uri.scheme == "http" || uri.scheme == "https") && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null) {
                    "$INTERNAL_URL_VARIABLE must be an http(s) base URL without credentials or query"
                }
                uri
            }
            val vapid = env(VAPID_KEY_FILE_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { file ->
                // A configured but unreadable key is an error of the deployment: better not to start.
                VapidKeys.fromFile(Path.of(file))
            }
            return PushConfig(vapid, hosts, internal, publicUrl, allowHttpEndpoints = development)
        }
    }
}
