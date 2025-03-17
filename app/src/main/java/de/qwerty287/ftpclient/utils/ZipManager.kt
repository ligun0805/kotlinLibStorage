package de.qwerty287.ftpclient.utils

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.io.*
import java.util.zip.*

data class ZipFileEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val path: String
)

object ZipManager {
    fun getZipContents(zipFile: File): List<ZipFileEntry> {
        val entries = mutableListOf<ZipFileEntry>()
        
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                entries.add(
                    ZipFileEntry(
                        name = entry.name.split("/").last(),
                        isDirectory = entry.isDirectory,
                        size = entry.size,
                        path = entry.name
                    )
                )
            }
        }
        
        return entries
    }

    fun deleteTempFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun createZip(files: List<File>, outputZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
            for (file in files) {
                addToZip(file, "", zos)
            }
        }
    }

    private fun addToZip(file: File, parentPath: String, zos: ZipOutputStream) {
        val path = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                addToZip(child, path, zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(path))
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    fun extractEntry(zipFile: File, entryName: String, outputFile: File) {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(entryName) ?: throw IOException("Entry not found")
            outputFile.parentFile?.mkdirs()
            
            zip.getInputStream(entry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun extractAll(zipFile: File, outputDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun getEntries(zipFile: File): List<ZipEntry> {
        return ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().map { entry ->
                ZipEntry(entry.name).apply {
                    time = entry.time
                    size = entry.size
                }
            }.toList()
        }
    }

    fun addFilesToExistingZip(zipFile: File, files: List<File>) {
        val tempFile = File(zipFile.parentFile, "${zipFile.name}.temp")
        
        // Copy existing entries
        ZipFile(zipFile).use { sourceZip ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                // Copy existing entries
                sourceZip.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        sourceZip.getInputStream(entry).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
                
                // Add new files
                files.forEach { file ->
                    addToZip(file, "", zos)
                }
            }
        }

        // Replace original file with temp file
        zipFile.delete()
        tempFile.renameTo(zipFile)
    }
} 