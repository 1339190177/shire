package com.phodal.shirelang.compiler.hobbit.ast

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.phodal.shirecore.middleware.PostCodeHandleContext

data class Condition(
    val conditionKey: String,
    val valueExpression: FrontMatterType.EXPRESSION,
)

sealed class Task(open val expression: FrontMatterType.EXPRESSION?) {
    class Default(override val expression: FrontMatterType.EXPRESSION?) : Task(expression)
    class CustomTask(override val expression: FrontMatterType.EXPRESSION?) : Task(expression)
}

data class Case(
    val caseKey: String,
    val valueExpression: Task,
)

data class TaskRoutes(
    val conditions: List<Condition>,
    val cases: List<Case>,
    val defaultTask: Task,
) {
    companion object {
        fun from(expression: FrontMatterType.ARRAY): TaskRoutes? {
            val arrays = expression.value as List<FrontMatterType>
            val taskRoutes = arrays.filterIsInstance<FrontMatterType.EXPRESSION>()
                .filter {
                    it.value is ConditionCase
                }
                .map { caseExpr ->
                    val conditionCase = caseExpr.value as ConditionCase
                    val conditions: List<Condition> = conditionCase.conditions.map {
                        val caseKeyValue = it.value as CaseKeyValue

                        Condition(
                            caseKeyValue.key.display(),
                            caseKeyValue.value as FrontMatterType.EXPRESSION
                        )
                    }

                    val cases: List<Case> = conditionCase.cases.map {
                        val caseKeyValue = it.value as CaseKeyValue
                        Case(
                            caseKeyValue.key.display(),
                            Task.Default(caseKeyValue.value as FrontMatterType.EXPRESSION)
                        )
                    }

                    TaskRoutes(
                        conditions = conditions,
                        cases = cases,
                        defaultTask = Task.Default(null)
                    )
                }

            return taskRoutes.firstOrNull()
        }
    }
}
