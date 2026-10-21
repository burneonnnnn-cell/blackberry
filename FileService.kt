package com.blackberry.loader

import java.io.File

class FileService : IFileService.Stub() {
    override fun deleteFileOrDirectory(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                deleteRecursive(file)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            val children = fileOrDirectory.list()
            if (children != null) {
                for (child in children) {
                    deleteRecursive(File(fileOrDirectory, child))
                }
            }
        }
        fileOrDirectory.delete()
    }
}