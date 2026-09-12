package com.example.androidkotlinapp

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** What came of trying to sign in or sign up. */
sealed class AuthResult {
    data class Success(val email: String) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * The one account this app knows about, kept on the phone.
 *
 * There is no server yet, so this is a local record and nothing more -- but the password is
 * still never written down as typed. It is salted and hashed, so a copy of the preferences
 * file gives up nothing, and the same code will keep working when a real backend arrives.
 */
object LocalAccount {

    private const val PREFS = "account_prefs"
    private const val KEY_EMAIL = "email"
    private const val KEY_HASH = "hash"
    private const val KEY_SALT = "salt"
    private const val KEY_SIGNED_IN = "signed_in"

    private const val MIN_PASSWORD = 6

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once an account exists on this phone, whoever is signed in. */
    fun exists(context: Context): Boolean =
        !prefs(context).getString(KEY_HASH, null).isNullOrEmpty()

    fun isSignedIn(context: Context): Boolean =
        exists(context) && prefs(context).getBoolean(KEY_SIGNED_IN, false)

    fun email(context: Context): String = prefs(context).getString(KEY_EMAIL, "").orEmpty()

    fun signOut(context: Context) {
        prefs(context).edit().putBoolean(KEY_SIGNED_IN, false).apply()
    }

    // ---------------------------------------------------------------- the two doors

    fun signUp(context: Context, email: String, password: String): AuthResult {
        val cleaned = email.trim().lowercase()
        validate(cleaned, password)?.let { return AuthResult.Failure(it) }
        if (exists(context)) {
            return AuthResult.Failure("An account already exists on this phone. Sign in instead.")
        }

        // The server is asked to claim the address before anything is written here, so two
        // devices cannot both believe they own the same account. An unreachable server is not
        // a reason to refuse: the account is made locally and mirrored on the next push.
        when (val remote = SyncClient.register(context, cleaned, password)) {
            is SyncClient.AuthOutcome.Failed -> return AuthResult.Failure(remote.message)
            is SyncClient.AuthOutcome.Offline -> Unit
            is SyncClient.AuthOutcome.Ok -> Unit
        }

        val salt = newSalt()
        prefs(context).edit()
            .putString(KEY_EMAIL, cleaned)
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(password, salt))
            .putBoolean(KEY_SIGNED_IN, true)
            .apply()

        restoreIfEmpty(context, cleaned, password)
        return AuthResult.Success(cleaned)
    }

    /**
     * Pulls the stored copy down, but only onto a phone with nothing of its own.
     *
     * This is the whole of "local first": the device's database is the truth, and a restore is
     * something that happens to an empty one. Running it over live data would resolve a
     * conflict by destroying the newer side, which is exactly what this architecture is for
     * avoiding.
     */
    private fun restoreIfEmpty(context: Context, email: String, password: String) {
        try {
            if (SyncClient.hasLocalData(context)) {
                SyncClient.push(context, email, password)
            } else {
                SyncClient.restore(context, email, password)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun signIn(context: Context, email: String, password: String): AuthResult {
        val cleaned = email.trim().lowercase()
        validate(cleaned, password)?.let { return AuthResult.Failure(it) }
        if (!exists(context)) {
            // Nothing on this phone -- which is exactly what a reinstall looks like. Ask the
            // server instead of refusing, and rebuild the local record from what it says.
            return when (val remote = SyncClient.login(context, cleaned, password)) {
                is SyncClient.AuthOutcome.Ok -> {
                    val salt = newSalt()
                    prefs(context).edit()
                        .putString(KEY_EMAIL, cleaned)
                        .putString(KEY_SALT, salt)
                        .putString(KEY_HASH, hash(password, salt))
                        .putBoolean(KEY_SIGNED_IN, true)
                        .apply()
                    restoreIfEmpty(context, cleaned, password)
                    AuthResult.Success(cleaned)
                }
                is SyncClient.AuthOutcome.Failed -> AuthResult.Failure(remote.message)
                is SyncClient.AuthOutcome.Offline -> AuthResult.Failure(
                    "No account on this phone, and the server cannot be reached."
                )
            }
        }

        val p = prefs(context)
        if (p.getString(KEY_EMAIL, "") != cleaned) {
            return AuthResult.Failure("That email does not match the account on this phone.")
        }
        val salt = p.getString(KEY_SALT, "").orEmpty()
        if (hash(password, salt) != p.getString(KEY_HASH, "")) {
            return AuthResult.Failure("Wrong password. Try again.")
        }

        p.edit().putBoolean(KEY_SIGNED_IN, true).apply()

        // Signing in against the local record still has to reach the server, because this is
        // the only moment the plaintext password exists and the encryption key can be derived
        // from it. An account made before syncing existed links itself right here, the first
        // time its owner signs in again -- and an unreachable server costs nothing but a
        // still-unlinked account that will try again next time.
        SyncClient.register(context, cleaned, password)

        restoreIfEmpty(context, cleaned, password)
        return AuthResult.Success(cleaned)
    }

    /**
     * True when an account exists here but has never been linked to the server.
     *
     * The password is not kept, and the key cannot be derived without it, so there is no way
     * to link one of these in the background: it takes one sign-in.
     */
    fun needsSyncLink(context: Context): Boolean =
        exists(context) && !SyncClient.isLinked(context)

    /** The first thing that is wrong with these details, or null when nothing is. */
    private fun validate(email: String, password: String): String? = when {
        email.isBlank() -> "Enter your email."
        !email.contains("@") || !email.substringAfter("@").contains(".") ->
            "That does not look like an email address."
        password.isBlank() -> "Enter a password."
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        else -> null
    }

    // ---------------------------------------------------------------- password handling

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Salted SHA-256. Not what a real server should use -- that wants a slow hash built to
     * resist guessing -- but enough that the stored file never holds the password itself.
     */
    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$salt::$password".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
