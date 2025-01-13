package com.phodal.shirelang.compiler.execute.command

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.phodal.shirecore.lookupFile

/**
 * The `DirShireCommand` class is responsible for listing files and directories in a tree-like structure for a given directory path within a project.
 * It implements the `ShireCommand` interface and provides an `execute` method to perform the directory listing operation asynchronously.
 *
 * The tree structure is visually represented using indentation and symbols (`├──`, `└──`) to denote files and subdirectories. Files are listed
 * first, followed by subdirectories, which are recursively processed to display their contents.
 *
 * Example output:
 * ```
 * myDirectory/
 *   ├── file1.txt
 *   ├── file2.txt
 *   └── subDirectory/
 *       ├── file3.txt
 *       └── subSubDirectory/
 *           └── file4.txt
 * ```
 *
 * @param myProject The project instance in which the directory resides.
 * @param dir The path of the directory to list.
 */
class DirShireCommand(private val myProject: Project, private val dir: String) : ShireCommand {
    private val output = StringBuilder()

    override suspend fun doExecute(): String? {
        val virtualFile = myProject.lookupFile(dir) ?: return "File not found: $dir"
        val psiDirectory = PsiManager.getInstance(myProject).findDirectory(virtualFile) ?: return null

        output.appendLine("$dir/")
        listDirectory(psiDirectory, 1)

        return output.toString()
    }

    private fun listDirectory(directory: PsiDirectory, depth: Int) {
        val files = directory.files
        val subdirectories = directory.subdirectories

        for ((index, file) in files.withIndex()) {
            if (index == files.size - 1) {
                output.appendLine("${"  ".repeat(depth)}└── ${file.name}")
            } else {
                output.appendLine("${"  ".repeat(depth)}├── ${file.name}")
            }
        }

        for ((index, subdirectory) in subdirectories.withIndex()) {
            if (index == subdirectories.size - 1) {
                output.appendLine("${"  ".repeat(depth)}└── ${subdirectory.name}/")
            } else {
                output.appendLine("${"  ".repeat(depth)}├── ${subdirectory.name}/")
            }
            listDirectory(subdirectory, depth + 1)
        }
    }
}

