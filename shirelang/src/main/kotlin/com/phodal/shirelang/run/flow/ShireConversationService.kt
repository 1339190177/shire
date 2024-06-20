package com.phodal.shirelang.run.flow

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.phodal.shire.llm.LlmProvider
import com.phodal.shirelang.compiler.ShireCompiledResult
import com.phodal.shirelang.run.ShireConsoleView
import kotlinx.coroutines.runBlocking

@Service(Service.Level.PROJECT)
class ShireConversationService(val project: Project) {
    /**
     * The cached conversations
     */
    private val cachedConversations: MutableMap<String, ShireProcessContext> = mutableMapOf()

    fun createConversation(scriptPath: String, result: ShireCompiledResult): ShireProcessContext {
        val conversation = ShireProcessContext(scriptPath, result, "", "")
        cachedConversations[scriptPath] = conversation
        return conversation
    }

    fun getConversation(scriptPath: String): ShireProcessContext? {
        return cachedConversations[scriptPath]
    }

    /**
     * Updates the LLM response for a given script path in the cached conversations.
     * If the script path exists in the cached conversations, the LLM response is updated with the provided value.
     *
     * @param scriptPath The script path for which the LLM response needs to be updated.
     * @param llmResponse The new LLM response to be updated for the given script path.
     */
    fun updateLlmResponse(scriptPath: String, llmResponse: String) {
        cachedConversations[scriptPath]?.let {
            cachedConversations[scriptPath] = it.copy(llmResponse = llmResponse)
        }
    }

    /**
     * Updates the IDE output for a conversation at the specified path.
     *
     * @param path The path of the conversation to update.
     * @param ideOutput The new IDE output to set for the conversation.
     */
    fun updateIdeOutput(path: String, ideOutput: String) {
        cachedConversations[path]?.let {
            cachedConversations[path] = it.copy(ideOutput = ideOutput)
        }
    }

    /**
     * Function to try re-running a conversation script.
     *
     * @param scriptPath The path of the script to re-run
     */
    fun tryFixWithLlm(scriptPath: String, consoleView: ShireConsoleView?) {
        if (cachedConversations.isEmpty()) {
            return
        }

        val conversation = cachedConversations[scriptPath] ?: return
        if (conversation.alreadyReRun) {
            return
        }

        conversation.alreadyReRun = true

        val prompt = StringBuilder()

        if (conversation.compiledResult.isLocalCommand) {
            prompt.append(
                """
                You are a top software developer in the world, which can help me to fix the issue.
                When I use shell-like language and compile the script, I got an error, can you help me to fix it?
                
                Origin script:
                ```shire
                ${conversation.compiledResult.input}
                ```
                
                Script with result:
                ####
                ${conversation.compiledResult.shireOutput}
                ####
                """.trimIndent()
            )
        }

        prompt.append(
            """
            You are a top software developer in the world, which can help me to fix the issue.
            
            Here is the run result, can you help me to fix it?
            Run result:
            ####
            ${conversation.ideOutput}
            ####
            """.trimIndent()
        )

        val finalPrompt = prompt.toString()
        if (consoleView != null) {
            runBlocking {
                LlmProvider.provider(project)?.stream(finalPrompt, "Shirelang", true)
                    ?.collect {
                        consoleView.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
                    }
            }
        }
    }

    fun getLlmResponse(scriptPath: String): String {
        return cachedConversations[scriptPath]?.llmResponse ?: ""
    }
}
