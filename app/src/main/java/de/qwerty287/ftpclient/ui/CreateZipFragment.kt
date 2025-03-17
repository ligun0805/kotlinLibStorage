package de.qwerty287.ftpclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.qwerty287.ftpclient.R
import de.qwerty287.ftpclient.utils.ZipManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CreateZipFragment : Fragment() {
    private val selectFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            createZipFromUris(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_zip, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.buttonSelectFiles).setOnClickListener {
            selectFiles.launch(arrayOf("*/*"))
        }
    }

    private fun createZipFromUris(uris: List<Uri>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val files = uris.map { uri ->
                        // Convert URIs to temporary files
                        val tempFile = File.createTempFile("zip_", null, requireContext().cacheDir)
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    }

                    val outputZip = File(
                        requireContext().getExternalFilesDir(null),
                        "archive_${System.currentTimeMillis()}.zip"
                    )
                    
                    ZipManager.createZip(files, outputZip)
                    
                    // Clean up temp files
                    files.forEach { it.delete() }
                }
                
                Toast.makeText(context, "ZIP created successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to create ZIP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 