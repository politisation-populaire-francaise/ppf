package org.politisationpopulairefrancaise.ui.messages

import java.util.TimeZone

internal actual fun localUtcOffsetSeconds(epochSeconds: Long): Int = TimeZone.getDefault().getOffset(epochSeconds * 1000) / 1000
