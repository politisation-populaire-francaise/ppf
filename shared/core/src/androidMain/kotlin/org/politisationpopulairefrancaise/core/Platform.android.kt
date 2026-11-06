package org.politisationpopulairefrancaise.core

import android.os.Build

actual fun currentPlatform(): PlatformInfo =
    PlatformInfo(name = "Android", version = "API ${Build.VERSION.SDK_INT}")
