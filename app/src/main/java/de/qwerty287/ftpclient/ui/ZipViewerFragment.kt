package de.qwerty287.ftpclient.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.qwerty287.ftpclient.R
import de.qwerty287.ftpclient.ui.files.ZipEntryAdapter
import de.qwerty287.ftpclient.ui.FragmentUtils.store
import de.qwerty287.ftpclient.utils.ZipManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipViewerFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ZipEntryAdapter
    private lateinit var zipFile: File

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_zip_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        adapter = ZipEntryAdapter { entry ->
            // Handle zip entry click - show options dialog
            showEntryOptionsDialog(entry)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        val remotePath = arguments?.getString("remotePath")
        if (remotePath != null) {
            loadRemoteZip(remotePath)
        } else {
            // Local ZIP file path
            val localPath = arguments?.getString("localPath")
            if (localPath != null) {
                zipFile = File(localPath)
                loadLocalZip(zipFile)
            }
        }
    }

    private fun showEntryOptionsDialog(entry: ZipEntry) {
        val options = arrayOf("Extract", "Extract All", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> extractEntry(entry)
                    1 -> extractAll()
                    2 -> deleteEntry(entry)
                }
            }
            .show()
    }

    private fun extractEntry(entry: ZipEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ZipManager.extractEntry(zipFile, entry.name,
                        File(requireContext().getExternalFilesDir(null), entry.name))
                }
                Toast.makeText(context, "Extracted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Extract failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractAll() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ZipManager.extractAll(zipFile,
                        requireContext().getExternalFilesDir(null)!!)
                }
                Toast.makeText(context, "Extracted all files successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Extract failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLocalZip(zipFile: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    ZipManager.getEntries(zipFile)
                }
                adapter.submitList(entries)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRemoteZip(remotePath: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Create a temporary file to store the downloaded ZIP
                val tempFile = File.createTempFile("temp_", ".zip", requireContext().cacheDir)
                
                try {
                    // Download the ZIP file
                    withContext(Dispatchers.IO) {
                        val client = store.getClient()
                        val outputStream = FileOutputStream(tempFile)
                        client.download(remotePath, outputStream)
                        outputStream.close()
                        
                        // Read ZIP contents
                        val zipFile = ZipFile(tempFile)
                        val entries = zipFile.entries().asSequence().map { entry ->
                            // Create a new ZipEntry with explicit type
                            ZipEntry(entry.name).apply {
                                time = entry.time
                                size = entry.size
                            }
                        }.toList()
                        
                        zipFile.close()
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            adapter.submitList(entries)
                        }
                    }
                } finally {
                    // Clean up temporary file
                    tempFile.delete()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteEntry(entry: ZipEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val tempFile = File(zipFile.parentFile, "${zipFile.name}.temp")
                    ZipFile(zipFile).use { sourceZip ->
                        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                            // Copy all entries except the one to be deleted
                            sourceZip.entries().asSequence()
                                .filter { it.name != entry.name }
                                .forEach { zipEntry ->
                                    zos.putNextEntry(ZipEntry(zipEntry.name))
                                    if (!zipEntry.isDirectory) {
                                        sourceZip.getInputStream(zipEntry).use { input ->
                                            input.copyTo(zos)
                                        }
                                    }
                                    zos.closeEntry()
                                }
                        }
                    }
                    
                    // Replace original file with temp file
                    zipFile.delete()
                    tempFile.renameTo(zipFile)
                    
                    // Reload the entries
                    loadLocalZip(zipFile)
                }
                Toast.makeText(context, "Entry deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 