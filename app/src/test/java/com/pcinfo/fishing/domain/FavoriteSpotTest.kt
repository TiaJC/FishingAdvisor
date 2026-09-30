package com.pcinfo.fishing.domain

import com.pcinfo.fishing.domain.model.FavoriteSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏钓点的距离与去重判定。
 *
 * 这里只测纯计算部分：FavoritesStore 依赖 Android Context，单元测试环境跑不了，
 * 而「两点算不算同一个钓点」正是最容易出错、也最影响体验的一段逻辑。
 */
class FavoriteSpotTest {

    private fun spot(lat: Double, lon: Double, id: String = "1") =
        FavoriteSpot(id = id, name = "测试钓点", latitude = lat, longitude = lon)

    @Test
    fun `同一坐标距离为零`() {
        val s = spot(22.5431, 114.0579)
        assertEquals(0.0, s.distanceMetersTo(22.5431, 114.0579), 1.0)
    }

    @Test
    fun `纬度差一度约 111 公里`() {
        val s = spot(22.0, 114.0)
        val d = s.distanceMetersTo(23.0, 114.0)
        assertTrue("实际 $d 米", d in 110_000.0..112_000.0)
    }

    @Test
    fun `阈值内判定为同一钓点，阈值外不是`() {
        val s = spot(22.5431, 114.0579)
        // 纬度差 0.003° ≈ 333 米
        val near = s.distanceMetersTo(22.5461, 114.0579)
        // 纬度差 0.01° ≈ 1.1 公里
        val far = s.distanceMetersTo(22.5531, 114.0579)
        assertTrue("近点 $near 米应判为同一钓点", near <= FavoriteSpot.SAME_SPOT_METERS)
        assertTrue("远点 $far 米不应判为同一钓点", far > FavoriteSpot.SAME_SPOT_METERS)
    }

    @Test
    fun `转成坐标时标记为收藏来源且无精度`() {
        val point = spot(22.5, 114.0).toGeoPoint()
        assertEquals(FavoriteSpot.PROVIDER_FAVORITE, point.provider)
        assertEquals(null, point.accuracyMeters)
        assertTrue(point.isValid())
    }
}
