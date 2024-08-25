package com.phodal.shirelang.compiler.hobbit.execute

import com.phodal.shirecore.agent.agenttool.browse.BrowseTool
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

object CrawlProcessor {
    suspend fun doExecute(url: String): String? {
        return BrowseTool.parse(url).body
    }

    fun execute(urls: Array<out String>): List<String> {
        return runBlocking {
            coroutineScope {
                urls.mapNotNull {
                    doExecute(it)
                }
            }
        }
    }
}
