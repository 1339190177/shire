package com.phodal.shirelang.java.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.*
import com.phodal.shirecore.search.algorithm.TfIdf
import com.phodal.shirecore.search.tokenizer.CodeNamingTokenizer


object JavaTestHelper {
    fun extractMethodCalls(project: Project, psiElement: PsiElement): String {
        val searchScope = GlobalSearchScope.allScope(project)

        return when (psiElement) {
            is PsiFile -> {
                PsiTreeUtil.findChildrenOfAnyType(psiElement, PsiClass::class.java)
                    .joinToString("\n") { extractMethodCalls(project, it) }
            }

            is PsiClass -> {
                PsiTreeUtil.findChildrenOfAnyType(psiElement, PsiMethod::class.java)
                    .joinToString("\n") { extractMethodCalls(project, it) }
            }

            is PsiMethod -> {
                PsiTreeUtil.findChildrenOfAnyType(psiElement.body, PsiMethodCallExpression::class.java)
                    .filter { isMethodCallFromInheritedClass(it, searchScope) }
                    .joinToString("\n") { it.text }
            }

            else -> ""
        }
    }

    private fun isMethodCallFromInheritedClass(
        callExpression: PsiMethodCallExpression,
        searchScope: GlobalSearchScope,
    ): Boolean {
        val resolvedMethod = callExpression.resolveMethod() ?: return false
        val containingClass = resolvedMethod.containingClass ?: return false

        return ClassInheritorsSearch.search(containingClass, searchScope, true).findAll().isNotEmpty()
    }

    fun searchSimilarTestCases(psiElement: PsiElement, minScore: Double = 1.0): List<PsiMethod> {
        val project = psiElement.project
        val psiMethod = psiElement as? PsiMethod ?: return emptyList()
        val methodName = psiMethod.name

        // 使用缓存机制获取所有测试方法
        val allTestMethods = getAllTestMethods(project)

        // 使用 TfIdf 进行相似度计算
        val tfIdf = TfIdf<String, List<PsiMethod>>()
        tfIdf.setTokenizer(CodeNamingTokenizer())

        allTestMethods.forEach { tfIdf.addDocument(it.name, it) }

        return tfIdf.tfidfs(methodName)
            .mapIndexedNotNull { index, measure ->
                if (measure > minScore) allTestMethods[index] else null
            }
    }

    /**
     * Retrieves all test methods from the given project.
     *
     * @param project the project from which to retrieve the test methods
     * @return a list of PsiMethod objects representing all test methods found in the project
     */
    private fun getAllTestMethods(project: Project): List<PsiMethod> {
        val cachedValue: CachedValue<List<PsiMethod>> = CachedValuesManager.getManager(project).createCachedValue {
            val testMethods = mutableListOf<PsiMethod>()
            val scope = GlobalSearchScope.projectScope(project)

            PsiShortNamesCache.getInstance(project).allClassNames
                .filter { it.contains("Test") }
                .forEach { className ->
                    PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)
                        .filter { it.containingFile.name.endsWith("Test.java") }
                        .forEach { psiClass ->
                            testMethods.addAll(psiClass.methods)
                        }
                }

            CachedValueProvider.Result.create(testMethods, PsiModificationTracker.MODIFICATION_COUNT)
        }

        return cachedValue.value
    }

    /**
     * Finds all the methods called by the given method.
     *
     * @param method the method for which callees need to be found
     * @return a list of PsiMethod objects representing the methods called by the given method
     */
    fun findCallees(project: Project, method: PsiMethod): List<String> {
        val calledMethods = mutableSetOf<PsiMethod>()
        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                calledMethods.add(expression.resolveMethod() ?: return)
            }
        })

        return calledMethods
            .mapNotNull {
                val containingClass = it.containingClass ?: return@mapNotNull null
                if (!ProjectScope.getProjectScope(project).contains(containingClass.containingFile.virtualFile)) {
                    return@mapNotNull null
                }

                "ClassName: ${containingClass.qualifiedName}\nMethodText:\n${it.text}"
            }
    }

    /**
     * Finds all the callers of a given method.
     *
     * @param method the method for which callers need to be found
     * @return a list of PsiMethod objects representing the callers of the given method
     */
    fun findCallers(project: Project, method: PsiMethod): List<String> {
        val callers: MutableList<PsiMethod> = ArrayList()

        ProgressManager.getInstance().runProcess(Runnable {
            val references = MethodReferencesSearch.search(method, method.useScope, true).findAll()
            for (reference in references) {
                PsiTreeUtil.getParentOfType(reference.element, PsiMethod::class.java)?.let {
                    callers.add(it)
                }
            }
        }, ProgressIndicatorBase())

        val psiMethods = callers.distinct()

        return psiMethods
            .mapNotNull {
                val containingClass = it.containingClass ?: return@mapNotNull null
                "ClassName: ${containingClass.qualifiedName}\nMethodText:\n${it.text}"
            }
    }
}
