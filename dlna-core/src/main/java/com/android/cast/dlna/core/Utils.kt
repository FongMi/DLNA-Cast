/*
 * Copyright (C) 2014 Kevin Shen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.cast.dlna.core

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.provider.MediaStore.Video
import android.text.format.Formatter
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

object Utils {
    // ------------------------------------------------------------------------------------------------------------------------
    // ---- Device Wifi Information
    // ------------------------------------------------------------------------------------------------------------------------
    private const val UNKNOWN = "<unknown>"
    private const val WIFI_DISABLED = "<disabled>"
    private const val WIFI_NO_CONNECT = "<not connect>"
    private const val PERMISSION_DENIED = "<permission denied>"

    fun getIpAddress(context: Context): String {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return manager?.connectionInfo?.ipAddress
            ?.takeIf { it != 0 }
            ?.let { Formatter.formatIpAddress(it) }
            ?: getHostAddress()
    }

    private fun getHostAddress(): String =
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress.orEmpty()

    /**
     * need permission 'Manifest.permission.ACCESS_FINE_LOCATION' and 'Manifest.permission.ACCESS_WIFI_STATE' if system sdk >= Android O.
     */
    fun getWiFiName(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return WIFI_DISABLED
        val wifiInfo = wifiManager.connectionInfo ?: return WIFI_NO_CONNECT
        return if (wifiInfo.ssid == WifiManager.UNKNOWN_SSID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.checkSelfPermission(permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (wifiManager.configuredNetworks != null) {
                        for (config in wifiManager.configuredNetworks) {
                            if (config.networkId == wifiInfo.networkId) {
                                return config.SSID.replace("\"".toRegex(), "")
                            }
                        }
                    }
                } else {
                    PERMISSION_DENIED
                }
            } else {
                return WIFI_NO_CONNECT
            }
            UNKNOWN
        } else {
            wifiInfo.ssid.replace("\"".toRegex(), "")
        }
    }

    fun getHttpBaseUrl(context: Context, port: Int = 9091) = "http://${getIpAddress(context)}:$port/"

    // ------------------------------------------------------------------------------------------------------------------------
    // ---- Others
    // ------------------------------------------------------------------------------------------------------------------------
    fun parseUri2File(context: Context, uri: Uri): File? {
        return if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]
            val path = if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            } else if ("raw".equals(type, ignoreCase = true)) {
                split[1]
            } else {
                getDataColumn(context, Video.Media.EXTERNAL_CONTENT_URI, "${MediaColumns._ID}=?", arrayOf(split[1]))
            }
            path?.let { File(it) }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            getDataColumn(context, uri)?.let { File(it) }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            uri.path?.let { File(it) }
        } else {
            null
        }
    }

    private fun getDataColumn(context: Context, uri: Uri, selection: String? = null, selectionArgs: Array<String>? = null): String? {
        val projection = arrayOf(MediaColumns.DATA)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(projection[0])
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                cursor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
