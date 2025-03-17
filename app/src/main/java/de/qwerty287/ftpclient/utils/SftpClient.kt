package de.qwerty287.ftpclient.utils

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.util.Properties

class SftpClient(
    private val host: String,
    private val username: String,
    private val password: String,
    private val port: Int = 22
) {
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    fun connect() {
        val jsch = JSch()
        session = jsch.getSession(username, host, port).apply {
            setPassword(password)
            val config = Properties()
            config.put("StrictHostKeyChecking", "no")
            setConfig(config)
            connect()
        }
        channel = (session?.openChannel("sftp") as ChannelSftp).apply {
            connect()
        }
    }

    fun downloadFile(remotePath: String, localPath: String) {
        channel?.get(remotePath, localPath)
    }

    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
    }
} 