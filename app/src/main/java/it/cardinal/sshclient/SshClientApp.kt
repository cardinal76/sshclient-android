package it.cardinal.sshclient

import android.app.Application
import it.cardinal.sshclient.data.HostRepository
import it.cardinal.sshclient.data.KeyRepository
import it.cardinal.sshclient.ssh.KnownHosts
import it.cardinal.sshclient.ssh.SshKeys
import java.io.File

class SshClientApp : Application() {

    val keys by lazy { KeyRepository(filesDir) }
    val hosts by lazy { HostRepository(filesDir) }
    val knownHosts by lazy { KnownHosts(File(filesDir, "known_hosts")) }

    override fun onCreate() {
        super.onCreate()
        SshKeys.installBouncyCastle()
    }
}
