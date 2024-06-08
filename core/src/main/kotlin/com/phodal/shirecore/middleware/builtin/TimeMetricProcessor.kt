package com.phodal.shirecore.middleware.builtin

import com.intellij.openapi.project.Project
import com.phodal.shirecore.middleware.BuiltinPostHandler
import com.phodal.shirecore.middleware.PostCodeHandleContext
import com.phodal.shirecore.middleware.PostProcessor

class TimeMetricProcessor : PostProcessor {
    private var startTime: Long? = null

    override val processorName: String = BuiltinPostHandler.TimeMetric.handleName

    override fun isApplicable(context: PostCodeHandleContext): Boolean {
        return true
    }

    override fun setup(context: PostCodeHandleContext): String {
        startTime = System.currentTimeMillis()
        return startTime.toString()
    }

    override fun execute(project: Project, context: PostCodeHandleContext, genText: String): String {
        val endTime = System.currentTimeMillis()
        return (endTime - startTime!!).toString()
    }

    override fun finish(context: PostCodeHandleContext): String {
        return ""
    }
}