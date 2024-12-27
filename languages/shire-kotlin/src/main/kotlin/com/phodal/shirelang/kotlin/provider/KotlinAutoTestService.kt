package com.phodal.shirelang.kotlin.provider

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.phodal.shirecore.provider.TestingService
import com.phodal.shirecore.provider.codemodel.ClassStructureProvider
import com.phodal.shirecore.provider.codemodel.FileStructureProvider
import com.phodal.shirecore.provider.codemodel.model.ClassStructure
import com.phodal.shirecore.variable.toolchain.unittest.AutoTestingPromptContext
import com.phodal.shirelang.kotlin.KotlinPsiUtil
import com.phodal.shirelang.kotlin.KotlinTypeResolver
import com.phodal.shirelang.kotlin.getReturnTypeReferences
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.io.File

class KotlinAutoTestService : TestingService() {
    override fun isApplicable(element: PsiElement): Boolean = element.language is KotlinLanguage
    override fun runConfigurationClass(project: Project): Class<out RunProfile> = GradleRunConfiguration::class.java
    override fun isApplicable(project: Project, file: VirtualFile): Boolean {
        return (file.extension == "kt" || file.extension == "kts") && PsiManager.getInstance(project)
            .findFile(file) is KotlinLanguage
    }

    override fun findOrCreateTestFile(
        sourceFile: PsiFile,
        project: Project,
        psiElement: PsiElement,
    ): AutoTestingPromptContext? {
        val sourceFilePath = sourceFile.virtualFile
        val parentDir = sourceFilePath.parent
        val className = sourceFile.name.replace(".kt", "") + "Test"

        val parentDirPath = ReadAction.compute<String, Throwable> {
            parentDir?.path
        } ?: return null

        val relatedModels = lookupRelevantClass(project, psiElement).distinctBy { it.name }

        if (!(parentDirPath.contains("/main/java") || parentDirPath.contains("/main/kotlin"))) {
            log.error("SourceFile is not under the main/kotlin or main/java directory: $parentDirPath")
            return null
        }

        var isNewFile = false

        val testDirPath = parentDir.path
            .replace("/main/kotlin/", "/test/kotlin/")
            .replace("/main/java/", "/test/java/")

        var testDir = LocalFileSystem.getInstance().findFileByPath(testDirPath)

        if (testDir == null || !testDir.isDirectory) {
            isNewFile = true
            // Create the test directory if it doesn't exist
            val testDirFile = File(testDirPath)
            if (!testDirFile.exists()) {
                testDirFile.mkdirs()

                LocalFileSystem.getInstance().refreshAndFindFileByPath(testDirPath)?.let { refreshedDir ->
                    testDir = refreshedDir
                }
            }
        }

        val testDirCreated: VirtualFile? =
            VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://$testDirPath")
        if (testDirCreated == null) {
            log.error("Failed to create test directory: $testDirPath")
            return null
        }

        // Test directory already exists, find the corresponding test file
        val testFilePath = testDirPath + "/" + sourceFile.name.replace(".kt", "Test.kt")
        val testFile = LocalFileSystem.getInstance().findFileByPath(testFilePath)

        project.guessProjectDir()?.refresh(true, true)

        val currentClass: String = ReadAction.compute<String, Throwable> {
            val classContext = when (psiElement) {
                is KtFile -> FileStructureProvider.from(psiElement)
                is KtClassOrObject -> ClassStructureProvider.from(psiElement)
                is KtNamedFunction -> {
                    PsiTreeUtil.getParentOfType(psiElement, KtClassOrObject::class.java)?.let {
                        ClassStructureProvider.from(it)
                    }
                }

                else -> null
            }

            return@compute classContext?.format() ?: ""
        }

        val imports: List<String> = runReadAction {
            (sourceFile as KtFile).importList?.imports?.map { it.text } ?: emptyList()
        }

        val relatedCode = relatedModels.map { it.format() }

        return if (testFile != null) {
            AutoTestingPromptContext(isNewFile, testFile, relatedCode, className, sourceFile.language, currentClass, imports)
        } else {
            val targetFile = createTestFile(sourceFile, testDir!!, project)
            AutoTestingPromptContext(isNewFile = true, targetFile, relatedCode, "", sourceFile.language, currentClass, imports)
        }
    }

    override fun lookupRelevantClass(project: Project, element: PsiElement): List<ClassStructure> {
        return ReadAction.compute<List<ClassStructure>, Throwable> {
            val elements = mutableListOf<ClassStructure>()
            val projectPath = project.guessProjectDir()?.path

            val resolvedClasses = KotlinTypeResolver.resolveByElement(element)

            // find the class in the same project
            resolvedClasses.forEach { (_, psiClass) ->
                val classPath = psiClass?.containingFile?.virtualFile?.path
                if (classPath?.contains(projectPath!!) == true) {
                    elements += ClassStructureProvider.from(psiClass) ?: return@forEach
                }
            }

            elements
        }
    }

    private fun createTestFile(
        sourceFile: PsiFile,
        testDir: VirtualFile,
        project: Project
    ): VirtualFile {
        val sourceFileName = sourceFile.name
        val testFileName = sourceFileName.replace(".kt", "Test.kt")
        val testFileContent = ""

        return WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
            val testFile = testDir.createChildData(this, testFileName)

            val document = FileDocumentManager.getInstance().getDocument(testFile)
            document?.setText(testFileContent)
            testFile
        }
    }
}
