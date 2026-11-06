package org.politisationpopulairefrancaise.ui.messages

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMTForDate

internal actual fun localUtcOffsetSeconds(epochSeconds: Long): Int =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())).toInt()
