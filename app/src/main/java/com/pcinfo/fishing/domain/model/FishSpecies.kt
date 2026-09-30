package com.pcinfo.fishing.domain.model

/**
 * 鱼种档案。
 *
 * 设计原则：这里只写「相对稳定、有文献或行业共识支撑」的物种属性
 * （栖息水层、最适水温、耐低氧能力、活动节律、偏好的水下结构），
 * 而把「今天该钓多深、选哪个钓位」这类随天气变化的判断交给 FishAdviceEngine。
 *
 * 数据来源与口径说明：
 * - 最适水温区间参考淡水养殖学常用摄食温度范围（如鲤科 20-28℃、鲈科 18-24℃）。
 * - 耐低氧等级参考各鱼种的窒息点（鲢鳙最高约 1.7-2.3 mg/L 即浮头；
 *   鲤鲫约 0.8-1.2 mg/L；乌鳢、鲶鱼因具辅助呼吸器官可耐受更低）。
 * - 这些是**物种层面的群体规律**，个体差异、地域种群与钓场类型会带来偏差。
 */

/** 栖息水层 */
enum class WaterLayer(val label: String) {
    BOTTOM("底层"),
    LOWER("中下层"),
    MIDDLE("中层"),
    UPPER("上层")
}

/** 耐低氧能力：决定低气压时该鱼种受影响的程度 */
enum class OxygenTolerance(val label: String, val note: String) {
    VERY_LOW("极不耐低氧", "窒息点高，气压一低最先浮头停口"),
    LOW("耐低氧较弱", "溶氧偏低时摄食明显下降"),
    MEDIUM("一般", "溶氧中等偏低时仍可摄食"),
    HIGH("较耐低氧", "短时低氧影响有限"),
    VERY_HIGH("极耐低氧", "具辅助呼吸器官，低气压下仍活跃")
}

/** 活动节律 */
enum class ActivityPattern(val label: String) {
    CREPUSCULAR("晨昏为主"),
    DIURNAL("白天为主"),
    NOCTURNAL("夜间为主"),
    ALL_DAY("全天可钓")
}

/** 水下结构类型：钓位推荐的基本单元 */
enum class SpotTag(val label: String) {
    WEED("水草区"),
    STRUCTURE("障碍/乱石"),
    OPEN_WATER("开阔水面"),
    INLET("进出水口"),
    DROP_OFF("深浅交界"),
    SHALLOW_FLAT("浅滩"),
    DEEP_BASIN("深水区"),
    SHADE("树荫/桥墩"),
    WINDWARD("下风口"),
    BAY("洄湾/背风")
}

/** 单个鱼种的静态档案 */
data class FishProfile(
    val name: String,
    val emoji: String,
    /** 栖息水层 */
    val layer: WaterLayer,
    /** 最适水温区间（℃），摄食最活跃 */
    val tempOptimal: ClosedFloatingPointRange<Double>,
    /** 仍会摄食的水温区间（℃），超出则基本停口 */
    val tempActive: ClosedFloatingPointRange<Double>,
    val oxygenTolerance: OxygenTolerance,
    val activity: ActivityPattern,
    /** 常规作钓水深（米） */
    val baseDepthM: ClosedFloatingPointRange<Double>,
    /** 偏好结构 */
    val preferredSpots: Set<SpotTag>,
    /** 常规饵料 */
    val baits: List<String>,
    /** 常规钓组 */
    val rig: String,
    /** 钓法 */
    val method: String,
    /** 一句话习性 */
    val habit: String
)

/**
 * 可选鱼种。
 *
 * ANY 为「不限鱼种」的综合模型，沿用鲤科温水性鱼类的通用参数，
 * 适合用户没想好目标鱼时使用。
 */
enum class FishSpecies(val profile: FishProfile) {

    ANY(
        FishProfile(
            name = "不限鱼种",
            emoji = "🎣",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 16.0..26.0,
            tempActive = 6.0..32.0,
            oxygenTolerance = OxygenTolerance.MEDIUM,
            activity = ActivityPattern.CREPUSCULAR,
            baseDepthM = 1.5..3.0,
            preferredSpots = setOf(SpotTag.DROP_OFF, SpotTag.WEED, SpotTag.INLET),
            baits = listOf("蚯蚓", "红虫", "腥香商品饵", "玉米"),
            rig = "主线 1.5 / 子线 0.8 / 伊势尼 4-5 号",
            method = "台钓底钓为主",
            habit = "按多数淡水鲤科鱼类的通用习性给出建议"
        )
    ),

    CRUCIAN(
        FishProfile(
            name = "鲫鱼",
            emoji = "🐟",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 16.0..26.0,
            tempActive = 4.0..30.0,
            oxygenTolerance = OxygenTolerance.HIGH,
            activity = ActivityPattern.ALL_DAY,
            baseDepthM = 1.0..2.5,
            preferredSpots = setOf(SpotTag.WEED, SpotTag.DROP_OFF, SpotTag.BAY, SpotTag.SHALLOW_FLAT),
            baits = listOf("红虫", "蚯蚓", "腥香商品饵", "酒米打窝"),
            rig = "主线 1.0 / 子线 0.6 / 袖钩 3-4 号",
            method = "台钓底钓，小钩细线，勤逗",
            habit = "底层小型鱼，适应性强、四季可钓，喜水草与深浅交界"
        )
    ),

