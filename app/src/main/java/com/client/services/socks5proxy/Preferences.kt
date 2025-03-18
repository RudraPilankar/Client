package com.client.services.socks5proxy

import android.content.Context
import android.content.SharedPreferences
import com.client.services.client.isSocks5ProxyRunning


class Preferences
    (context: Context) {
    private val prefs: SharedPreferences

    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS)
    }

    var workers: Int
        get() = prefs.getInt(WORKERS, 16)
        set(workers) {
            val editor = prefs.edit()
            editor.putInt(WORKERS, workers)
            editor.commit()
        }

    var listenAddress: String?
        get() = prefs.getString(LISTEN_ADDR, "::")
        set(addr) {
            val editor = prefs.edit()
            editor.putString(LISTEN_ADDR, addr)
            editor.commit()
        }

    var listenPort: Int
        get() = prefs.getInt(LISTEN_PORT, 1080)
        set(port) {
            val editor = prefs.edit()
            editor.putInt(LISTEN_PORT, port)
            editor.commit()
        }

    var uDPListenAddress: String?
        get() = prefs.getString(UDP_LISTEN_ADDR, "")
        set(addr) {
            val editor = prefs.edit()
            editor.putString(UDP_LISTEN_ADDR, addr)
            editor.commit()
        }

    var uDPListenPort: Int
        get() = prefs.getInt(UDP_LISTEN_PORT, 1080)
        set(port) {
            val editor = prefs.edit()
            editor.putInt(UDP_LISTEN_PORT, port)
            editor.commit()
        }

    var bindIPv4Address: String?
        get() = prefs.getString(BIND_IPV4_ADDR, "0.0.0.0")
        set(addr) {
            val editor = prefs.edit()
            editor.putString(BIND_IPV4_ADDR, addr)
            editor.commit()
        }

    var bindIPv6Address: String?
        get() = prefs.getString(BIND_IPV6_ADDR, "::")
        set(addr) {
            val editor = prefs.edit()
            editor.putString(BIND_IPV6_ADDR, addr)
            editor.commit()
        }

    var bindInterface: String?
        get() = prefs.getString(BIND_IFACE, "")
        set(name) {
            val editor = prefs.edit()
            editor.putString(BIND_IFACE, name)
            editor.commit()
        }

    var authUsername: String?
        get() = prefs.getString(AUTH_USER, "")
        set(user) {
            val editor = prefs.edit()
            editor.putString(AUTH_USER, user)
            editor.commit()
        }

    var authPassword: String?
        get() = prefs.getString(AUTH_PASS, "")
        set(pass) {
            val editor = prefs.edit()
            editor.putString(AUTH_PASS, pass)
            editor.commit()
        }

    var listenIPv6Only: Boolean
        get() = prefs.getBoolean(LISTEN_IPV6_ONLY, false)
        set(enable) {
            val editor = prefs.edit()
            editor.putBoolean(LISTEN_IPV6_ONLY, enable)
            editor.commit()
        }

    var enable: Boolean
        get() = if (prefs.getBoolean(ENABLE, false) != isSocks5ProxyRunning) {
            val editor = prefs.edit()
            editor.putBoolean(ENABLE, isSocks5ProxyRunning)
            editor.commit()
            prefs.getBoolean(ENABLE, false)
        } else {
            prefs.getBoolean(ENABLE, false)
        }
        set(enable) {
            val editor = prefs.edit()
            editor.putBoolean(ENABLE, enable)
            editor.commit()
        }

    val taskStackSize: Int
        get() = 8192

    companion object {
        const val PREFS_NAME: String = "SocksPrefs"
        const val WORKERS: String = "Workers"
        const val LISTEN_ADDR: String = "ListenAddr"
        const val LISTEN_PORT: String = "ListenPort"
        const val UDP_LISTEN_ADDR: String = "UDPListenAddr"
        const val UDP_LISTEN_PORT: String = "UDPListenPort"
        const val BIND_IPV4_ADDR: String = "BindIPv4Addr"
        const val BIND_IPV6_ADDR: String = "BindIPv6Addr"
        const val BIND_IFACE: String = "BindIface"
        const val AUTH_USER: String = "AuthUser"
        const val AUTH_PASS: String = "AuthPass"
        const val LISTEN_IPV6_ONLY: String = "ListenIPv6Only"
        const val ENABLE: String = "Enable"
    }
}
