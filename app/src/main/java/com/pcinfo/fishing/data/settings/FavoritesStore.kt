package com.pcinfo.fishing.data.settings

import android.content.Context
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.domain.model.FavoriteSpot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 收藏钓点持久化。
 *
 * 与 [com.pcinfo.fishing.data.settings.SettingsStore] 分开存放：
 * 「恢复默认设置」应该还原权重与开关，但不该把用户辛苦攒的钓点一起删掉。
 */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<FavoriteSpot> {
        val raw = prefs.getString(K_FAVORITES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FavoriteSpot>>(raw)
        } catch (t: Throwable) {
            // 数据损坏时宁可清空也不要让应用起不来
            Logger.w("favorites decode failed, cleared: ${t.message}")
            prefs.edit().remove(K_FAVORITES).apply()
            emptyList()
        }
    }

    private fun save(list: List<FavoriteSpot>) {
        prefs.edit().putString(K_FAVORITES, json.encodeToString(list)).apply()
    }

    /** 追加一个钓点，返回保存后的完整列表 */
    fun add(spot: FavoriteSpot): List<FavoriteSpot> {
        val next = load() + spot
        save(next)
        return next
    }

    fun remove(id: String): List<FavoriteSpot> {
        val next = load().filterNot { it.id == id }
        save(next)
        return next
    }

    private companion object {
        const val PREF_NAME = "fishing_favorites"
        const val K_FAVORITES = "favorites_json"
    }
}
