package com.phodal.shirecore.provider.http

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface HttpHandler {
    fun isApplicable(type: HttpHandlerType): Boolean

    fun execute(project: Project, content: String) : String?

    companion object {
        private val EP_NAME: ExtensionPointName<HttpHandler> =
            ExtensionPointName("com.phodal.shireHttpHandler")

        fun provide(type: HttpHandlerType): HttpHandler? {
            return EP_NAME.extensionList.find { it.isApplicable(type) }
        }
    }
}

enum class HttpHandlerType(val id: String) {
    CURL("cURL"),
}