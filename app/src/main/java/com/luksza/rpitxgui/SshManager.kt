import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class SshManager {
    private var session: Session? = null
    private var channel: ChannelExec? = null

    private var username = ""
    private var host = ""
    private var password = ""
    private var sshSession: Session? = null

    private suspend fun reconnectIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (sshSession?.isConnected != true) {
                sshSession?.disconnect()
                val jsch = JSch()
                sshSession = jsch.getSession(username, host, 22)
                sshSession?.setPassword(password)
                sshSession?.setConfig("StrictHostKeyChecking", "no")
                sshSession?.setConfig("ConnectTimeout", "10000")
                sshSession?.setConfig("ServerAliveInterval", "60000")
                sshSession?.connect(10000)
                true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e("SshManager", "Reconnect failed", e)
            false
        }
    }





    fun connect(host: String, username: String, password: String): Boolean {
        return try {
            val jsch = JSch()
            this.host = host
            this.username = username
            this.password = password
            session = jsch.getSession(username, host, 22)
            session?.setPassword(password)
            session?.setConfig("StrictHostKeyChecking", "no")
            session?.connect(5000)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun execute(command: String): String {
        Log.i("SshManager", "ssh execute $command")
        if (!reconnectIfNeeded()) return "false"


        return withContext(Dispatchers.IO) { // ✅ Move to IO dispatcher
            try {
                val channel = sshSession!!.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                Log.i("SshManager", "ssh3 execute $command")

                val output = ByteArrayOutputStream()
                val error = ByteArrayOutputStream()
                channel.setOutputStream(output)
                channel.setErrStream(error)

                channel.connect()

                // ✅ Better waiting with timeout (prevents infinite loops)
                val startTime = System.currentTimeMillis()
                val timeoutMs = 30000L // 30 second timeout
                while (!channel.isClosed && (System.currentTimeMillis() - startTime) < timeoutMs) {
                    delay(100) // Use coroutine delay instead of Thread.sleep
                }

                if (!channel.isClosed) {
                    channel.disconnect() // Force disconnect on timeout
                }

                val result = output.toString()
                val err = error.toString()

                if (err.isNotEmpty()) {
                    Log.e("SshManager", "SSH error output: $err")
                }

                result

            } catch (e: Exception) {
                Log.e("SshManager", "ssh error execute ${e.message}", e)
                e.message ?: "Error"
            }
        }
    }



       suspend fun uploadFile(localFile: File, remotePath: String) {
           withContext(Dispatchers.IO) {
               if (!reconnectIfNeeded()) throw Exception("SSH connection failed")

               repeat(3) { attempt ->
                   try {
                       val sftp = sshSession!!.openChannel("sftp") as ChannelSftp
                       sftp.connect()
                       sftp.put(localFile.inputStream(), remotePath)
                       sftp.disconnect()
                       return@withContext  // ✅ SUCCESS
                   } catch (e: Exception) {
                       Log.w("SshManager", "Upload attempt ${attempt + 1} failed", e)
                       delay(1000)
                       reconnectIfNeeded()
                   }
               }
               throw Exception("Upload failed after 3 attempts")
           }
    }



    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
    }

}
