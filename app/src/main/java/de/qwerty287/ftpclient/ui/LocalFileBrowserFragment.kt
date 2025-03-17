package de.qwerty287.ftpclient.ui

import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.qwerty287.ftpclient.R
import de.qwerty287.ftpclient.utils.ZipManager
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.OnBackPressedCallback
import android.widget.EditText
import android.text.InputType
import androidx.appcompat.app.AlertDialog

class LocalFileBrowserFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private var currentPath: File? = null
    private val selectedFiles = mutableSetOf<File>()
    private var isSelectionMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)  // Enable options menu in fragment

        recyclerView = view.findViewById(R.id.recyclerView)
        adapter = FileAdapter(
            onFileClick = { file ->
                if (isSelectionMode) {
                    toggleFileSelection(file)
                } else {
                    if (file.isDirectory) {
                        browseDirectory(file)
                    } else if (file.extension.equals("zip", ignoreCase = true)) {
                        showZipOptions(file)
                    }
                }
            },
            onFileLongClick = { file ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleFileSelection(file)
                    updateActionBar()
                }
                true
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Setup FAB
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showFabMenu()
        }

        // Setup ActionBar
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        // Start with root storage directory
        val initialPath = Environment.getExternalStorageDirectory()
        browseDirectory(initialPath)

        // Correct back press handling
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        isSelectionMode -> {
                            // Clear selection if in selection mode
                            clearSelection()
                        }
                        currentPath?.absolutePath != Environment.getExternalStorageDirectory().absolutePath -> {
                            // Go to parent directory if not in root
                            currentPath?.parentFile?.let { parentDir ->
                                browseDirectory(parentDir)
                            }
                        }
                        else -> {
                            // If in root directory, allow normal back button behavior
                            isEnabled = false
                            requireActivity().onBackPressed()
                        }
                    }
                }
            }
        )
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        adapter.setSelectedFiles(selectedFiles)
        updateActionBar()
        
        // If no files are selected, exit selection mode
        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }

    private fun updateActionBar() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = when {
                isSelectionMode -> "${selectedFiles.size} selected"
                currentPath?.absolutePath == Environment.getExternalStorageDirectory().absolutePath -> 
                    "Storage"
                else -> currentPath?.name ?: "Files"
            }
        }
        activity?.invalidateOptionsMenu()
    }

    private fun showFabMenu() {
        val fab = view?.findViewById<FloatingActionButton>(R.id.fab) ?: return
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), fab)
        popup.menuInflater.inflate(R.menu.fab_menu, popup.menu)
        
        // Show/hide menu items based on selection state
        popup.menu.apply {
            findItem(R.id.action_add_to_zip)?.isVisible = selectedFiles.isNotEmpty()
            findItem(R.id.action_create_zip)?.isVisible = selectedFiles.isNotEmpty()
            findItem(R.id.action_delete)?.isVisible = selectedFiles.isNotEmpty()
        }
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_create_zip -> {
                    if (selectedFiles.isNotEmpty()) {
                        createZipFromSelection()
                    }
                    true
                }
                R.id.action_add_to_zip -> {
                    if (selectedFiles.isNotEmpty()) {
                        showZipPicker()
                    }
                    true
                }
                R.id.action_delete -> {
                    if (selectedFiles.isNotEmpty()) {
                        showDeleteConfirmation()
                    }
                    true
                }
                R.id.action_new_folder -> {
                    showNewFolderDialog()
                    true
                }
                R.id.action_new_file -> {
                    showNewFileDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun createZipFromSelection() {
        if (selectedFiles.isEmpty()) return

        val input = EditText(context).apply {
            hint = "Enter ZIP file name (or leave empty for auto-name)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create ZIP")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString()
                val finalName = when {
                    fileName.isEmpty() -> generateZipFileName()
                    fileName.endsWith(".zip") -> fileName
                    else -> "$fileName.zip"
                }
                createZipWithName(finalName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateZipFileName(): String {
        val timestamp = java.text.SimpleDateFormat("MM_dd_HH_mm_ss", java.util.Locale.US)
            .format(java.util.Date())
        return "archive_${timestamp}.zip"
    }

    private fun createZipWithName(fileName: String) {
        lifecycleScope.launch {
            try {
                val zipFile = File(currentPath, fileName)
                withContext(Dispatchers.IO) {
                    ZipManager.createZip(selectedFiles.toList(), zipFile)
                }
                Toast.makeText(context, "ZIP created: ${zipFile.name}", Toast.LENGTH_SHORT).show()
                clearSelection()
                browseDirectory(currentPath!!)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to create ZIP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showZipPicker() {
        val zipFiles = currentPath?.listFiles()?.filter { 
            it.isFile && it.extension.equals("zip", ignoreCase = true) 
        } ?: return

        if (zipFiles.isEmpty()) {
            Toast.makeText(context, "No ZIP files in current directory", Toast.LENGTH_SHORT).show()
            return
        }

        val zipNames = zipFiles.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select ZIP file")
            .setItems(zipNames) { _, which ->
                addToExistingZip(zipFiles[which])
            }
            .show()
    }

    private fun addToExistingZip(zipFile: File) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ZipManager.addFilesToExistingZip(zipFile, selectedFiles.toList())
                }
                Toast.makeText(context, "Files added to ${zipFile.name}", Toast.LENGTH_SHORT).show()
                clearSelection()
                browseDirectory(currentPath!!)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to add files: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun browseDirectory(directory: File) {
        currentPath = directory
        val files = directory.listFiles()?.toList() ?: emptyList()
        adapter.submitList(files.sortedWith(compareBy({ !it.isDirectory }, { it.name })))
        
        // Update the ActionBar title to show current path
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = when {
                directory.absolutePath == Environment.getExternalStorageDirectory().absolutePath -> 
                    "Storage"
                else -> directory.name
            }
        }
    }

    private fun showZipOptions(file: File) {
        val options = arrayOf("Extract Here", "Extract to...", "View Contents", "Rename")
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> extractZip(file, currentPath!!)
                    1 -> showExtractLocationPicker(file)
                    2 -> viewZipContents(file)
                    3 -> showRenameDialog(file)
                }
            }
            .show()
    }

    private fun extractZip(zipFile: File, destination: File) {
        try {
            ZipManager.extractAll(zipFile, destination)
            Toast.makeText(context, "Extracted successfully", Toast.LENGTH_SHORT).show()
            // Refresh the current directory
            browseDirectory(currentPath!!)
        } catch (e: Exception) {
            Toast.makeText(context, "Extract failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExtractLocationPicker(zipFile: File) {
        // TODO: Implement directory picker
        Toast.makeText(context, "Coming soon...", Toast.LENGTH_SHORT).show()
    }

    private fun viewZipContents(file: File) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ZipViewerFragment().apply {
                arguments = Bundle().apply {
                    putString("localPath", file.absolutePath)
                }
            })
            .addToBackStack(null)
            .commit()
    }

    private fun clearSelection() {
        selectedFiles.clear()
        isSelectionMode = false
        adapter.setSelectedFiles(emptySet())
        updateActionBar()
        activity?.invalidateOptionsMenu()
    }

    // Add this to handle menu items
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (isSelectionMode) {
            inflater.inflate(R.menu.selection_menu, menu)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    clearSelection()
                    true
                } else {
                    currentPath?.parentFile?.let { parentDir ->
                        browseDirectory(parentDir)
                        true
                    } ?: false
                }
            }
            R.id.action_clear_selection -> {
                clearSelection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Files")
            .setMessage("Are you sure you want to delete ${selectedFiles.size} item(s)?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedFiles() {
        var successCount = 0
        var failCount = 0
        
        selectedFiles.forEach { file ->
            if (file.deleteRecursively()) {
                successCount++
            } else {
                failCount++
            }
        }

        val message = when {
            failCount == 0 -> "Deleted $successCount items"
            successCount == 0 -> "Failed to delete $failCount items"
            else -> "Deleted $successCount items, failed to delete $failCount items"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        clearSelection()
        browseDirectory(currentPath!!)
    }

    private fun showNewFolderDialog() {
        val input = EditText(context).apply {
            hint = "Folder Name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val folderName = input.text.toString()
                if (folderName.isNotEmpty()) {
                    createNewFolder(folderName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewFolder(folderName: String) {
        val newFolder = File(currentPath, folderName)
        if (newFolder.exists()) {
            Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
            return
        }

        if (newFolder.mkdir()) {
            Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
            browseDirectory(currentPath!!)
        } else {
            Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNewFileDialog() {
        val input = EditText(context).apply {
            hint = "File Name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString()
                if (fileName.isNotEmpty()) {
                    createNewFile(fileName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewFile(fileName: String) {
        val newFile = File(currentPath, fileName)
        if (newFile.exists()) {
            Toast.makeText(context, "File already exists", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (newFile.createNewFile()) {
                Toast.makeText(context, "File created", Toast.LENGTH_SHORT).show()
                browseDirectory(currentPath!!)
            } else {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(context).apply {
            setText(file.name)
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty() && newName != file.name) {
                    // Ensure .zip extension for ZIP files
                    val finalName = if (file.extension == "zip" && !newName.endsWith(".zip")) {
                        "$newName.zip"
                    } else {
                        newName
                    }
                    renameFile(file, finalName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameFile(file: File, newName: String) {
        val newFile = File(file.parent, newName)
        if (file.renameTo(newFile)) {
            Toast.makeText(context, "File renamed", Toast.LENGTH_SHORT).show()
            browseDirectory(file.parentFile!!)
        } else {
            Toast.makeText(context, "Failed to rename file", Toast.LENGTH_SHORT).show()
        }
    }
}

class FileAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Boolean
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {
    
    private var files: List<File> = emptyList()
    private var selectedFiles: Set<File> = emptySet()

    fun submitList(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    fun setSelectedFiles(selected: Set<File>) {
        selectedFiles = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.text1.text = file.name
        holder.text2.text = if (file.isDirectory) {
            "Directory"
        } else {
            "${file.length() / 1024} KB"
        }
        
        holder.itemView.apply {
            isSelected = selectedFiles.contains(file)
            setBackgroundColor(if (isSelected) 0x330000FF else 0x00000000)
            setOnClickListener { onFileClick(file) }
            setOnLongClickListener { onFileLongClick(file) }
        }
    }

    override fun getItemCount() = files.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }
} 