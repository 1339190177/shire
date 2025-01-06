package com.phodal.shirelang.debugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.phodal.shirelang.compiler.variable.resolver.base.VariableResolver
import kotlinx.datetime.Instant


data class SnapshotMetadata(
    val createdAt: Instant,          // 创建时间
    val version: String,             // 版本号或其他标识
    val file: VirtualFile,           // 文件的虚拟路径
)

/**
 * Variable Snapshot will store all change flow of a variable. For example:
 * ```shire
 * ---
 * variables:
 *   "controllers": /.*.java/ { cat | grep("class\s+([a-zA-Z]*Controller)")  }
 * ---
 * ```
 *
 * The variable snapshot should store:
 *
 * - the value after cat function
 * - the value after grep function
 */
data class VariableOperation(
    val functionName: String,
    val timestamp: Long,
    val value: Any?,
)

class UserCustomVariableSnapshot(
    val variableName: String,
    val value: Any? = null,
    val className: String? = VariableResolver::class.java.name,
    val operations: List<VariableOperation> = mutableListOf(),
    private val context: ExecutionContext = ExecutionContext(),
) : UserDataHolderBase() {
    private val valueHistory = mutableListOf<Any>()
    private var currentValue: Any? = null

    fun recordValue(value: Any, functionIndex: Int = -1) {
        currentValue = value
        valueHistory.add(value)
    }

    fun getCurrentValue(): Any? = currentValue

    fun getHistory(): List<Any> = valueHistory.toList()
}

@Service(Service.Level.PROJECT)
class VariableSnapshotRecorder {
    private val snapshots = mutableListOf<UserCustomVariableSnapshot>()

    fun addSnapshot(variableName: String, value: Any, operation: String? = null, operationArg: Any? = null) {
        val operationList = mutableListOf<VariableOperation>()
        if (operation != null) {
            operationList.add(VariableOperation(operation, System.currentTimeMillis(), operationArg))
        }

        val result = when (value) {
            is Array<*> -> {
                value.joinToString(", ")
            }

            is List<*> -> {
                value.joinToString(", ")
            }

            else -> {
                value.toString()
            }
        }

        snapshots.add(UserCustomVariableSnapshot(variableName, result, operations = operationList))
    }

    fun clear() {
        snapshots.clear()
    }

    fun all(): List<UserCustomVariableSnapshot> {
        return snapshots
    }

    companion object {
        fun getInstance(project: Project): VariableSnapshotRecorder {
            return project.getService(VariableSnapshotRecorder::class.java)
        }
    }
}

data class ExecutionContext(
    val variables: MutableMap<String, Any> = mutableMapOf(),
    val environment: MutableMap<String, String> = mutableMapOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf(),
)

