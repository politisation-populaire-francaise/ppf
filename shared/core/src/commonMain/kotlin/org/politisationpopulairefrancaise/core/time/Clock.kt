package org.politisationpopulairefrancaise.core.time

/** Current time as seconds since 1970-01-01 UTC, the unit used by signed events (`created_at`). */
expect fun currentEpochSeconds(): Long
