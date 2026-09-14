package sishbi.sqd

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object SqdCodeFileType : LanguageFileType(SqdCodeLanguage) {
    override fun getName() = "SqdCode"

    override fun getDescription() = SqdCodeMessageBundle.message("filetype.sqdcode.description")

    override fun getDefaultExtension() = "sq"

    override fun getIcon(): Icon = SqdCodeIcons.FILE
}
