package org.politisationpopulairefrancaise.core

import platform.UIKit.UIDevice

actual fun currentPlatform(): PlatformInfo =
    PlatformInfo(
        name = UIDevice.currentDevice.systemName,
        version = UIDevice.currentDevice.systemVersion,
    )