    CARP(
        FishProfile(
            name = "鲤鱼",
            emoji = "🐠",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 20.0..28.0,
            tempActive = 8.0..32.0,
            oxygenTolerance = OxygenTolerance.MEDIUM,
            activity = ActivityPattern.CREPUSCULAR,
            baseDepthM = 2.0..4.0,
            preferredSpots = setOf(SpotTag.DROP_OFF, SpotTag.INLET, SpotTag.BAY, SpotTag.STRUCTURE),
            baits = listOf("发酵玉米", "薯味饵", "螺鲤类商品饵", "麦粒"),
            rig = "主线 2.0 / 子线 1.2 / 伊势尼 5-7 号",
            method = "底钓守钓，重窝守大物",
            habit = "底层大体型鱼，警惕性高，喜拱泥觅食，弱光时靠边"
        )
    ),

    GRASS(
        FishProfile(
            name = "草鱼",
            emoji = "🌿",
            layer = WaterLayer.MIDDLE,
            tempOptimal = 22.0..30.0,
            tempActive = 12.0..34.0,
            oxygenTolerance = OxygenTolerance.LOW,
            activity = ActivityPattern.DIURNAL,
            baseDepthM = 1.5..3.0,
            preferredSpots = setOf(SpotTag.WEED, SpotTag.WINDWARD, SpotTag.OPEN_WATER, SpotTag.SHADE),
            baits = listOf("嫩玉米", "芦苇芯", "草把", "微酸发酵饵"),
            rig = "主线 2.5 / 子线 1.5 / 伊势尼 6-8 号",
            method = "底钓或浮钓，高温可钓半水",
            habit = "草食性中上层鱼，高温季节活性最强，追草边与下风口"
        )
    ),

    BIGHEAD(
        FishProfile(
            name = "鲢鳙",
            emoji = "💨",
            layer = WaterLayer.UPPER,
            tempOptimal = 22.0..32.0,
            tempActive = 15.0..34.0,
            oxygenTolerance = OxygenTolerance.VERY_LOW,
            activity = ActivityPattern.DIURNAL,
            baseDepthM = 0.5..2.0,
            preferredSpots = setOf(SpotTag.OPEN_WATER, SpotTag.WINDWARD, SpotTag.DEEP_BASIN),
            baits = listOf("酸臭发酵雾化饵", "商品鲢鳙饵", "豆腐渣打窝"),
            rig = "主线 3.0 / 子线 2.0 / 新关东 1-2 号",
            method = "浮钓抽窝，靠雾化诱鱼，禁止打重窝",
            habit = "上层滤食性鱼，最不耐低氧，气压一低最先浮头停口"
        )
    ),

    BLACK(
        FishProfile(
            name = "青鱼",
            emoji = "⚫",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 20.0..28.0,
            tempActive = 10.0..32.0,
            oxygenTolerance = OxygenTolerance.MEDIUM,
            activity = ActivityPattern.CREPUSCULAR,
            baseDepthM = 3.0..6.0,
            preferredSpots = setOf(SpotTag.DEEP_BASIN, SpotTag.DROP_OFF, SpotTag.STRUCTURE),
            baits = listOf("螺蛳", "蚌肉", "玉米", "商品螺味饵"),
            rig = "主线 3.0 以上 / 子线 2.0 / 伊势尼 9-11 号",
            method = "深水重窝守钓，需极大耐心",
            habit = "底层大型鱼，主食螺蚌，常年居深水，力道极大"
        )
    ),

    BASS(
        FishProfile(
            name = "鲈鱼",
            emoji = "🦈",
            layer = WaterLayer.LOWER,
            tempOptimal = 18.0..24.0,
            tempActive = 8.0..30.0,
            oxygenTolerance = OxygenTolerance.LOW,
            activity = ActivityPattern.CREPUSCULAR,
            baseDepthM = 1.5..3.5,
            preferredSpots = setOf(SpotTag.STRUCTURE, SpotTag.DROP_OFF, SpotTag.WEED, SpotTag.SHADE),
            baits = listOf("软虫", "米诺", "亮片", "德州/无铅钓组"),
            rig = "PE 1.0-1.5 号 + 前导碳线 3-4 号",
            method = "路亚为主，搜障碍与深浅交界",
            habit = "中下层掠食鱼，伏击型，靠障碍与结构藏身"
        )
    ),

    TOPMOUTH(
        FishProfile(
            name = "翘嘴",
            emoji = "🚀",
            layer = WaterLayer.UPPER,
            tempOptimal = 18.0..28.0,
            tempActive = 8.0..32.0,
            oxygenTolerance = OxygenTolerance.LOW,
            activity = ActivityPattern.CREPUSCULAR,
            baseDepthM = 0.5..3.0,
            preferredSpots = setOf(SpotTag.OPEN_WATER, SpotTag.WINDWARD, SpotTag.BAY, SpotTag.DROP_OFF),
            baits = listOf("亮片", "铅笔", "波爬", "活小鱼"),
            rig = "PE 0.8-1.2 号 + 前导 3 号",
            method = "路亚远投搜索，追炸水面",
            habit = "上层追击型掠食鱼，成群追饵鱼，晨昏炸水最频繁"
        )
    ),

