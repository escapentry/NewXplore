package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.files.SortOrder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.exploredDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "explored_prefs",
)

data class ToolSettingsSnapshot(
  val hasPin: Boolean,
  val showHiddenFiles: Boolean,
  val sortOrder: SortOrder,
)

class PinPreferences(private val context: Context) {

  companion object {
    private val KEY_PIN_HASH = stringPreferencesKey("su_pin_hash")
    private val KEY_PIN_SALT = stringPreferencesKey("su_pin_salt")
    private val KEY_SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
    private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")

    private val random = SecureRandom()

    fun generateSaltHex(): String {
      val bytes = ByteArray(16)
      random.nextBytes(bytes)
      return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val input = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
      return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
      if (a.length != b.length) return false
      var diff = 0
      for (i in a.indices) {
        diff = diff or (a[i].code xor b[i].code)
      }
      return diff == 0
    }
  }

  val settingsFlow: Flow<ToolSettingsSnapshot> = context.exploredDataStore.data.map { prefs ->
    val hasPin = !prefs[KEY_PIN_HASH].isNullOrBlank() && !prefs[KEY_PIN_SALT].isNullOrBlank()
    val showHidden = prefs[KEY_SHOW_HIDDEN] ?: true
    val sortName = prefs[KEY_SORT_ORDER] ?: SortOrder.NAME.name
    val sortOrder = SortOrder.entries.firstOrNull { it.name == sortName } ?: SortOrder.NAME
    ToolSettingsSnapshot(
      hasPin = hasPin,
      showHiddenFiles = showHidden,
      sortOrder = sortOrder,
    )
  }

  suspend fun hasPin(): Boolean {
    val prefs = context.exploredDataStore.data.first()
    return !prefs[KEY_PIN_HASH].isNullOrBlank() && !prefs[KEY_PIN_SALT].isNullOrBlank()
  }

  suspend fun setPin(pin: String) {
    val salt = generateSaltHex()
    val hash = hashPin(pin, salt)
    context.exploredDataStore.edit { prefs ->
      prefs[KEY_PIN_SALT] = salt
      prefs[KEY_PIN_HASH] = hash
    }
  }

  suspend fun verifyPin(pin: String): Boolean {
    val prefs = context.exploredDataStore.data.first()
    val salt = prefs[KEY_PIN_SALT] ?: return false
    val expectedHash = prefs[KEY_PIN_HASH] ?: return false
    val candidateHash = hashPin(pin, salt)
    return constantTimeEquals(expectedHash, candidateHash)
  }

  suspend fun setShowHiddenFiles(show: Boolean) {
    context.exploredDataStore.edit { prefs ->
      prefs[KEY_SHOW_HIDDEN] = show
    }
  }

  suspend fun setSortOrder(order: SortOrder) {
    context.exploredDataStore.edit { prefs ->
      prefs[KEY_SORT_ORDER] = order.name
    }
  }
}
