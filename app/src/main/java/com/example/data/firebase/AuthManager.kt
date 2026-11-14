package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val user: FirebaseUser?) : AuthResult
    data object Cancelled : AuthResult
    data object Unauthenticated : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Coordinates Google Sign-In via Android's [CredentialManager], Firebase Authentication
 * token exchange, credential-state sign-out, and reactive auth-state observation.
 */
class AuthManager(
    context: Context,
    private val auth: FirebaseAuth = Firebase.auth,
    private val credentialManager: CredentialManager = CredentialManager.create(context.applicationContext)
) {
    private val appContext: Context = context.applicationContext

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Emits the current [FirebaseUser] whenever Firebase Authentication state changes.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Attempts silent sign-in for returning users who have previously authorized this app.
     */
    suspend fun attemptAutoSignIn(uiContext: Context): AuthResult {
        auth.currentUser?.let { existingUser ->
            return AuthResult.Success(existingUser)
        }

        val clientId = resolveWebClientId() ?: return AuthResult.Unauthenticated

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val targetContext = uiContext.findActivity() ?: uiContext
            val result = credentialManager.getCredential(targetContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                AuthResult.Success(authResult.user)
            } else {
                AuthResult.Unauthenticated
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in unavailable: ${e.message}")
            AuthResult.Unauthenticated
        }
    }

    /**
     * Launches the interactive Credential Manager Google Sign-In bottom sheet and exchanges
     * the resulting Google ID token with Firebase Authentication.
     */
    suspend fun signInWithGoogle(uiContext: Context): AuthResult {
        val clientId = resolveWebClientId()
            ?: return AuthResult.Error("Google Sign-In configuration missing: default_web_client_id not found")

        val activity = uiContext.findActivity()
            ?: return AuthResult.Error("Unable to find host Activity for Credential Manager.")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                AuthResult.Success(authResult.user)
            } else {
                AuthResult.Error("Unexpected credential type returned.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available: ${e.message}", e)
            AuthResult.Error("No Google account found on this device. Add a Google account in Settings and try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthResult.Error(e.localizedMessage ?: "Sign in failed")
        }
    }

    /**
     * Clears Credential Manager session state and signs out from Firebase Authentication.
     */
    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    private fun resolveWebClientId(): String? = try {
        appContext.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Unable to resolve default_web_client_id", e)
        null
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        const val TAG = "SkillRankAuth"
    }
}
