package com.rinse.app.data

import android.content.Context
import android.net.Uri
import com.rinse.app.model.LaundryMachine
import org.json.JSONArray
import org.json.JSONObject

/** Seed the original five machines once, then persist the entire user-managed list. */
class MachineStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("rinse_machines", Context.MODE_PRIVATE)

    fun load(): List<LaundryMachine> {
        val saved = preferences.getString("machines", "[]") ?: "[]"
        val existing: List<LaundryMachine> = try {
            val array = JSONArray(saved)
            buildList<LaundryMachine> {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val url = validatedUrl(entry.optString("url")) ?: continue
                    val name = entry.optString("name").trim()
                    if (name.isBlank() || any { it.url == url }) continue
                    add(LaundryMachine(name, entry.optString("type", "洗衣机"), url))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (preferences.getBoolean("initialized", false)) return existing

        // Previous versions stored only custom machines. Keep those while seeding the defaults.
        val defaults = LaundryRepository.defaultMachines
        val defaultIds = defaults.map { Uri.parse(it.url).getQueryParameter("NQT") }.toSet()
        val seeded = defaults + existing.filterNot {
            Uri.parse(it.url).getQueryParameter("NQT") in defaultIds
        }
        save(seeded)
        return seeded
    }

    fun save(machines: List<LaundryMachine>): Boolean {
        val array = JSONArray()
        machines.forEach { machine ->
            array.put(JSONObject().put("name", machine.name).put("type", machine.type).put("url", machine.url))
        }
        return preferences.edit().putString("machines", array.toString())
            .putBoolean("initialized", true).commit()
    }

    companion object {
        /** Scan results must be the same HTTPS Qiekj equipment links supported by LaundryRepository. */
        fun validatedUrl(raw: String): String? {
            val value = raw.trim()
            return try {
                val uri = Uri.parse(value)
                if (uri.scheme != "https" || !uri.host.equals("h5.qiekj.com", ignoreCase = true) ||
                    uri.path != "/skip" || uri.getQueryParameter("NQT").isNullOrBlank() ||
                    uri.userInfo != null
                ) null else value
            } catch (_: Exception) {
                null
            }
        }
    }
}
