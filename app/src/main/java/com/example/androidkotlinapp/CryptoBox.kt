package com.example.androidkotlinapp

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * The lock on everything that leaves the phone.
 *
 * Two different values are derived from the one password, under two different salts:
 *
 *  - the **verifier** goes to the server and proves the password is right;
 *  - the **key** never leaves the device and is what actually encrypts the data.
 *
 * Both are slow to compute, so holding a database full of verifiers is no shortcut to any key.
 * The server stores ciphertext it has no means of opening -- which is the point: nobody with
 * access to the backend, the account owner included, can read a habit or a link out of it.
 *
 * The cost of that is absolute and worth stating plainly: there is no recovery. Forget the
 * password and the data is unreadable by anyone, for good.
 */
object CryptoBox {

    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private const val VERIFIER_DOMAIN = "focus-verifier-v1:"

    /**
     * What the server is told, so it can recognise the password without learning it.
     *
     * Salted with the email so the same password on two accounts produces two different
     * verifiers, and stretched so the stored value cannot be reversed by guessing quickly.
     */
    fun verifier(email: String, password: String): String {
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("$VERIFIER_DOMAIN${email.trim().lowercase()}".toByteArray())
        return derive(password, salt).joinToString("") { "%02x".format(it) }
    }

    /**
     * The AES key the data is actually encrypted under.
     *
     * [keySalt] is the random salt the server generated when the account was made, and is the
     * reason a fresh install can rebuild exactly the same key from the same password.
     */
    fun key(password: String, keySalt: String): SecretKeySpec =
        SecretKeySpec(derive(password, keySalt.toByteArray()), "AES")

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    // ---------------------------------------------------------------- sealing

    /** Ciphertext and the nonce it was made with, both base64, ready to be posted. */
    data class Sealed(val ciphertext: String, val iv: String)

    /**
     * Encrypts [plaintext] under [key].
     *
     * A fresh random nonce every time: GCM is only safe while a nonce is never reused with the
     * same key, and this is written on every push.
     */
    fun seal(key: SecretKeySpec, plaintext: String): Sealed {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val out = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Sealed(
            ciphertext = Base64.encodeToString(out, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Opens what [seal] produced, or returns null.
     *
     * A failure here means the wrong password or a tampered blob -- GCM authenticates as well
     * as encrypts, so altered ciphertext fails to open rather than decrypting to rubbish.
     */
    fun open(key: SecretKeySpec, ciphertext: String, iv: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
