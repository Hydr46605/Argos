/**
 * Local encrypted persistence of session material.
 *
 * Contracts are interfaces so Android hosts may back them with
 * EncryptedSharedPreferences while JVM hosts use the AES-GCM file store.
 * Nothing stored here is ever written unencrypted; a corrupted ciphertext
 * degrades into an empty store rather than an error.
 */
@file:Suppress("unused")

package it.hydr4.argo.storage
