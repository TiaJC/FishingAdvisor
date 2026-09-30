package com.pcinfo.fishing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.model.SpotAdvice
import com.pcinfo.fishing.domain.model.SpeciesAdvice

/**
 * 鱼种化作钓建议卡：水温、水深、钓位、饵料、钓组与注意事项。
 *
 * 每项都附带理由，因为「给结论不给依据」的建议无法被用户校验，
 * 用户也就无法根据自己的经验判断该不该听。
 */
@Composable
fun SpeciesAdviceCard(
    advice: SpeciesAdvice,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${advice.species.profile.emoji} ${advice.species.profile.name}作钓建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = advice.species.profile.habit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 水温与水深
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    label = "估算水温",
                    value = "%.1f℃".format(advice.waterTempC),
                    hint = if (advice.waterTempReliable) "由气温推算" else "历史数据不足",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                MetricCell(
                    label = "建议水深",
                    value = advice.depthText,
                    hint = advice.species.profile.layer.label,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!advice.waterTempReliable) {
                Text(
                    text = "水温为气温推算值，建议用水温计实测校正",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ReasonLine(text = advice.depthReason)

            if (advice.fishOffBottom) {
                Spacer(modifier = Modifier.height(8.dp))
                HighlightLine(text = "底层缺氧，建议改钓浮 / 钓离底，逐层找鱼")
            }

            // 钓位
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(text = "推荐钓位")
            advice.spots.forEachIndexed { index, spot ->
                SpotItem(index = index + 1, spot = spot)
            }

            // 饵料
            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle(text = "饵料")
            FlowTexts(items = advice.baits)
            Spacer(modifier = Modifier.height(6.dp))
            ReasonLine(text = advice.baitReason)

            // 钓组与钓法
            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle(text = "钓组")
            Text(
                text = advice.rig,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SectionTitle(text = "钓法")
            Text(
                text = advice.method,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (advice.cautions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle(text = "注意")
                advice.cautions.forEach {
                    Text(
                        text = "· $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ReasonLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HighlightLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun SpotItem(index: Int, spot: SpotAdvice) {
    Row(modifier = Modifier.padding(top = 10.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spot.tag.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "匹配度 ${spot.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = spot.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 轻量标签流：横向排列的胶囊文本 */
@Composable
private fun FlowTexts(items: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            Text(
                text = "· $item",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )
        }
    }
}
