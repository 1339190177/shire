package com.phodal.shirelang.java.impl

import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.phodal.shirecore.buildsystem.BuildSystemContext
import com.phodal.shirecore.provider.context.BuildSystemProvider
import com.phodal.shirelang.java.toolchain.JavaTasksUtil
import com.phodal.shirelang.java.toolchain.JavaLanguageDetector
import org.jetbrains.plugins.gradle.util.GradleConstants

open class JavaBuildSystemProvider : BuildSystemProvider() {
    override fun collect(project: Project): BuildSystemContext {
        val projectDataManager = ProjectDataManager.getInstance()
        val buildToolName: String
        var taskString = ""

        val gradleInfo = projectDataManager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        if (gradleInfo.isNotEmpty()) {
            buildToolName = "Gradle"
            taskString = JavaTasksUtil.collectGradleTasks(project).joinToString(" ") { it.text }
        } else {
            buildToolName = "Maven"
        }

        val javaVersion = JavaLanguageDetector.detectLanguageLevel(project, null)

        return BuildSystemContext(
            buildToolName = buildToolName,
            buildToolVersion = "",
            languageName = "Java",
            languageVersion = "$javaVersion",
            taskString = taskString
        )
    }

}

val GRADLE_COMPLETION_COMPARATOR = Comparator<String> { o1, o2 ->
    when {
        o1.startsWith("--") && o2.startsWith("--") -> o1.compareTo(o2)
        o1.startsWith("-") && o2.startsWith("--") -> -1
        o1.startsWith("--") && o2.startsWith("-") -> 1
        o1.startsWith(":") && o2.startsWith(":") -> o1.compareTo(o2)
        o1.startsWith(":") && o2.startsWith("-") -> -1
        o1.startsWith("-") && o2.startsWith(":") -> 1
        o2.startsWith("-") -> -1
        o2.startsWith(":") -> -1
        o1.startsWith("-") -> 1
        o1.startsWith(":") -> 1
        else -> o1.compareTo(o2)
    }
}