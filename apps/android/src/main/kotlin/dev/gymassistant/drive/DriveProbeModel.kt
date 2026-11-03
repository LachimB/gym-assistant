package dev.gymassistant.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gymassistant.m0.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveProbeModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("synthetic-drive-probe-v1", 0)
    private val mutableStatus = MutableStateFlow("Synthetic test only. No workout upload or stored app credentials.")
    val status: StateFlow<String> = mutableStatus
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy
    // Request state and tokens are transient. The approved email is private build configuration.
    private var email = ""
    private var reading = false

    fun begin(expectedEmail: String): Boolean {
        if (mutableBusy.value) return false
        val value = expectedEmail.trim()
        if (BuildConfig.SYNTHETIC_DRIVE_ACCOUNT.isBlank() || !value.equals(BuildConfig.SYNTHETIC_DRIVE_ACCOUNT, ignoreCase = true)) {
            mutableStatus.value = "This test build is not configured for that approved personal account."; return false
        }
        if (!value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            mutableStatus.value = "Enter the approved personal Google account email."; return false
        }
        val attempts = preferences.getInt("authorization-attempts", 0)
        if (attempts !in 0 until 6 || !preferences.edit().putInt("authorization-attempts", attempts + 1).commit()) {
            mutableStatus.value = "Synthetic authorization attempt limit reached. Stop for review; do not clear app data."; return false
        }
        email = value
        mutableBusy.value = true
        mutableStatus.value = "Waiting for Google authorization for the selected personal account…"
        return true
    }

    fun failed() {
        if (reading) return // A duplicate SDK callback must not unlock a second concurrent probe.
        email = ""; mutableBusy.value = false
        mutableStatus.value = "Authorization did not complete. Workout data is unchanged; no automatic retry."
    }

    fun authorized(scopes: Collection<String>, token: String?) {
        if (reading) return
        if (!mutableBusy.value || !exactDriveScope(scopes) || token.isNullOrBlank()) { failed(); return }
        reading = true
        val expectedEmail = email
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val transport = GoogleProbeTransport {
                        synchronized(preferences) {
                            val used = preferences.getInt("direct-requests", 0)
                            check(used in 0 until 20 && preferences.edit().putInt("direct-requests", used + 1).commit()) {
                                "Synthetic request limit reached"
                            }
                        }
                    }
                    runSyntheticDriveProbe(token, expectedEmail, transport)
                }
                mutableStatus.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never print network/SDK exceptions or provider bodies, which may contain credentials.
                mutableStatus.value = "Synthetic Drive probe stopped. Check the personal account, connection or test-file grant. No automatic retry or workout upload."
            } finally {
                email = ""; reading = false; mutableBusy.value = false
            }
        }
    }
}
