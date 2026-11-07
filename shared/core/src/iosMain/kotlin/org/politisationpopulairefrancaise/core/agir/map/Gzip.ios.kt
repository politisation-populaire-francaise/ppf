package org.politisationpopulairefrancaise.core.agir.map

// Not available yet: the iOS application is not compiled (no Apple developer account). To be
// written with platform.zlib (inflateInit2 with 16 + MAX_WBITS) when iOS is built; until then
// compressed tiles read as missing and the map shows the list instead.

internal actual fun platformGunzip(bytes: ByteArray, maxOutput: Int): ByteArray? = null

internal actual fun platformGzip(bytes: ByteArray): ByteArray? = null
