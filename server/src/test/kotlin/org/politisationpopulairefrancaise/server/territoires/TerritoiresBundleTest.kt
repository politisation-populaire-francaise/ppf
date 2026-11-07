package org.politisationpopulairefrancaise.server.territoires

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * The national level shipped with the application (shared/ui composeResources) is exactly the one
 * the server builds from its example set: the application reads it offline from the first launch,
 * and the device then recognises the packages the development server announces.
 *
 * To regenerate it after a change of the example set or of the format: delete the file, then run
 * `gradlew :server:test --tests "*TerritoiresBundleTest*"`, which writes it again; commit it.
 */
class TerritoiresBundleTest {
    private val bundled: Path = Paths.get("..", "shared", "ui", "src", "commonMain", "composeResources", "files", "territoires", "national.json")

    @Test
    fun theShippedNationalLevelIsTheOneOfTheExampleSet() {
        val built = ExampleTerritoiresSource().use { s -> TerritoiresBuilder(s.example, s.prepare()) { s.open(it) }.build() }
        if (!Files.exists(bundled)) {
            Files.createDirectories(bundled.parent)
            Files.write(bundled, built.national)
            println("Wrote ${bundled.toAbsolutePath().normalize()}: commit it.")
        }
        assertContentEquals(built.national, Files.readAllBytes(bundled), "The shipped national level differs from the example set: delete it and run this test again.")
    }
}
