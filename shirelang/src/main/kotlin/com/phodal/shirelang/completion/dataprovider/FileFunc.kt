package com.phodal.shirelang.completion.dataprovider

import com.intellij.icons.AllIcons
import javax.swing.Icon

enum class FileFunc(val funcName: String, val description: String, val icon: Icon) {
    Regex("regex", "Read the content of a file by regex", AllIcons.Actions.Regex),
    ;

    companion object {
        fun all(): List<FileFunc> {
            return entries
        }

        fun fromString(funcName: String): FileFunc? {
            return entries.find { it.funcName == funcName }
        }
    }
}