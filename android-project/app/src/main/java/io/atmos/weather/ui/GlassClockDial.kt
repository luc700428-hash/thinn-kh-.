package io.atmos.weather.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
@Composable fun GlassClockDial(hour:Int,minute:Int,onHour:(Int)->Unit,onMinute:(Int)->Unit){IOSClockDrums(hour,minute,onHour,onMinute)}
@Composable fun ThinnNumberWheel(label:String,value:Int,min:Int,max:Int,onChange:(Int)->Unit,modifier:Modifier=Modifier){IOSWheelRow(modifier){IOSDrumColumn(value-min,max-min+1,{onChange(it+min)},Modifier.weight(1f),label,false,display={"${it+min}"})}}
