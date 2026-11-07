package io.atmos.weather.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassDivider

internal val seismicHealthyInk:Color @Composable get()=if(MaterialTheme.colorScheme.background.red<.5f)Color(0xFF8BEFBE) else Color(0xFF006C40)
internal val seismicCautionInk:Color @Composable get()=if(MaterialTheme.colorScheme.background.red<.5f)Color(0xFFFFD09E) else Color(0xFF7D4200)

@Composable internal fun SeismicObservationSummary(stations:String,intensity:String,pga:String,status:String) {
    val p=MaterialTheme.colorScheme;val scale=LocalDensity.current.fontScale
    val entries=listOf(Triple("有效測站",stations,""),Triple("最大震度",intensity,""),Triple("最大 PGA",pga,"gal"))
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("seismic-observation-summary")) {
        if(maxWidth.value>=360f*scale) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                entries.forEach{(title,value,unit)->Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(p.surface).padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(title,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                    Text(value+(if(unit.isEmpty())"" else " $unit"),color=p.onSurface,fontSize=25.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.fillMaxWidth())
                    Text(status,color=if(status=="最新觀測")seismicHealthyInk else p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                }}
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(horizontal=16.dp)) {
                entries.forEachIndexed{i,(title,value,unit)->
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(title,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.weight(1f))
                        Text(value+(if(unit.isEmpty())"" else " $unit"),color=p.onSurface,fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold)
                    }
                    if(i<entries.lastIndex)GlassDivider()
                }
                Text(status,color=if(status=="最新觀測")seismicHealthyInk else p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.padding(bottom=12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SeismicStationLegend() {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("0" to Color(0xFF269D72),"1–2" to Color(0xFF147AB9),"3–4" to Color(0xFFB86B00),"5+" to Color(0xFFD84946)).forEach{(label,color)->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).background(color,CircleShape));Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp)
            }
        }
        Text("✚ 震央",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp)
    }
}
