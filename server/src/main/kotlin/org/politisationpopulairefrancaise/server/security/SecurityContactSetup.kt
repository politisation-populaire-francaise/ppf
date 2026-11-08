package org.politisationpopulairefrancaise.server.security

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.web.SecurityContact

/**
 * Reads how researchers reach the team about a vulnerability (S-92, `security.txt` and `/securite`):
 *
 * - PPF_PUBLIC_URL: the address of the site (`https://…`, no trailing slash); `http://localhost:<port>` in
 *   development when absent.
 * - PPF_SECURITY_NOSTR_KEY: the public key (npub or 64 hexadecimal characters) of the security contact,
 *   reached by encrypted private messages (NIP-17). Its secret key is never on a server.
 * - PPF_SECURITY_GITHUB_URL: `https://github.com/<owner>/<repository>/security/advisories/new`, once the
 *   repository is on GitHub with private vulnerability reporting turned on.
 *
 * A malformed value stops the server (a wrong contact must not be published). In production, the server
 * also refuses to start without an `https://` address or without at least one private channel: the
 * specifications ask for a published, encrypted contact before the public opening (section 5.7).
 */
object SecurityContactSetup {
    const val PUBLIC_URL_VARIABLE = "PPF_PUBLIC_URL"
    const val NOSTR_KEY_VARIABLE = "PPF_SECURITY_NOSTR_KEY"
    const val GITHUB_URL_VARIABLE = "PPF_SECURITY_GITHUB_URL"

    fun fromEnvironment(environment: String, port: Int, env: (String) -> String? = System::getenv): SecurityContact {
        val publicUrl = env(PUBLIC_URL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/')
            ?: "http://localhost:$port"
        require(publicUrl.startsWith("https://") || (environment == "development" && publicUrl.startsWith("http://localhost"))) {
            "$PUBLIC_URL_VARIABLE must be an https:// address outside development"
        }
        val nostrKey = env(NOSTR_KEY_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            runCatching { if (value.startsWith("npub1")) PublicKey.fromNpub(value) else PublicKey.fromHex(value.lowercase()) }
                .getOrElse { throw IllegalArgumentException("$NOSTR_KEY_VARIABLE is not a public key (npub or 64 hexadecimal characters)") }
        }
        val github = env(GITHUB_URL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
        val contact = SecurityContact(publicUrl = publicUrl, nostrKey = nostrKey, githubAdvisoryUrl = github)
        require(environment != "production" || contact.hasPrivateChannel) {
            "In production, set $NOSTR_KEY_VARIABLE or $GITHUB_URL_VARIABLE: security.txt needs a private contact (S-92)"
        }
        return contact
    }
}
