package com.phodal.shire.database

import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.database.console.evaluation.EvaluationRequest
import com.intellij.database.console.session.DatabaseSession
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.console.session.getSessionTitle
import com.intellij.database.datagrid.*
import com.intellij.database.intentions.RunQueryInConsoleIntentionAction.Manager.chooseAndRunRunners
import com.intellij.database.model.RawDataSource
import com.intellij.database.script.PersistenceConsoleProvider
import com.intellij.database.settings.DatabaseSettings
import com.intellij.database.util.DbImplUtilCore
import com.intellij.database.vfs.DbVFSUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Consumer
import com.phodal.shire.database.DatabaseSchemaAssistant.allRawDatasource
import java.util.concurrent.CompletableFuture

object SQLExecutor {
    fun executeSqlQuery(project: Project, sql: String): String {
        val file = LightVirtualFile("temp.sql", sql)
        val psiFile = runReadAction { PsiManager.getInstance(project).findFile(file) }
            ?: return "ShireError[Database]: Can't find PSI file"

        val dataSource = allRawDatasource(project).firstOrNull()
            ?: throw IllegalArgumentException("ShireError[Database]: No database found")

        val execOptions = DatabaseSettings.getSettings().execOptions.last()
        val console: JdbcConsole? = JdbcConsole.getActiveConsoles(project).firstOrNull()
            ?: JdbcConsoleProvider.getValidConsole(project, file)
            ?: createConsole(project, file)

        if (console != null) {
            return executeSqlInConsole(console, sql, dataSource)
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: throw IllegalArgumentException("ShireError[Database]: No editor found")

        val scriptModel = SqlPsiFacade.getInstance(project).createScriptModel(psiFile)

        val info = JdbcConsoleProvider.Info(psiFile, psiFile, editor as EditorEx, scriptModel, execOptions, null)
        val runners: MutableList<PersistenceConsoleProvider.Runner> = runReadAction {
            getAttachDataSourceRunnersReflect(info)
        }

        if (runners.size == 1) {
            var result = ""
            runInEdt {
                result = executeSql(project, dataSource, sql) ?: "Error"
            }

            return result
        } else {
            chooseAndRunRunners(runners, info.editor, null)
            return "ShireError[Database]: Currently not support multiple runners"
        }
    }

//    private fun getAttachDataSourceRunners(info: JdbcConsoleProvider.Info): MutableList<PersistenceConsoleProvider.Runner> {
//        val virtualFile = info.editor!!.virtualFile
//        val project = info.originalFile.project
//        val title = getSessionTitle(virtualFile)
//        val consumer: Consumer<in DatabaseSession> =
//            Consumer<DatabaseSession> { newSession: DatabaseSession? ->
//                val console = JdbcConsoleProvider.attachConsole(
//                    project,
//                    newSession!!, virtualFile
//                )
//                if (console != null) {
//                    val runnable = Runnable { JdbcConsoleProvider.doRunQueryInConsole(console, info) }
//                    if (DbVFSUtils.isAssociatedWithDataSourceAndSchema(virtualFile)) {
//                        runnable.run()
//                    } else {
//                        DatabaseRunners.chooseSchemaAndRun(info.editor!!, runnable)
//                    }
//                }
//            }
//
//        return DatabaseRunners.getAttachDataSourceRunners(info.file, title, consumer)
//    }

    private fun getAttachDataSourceRunnersReflect(info: JdbcConsoleProvider.Info): MutableList<PersistenceConsoleProvider.Runner> {
        val virtualFile = info.editor!!.virtualFile
        val project = info.originalFile.project
        val title = getSessionTitle(virtualFile)
        val consumer: Consumer<in DatabaseSession> = Consumer<DatabaseSession> { newSession: DatabaseSession? ->
            val console = JdbcConsoleProvider.attachConsole(project, newSession!!, virtualFile)
            if (console != null) {
                val runnable = Runnable { JdbcConsoleProvider.doRunQueryInConsole(console, info) }
                try {
                    // 使用反射调用 DbVFSUtils.isAssociatedWithDataSourceAndSchema
                    val isAssociatedMethod = DbVFSUtils::class.java.getDeclaredMethod(
                        "isAssociatedWithDataSourceAndSchema",
                        virtualFile::class.java
                    )
                    val isAssociated = isAssociatedMethod.invoke(null, virtualFile) as Boolean

                    if (isAssociated) {
                        runnable.run()
                    } else {
                        val chooseSchemaMethod = Class.forName("com.intellij.database.console.DatabaseRunners")
                            .getDeclaredMethod("chooseSchemaAndRun", EditorEx::class.java, Runnable::class.java)
                        chooseSchemaMethod.invoke(null, info.editor, runnable)
                    }
                } catch (e: Exception) {
                    println("ShireError[Database]: Failed to run query in console")
                    throw e
                }
            }
        }

        try {
            // 使用反射调用 DatabaseRunners.getAttachDataSourceRunners
            val getRunners = Class.forName("com.intellij.database.console.DatabaseRunners")
                .getDeclaredMethod(
                    "getAttachDataSourceRunners",
                    PsiFile::class.java,
                    String::class.java,
                    com.intellij.util.Consumer::class.java
                )
            @Suppress("UNCHECKED_CAST")
            return getRunners.invoke(null, info.file, title, consumer) as MutableList<PersistenceConsoleProvider.Runner>
        } catch (e: Exception) {
            println("ShireError[Database]: Failed to get runners")
            throw e
        }
    }

    private fun executeSqlInConsole(console: JdbcConsole, sql: String, dataSource: RawDataSource): String {
        val future: CompletableFuture<String> = CompletableFuture()
        return ApplicationManager.getApplication().executeOnPooledThread<String> {
            val messageBus = console.session.messageBus
            val newConsoleRequest = EvaluationRequest.newRequest(console, sql, dataSource.dbms)
            messageBus.dataProducer.processRequest(newConsoleRequest)
            messageBus.addConsumer(object : DataConsumer {
                var result = mutableListOf<GridRow>()

                override fun setColumns(context: GridDataRequest.Context, columns: Array<out GridColumn>) {
                    // 空实现
                }

                override fun addRows(context: GridDataRequest.Context, rows: MutableList<out GridRow>) {
                    result += rows
                    if (rows.size < 100) {
                        future.complete(result.toString())
                    }
                }

                override fun afterLastRowAdded(context: GridDataRequest.Context, total: Int) {
                    // 空实现
                }
            })
            return@executeOnPooledThread future.get()
        }.get()
    }

    private fun executeSql(project: Project, dataSource: RawDataSource, query: String): String? {
        val future: CompletableFuture<String> = CompletableFuture()
        val localDs = DbImplUtilCore.getLocalDataSource(dataSource)

        val session = DatabaseSessionManager.getSession(project, localDs)
        val messageBus = session.messageBus
        messageBus.addConsumer(object : DataConsumer {
            var result = mutableListOf<GridRow>()
            override fun addRows(context: GridDataRequest.Context, rows: MutableList<out GridRow>) {
                result += rows
                if (rows.size < 100) {
                    future.complete(result.toString())
                }
            }

            override fun afterLastRowAdded(context: GridDataRequest.Context, total: Int) {
                // 空实现
            }
        })

        val request =
            object : DataRequest.QueryRequest(session, query, DataRequest.newConstraints(dataSource.dbms), null) {}
        messageBus.dataProducer.processRequest(request)
        return future.get()
    }

    private fun createConsole(project: Project, file: LightVirtualFile): JdbcConsole? {
        val attached = JdbcConsoleProvider.findOrCreateSession(project, file) ?: return null
        return JdbcConsoleProvider.attachConsole(project, attached, file)
    }
}