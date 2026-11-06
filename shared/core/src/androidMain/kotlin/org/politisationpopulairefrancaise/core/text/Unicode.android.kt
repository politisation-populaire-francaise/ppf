package org.politisationpopulairefrancaise.core.text

import java.text.Normalizer

actual fun normalizeNfkd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
