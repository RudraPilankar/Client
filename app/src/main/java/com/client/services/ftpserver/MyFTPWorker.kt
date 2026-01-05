package com.client.services.ftpserver

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale


/**
 * Class for a FTP server worker thread.
 *
 * @author Moritz Stueckler (SID 20414726)
 */
class MyFTPWorker(
    private val controlSocket: Socket, private val dataPort: Int
) : Thread() {
    /**
     * Enable debugging output to console
     */
    private val debugMode = true

    /**
     * Indicating the last set transfer type
     */
    private enum class transferType {
        ASCII, BINARY
    }

    /**
     * Indicates the authentification status of a user
     */
    private enum class userStatus {
        NOTLOGGEDIN, ENTEREDUSERNAME, LOGGEDIN
    }

    // Path information
    private val root: String
    private var currDirectory: String
    private val fileSeparator = "/"

    private var controlOutWriter: PrintWriter? = null
    private var controlIn: BufferedReader? = null

    // data Connection
    private var dataSocket: ServerSocket? = null
    private var dataConnection: Socket? = null
    private var dataOutWriter: PrintWriter? = null

    private var transferMode = transferType.ASCII

    // user properly logged in?
    private var currentUserStatus = userStatus.NOTLOGGEDIN
    private val validUser = "comp4621"
    private val validPassword = "network"

    private var quitCommandLoop = false

    /**
     * Create new worker with given client socket
     *
     * @param controlSocket   the socket for the current client
     * @param dataPort the port for the data connection
     */
    init {
        this.currDirectory = "/storage/emulated/0"
        this.root = "/storage/emulated/0"
    }

    /**
     * Run method required by Java thread model
     */
    override fun run() {
        debugOutput("Current working directory " + this.currDirectory)
        try {
            // Input from client
            controlIn = BufferedReader(InputStreamReader(controlSocket.getInputStream()))

            // Output to client, automatically flushed after each print
            controlOutWriter = PrintWriter(controlSocket.getOutputStream(), true)

            // Greeting
            sendMsgToClient("220 Welcome to the COMP4621 FTP-Server")

            // Get new command from client
            while (!quitCommandLoop) {
                executeCommand(controlIn!!.readLine())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Clean up
            try {
                controlIn!!.close()
                controlOutWriter!!.close()
                controlSocket.close()
                debugOutput("Sockets closed and worker stopped")
            } catch (e: IOException) {
                e.printStackTrace()
                debugOutput("Could not close sockets")
            }
        }
    }

    /**
     * Main command dispatcher method. Separates the command from the arguments and
     * dispatches it to single handler functions.
     *
     * @param c the raw input from the socket consisting of command and arguments
     */
    private fun executeCommand(c: String) {
        // split command and arguments
        val index = c.indexOf(' ')
        val command = (if (index == -1) c.uppercase(Locale.getDefault()) else (c.substring(
            0,
            index
        )).uppercase(
            Locale.getDefault()
        ))
        val args = (if (index == -1) null else c.substring(index + 1))

        debugOutput("Command: " + command + " Args: " + args)

        // dispatcher mechanism for different commands
        when (command) {
            "USER" -> handleUser(args!!)
            "PASS" -> handlePass(args!!)
            "CWD" -> handleCwd(args!!)
            "LIST" -> handleNlst(args)
            "NLST" -> handleNlst(args)
            "PWD", "XPWD" -> handlePwd()
            "QUIT" -> handleQuit()
            "PASV" -> handlePasv()
            "EPSV" -> handleEpsv()
            "SYST" -> handleSyst()
            "FEAT" -> handleFeat()
            "PORT" -> handlePort(args!!)
            "EPRT" -> handleEPort(args!!)
            "RETR" -> handleRetr(args)
            "MKD", "XMKD" -> handleMkd(args)
            "RMD", "XRMD" -> handleRmd(args)
            "TYPE" -> handleType(args!!)
            "STOR" -> handleStor(args)
            else -> sendMsgToClient("501 Unknown command")
        }
    }

    /**
     * Sends a message to the connected client over the control connection. Flushing
     * is automatically performed by the stream.
     *
     * @param msg The message that will be sent
     */
    private fun sendMsgToClient(msg: String?) {
        controlOutWriter!!.println(msg)
    }

    /**
     * Send a message to the connected client over the data connection.
     *
     * @param msg Message to be sent
     */
    private fun sendDataMsgToClient(msg: String?) {
        if (dataConnection == null || dataConnection!!.isClosed()) {
            sendMsgToClient("425 No data connection was established")
            debugOutput("Cannot send message, because no data connection is established")
        } else {
            dataOutWriter!!.print(msg + '\r' + '\n')
        }
    }

    /**
     * Open a new data connection socket and wait for new incoming connection from
     * client. Used for passive mode.
     *
     * @param port Port on which to listen for new incoming connection
     */
    private fun openDataConnectionPassive(port: Int) {
        try {
            dataSocket = ServerSocket(port)
            dataConnection = dataSocket!!.accept()
            dataOutWriter = PrintWriter(dataConnection!!.getOutputStream(), true)
            debugOutput("Data connection - Passive Mode - established")
        } catch (e: IOException) {
            debugOutput("Could not create data connection.")
            e.printStackTrace()
        }
    }

    /**
     * Connect to client socket for data connection. Used for active mode.
     *
     * @param ipAddress Client IP address to connect to
     * @param port      Client port to connect to
     */
    private fun openDataConnectionActive(ipAddress: String?, port: Int) {
        try {
            dataConnection = Socket(ipAddress, port)
            dataOutWriter = PrintWriter(dataConnection!!.getOutputStream(), true)
            debugOutput("Data connection - Active Mode - established")
        } catch (e: IOException) {
            debugOutput("Could not connect to client data socket")
            e.printStackTrace()
        }
    }

    /**
     * Close previously established data connection sockets and streams
     */
    private fun closeDataConnection() {
        try {
            dataOutWriter!!.close()
            dataConnection!!.close()
            if (dataSocket != null) {
                dataSocket!!.close()
            }

            debugOutput("Data connection was closed")
        } catch (e: IOException) {
            debugOutput("Could not close data connection")
            e.printStackTrace()
        }
        dataOutWriter = null
        dataConnection = null
        dataSocket = null
    }

    /**
     * Handler for USER command. User identifies the client.
     *
     * @param username Username entered by the user
     */
    private fun handleUser(username: String) {
        if (username.lowercase(Locale.getDefault()) == validUser) {
            sendMsgToClient("331 User name okay, need password")
            currentUserStatus = userStatus.ENTEREDUSERNAME
        } else if (currentUserStatus == userStatus.LOGGEDIN) {
            sendMsgToClient("530 User already logged in")
        } else {
            sendMsgToClient("530 Not logged in")
        }
    }

    /**
     * Handler for PASS command. PASS receives the user password and checks if it's
     * valid.
     *
     * @param password Password entered by the user
     */
    private fun handlePass(password: String) {
        // User has entered a valid username and password is correct
        if (currentUserStatus == userStatus.ENTEREDUSERNAME && password == validPassword) {
            currentUserStatus = userStatus.LOGGEDIN
            sendMsgToClient("230-Welcome to HKUST")
            sendMsgToClient("230 User logged in successfully")
        } else if (currentUserStatus == userStatus.LOGGEDIN) {
            sendMsgToClient("530 User already logged in")
        } else {
            sendMsgToClient("530 Not logged in")
        }
    }

    /**
     * Handler for CWD (change working directory) command.
     *
     * @param args New directory to be created
     */
    private fun handleCwd(args: String) {
        var path = currDirectory

        // go one level up (cd ..)
        if (args == "..") {
            val ind = path.lastIndexOf(fileSeparator)
            if (ind > 0) {
                path = path.take(ind)
            }
        } else if (args != ".") {
            path = args
        }

        // check if file exists, is directory and is not above root directory
        val f = File(path)

        if (f.exists() && f.isDirectory() && (path.length >= root.length)) {
            currDirectory = path
            sendMsgToClient("250 The current directory has been changed to " + currDirectory)
        } else {
            sendMsgToClient("550 Requested action not taken. File unavailable.")
        }
    }

    /**
     * Handler for NLST (Named List) command. Lists the directory content in a short
     * format (names only)
     *
     * @param args The directory to be listed
     */
    private fun handleNlst(args: String?) {
        if (dataConnection == null || dataConnection!!.isClosed()) {
            sendMsgToClient("425 No data connection was established")
        } else {
            val dirContent = nlstHelper(args)

            if (dirContent == null) {
                sendMsgToClient("550 File does not exist.")
            } else {
                sendMsgToClient("125 Opening ASCII mode data connection for file list.")

                for (i in dirContent.indices) {
                    sendDataMsgToClient(dirContent[i])
                }

                sendMsgToClient("226 Transfer complete.")
                closeDataConnection()
            }
        }
    }

    /**
     * A helper for the NLST command. The directory name is obtained by appending
     * "args" to the current directory
     *
     * @param args The directory to list
     * @return an array containing names of files in a directory. If the given name
     * is that of a file, then return an array containing only one element
     * (this name). If the file or directory does not exist, return nul.
     */
    private fun nlstHelper(args: String?): Array<String?>? {
        // Construct the name of the directory to list.
        var filename = currDirectory
        if (args != null) {
            filename = filename + fileSeparator + args
        }

        // Now get a File object, and see if the name we got exists and is a
        // directory.
        val f = File(filename)

        if (f.exists() && f.isDirectory()) {
            return f.list()
        } else if (f.exists() && f.isFile()) {
            val allFiles = arrayOfNulls<String>(1)
            allFiles[0] = f.getName()
            return allFiles
        } else {
            return null
        }
    }

    /**
     * Handler for the PORT command. The client issues a PORT command to the server
     * in active mode, so the server can open a data connection to the client
     * through the given address and port number.
     *
     * @param args The first four segments (separated by comma) are the IP address.
     * The last two segments encode the port number (port = seg1*256 +
     * seg2)
     */
    private fun handlePort(args: String) {
        // Extract IP address and port number from arguments
        val stringSplit: Array<String?> =
            args.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val hostName =
            stringSplit[0] + "." + stringSplit[1] + "." + stringSplit[2] + "." + stringSplit[3]

        val p = stringSplit[4]!!.toInt() * 256 + stringSplit[5]!!.toInt()

        // Initiate data connection to client
        openDataConnectionActive(hostName, p)
        sendMsgToClient("200 Command OK")
    }

    /**
     * Handler for the EPORT command. The client issues an EPORT command to the
     * server in active mode, so the server can open a data connection to the client
     * through the given address and port number.
     *
     * @param args This string is separated by vertical bars and encodes the IP
     * version, the IP address and the port number
     */
    private fun handleEPort(args: String) {
        val IPV4 = "1"
        val IPV6 = "2"

        // Example arg: |2|::1|58770| or |1|132.235.1.2|6275|
        val splitArgs: Array<String?> =
            args.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val ipVersion = splitArgs[1]
        val ipAddress = splitArgs[2]

        require(!(IPV4 != ipVersion || IPV6 != ipVersion)) { "Unsupported IP version" }

        val port = splitArgs[3]!!.toInt()

        // Initiate data connection to client
        openDataConnectionActive(ipAddress, port)
        sendMsgToClient("200 Command OK")
    }

    /**
     * Handler for PWD (Print working directory) command. Returns the path of the
     * current directory back to the client.
     */
    private fun handlePwd() {
        sendMsgToClient("257 \"" + currDirectory + "\"")
    }

    /**
     * Handler for PASV command which initiates the passive mode. In passive mode
     * the client initiates the data connection to the server. In active mode the
     * server initiates the data connection to the client.
     */
    private fun handlePasv() {
        // Using fixed IP for connections on the same machine
        // For usage on separate hosts, we'd need to get the local IP address from
        // somewhere
        // Java sockets did not offer a good method for this
        val myIp = "127.0.0.1"
        val myIpSplit: Array<String?>? =
            myIp.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val p1 = dataPort / 256
        val p2 = dataPort % 256

        sendMsgToClient(
            ("227 Entering Passive Mode (" + myIpSplit!![0] + "," + myIpSplit[1] + "," + myIpSplit[2] + ","
                    + myIpSplit[3] + "," + p1 + "," + p2 + ")")
        )

        openDataConnectionPassive(dataPort)
    }

    /**
     * Handler for EPSV command which initiates extended passive mode. Similar to
     * PASV but for newer clients (IPv6 support is possible but not implemented
     * here).
     */
    private fun handleEpsv() {
        sendMsgToClient("229 Entering Extended Passive Mode (|||$dataPort|)")
        openDataConnectionPassive(dataPort)
    }

    /**
     * Handler for the QUIT command.
     */
    private fun handleQuit() {
        sendMsgToClient("221 Closing connection")
        quitCommandLoop = true
    }

    private fun handleSyst() {
        sendMsgToClient("215 COMP4621 FTP Server Homebrew")
    }

    /**
     * Handler for the FEAT (features) command. Feat transmits the
     * abilities/features of the server to the client. Needed for some ftp clients.
     * This is just a dummy message to satisfy clients, no real feature information
     * included.
     */
    private fun handleFeat() {
        sendMsgToClient("211-Extensions supported:")
        sendMsgToClient("211 END")
    }

    /**
     * Handler for the MKD (make directory) command. Creates a new directory on the
     * server.
     *
     * @param args Directory name
     */
    private fun handleMkd(args: String?) {
        // Allow only alphanumeric characters
        if (args != null && args.matches("^[a-zA-Z0-9]+$".toRegex())) {
            val dir = File(currDirectory + fileSeparator + args)

            if (!dir.mkdir()) {
                sendMsgToClient("550 Failed to create new directory")
                debugOutput("Failed to create new directory")
            } else {
                sendMsgToClient("250 Directory successfully created")
            }
        } else {
            sendMsgToClient("550 Invalid name")
        }
    }

    /**
     * Handler for RMD (remove directory) command. Removes a directory.
     *
     * @param dir directory to be deleted.
     */
    private fun handleRmd(dir: String?) {
        var filename: String? = currDirectory

        // only alphanumeric folder names are allowed
        if (dir != null && dir.matches("^[a-zA-Z0-9]+$".toRegex())) {
            filename = filename + fileSeparator + dir

            // check if file exists, is directory
            val d = File(filename)

            if (d.exists() && d.isDirectory()) {
                d.delete()

                sendMsgToClient("250 Directory was successfully removed")
            } else {
                sendMsgToClient("550 Requested action not taken. File unavailable.")
            }
        } else {
            sendMsgToClient("550 Invalid file name.")
        }
    }

    /**
     * Handler for the TYPE command. The type command sets the transfer mode to
     * either binary or ascii mode
     *
     * @param mode Transfer mode: "a" for Ascii. "i" for image/binary.
     */
    private fun handleType(mode: String) {
        if (mode.uppercase(Locale.getDefault()) == "A") {
            transferMode = transferType.ASCII
            sendMsgToClient("200 OK")
        } else if (mode.uppercase(Locale.getDefault()) == "I") {
            transferMode = transferType.BINARY
            sendMsgToClient("200 OK")
        } else sendMsgToClient("504 Not OK")
    }

    /**
     * Handler for the RETR (retrieve) command. Retrieve transfers a file from the
     * ftp server to the client.
     *
     * @param file The file to transfer to the user
     */
    private fun handleRetr(file: String?) {
        val f = File(currDirectory + fileSeparator + file)

        if (!f.exists()) {
            sendMsgToClient("550 File does not exist")
        } else {
            // Binary mode

            if (transferMode == transferType.BINARY) {
                var fout: BufferedOutputStream? = null
                var fin: BufferedInputStream? = null

                sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName())

                try {
                    // create streams
                    fout = BufferedOutputStream(dataConnection!!.getOutputStream())
                    fin = BufferedInputStream(FileInputStream(f))
                } catch (e: Exception) {
                    debugOutput("Could not create file streams")
                }

                debugOutput("Starting file transmission of " + f.getName())

                // write file with buffer
                val buf = ByteArray(1024)
                var l = 0
                try {
                    while ((fin!!.read(buf, 0, 1024).also { l = it }) != -1) {
                        fout!!.write(buf, 0, l)
                    }
                } catch (e: IOException) {
                    debugOutput("Could not read from or write to file streams")
                    e.printStackTrace()
                }

                // close streams
                try {
                    fin!!.close()
                    fout!!.close()
                } catch (e: IOException) {
                    debugOutput("Could not close file streams")
                    e.printStackTrace()
                }

                debugOutput("Completed file transmission of " + f.getName())

                sendMsgToClient("226 File transfer successful. Closing data connection.")
            } else {
                sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName())

                var rin: BufferedReader? = null
                var rout: PrintWriter? = null

                try {
                    rin = BufferedReader(FileReader(f))
                    rout = PrintWriter(dataConnection!!.getOutputStream(), true)
                } catch (e: IOException) {
                    debugOutput("Could not create file streams")
                }

                var s: String?

                try {
                    while ((rin!!.readLine().also { s = it }) != null) {
                        rout!!.println(s)
                    }
                } catch (e: IOException) {
                    debugOutput("Could not read from or write to file streams")
                    e.printStackTrace()
                }

                try {
                    rout!!.close()
                    rin!!.close()
                } catch (e: IOException) {
                    debugOutput("Could not close file streams")
                    e.printStackTrace()
                }
                sendMsgToClient("226 File transfer successful. Closing data connection.")
            }
        }
        closeDataConnection()
    }

    /**
     * Handler for STOR (Store) command. Store receives a file from the client and
     * saves it to the ftp server.
     *
     * @param file The file that the user wants to store on the server
     */
    private fun handleStor(file: String?) {
        if (file == null) {
            sendMsgToClient("501 No filename given")
        } else {
            val f = File(currDirectory + fileSeparator + file)

            if (f.exists()) {
                sendMsgToClient("550 File already exists")
            } else {
                // Binary mode

                if (transferMode == transferType.BINARY) {
                    var fout: BufferedOutputStream? = null
                    var fin: BufferedInputStream? = null

                    sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName())

                    try {
                        // create streams
                        fout = BufferedOutputStream(FileOutputStream(f))
                        fin = BufferedInputStream(dataConnection!!.getInputStream())
                    } catch (e: Exception) {
                        debugOutput("Could not create file streams")
                    }

                    debugOutput("Start receiving file " + f.getName())

                    // write file with buffer
                    val buf = ByteArray(1024)
                    var l = 0
                    try {
                        while ((fin!!.read(buf, 0, 1024).also { l = it }) != -1) {
                            fout!!.write(buf, 0, l)
                        }
                    } catch (e: IOException) {
                        debugOutput("Could not read from or write to file streams")
                        e.printStackTrace()
                    }

                    // close streams
                    try {
                        fin!!.close()
                        fout!!.close()
                    } catch (e: IOException) {
                        debugOutput("Could not close file streams")
                        e.printStackTrace()
                    }

                    debugOutput("Completed receiving file " + f.getName())

                    sendMsgToClient("226 File transfer successful. Closing data connection.")
                } else {
                    sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName())

                    var rin: BufferedReader? = null
                    var rout: PrintWriter? = null

                    try {
                        rin = BufferedReader(InputStreamReader(dataConnection!!.getInputStream()))
                        rout = PrintWriter(FileOutputStream(f), true)
                    } catch (e: IOException) {
                        debugOutput("Could not create file streams")
                    }

                    var s: String?

                    try {
                        while ((rin!!.readLine().also { s = it }) != null) {
                            rout!!.println(s)
                        }
                    } catch (e: IOException) {
                        debugOutput("Could not read from or write to file streams")
                        e.printStackTrace()
                    }

                    try {
                        rout!!.close()
                        rin!!.close()
                    } catch (e: IOException) {
                        debugOutput("Could not close file streams")
                        e.printStackTrace()
                    }
                    sendMsgToClient("226 File transfer successful. Closing data connection.")
                }
            }
            closeDataConnection()
        }
    }

    /**
     * Debug output to the console. Also includes the Thread ID for better
     * readability.
     *
     * @param msg Debug message
     */
    private fun debugOutput(msg: String?) {
        if (debugMode) {
            println("Thread " + this.getId() + ": " + msg)
        }
    }
}
