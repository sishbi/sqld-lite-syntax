package sishbi.sqldlite

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object SqldLiteFileType : LanguageFileType(SqldLiteLanguage) {
    override fun getName() = "SqldLite"

    override fun getDescription() = SqldLiteMessageBundle.message("filetype.sqldlite.description")

    override fun getDefaultExtension() = "sq"

    override fun getIcon(): Icon = SqldLiteIcons.FILE
}
