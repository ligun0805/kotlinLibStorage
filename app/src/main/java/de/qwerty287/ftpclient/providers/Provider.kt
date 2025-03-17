package de.qwerty287.ftpclient.providers

import android.content.Context
import de.qwerty287.ftpclient.providers.sftp.SFTPClient

enum class Provider {
    SFTP;

    fun get(context: Context): Client {
        return when (this) {
            SFTP -> SFTPClient(context)
        }
    }
}