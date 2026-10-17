package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val glassBorder: Brush get() = ThinnOpticalMaterial.rim

/** Native full-width bottom sheet with user-controlled drag handle. */
@Composable
fun GlassModalBottomSheet(onDismissRequest:()->Unit,containerColor:Color=Color(0xFF12344F),
    contentColor:Color=Color.White,content:@Composable ColumnScope.()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val reduceMotion=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    var offsetPx by remember { mutableFloatStateOf(750f) }
    val animatedY by animateFloatAsState(offsetPx,
        animationSpec=tween(if(reduceMotion)0 else 240),label="底部面板進退場")
    LaunchedEffect(Unit) { offsetPx=0f }
    fun dismissAnimated() {
        scope.launch { offsetPx=900f; delay(if(reduceMotion)0 else 240);onDismissRequest() }
    }
    Dialog(onDismissRequest={dismissAnimated()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().heightIn(max=780.dp)
                .offset { IntOffset(0,animatedY.toInt()) }
                .clip(RoundedCornerShape(topStart=28.dp,topEnd=28.dp))
                .background(ThinnOpticalMaterial.raised)
                .border(1.dp,glassBorder,RoundedCornerShape(topStart=28.dp,topEnd=28.dp))
                .thinnSpecularEdge(28.dp)
                .navigationBarsPadding()) {
                var dragged by remember { mutableFloatStateOf(0f) }
                Box(Modifier.fillMaxWidth().height(36.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onVerticalDrag={change,amount->
                            dragged=(dragged+amount).coerceIn(0f,750f)
                            offsetPx=dragged
                            change.consume()
                        },onDragEnd={
                            if(dragged>100f) dismissAnimated() else offsetPx=0f
                            dragged=0f
                        },onDragCancel={offsetPx=0f;dragged=0f})
                    },contentAlignment=Alignment.Center) {
                    Box(Modifier.size(width=42.dp,height=5.dp).clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha=.64f)))
                }
                content()
            }
        }
    }
}

@Composable
fun GlassAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    title:@Composable (()->Unit)?=null,text:@Composable (()->Unit)?=null) {
    val context=LocalContext.current
    val reduceMotion=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown=true }
    val opacity by animateFloatAsState(if(shown)1f else 0f,animationSpec=tween(if(reduceMotion)0 else 180),label="對話框進場")
    val scale by animateFloatAsState(if(shown)1f else .88f,
        animationSpec=tween(if(reduceMotion)0 else 190),label="對話框縮放")
    Dialog(onDismissRequest=onDismissRequest) {
        Column(Modifier.fillMaxWidth()
            .graphicsLayer { scaleX=scale;scaleY=scale;alpha=opacity }
            .clip(RoundedCornerShape(29.dp))
            .background(ThinnOpticalMaterial.raised)
            .border(1.dp,glassBorder,RoundedCornerShape(29.dp)).thinnSpecularEdge(29.dp).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            title?.invoke();text?.invoke()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {confirmButton()}
        }
    }
}

@Composable
fun GlassOutlinedTextField(value:String,onValueChange:(String)->Unit,
    modifier:Modifier=Modifier,singleLine:Boolean=true,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,
    keyboardActions:KeyboardActions=KeyboardActions.Default,
    leadingIcon:(@Composable ()->Unit)?=null,
    label:(@Composable ()->Unit)?=null,
    colors:TextFieldColors?=null) {
    androidx.compose.material3.TextField(
        value=value,onValueChange=onValueChange,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),
        singleLine=singleLine,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        leadingIcon=leadingIcon,label=label,shape=RoundedCornerShape(ThinnDesignTokens.radiusControl),
        colors=colors ?: androidx.compose.material3.TextFieldDefaults.colors(
            focusedTextColor=Color.White,unfocusedTextColor=Color.White,
            focusedContainerColor=Color.White.copy(alpha=.16f),
            unfocusedContainerColor=Color.White.copy(alpha=.09f),
            focusedIndicatorColor=Color.Transparent,
            unfocusedIndicatorColor=Color.Transparent,
            cursorColor=ThinnDesignTokens.accent,
            focusedLabelColor=ThinnDesignTokens.accent,
            unfocusedLabelColor=Color(0xFFCBDCE8)
        )
    )
}

@Composable
fun GlassDivider(modifier:Modifier=Modifier,color:Color=Color.White.copy(alpha=.19f)) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun GlassDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,
    content:@Composable ColumnScope.()->Unit) {
    if(expanded) androidx.compose.ui.window.Popup(
        alignment=Alignment.TopStart,
        onDismissRequest=onDismissRequest,
        properties=androidx.compose.ui.window.PopupProperties(focusable=true)
    ) {
        Column(Modifier.widthIn(min=230.dp,max=310.dp).heightIn(max=340.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ThinnOpticalMaterial.surface)
            .border(1.dp,glassBorder,RoundedCornerShape(22.dp))
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(7.dp),content=content)
    }
}

@Composable
fun GlassDropdownMenuItem(text:@Composable ()->Unit,onClick:()->Unit) {
    val haptic=io.atmos.weather.haptics.LocalHapticEngine.current
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight=48.dp)
        .clip(RoundedCornerShape(14.dp)).clickable {
            haptic?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP)
            onClick()
        }
        .padding(horizontal=12.dp,vertical=11.dp),contentAlignment=Alignment.CenterStart) {text()}
}

@Composable
fun GlassSurfaceBox(modifier:Modifier=Modifier,
    shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(18.dp),
    color:Color=Color.White.copy(alpha=.14f),
    border:androidx.compose.foundation.BorderStroke?=null,
    content:@Composable ()->Unit) {
    Box(modifier.clip(shape).background(color).border(border ?: androidx.compose.foundation.BorderStroke(1.dp,glassBorder),shape),
        contentAlignment=Alignment.Center) {content()}
}

@Composable
fun GlassAppTheme(content:@Composable ()->Unit) { ThinnAppTheme(content) }
