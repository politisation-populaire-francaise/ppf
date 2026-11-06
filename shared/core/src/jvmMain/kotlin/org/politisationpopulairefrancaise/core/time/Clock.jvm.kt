package org.politisationpopulairefrancaise.core.time

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
