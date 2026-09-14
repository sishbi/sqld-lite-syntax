package sishbi.sqd

import com.intellij.lang.Language

/** The language of `.sq` query files and `.sqm` migration files. */
object SqdCodeLanguage : Language("SqdCode") {
    /**
     * [Language] is serializable, and without this, deserialising makes a second instance that no
     * identity comparison in the platform matches. Called by Java serialization, never by name.
     */
    @Suppress("unused")
    private fun readResolve(): Any = SqdCodeLanguage
}
