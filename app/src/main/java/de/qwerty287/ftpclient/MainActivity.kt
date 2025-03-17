package de.qwerty287.ftpclient

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.color.DynamicColors
import de.qwerty287.ftpclient.data.Connection
import de.qwerty287.ftpclient.databinding.ActivityMainBinding
import de.qwerty287.ftpclient.providers.Client
import de.qwerty287.ftpclient.providers.sftp.KeyFileManager
import de.qwerty287.ftpclient.providers.sftp.KeyVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import android.view.Menu
import android.view.MenuItem
import de.qwerty287.ftpclient.ui.ZipManagerActivity

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    internal val state = StateStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // replace BountyCastle provider (see https://github.com/hierynomus/sshj/issues/540#issuecomment-596017926)
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 0)

        DynamicColors.applyToActivityIfAvailable(this)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        state.kfm = KeyFileManager(this)
        state.kv = KeyVerifier(this)

        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val options = Bundle()
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                if (intent.action == Intent.ACTION_SEND) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        options.putString(
                            "uri",
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java).toString()
                        )
                    } else {
                        options.putString("uri", intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString())
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= 33) {
                        options.putParcelableArrayList(
                            "uris",
                            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        )
                    } else {
                        options.putParcelableArrayList(
                            "uris",
                            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        )
                    }
                }
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                options.putString("text", intent.getCharSequenceExtra(Intent.EXTRA_TEXT).toString())
            } else {
                finish()
            }
            navController.navigate(R.id.action_to_UploadFileIntentFragment, options)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_zip_manager -> {
                startActivity(Intent(this, ZipManagerActivity::class.java))
                true
            }
            // Add other menu items here
            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class StateStore {
        private var client: Client? = null
        private var clientConnId = -1

        lateinit var kfm: KeyFileManager
        lateinit var kv: KeyVerifier

        private fun setClient(connection: Connection, userPassword: String?) {
            exitClient()
            client = connection.client(this@MainActivity, userPassword)
            clientConnId = connection.id
        }

        /**
         * If [connection] is null, callers must make sure that it's impossible that an old client is used.
         */
        fun getClient(connection: Connection? = null, userPassword: String? = null): Client {
            if (connection != null && !connected(connection.id)) {
                setClient(connection, userPassword)
            }

            return client!!
        }

        fun exitClient() {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (client != null && client!!.isConnected) {
                        try {
                            client!!.exit()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        private fun connected(connId: Int): Boolean {
            return clientConnId == connId && client?.isConnected == true
        }

        fun invalidateClient() {
            client = null
            clientConnId = -1
        }
    }
}