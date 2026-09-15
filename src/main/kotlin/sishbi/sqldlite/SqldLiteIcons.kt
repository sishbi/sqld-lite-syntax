package sishbi.sqldlite

import com.intellij.ui.IconManager

object SqldLiteIcons {
    // IconManager, not IconLoader: it is what the platform's own generated icon holders use, and it
    // registers the icon so the Remote Dev layer can resolve it. The dark variant is found by the
    // `_dark` suffix; the platform does not invert an SVG on its own.
    // The ClassLoader overload, not the Class one: the latter is deprecated on the target build.
    val FILE = IconManager.getInstance().getIcon("/icons/sqldLiteFile.svg", SqldLiteIcons::class.java.classLoader)
}
