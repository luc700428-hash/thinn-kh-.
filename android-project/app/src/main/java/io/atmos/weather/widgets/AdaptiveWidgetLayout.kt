package io.atmos.weather.widgets

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.atmos.weather.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Actual launcher dp; independent text regions reflow rather than stretching one screenshot. */
internal data class WidgetRegion(val kind:Kind,val x:Float,val y:Float,val width:Float,val height:Float,
    val text:String="",val font:Float=0f,val soft:Boolean=false,val lines:Int=1,val bold:Boolean=false,val index:Int=0){
    enum class Kind{TEXT,TEMPERATURE,TIME,DATE,ART,ANALOG,HOUR_ART,COUNTDOWN}
}
internal object AdaptiveWidgetLayout{
    private const val LINE=1.65f
    private fun units(s:String)=s.sumOf{if(it.code>=0x2e80)1.0 else .64}.toFloat().coerceAtLeast(1f)
    fun create(p:WidgetLayoutPlan,family:Int,d:OpticalWidgetData,hour24:Boolean,showUpdated:Boolean=true,showAgenda:Boolean=true):List<WidgetRegion>{
        val out=mutableListOf<WidgetRegion>();val w=p.innerWidth;val h=p.innerHeight;val gap=if(h<180)3f else 6f
        var y=0f
        fun add(kind:WidgetRegion.Kind,x:Float,top:Float,width:Float,height:Float,text:String="",font:Float=0f,soft:Boolean=false,lines:Int=1,bold:Boolean=false,index:Int=0){
            if(width>=1&&height>=1&&top>=0&&top+height<=h+.1f&&x>=0&&x+width<=w+.1f)
                out+=WidgetRegion(kind,x,top,width,height,text,font,soft,lines,bold,index)
        }
        fun text(value:String,font:Float=p.body,soft:Boolean=false,bold:Boolean=false,lines:Int=1):Boolean{
            val height=font*LINE*lines
            if(y+height>h)return false
            add(WidgetRegion.Kind.TEXT,0f,y,w,height,value,font,soft,lines,bold);y+=height+gap;return true
        }
        // Width/height caps apply to the base at MAX, preserving increasing primary sizes.
        fun fit(desired:Float,text:String,width:Float,maxHeight:Float=h)=minOf(desired/p.textScale,
            width/(units(text)*WidgetTypography.MAX),maxHeight/(LINE*WidgetTypography.MAX))*p.textScale
        val city=io.atmos.weather.location.LocationLabels.microcity(d.city).ifBlank{d.city}
        val clocks=family in listOf(2,6,8);val safety=family in listOf(4,5)
        val timeSample=if(hour24)"00:00" else "00:00 PM"
        val range=d.highLow.replace("最高 ","高 ").replace("最低 ","低 ")
        val artKind=if(family==6)WidgetRegion.Kind.ANALOG else WidgetRegion.Kind.ART
        val footerH=if(showUpdated)p.caption*LINE+gap else 0f
        val maximumBodyBase=minOf(28f*p.typeGrowth,w/(4f*WidgetTypography.MAX),h/(7.2f*WidgetTypography.MAX))
        fun footer(){
            if(!showUpdated||y+p.caption*LINE>h)return
            val stamp=if(d.stamp>0)Instant.ofEpochMilli(d.stamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) else "待同步"
            val label=when(d.age){WidgetPresentationRules.Freshness.CACHED->"快取 · $stamp";WidgetPresentationRules.Freshness.EXPIRED->"資料已過期";WidgetPresentationRules.Freshness.MISSING->"等待同步";else->"更新 $stamp"}
            add(WidgetRegion.Kind.TEXT,0f,h-p.caption*LINE,w,p.caption*LINE,label,p.caption,true)
        }
        fun time(top:Float,width:Float,size:Float,date:Boolean):Float{
            var end=top+size*LINE;add(WidgetRegion.Kind.TIME,0f,top,width,size*LINE,font=size)
            if(date){add(WidgetRegion.Kind.DATE,0f,end,width,p.caption*LINE,font=p.caption,soft=true);end+=p.caption*LINE}
            return end
        }
        if(p.strip){
            val art=minOf(h,w*.24f,64f*p.typeGrowth)
            val primaryW=if(w>360)w*.43f else (w-art-gap).coerceAtLeast(w*.60f)
            val size=if(clocks)fit(p.clock,timeSample,primaryW,h) else fit(p.hero,d.temperature,primaryW,h)
            if(clocks){
                val temp=fit(p.body,d.temperature,w*.34f,h*.50f)
                val dateW=w*.62f
                val dateSize=minOf(p.caption/p.textScale,dateW/(4.5f*WidgetTypography.MAX))*p.textScale
                val rowH=maxOf(dateSize,temp)*LINE
                val title=p.city*LINE+gap+size*LINE+gap+rowH<=h
                val top=if(title){add(WidgetRegion.Kind.TEXT,0f,0f,w,p.city*LINE,city,p.city,bold=true);p.city*LINE+gap}else 0f
                time(top,primaryW,size,false)
                val dateY=top+size*LINE+gap
                add(WidgetRegion.Kind.DATE,0f,dateY,dateW,dateSize*LINE,font=dateSize,soft=true)
                add(WidgetRegion.Kind.TEMPERATURE,dateW+gap,dateY,w-dateW-gap,temp*LINE,d.temperature,temp,bold=true)
                val side=minOf(art,size*LINE)
                add(artKind,w-side,top,side,side)
            }else{
                val title=p.city*LINE+gap+size*LINE<=h
                val top=if(title){add(WidgetRegion.Kind.TEXT,0f,0f,primaryW,p.city*LINE,city,p.city,bold=true);p.city*LINE+gap}else 0f
                add(WidgetRegion.Kind.TEMPERATURE,0f,top,primaryW,size*LINE,d.temperature,size,bold=true)
                add(artKind,w-art,(h-art)/2,art,art)
                if(w>360){
                    val x=primaryW+gap;val rest=w-art-gap-x
                    if(rest>p.body*5){
                        add(WidgetRegion.Kind.TEXT,x,0f,rest,p.body*LINE,d.description(),p.body)
                        if(h>=p.body*LINE*2+gap)add(WidgetRegion.Kind.TEXT,x,p.body*LINE+gap,rest,p.caption*LINE,if(safety)d.alert else range,p.caption,true)
                    }
                }
            }
            return out
        }
        text(if(safety&&d.alertRecent)"最新地震 · $city" else city,p.city,bold=true)
        if(family==1){
            val minimum=(p.caption+p.body)*LINE+gap
            if(y+p.body*LINE+gap+minimum<=h)text("${d.temperature} · ${d.description()}",p.body,true)
            if(d.hours.isEmpty()){text("開啟 App 同步逐時預報",p.body);return out}
            val columns=minOf((w/(p.caption*3.6f)).toInt().coerceIn(1,6),if(h>w*1.4f)2 else if(h>w*.8f)3 else 6)
            val glyph=if(h-y>=minimum+28f*p.typeGrowth+gap)28f*p.typeGrowth else 0f
            val rowH=minimum+if(glyph>0)glyph+gap else 0f
            val rows=((h-y)/rowH).toInt().coerceIn(1,6)
            d.hours.take(rows*columns).chunked(columns).forEachIndexed{r,row->
                val cw=w/row.size;val top=y+r*rowH
                row.forEachIndexed{i,hour->
                    val x=i*cw;add(WidgetRegion.Kind.TEXT,x,top,cw,p.caption*LINE,hour.time,p.caption,true)
                    if(glyph>0)add(WidgetRegion.Kind.HOUR_ART,x+(cw-minOf(glyph,cw*.7f))/2,top+p.caption*LINE+gap,minOf(glyph,cw*.7f),minOf(glyph,cw*.7f),index=r*columns+i)
                    add(WidgetRegion.Kind.TEXT,x,top+p.caption*LINE+(if(glyph>0)glyph+gap else 0f),cw,p.body*LINE,hour.temperature,p.body,bold=true)
                }
            }
            y+=((minOf(rows*columns,d.hours.size)+columns-1)/columns)*rowH
            if(y+p.caption*LINE<=h-footerH)text(range,p.caption,true)
            if(y+(p.caption+p.body)*LINE+gap<=h-footerH){text("降雨 ${d.rain} · 風速 ${d.wind}",p.body,true)}
            footer();return out
        }
        if(safety){
            text(d.alert,p.body,bold=true,lines=if(h-y>=p.body*LINE*2+p.caption*LINE+gap)2 else 1)
            val size=fit(p.hero,d.temperature,w*.56f,h*.25f)
            val art=minOf(w*.35f,(h-y-size*LINE-gap).coerceAtLeast(size*LINE),p.icon);val row=maxOf(size*LINE,art)
            if(y+row<=h){
                add(WidgetRegion.Kind.TEMPERATURE,0f,y,w*.58f,size*LINE,d.temperature,size,bold=true)
                if(art>=20)add(WidgetRegion.Kind.ART,w-art,y,art,art)
                y+=row+gap
            }
            if(d.alertDetail.isNotBlank())text(d.alertDetail,p.caption,true,lines=if(h-y>=p.caption*LINE*2)2 else 1)
            footer();return out
        }
        if(family==3){
            text("${d.temperature} · ${d.description()}",p.body)
            val values=listOf("濕度" to d.humidity,"風速" to d.wind,"空氣 AQI" to d.air,"紫外線" to d.uv)
            val cols=(w/(p.body*6f)).toInt().coerceIn(1,2)
            val cell=(p.caption+p.body)*LINE+gap;val stacked=h-y>=cell*((values.size+cols-1)/cols)
            values.chunked(cols).forEach{row->
                val cw=(w-gap*(row.size-1))/row.size
                row.forEachIndexed{i,(label,value)->
                    if(stacked){
                        add(WidgetRegion.Kind.TEXT,i*(cw+gap),y,cw,p.caption*LINE,label,p.caption,true)
                        add(WidgetRegion.Kind.TEXT,i*(cw+gap),y+p.caption*LINE,cw,p.body*LINE,value,p.body,bold=true)
                    }else add(WidgetRegion.Kind.TEXT,i*(cw+gap),y,cw,p.body*LINE,"$label $value",p.body)
                };y+=if(stacked)cell else p.body*LINE+gap
            };footer();return out
        }
        if(clocks){
            val wide=w/h>1.5f||(family!=6&&w>=230f&&p.mode!=WidgetLayoutPlan.Mode.PORTRAIT)
            val clockWidth=if(wide)w*.64f else w
            val weatherFont=fit(p.body,d.temperature,clockWidth*.46f,h*.30f)
            val rowBase=minOf(maximumBodyBase,clockWidth*.46f/(units(d.temperature)*WidgetTypography.MAX),h*.30f/(LINE*WidgetTypography.MAX))
            val budget=((h-gap*3)-(maximumBodyBase+rowBase)*LINE*WidgetTypography.MAX).coerceAtLeast(12f)
            val clock=fit(p.clock,timeSample,clockWidth,budget)
            val artTop=y
            y=time(y,clockWidth,clock,false)+gap
            val dateWidth=clockWidth*.52f
            val dateFont=minOf(p.caption/p.textScale,dateWidth/(4.5f*WidgetTypography.MAX))*p.textScale
            val row=maxOf(dateFont,weatherFont)*LINE
            add(WidgetRegion.Kind.DATE,0f,y,dateWidth,dateFont*LINE,font=dateFont,soft=true)
            add(WidgetRegion.Kind.TEMPERATURE,dateWidth+gap,y,clockWidth-dateWidth-gap,weatherFont*LINE,d.temperature,weatherFont,bold=true)
            y+=row+gap
            val art=if(wide)minOf(w*.31f,h-artTop) else minOf(w*.62f,(h-y).coerceAtLeast(0f),p.icon*1.5f)
            if(art>=24){add(artKind,if(wide)w-art else (w-art)/2,if(wide)artTop else y,art,art);if(!wide)y+=art+gap}
            if(y+p.body*LINE<=h-footerH)text(d.description(),p.body,true)
        }else{
            val portrait=p.mode==WidgetLayoutPlan.Mode.PORTRAIT
            val primaryW=if(portrait)w else w*.58f
            val size=fit(p.hero,d.temperature,primaryW,h*.55f)
            val inline=w>=230f
            val timeW=if(inline)w*.62f else w
            val dateW=if(inline)w-timeW-gap else w
            val dateBase=minOf(maximumBodyBase*.86f,dateW/(4.5f*WidgetTypography.MAX))
            val heroMaximum=minOf(88f*p.typeGrowth,w/(2f*WidgetTypography.MAX),h*.54f/(LINE*WidgetTypography.MAX),
                primaryW/(units(d.temperature)*WidgetTypography.MAX),h*.55f/(LINE*WidgetTypography.MAX))
            val timeBudget=(h-gap*3-(maximumBodyBase+heroMaximum+(if(inline)0f else dateBase))*LINE*WidgetTypography.MAX).coerceAtLeast(0f)
            val timeSize=fit(minOf(p.clock,28f*p.typeGrowth*p.textScale),timeSample,timeW,timeBudget)
            val dateSize=minOf(p.caption/p.textScale,dateBase)*p.textScale
            val clockHeight=if(inline)maxOf(timeSize,dateSize)*LINE else (timeSize+dateSize)*LINE
            val withClock=family==7&&h>=230f&&timeSize>=12f*p.textScale
            val clockReserve=if(withClock)clockHeight+gap else 0f
            val reserve=size*LINE+gap+clockReserve+(if(h>=240)p.body*LINE+gap else 0f)
            if(portrait){
                val art=minOf(w*.88f,(h-y-reserve-footerH)*.50f,p.icon*1.8f).coerceAtLeast(0f)
                if(art>=28){add(artKind,(w-art)/2,y,art,art);y+=art+gap}
                add(WidgetRegion.Kind.TEMPERATURE,0f,y,w,size*LINE,d.temperature,size,bold=true);y+=size*LINE+gap
            }else{
                val art=minOf(w*.38f,(h-y-size*LINE-clockReserve-gap).coerceAtLeast(size*LINE),p.icon)
                add(WidgetRegion.Kind.TEMPERATURE,0f,y,primaryW,size*LINE,d.temperature,size,bold=true)
                if(art>=16)add(artKind,w-art,y,art,art)
                y+=maxOf(size*LINE,art)+gap
            }
            if(withClock&&y+clockHeight<=h){
                add(WidgetRegion.Kind.TIME,0f,y,timeW,timeSize*LINE,font=timeSize)
                add(WidgetRegion.Kind.DATE,if(inline)timeW+gap else 0f,if(inline)y else y+timeSize*LINE,dateW,dateSize*LINE,font=dateSize,soft=true)
                y+=clockHeight+gap
            }
            if(y+p.body*LINE<=h-footerH)text(d.description(),p.body,true)
        }
        if(y+p.caption*LINE<=h-footerH)text(range,p.caption,true)
        if(clocks&&y+(p.caption+p.body)*LINE+gap*2<=h-footerH){
            text("下一個鬧鐘",p.caption,true);text(d.alarm,p.body)
            if(d.alarmAt?.let{it-System.currentTimeMillis() in 1..86_400_000L}==true&&y+p.body*LINE<=h-footerH){add(WidgetRegion.Kind.COUNTDOWN,0f,y,w,p.body*LINE,font=p.body);y+=p.body*LINE+gap}
            if(showAgenda&&y+p.caption*LINE<=h-footerH)text(d.task,p.caption,true)
        }
        val columns=(w/(p.body*5.5f)).toInt().coerceIn(1,if(w>620)4 else 2)
        val cellH=(p.caption+p.body)*LINE+gap
        val metrics=listOf("降雨" to d.rain,"風速" to d.wind)
        val rows=((h-y-footerH)/cellH).toInt().coerceIn(0,(metrics.size+columns-1)/columns)
        metrics.take(rows*columns).chunked(columns).forEach{row->
            val cw=(w-gap*(row.size-1))/row.size
            row.forEachIndexed{i,(label,value)->
                add(WidgetRegion.Kind.TEXT,i*(cw+gap),y,cw,p.caption*LINE,label,p.caption,true)
                add(WidgetRegion.Kind.TEXT,i*(cw+gap),y+p.caption*LINE,cw,p.body*LINE,value,p.body,bold=true)
            };y+=cellH
        }
        val hourH=(p.caption+p.body)*LINE+28f*p.typeGrowth+gap*2
        if(d.hours.isNotEmpty()&&y+hourH<=h-footerH){
            val count=(w/(p.body*3.5f)).toInt().coerceIn(1,6);val hours=d.hours.take(count);val cw=w/hours.size
            hours.forEachIndexed{i,hour->
                add(WidgetRegion.Kind.TEXT,i*cw,y,cw,p.caption*LINE,hour.time,p.caption,true)
                val art=minOf(28f*p.typeGrowth,cw*.7f)
                add(WidgetRegion.Kind.HOUR_ART,i*cw+(cw-art)/2,y+p.caption*LINE+gap,art,art,index=i)
                add(WidgetRegion.Kind.TEXT,i*cw,y+p.caption*LINE+28f*p.typeGrowth+gap*2,cw,p.body*LINE,hour.temperature,p.body,bold=true)
            };y+=hourH
        }
        footer()
        return out
    }
}

