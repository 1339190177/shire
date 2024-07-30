package com.phodal.shirecore.provider.shire

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

enum class ShireQLDataType(val dataKey: String) {
    GIT_COMMIT("GitCommit"),
    GIT_BRANCH("GitBranch"),
    GIT_FILE_COMMIT("GitFileCommit"),
    GIT_FILE_BRANCH("GitFileBranch")
}

interface ShireQLDataProvider {
    fun lookupGitData(myProject: Project, dataTypes: List<ShireQLDataType>): Map<ShireQLDataType, Any>

    companion object {
        private val EP_NAME: ExtensionPointName<ShireQLDataProvider> =
            ExtensionPointName("com.phodal.shireQLDataProvider")

        fun all(): List<ShireQLDataProvider> {
            return EP_NAME.extensionList
        }
    }
}