    SNAKEHEAD(
        FishProfile(
            name = "黑鱼",
            emoji = "🐍",
            layer = WaterLayer.UPPER,
            tempOptimal = 20.0..30.0,
            tempActive = 12.0..34.0,
            oxygenTolerance = OxygenTolerance.VERY_HIGH,
            activity = ActivityPattern.DIURNAL,
            baseDepthM = 0.3..2.0,
            preferredSpots = setOf(SpotTag.WEED, SpotTag.SHALLOW_FLAT, SpotTag.SHADE),
            baits = listOf("雷蛙", "软虫", "活泥鳅", "小鱼"),
            rig = "雷强竿 + PE 3-6 号（重草区）",
            method = "雷蛙搜草洞，或传统钓守窝护幼",
            habit = "具鳃上器可直吸空气，极耐低氧，护幼期攻击性极强"
        )
    ),

    CATFISH(
        FishProfile(
            name = "鲶鱼",
            emoji = "🐱",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 22.0..30.0,
            tempActive = 12.0..34.0,
            oxygenTolerance = OxygenTolerance.VERY_HIGH,
            activity = ActivityPattern.NOCTURNAL,
            baseDepthM = 1.5..4.0,
            preferredSpots = setOf(SpotTag.STRUCTURE, SpotTag.INLET, SpotTag.DEEP_BASIN, SpotTag.SHADE),
            baits = listOf("鸡肝", "大蚯蚓", "臭鱼", "活泥鳅"),
            rig = "主线 3.0 / 子线 2.0 / 伊势尼 8-10 号",
            method = "夜钓为主，重味型死守",
            habit = "底层夜行鱼，靠嗅觉觅食，耐低氧，喜暗流与乱石"
        )
    ),

    YELLOWCAT(
        FishProfile(
            name = "黄颡鱼",
            emoji = "🟡",
            layer = WaterLayer.BOTTOM,
            tempOptimal = 20.0..28.0,
            tempActive = 10.0..32.0,
            oxygenTolerance = OxygenTolerance.HIGH,
            activity = ActivityPattern.NOCTURNAL,
            baseDepthM = 1.0..3.0,
            preferredSpots = setOf(SpotTag.STRUCTURE, SpotTag.INLET, SpotTag.BAY, SpotTag.WEED),
            baits = listOf("红虫", "蚯蚓", "鸡肝"),
            rig = "主线 1.5 / 子线 1.0 / 伊势尼 5-6 号（注意摘钩）",
            method = "夜钓底钓，常成窝上鱼",
            habit = "底层小型夜行鱼，贪食易吞钩，喜乱石与缓流"
        )
    ),

    TILAPIA(
        FishProfile(
            name = "罗非鱼",
            emoji = "🔥",
            layer = WaterLayer.LOWER,
            tempOptimal = 25.0..32.0,
            tempActive = 16.0..35.0,
            oxygenTolerance = OxygenTolerance.HIGH,
            activity = ActivityPattern.DIURNAL,
            baseDepthM = 1.0..2.5,
            preferredSpots = setOf(SpotTag.SHALLOW_FLAT, SpotTag.WINDWARD, SpotTag.BAY),
            baits = listOf("腥味商品饵", "虾粉", "肝味饵", "冻饵"),
            rig = "主线 1.5 / 子线 1.0 / 袖钩 5-6 号",
            method = "底钓，高温期活性最强",
            habit = "南方温水性鱼，低于 15℃ 摄食骤减、10℃ 以下大量死亡"
        )
    ),

    BREAM(
        FishProfile(
            name = "鳊鱼",
            emoji = "🍂",
            layer = WaterLayer.LOWER,
            tempOptimal = 20.0..28.0,
            tempActive = 10.0..32.0,
            oxygenTolerance = OxygenTolerance.MEDIUM,
            activity = ActivityPattern.DIURNAL,
            baseDepthM = 1.5..3.0,
            preferredSpots = setOf(SpotTag.DROP_OFF, SpotTag.WEED, SpotTag.OPEN_WATER, SpotTag.WINDWARD),
            baits = listOf("嫩玉米", "麦粒", "商品鲫鲤饵", "菜籽饼打窝"),
            rig = "主线 1.5 / 子线 1.0 / 伊势尼 4-5 号",
            method = "可底钓可钓半水，常截口",
            habit = "中下层鱼，喜成群巡游，觅食时常离底截饵"
        )
    );

    val displayName: String get() = profile.name

    companion object {
        /** 界面选择顺序 */
        val selectable: List<FishSpecies> = entries.toList()

        fun fromId(id: String): FishSpecies = entries.firstOrNull { it.name == id } ?: ANY
    }
}
