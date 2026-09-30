package com.pcinfo.fishing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.OxygenTolerance

private val KNOWLEDGE_TABS = listOf("算法", "鱼种", "数据", "证据", "术语")

/**
 * 百科页：把支撑钓鱼指数的全部依据摊开给用户看。
 *
 * 设计取向：与其给一个「黑箱分数」，不如把公式、分档、鱼种参数、数据来源与
 * 每项因子的证据强度都列出来。用户能校验，才可能信任；发现不合理，也知道该改哪一条。
 */
@Composable
fun KnowledgeScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 显式 fillMaxWidth：否则 Surface 会按文字宽度收缩，背景不能铺满整行
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "钓鱼百科",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "算法、鱼种档案、数据来源与证据强度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = tabIndex,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            KNOWLEDGE_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tabIndex) {
                0 -> AlgorithmTab(settings)
                1 -> SpeciesTab()
                2 -> DataSourceTab()
                3 -> EvidenceTab()
                4 -> GlossaryTab()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ 算法

@Composable
private fun AlgorithmTab(settings: AppSettings) {
    val spec = FishingScoreEngine.algorithmSpec(
        species = settings.species,
        weights = settings.weights,
        options = settings.options
    )

    SectionCard(title = "总分公式") {
        Text(
            text = spec.formula,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "当前生效配置：目标鱼种 ${settings.species.profile.name}，" +
                "权重合计按 ${settings.weights.total} 归一化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionCard(title = "因子权重") {
        spec.factors.forEach { factor ->
            KeyValueRow(key = factor.label, value = factor.weightText)
            Text(
                text = factor.basis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }

    SectionCard(title = "分档规则") {
        spec.factors.forEach { factor ->
            Text(
                text = factor.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            factor.bands.forEach { band ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        text = band.condition,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${band.score} 分",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    SectionCard(title = "等级划分") {
        spec.levels.forEach { level ->
            KeyValueRow(
                key = "${level.range} 分",
                value = "${level.level.emoji} ${level.level.label}"
            )
        }
    }

    SectionCard(title = "计算备注") {
        spec.notes.forEach { Bullet(text = it) }
    }
}

// ------------------------------------------------------------------ 鱼种

@Composable
private fun SpeciesTab() {
    Text(
        text = "共 ${FishSpecies.selectable.size} 个鱼种档案。水温区间为物种层面的群体规律，" +
            "个体差异、地域种群与钓场类型都会带来偏差。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FishSpecies.selectable.forEach { species ->
        val p = species.profile
        SectionCard(title = "${p.emoji} ${p.name}") {
            Text(
                text = p.habit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            KeyValueRow(key = "栖息水层", value = p.layer.label)
            KeyValueRow(
                key = "最适水温",
                value = "%.0f ~ %.0f℃".format(p.tempOptimal.start, p.tempOptimal.endInclusive)
            )
            KeyValueRow(
                key = "可摄食水温",
                value = "%.0f ~ %.0f℃".format(p.tempActive.start, p.tempActive.endInclusive)
            )
            KeyValueRow(key = "耐低氧", value = "${p.oxygenTolerance.label}（${p.oxygenTolerance.note}）")
            KeyValueRow(key = "活动节律", value = p.activity.label)
            KeyValueRow(
                key = "常规水深",
                value = "%.1f ~ %.1f 米".format(p.baseDepthM.start, p.baseDepthM.endInclusive)
            )
            KeyValueRow(key = "偏好结构", value = p.preferredSpots.joinToString("、") { it.label })
            KeyValueRow(key = "常规饵料", value = p.baits.joinToString("、"))
            KeyValueRow(key = "推荐钓组", value = p.rig)
            KeyValueRow(key = "钓法", value = p.method)
        }
    }

    SectionCard(title = "耐低氧等级说明") {
        OxygenTolerance.entries.forEach { level ->
            KeyValueRow(key = level.label, value = level.note)
        }
    }
}

// ------------------------------------------------------------------ 数据

@Composable
private fun DataSourceTab() {
    SectionCard(title = "气象数据 · Open-Meteo") {
        Text(
            text = "接口 https://api.open-meteo.com/v1/forecast，免费且无需 API Key。" +
                "请求过去 24 小时 + 未来 30 小时逐小时数据，时区按坐标自动判定。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        KeyValueRow(key = "temperature_2m", value = "2 米气温（℃）")
        KeyValueRow(key = "surface_pressure", value = "地表气压（hPa）")
        KeyValueRow(key = "wind_speed_10m", value = "10 米风速（km/h）")
        KeyValueRow(key = "wind_direction_10m", value = "风的来向（气象角度，0°=正北）")
        KeyValueRow(key = "wind_gusts_10m", value = "阵风风速（km/h）")
        KeyValueRow(key = "precipitation", value = "小时降水量（mm）")
        KeyValueRow(key = "precipitation_probability", value = "降水概率（%）")
        KeyValueRow(key = "cloud_cover", value = "云量（%）")
        KeyValueRow(key = "relative_humidity_2m", value = "相对湿度（%）")
        KeyValueRow(key = "apparent_temperature", value = "体感温度（℃）")
        KeyValueRow(key = "weather_code", value = "WMO 天气现象代码")
        KeyValueRow(key = "sunrise / sunset", value = "当日日出日落时刻")
    }

    SectionCard(title = "数据精度与时效") {
        Bullet(text = "Open-Meteo 为多模式融合的再分析与预报产品，水平分辨率约 11 公里，逐小时输出。")
        Bullet(text = "气压、风速为模式网格值，与钓场实测会有偏差；峡谷、湖湾等地形复杂处偏差更大。")
        Bullet(text = "天气数据本地缓存 5 分钟，频繁刷新不会重复打接口。")
        Bullet(text = "本应用不使用海平面气压，而是地表气压，因此高海拔地区读数天然偏低，属正常现象。")
    }

    SectionCard(title = "地址数据 · BigDataCloud") {
        Text(
            text = "先尝试系统 Geocoder（快、零请求），失败时回退 BigDataCloud 免密钥接口，" +
                "并以 zh 请求中文地址。地址仅用于展示，失败不影响钓鱼指数计算。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "地址结果缓存 10 分钟。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionCard(title = "定位通道") {
        KeyValueRow(key = "GPS 卫星定位", value = "精度最高，超时 8 秒，室内可能拿不到")
        KeyValueRow(key = "网络基站定位", value = "室内可用，精度较低，超时 5 秒")
        KeyValueRow(key = "GMS 融合定位", value = "仅作最后兜底，超时 5 秒（国产 ROM 多无 GMS）")
        Bullet(text = "每个通道都先读系统缓存位置，避免室内干等搜星。")
        Bullet(text = "经纬度仅用于查询天气与地址，不上传、不落库；日志对坐标做脱敏。")
    }

    SectionCard(title = "水温是怎么算出来的") {
        Text(
            text = "水温 ≈ 0.6 × 过去24小时平均气温 + 0.4 × 当前气温 − 1.0℃",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Bullet(text = "前项承担季节基准（水比热容约为空气 4 倍，水温变化滞后且被平滑）。")
        Bullet(text = "后项让水温跟随当日冷暖趋势。")
        Bullet(text = "常数项粗略补偿蒸发降温。")
        Bullet(text = "局限：无法反映水深分层，夏季表层与 3 米以下可差 3-6℃；" +
            "浅塘、水库、流水差异明显；冰期与泄洪期误差很大。")
        Bullet(text = "若有水温计或探鱼器，请实测校正；设置页可关闭估算直接用气温评分。")
    }
}

// ------------------------------------------------------------------ 证据

@Composable
private fun EvidenceTab() {
    SectionCard(title = "各因子的证据强度") {
        Text(
            text = "把「经验」和「机理」分开标注，是为了让你知道哪些结论可以放心用、" +
                "哪些只能当参考。强度越高，说明背后越有可量化的生理或物理事实。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        EVIDENCE_ROWS.forEach { row ->
            EvidenceRow(level = row.first, name = row.second, detail = row.third)
        }
    }

    SectionCard(title = "一个容易被忽略的定量事实") {
        Text(
            text = "气压从 1013 降到 983 hPa（相当于强锋面过境，降幅 30 hPa），" +
                "对水体饱和溶氧的影响只相当于水温升高约 1.5℃。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "这就是为什么默认权重里气压类合计 45 偏高——它更多是**天气系统的代理指标**" +
                "（低气压常伴随高温、静风、阴天，这三者才是真正压低溶氧的因素），" +
                "而不是溶氧的直接主因。若你更相信水温，可在设置页调低气压权重。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionCard(title = "存在真实分歧的部分") {
        Bullet(text = "气压趋势：国内通行说法是「骤降停口」；欧美主流经验则认为锋面前的缓慢下降" +
            "（2-12 小时）是进食高峰。两者可能都对，取决于季节温度背景。当前按国内口径实现。")
        Bullet(text = "风向方位：物理上风向本身不影响鱼，起作用的是风引起的水体混合与饵鱼迁移。" +
            "「西南风钓鱼空」是中国东部季风区的气候口诀，换个气候区未必成立，可在设置页关闭。")
    }
}

private val EVIDENCE_ROWS: List<Triple<String, String, String>> = listOf(
    Triple(
        "强",
        "水温 / 溶氧",
        "鱼是变温动物，代谢与消化酶活性由水温直接决定。" +
            "例如鲤在 8-14℃ 时消化需约 20 小时，26-30℃ 时仅需 4-6 小时，这类数据是可量化的生理事实。"
    ),
    Triple(
        "中",
        "风力",
        "风浪增氧的物理机制明确（增大气液接触面、破坏分层），" +
            "但「2-3 级最佳」的阈值来自作钓便利性与经验，边界是模糊的。"
    ),
    Triple(
        "中",
        "时段",
        "多数淡水鱼晨昏觅食有行为学支持（弱光下捕食效率高、岸边天敌压力小），" +
            "但夜间光合作用停止导致溶氧最低这一条同样成立，两者叠加后结论因水体而异。"
    ),
    Triple(
        "中",
        "降水",
        "大雨停口与人身安全风险是明确的；「小雨增氧鱼口变好」则偏向经验，且雨前雨中雨后差别很大。"
    ),
    Triple(
        "弱-中",
        "气压趋势",
        "作为天气系统的代理指标有价值；作为溶氧的直接原因则量级不足。" +
            "国内外经验在方向上还存在分歧（见下）。"
    ),
    Triple(
        "弱",
        "气压绝对值",
        "30 hPa 的气压变化对溶氧的影响仅相当于水温变化 1.5℃。" +
            "高海拔地区读数天然偏低，也不能直接套用海平面标准。"
    ),
    Triple(
        "弱",
        "风向方位",
        "「东风好、西南风差」是地域性气候口诀，缺乏跨区域一致的机理支撑，" +
            "已在设置页提供关闭开关。"
    )
)

@Composable
private fun EvidenceRow(level: String, name: String, detail: String) {
    val color = when (level) {
        "强" -> Color(0xFF1B8A3C)
        "中" -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = level,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ------------------------------------------------------------------ 术语

@Composable
private fun GlossaryTab() {
    SectionCard(title = "术语表") {
        GLOSSARY.forEach { (term, meaning) ->
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(
                    text = term,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val GLOSSARY: List<Pair<String, String>> = listOf(
    "蒲福风级" to "0-12 级风力等级，按 10 米高处的风速划分。2-3 级为 6-19 km/h，是作钓最舒适区间。",
    "地表气压" to "测站所在高度的实际气压，随海拔升高而降低；与天气预报常用的海平面气压不同。",
    "溶解氧" to "水中溶解的氧气浓度（mg/L）。低于鱼种窒息点时鱼会浮头甚至死亡，是摄食意愿的直接约束。",
    "窒息点" to "鱼种能耐受的最低溶氧浓度。鲢鳙约 1.7-2.3 mg/L 即浮头；鲤鲫约 0.8-1.2 mg/L；" +
        "乌鳢、鲶鱼因具辅助呼吸器官可耐受更低。",
    "温跃层" to "夏季深水湖中水温随深度急剧下降的水层。鱼常聚集在温跃层上方，因为下方既冷又缺氧。",
    "晨昏性" to "在日出与日落前后活动最频繁的习性。多数淡水鱼属此类，弱光下敢靠边觅食。",
    "WMO 天气代码" to "世界气象组织定义的天气现象编码，如 0=晴、61-65=降雨、95=雷阵雨。",
    "阵风" to "短时间内的最大风速，通常高于平均风速，判断抛竿与安全风险时更值得看。",
    "体感温度" to "综合气温、湿度、风速与辐射后人体感受到的冷暖，与鱼的感受无关，仅供穿着参考。",
    "气压趋势" to "本应用中指「当前时刻气压 − 3 小时前气压」，历史不足 3 小时时按线性折算。" +
        "单位 hPa/3h。"
)

// ------------------------------------------------------------------ 通用件

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