internal fun adaptiveWidgetRemote(context:Context,family:Int,d:OpticalWidgetData,p:WidgetLayoutPlan,colors:WidgetGlassRenderer.Palette,
    motion:Boolean,showUpdated:Boolean=true,showAgenda:Boolean=true):RemoteViews{
    val result=RemoteViews(context.packageName,R.layout.widget_adaptive);val dip=TypedValue.COMPLEX_UNIT_DIP
    // Launchers reapply this RemoteViews to an existing tree, including r4's duplicates.
    // Replace the complete region set before adding the latest layout. Otherwise old
    // TextClocks and ViewFlippers remain attached and accumulate on every update.
    result.removeAllViews(R.id.widget_adaptive_root)
    val seed=(d.stamp xor(d.stamp ushr 32)).toInt()
    val bold=context.getSharedPreferences("thinn_settings",0).getFloat("v4_fontweight",400f)>=620f
    AdaptiveWidgetLayout.create(p,family,d,android.text.format.DateFormat.is24HourFormat(context),showUpdated,showAgenda).forEach{r->
        var id=R.id.widget_adaptive_text
        val remote=when(r.kind){
            WidgetRegion.Kind.ART->if(family in listOf(7,8)){id=R.id.buddy_root;buddyRemote(context,d,motion,seed,r.width)}else{
                id=R.id.widget_glyph_root;weatherRemote(context,d.condition,d.day,r.width,motion)
            }
            WidgetRegion.Kind.ANALOG->{id=R.id.widget_analog;RemoteViews(context.packageName,R.layout.widget_analog_clock)}
            WidgetRegion.Kind.HOUR_ART->{id=R.id.widget_glyph_root;val hour=d.hours[r.index];weatherRemote(context,WidgetWeatherArt.condition(hour.code),hour.day,r.width,false)}
            WidgetRegion.Kind.TIME,WidgetRegion.Kind.DATE->{
                val date=r.kind==WidgetRegion.Kind.DATE
                id=if(family==8)if(date)R.id.buddy_clock_date else R.id.buddy_clock_time else if(date)R.id.widget_clock_date else R.id.widget_clock
                val layout=if(family==8)if(date)R.layout.widget_adaptive_buddy_date else R.layout.widget_adaptive_buddy_time else if(date)R.layout.widget_adaptive_date else R.layout.widget_adaptive_time
                RemoteViews(context.packageName,layout).apply{
                    setCharSequence(id,"setFormat12Hour",if(date)"M月d日" else "h:mm a");setCharSequence(id,"setFormat24Hour",if(date)"M月d日" else "HH:mm")
                    setTextViewTextSize(id,dip,r.font);setTextColor(id,if(date)colors.secondary else colors.ink)
                    setContentDescription(id,if(date)"今天日期" else "目前時間，依手機時鐘自動更新")
                }
            }
            WidgetRegion.Kind.COUNTDOWN->{
                id=R.id.widget_countdown
                RemoteViews(context.packageName,R.layout.widget_live_countdown).apply{
                    d.alarmAt?.let{at->WidgetPresentationRules.countdownBase(at,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime())}?.let{base->
                        setChronometer(id,base,null,true);setChronometerCountDown(id,true)
                    };setTextViewTextSize(id,dip,r.font);setTextColor(id,colors.accent)
                }
            }
            else->{
                val temp=r.kind==WidgetRegion.Kind.TEMPERATURE&&family==8;id=if(temp)R.id.buddy_clock_temp else R.id.widget_adaptive_text
                val hero=!temp&&r.kind==WidgetRegion.Kind.TEMPERATURE&&r.font>=26f
                RemoteViews(context.packageName,if(temp)R.layout.widget_adaptive_temperature else if(hero)R.layout.widget_adaptive_hero else R.layout.widget_adaptive_text).apply{
                    val reading=android.text.SpannableString(r.text).apply{if(!hero&&(r.bold||bold))setSpan(android.text.style.StyleSpan(Typeface.BOLD),0,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
                    setTextViewText(id,reading);setTextViewTextSize(id,dip,r.font);setTextColor(id,if(r.soft)colors.secondary else colors.ink)
                    setInt(id,"setMaxLines",r.lines);setInt(id,"setGravity",android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START)
                }
            }
        }
        remote.setViewLayoutWidth(id,r.width,dip);remote.setViewLayoutHeight(id,r.height,dip)
        remote.setViewLayoutMargin(id,RemoteViews.MARGIN_LEFT,r.x,dip);remote.setViewLayoutMargin(id,RemoteViews.MARGIN_TOP,r.y,dip)
        result.addView(R.id.widget_adaptive_root,remote)
    };return result
}
private fun weatherRemote(context:Context,condition:io.atmos.weather.domain.model.WeatherCondition,day:Boolean,size:Float,motion:Boolean):RemoteViews{
    val px=(size*context.resources.displayMetrics.density).toInt().coerceIn(24,192)
    if(!motion||condition==io.atmos.weather.domain.model.WeatherCondition.UNKNOWN){
        return RemoteViews(context.packageName,R.layout.widget_weather_still).apply{
            setImageViewBitmap(R.id.widget_glyph_static,WidgetWeatherArt.render(condition,day,px,0))
            setContentDescription(R.id.widget_glyph_static,condition.label())
        }
    }
    return RemoteViews(context.packageName,R.layout.widget_motion_glyph).apply{
        setImageViewBitmap(R.id.widget_glyph_a,WidgetWeatherArt.render(condition,day,px,0));setImageViewBitmap(R.id.widget_glyph_b,WidgetWeatherArt.render(condition,day,px,1))
        setImageViewBitmap(R.id.widget_glyph_static,WidgetWeatherArt.render(condition,day,px,0))
        setViewVisibility(R.id.widget_glyph_motion,if(motion&&condition!=io.atmos.weather.domain.model.WeatherCondition.UNKNOWN)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_glyph_static,if(motion&&condition!=io.atmos.weather.domain.model.WeatherCondition.UNKNOWN)View.GONE else View.VISIBLE)
        setContentDescription(R.id.widget_glyph_static,condition.label())
    }
}
