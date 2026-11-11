package it.cardinal.sshclient

import android.app.Application
import it.cardinal.sshclient.data.HostRepository
import it.cardinal.sshclient.data.KeyRepository
import it.cardinal.sshclient.ssh.KnownHosts
import java.io.File

/**
 * Tiene gli archivi. BouncyCastle non si carica qui: costa, e lo installa
 * `SshKeys` al primo uso, che avviene sempre fuori dal thread dell'interfaccia.
 */
class SshClientApp : Application() {

    val keys by lazy { KeyRepository(filesDir) }
    val hosts by lazy { HostRepository(filesDir) }
    val knownHosts by lazy { KnownHosts(File(filesDir, "known_hosts")) }
}
