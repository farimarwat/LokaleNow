package com.farimarwat.lokalenow.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Calculates the SHA-256 hash of the specified file.
 *
 * @param file The file to calculate the hash for.
 * @return The calculated hash as a hexadecimal string.
 */
fun File.calculateFileHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    FileInputStream(this).use { inputStream ->
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().encodeHex()
}

/**
 * Encodes the byte array as a hexadecimal string.
 *
 * @return The hexadecimal representation of the byte array.
 */
private fun ByteArray.encodeHex(): String {
    return joinToString("") { "%02x".format(it) }
}