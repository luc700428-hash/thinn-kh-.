package io.atmos.weather.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.visual.LandmarkId
import io.atmos.weather.domain.visual.WeatherScene
import io.atmos.weather.domain.visual.TaiwanLandmarks
import kotlin.math.cos
import kotlin.math.sin

/** One deterministic art direction for hero and widgets; snapshots differ only by weather and size. */
object WeatherArtRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun color(value: String) = AndroidColor.parseColor(value)
    private fun paint(c: Int, alpha: Int = 255) = p.apply { reset(); isAntiAlias=true; isFilterBitmap=true; color=c; this.alpha=alpha }
    private fun oval(c: Canvas, l: Float,t: Float,r: Float,b: Float,fill:Int,alpha:Int=255) { c.drawOval(l,t,r,b,paint(fill,alpha)) }
    private fun rect(c: Canvas,l:Float,t:Float,r:Float,b:Float,fill:Int,alpha:Int=255) { c.drawRect(l,t,r,b,paint(fill,alpha)) }
    private fun round(c: Canvas,l:Float,t:Float,r:Float,b:Float,rad:Float,fill:Int,alpha:Int=255) { c.drawRoundRect(l,t,r,b,rad,rad,paint(fill,alpha)) }
    private fun line(c:Canvas,x:Float,y:Float,ex:Float,ey:Float,stroke:Float,fill:Int,alpha:Int=255) {
        val brush=paint(fill,alpha).apply { style=Paint.Style.STROKE; strokeWidth=stroke; strokeCap=Paint.Cap.ROUND }
        c.drawLine(x,y,ex,ey,brush); brush.style=Paint.Style.FILL
    }
    private fun poly(c: Canvas, fill: Int, vararg v: Float) {
        val path=Path().apply { moveTo(v[0],v[1]); for(i in 2 until v.size step 2)lineTo(v[i],v[i+1]); close() }
        c.drawPath(path,paint(fill))
    }
    private fun circle(c:Canvas,x:Float,y:Float,r:Float,fill:Int,alpha:Int=255){c.drawCircle(x,y,r,paint(fill,alpha))}

    /** Render a frame with no heavy external engine, for use by Compose & RemoteViews-backed Glance. */
    fun drawScene(canvas: Canvas, width: Float, height: Float, scene: WeatherScene?, phase: Float = 0f) {
        val checkpoint=canvas.save()
        canvas.scale(width/1000f,height/650f)
        val night=(scene?.nightLight ?: 0f) > .4f
        val snow=(scene?.snowAccumulation ?: 0f) > .2f
        val rain=(scene?.wetness ?: 0f) > .2f
        val land=scene?.landmark ?: LandmarkId.WEATHER_COTTAGE
        // Halo with soft radial translucency, not an opaque disc.
        val glow=if(night) color("#8ABCF4") else color("#FFE6B0")
        p.reset();p.isAntiAlias=true;p.shader=RadialGradient(510f,300f,340f,intArrayOf((0x42 shl 24) or (glow and 0xFFFFFF),AndroidColor.TRANSPARENT),null,Shader.TileMode.CLAMP)
        canvas.drawCircle(510f,300f,340f,p);p.shader=null
        oval(canvas,145f,557f,858f,620f,color("#082941"),74)
        oval(canvas,177f,532f,826f,597f,if(snow)color("#DBEFF7") else if(rain)color("#648B8F") else color("#84BFAE"))
        oval(canvas,197f,532f,809f,563f,if(snow)color("#F7FCFF") else color("#B8E3CE"),110)
        when(land){
            LandmarkId.TAIPEI_101 -> taipei(canvas,night)
            LandmarkId.TOKYO_SKYTREE -> skytree(canvas,night)
            LandmarkId.EIFFEL_TOWER -> eiffel(canvas,night)
            LandmarkId.WEATHER_COTTAGE -> cottage(canvas,night,snow,rain,phase)
            LandmarkId.TAIWAN_COUNTY -> countyDiorama(canvas,scene?.county ?: "台北市",night,snow,scene?.locality ?: "")
        }
        if(rain) for(i in 0 until 32){
            val x=210f+((i*187)%590).toFloat(); val y=130f+((i*83 + (phase*650).toInt())%400)
            line(canvas,x,y,x-7f,y+21f,2f,color("#D0F2FF"),90)
        }
        if(snow) for(i in 0 until 32){
            val x=180f+((i*173)%650).toFloat(); val y=130f+((i*79 + (phase*230).toInt())%420)
            circle(canvas,x,y,2.2f+(i%3),AndroidColor.WHITE,165)
        }
        canvas.restoreToCount(checkpoint)
    }


    /** Differentiated, lightweight 2.5D county motifs; not mislabeled as 22 finished GLB models. */
    private fun countyDiorama(c: Canvas, county: String, night: Boolean, snow: Boolean, locality: String) {
        // Every selected district has a stable arrangement, shared by home + widgets.
        // Geographic identity is provided by reverse geocoding, not a fake 3D scan.
        val districtSeed = locality.hashCode().and(0x7fffffff)
        val i = TaiwanLandmarks.counties.indexOf(county).coerceAtLeast(0)
        val blue = if (night) color("#4C88A4") else color("#67B2C7")
        val stone = if (night) color("#9CA8B9") else color("#E2C8A5")
        val warm = if (night) color("#FFDDA4") else color("#D4EAF0")
        val green = if (snow) color("#D9F0F7") else color("#7DAE95")
        // The same three-layer oval palette is used by home and launcher widgets.
        oval(c,195f,509f,810f,559f,green)
        for (j in 0..5) {
            val x = 255f + 95f*j
            val h = 85f+ ((j*19+i*13+(districtSeed%49))%85)
            rect(c,x,502f-h,x+68f,505f,if(j%2==0)stone else blue)
            poly(c,color("#EAF4F2"),x,502f-h,x+17f,485f-h,x+85f,485f-h,x+68f,502f-h)
            if (night) for (k in 0..3) rect(c,x+14f+k*12f,465f-h,x+20f+k*12f,473f-h,warm)
        }
        when(county) {
            "台北市" -> taipei(c,night)
            "新北市" -> {
                // Danjiang Bridge-inspired miniature: river deck, asymmetrical mast and fan stays.
                // Stylised regional landmark, not a detailed engineering/3D reconstruction.
                oval(c,202f,494f,831f,556f,color("#5BB5D8"))
                round(c,262f,404f,789f,440f,8f,color("#D1E0E8"))
                poly(c,color("#E2F2F7"),554f,232f,577f,224f,604f,440f,573f,439f)
                poly(c,color("#87AAC2"),577f,224f,606f,439f,620f,444f)
                for (j in 0..8) {
                    val x = 292f + j*48f
                    line(c,568f,250f,x,405f,2f,color("#EDF6FA"),210)
                }
                for (j in 0..3) {
                    val x=315f+j*160f
                    rect(c,x,438f,x+15f,513f,color("#7D98A7"))
                }
                round(c,260f,398f,797f,410f,3f,color("#EAF9FF"))
            }
            "台南市", "金門縣", "連江縣", "新竹市" -> {
                val main = if(night)color("#CFA47C") else color("#B96C60")
                rect(c,420f,307f,600f,514f,stone)
                poly(c,main,393f,319f,508f,235f,626f,319f)
                rect(c,452f,368f,570f,520f,color("#7A9AB0"))
                for (j in 0..2) round(c,459f+j*37f,345f,482f+j*37f,367f,5f,warm)
            }
            "高雄市", "基隆市", "屏東縣", "澎湖縣" -> {
                oval(c,244f,486f,765f,534f,color("#69C6D7"))
                poly(c,stone,468f,245f,520f,245f,546f,495f,436f,495f)
                round(c,428f,394f,553f,418f,15f,warm)
                line(c,494f,202f,494f,246f,7f,color("#F7E7C3"))
                for (j in 0..2) poly(c,blue,270f+j*155f,498f,350f+j*155f,498f,323f+j*155f,474f)
            }
            "嘉義縣", "花蓮縣", "南投縣", "宜蘭縣", "台東縣" -> {
                poly(c,color("#6EA18F"),298f,486f,470f,196f,605f,487f)
                poly(c,color("#8EC7B2"),472f,481f,642f,278f,756f,490f)
                if(snow) poly(c,color("#F3F7FC"),427f,268f,470f,196f,517f,285f)
                rect(c,366f,442f,668f,506f,color("#A36B55"))
                for (j in 0..4) rect(c,390f+j*50f,449f,418f+j*50f,497f,warm)
            }
            "桃園市", "新竹縣", "台中市" -> {
                round(c,362f,295f,645f,518f,54f,stone)
                round(c,390f,336f,617f,495f,25f,blue)
                for(j in 0..3) rect(c,410f+j*44f,358f,438f+j*44f,388f,warm)
            }
            else -> { // 其餘縣市：獨立比例、輪廓與文化意象色彩
                val main=if(night)color("#DFB49F") else color("#C98D71")
                poly(c,main,390f,415f,502f,210f,610f,415f)
                rect(c,418f,402f,582f,523f,stone)
                for(j in 0..2) round(c,432f+j*46f,429f,464f+j*46f,471f,6f,warm)
            }
        }
        // Name is part of the accessible hero header, not stamped over the landmark art.
    }
    private fun cottage(c:Canvas,night:Boolean,snow:Boolean,rain:Boolean,phase:Float){
        val front=if(night)color("#F3CCA8") else color("#FFE5BE")
        val side=if(night)color("#C68C79") else color("#E6B58F")
        val roof=if(snow)color("#DCEFF5") else if(rain)color("#6C8098") else color("#A7545E")
        val roofLight=if(snow)color("#FFFFFF") else if(rain)color("#91AFC0") else color("#E18C83")
        // Small cobbled pathway through the garden.
        for(i in 0..5){val y=525f+i*9f;oval(c,488f-i*8,y,525f+i*7,y+9f,color("#E8D6BE"),190)}
        // Trees behind roof to create depth.
        line(c,267f,396f,260f,503f,18f,color("#775B55"));circle(c,258f,373f,72f,color("#4E8C76"));circle(c,289f,352f,53f,color("#84B49A"))
        line(c,749f,380f,763f,510f,17f,color("#715B4E"));circle(c,743f,353f,65f,color("#397F6B"));circle(c,779f,365f,43f,color("#7AAA91"))
        // Main 3D isometric house with offset side wall and wood corner trims.
        poly(c,side,674f,324f,759f,353f,759f,511f,674f,489f)
        poly(c,front,341f,333f,674f,333f,674f,510f,341f,510f)
        rect(c,350f,339f,361f,507f,color("#C79373"))
        rect(c,656f,336f,674f,509f,color("#D09D7A"))
        line(c,361f,480f,672f,480f,5f,color("#DEAF88"),120)
        // Chimney is behind the roof peak.
        poly(c,color("#956E71"),618f,187f,659f,203f,659f,314f,618f,300f)
        poly(c,color("#C78B82"),618f,187f,637f,179f,675f,192f,659f,203f)
        // Main roof has left lit plane and a darker oblique right plane.
        poly(c,roofLight,313f,339f,498f,207f,675f,332f,675f,347f,496f,231f,333f,352f)
        poly(c,roof,498f,207f,668f,257f,782f,354f,675f,332f)
        line(c,317f,343f,498f,212f,7f,if(snow)color("#FFFFFF") else color("#F6B1A1"),210)
        if(snow){
            line(c,322f,339f,497f,212f,11f,color("#FFFFFF"))
            line(c,498f,211f,773f,351f,10f,color("#F4FAFF"))
        }
        // Dormer with tiny round window makes the silhouette recognizable.
        poly(c,color("#DAB094"),483f,303f,546f,303f,546f,361f,483f,361f)
        poly(c,roof,477f,310f,514f,271f,551f,309f)
        circle(c,514f,323f,16f,color("#7CAFC8"))
        circle(c,511f,317f,5f,color("#E8FCFF"),150)
        // Door and porch columns with glow.
        round(c,470f,411f,555f,512f,14f,color("#735B64"))
        round(c,483f,421f,545f,511f,8f,color("#7E9F9C"))
        circle(c,531f,468f,4.4f,color("#F4DCAD"))
        round(c,451f,507f,573f,523f,6f,color("#D5C8B6"))
        val window=if(night)color("#FFF0A5") else color("#8FCCD6")
        for(x in listOf(390f,594f)) {
            round(c,x,389f,x+56f,441f,11f,color("#E9B796"))
            round(c,x+6f,395f,x+50f,435f,6f,window)
            line(c,x+28f,395f,x+28f,435f,4f,color("#A16E60"))
            line(c,x+5f,416f,x+51f,416f,3f,color("#A16E60"))
            if(night){
                val shader=paint(window,110).apply{this.shader=RadialGradient(x+28,414f,50f,window,AndroidColor.TRANSPARENT,Shader.TileMode.CLAMP)}
                c.drawCircle(x+28,414f,50f,shader);shader.shader=null
            }
        }
        round(c,702f,392f,736f,441f,4f,window)
        // Tiny weather vane, fence posts and ceramic pots.
        line(c,495f,207f,495f,158f,4f,color("#EEE4D6"))
        line(c,474f,171f,521f,171f,3f,color("#E2D9C6"));circle(c,495f,171f,6f,color("#F3C777"))
        for(x in listOf(286f,308f,782f,803f)) { round(c,x,473f,x+8f,535f,3f,color("#F1E2C9"));circle(c,x+4f,469f,6f,color("#F1E2C9")) }
        line(c,281f,506f,320f,506f,5f,color("#F1E2C9"));line(c,775f,507f,814f,507f,5f,color("#F1E2C9"))
        for(x in listOf(367f,721f)){
            round(c,x,484f,x+43f,515f,5f,color("#B4715D"))
            for(i in 0..4) circle(c,x+(i*9f),480f-(i%3)*6f,13f,color("#4E957B"))
            circle(c,x+18f,463f,7f,color("#E7B28B"))
        }
        if(rain){
            // Rain glints along the miniature island edge.
            oval(c,430f,543f,637f,557f,color("#B7F4FB"),90)
            for(i in 0..9) circle(c,320f+45f*i,552f+(i%3)*6f,3.5f,color("#D3F9FF"),140)
        }
        if(snow){rect(c,350f,502f,673f,510f,color("#EAF9FF"),230)}
        if(!night && !snow) for(i in 0..7){
            circle(c,260f+i*61f,540f+(i%3)*5f,3.4f,if(i%2==0)color("#F6DE9C") else color("#F7C7B7"))
        }
    }
    private fun taipei(c:Canvas,night:Boolean){
        rect(c,326f,490f,694f,538f,color("#34546B"));rect(c,400f,461f,614f,505f,color("#376C7F"))
        val floor=if(night)color("#479EAB") else color("#62BBB9")
        for(i in 0..8){val b=479f-i*37f;val w=123f-i*3.1f
            poly(c,floor,505f-w,b-38f,505f+w,b-38f,505f+w*.84f,b,505f-w*.84f,b)
            line(c,505f-w,b-38f,505f+w,b-38f,4f,color("#B0DFE0"),180)
            for(j in -3..3){val x=505f+j*21f;round(c,x,b-29f,x+11f,b-21f,1f,if(night)color("#FFE1A0") else color("#B8F3F1"))}
        }
        line(c,505f,95f,505f,150f,8f,color("#C4EAEB"));circle(c,505f,94f,7f,color("#EDE7D2"))
    }
    private fun skytree(c:Canvas,night:Boolean){
        rect(c,322f,497f,685f,539f,color("#3D5C75"))
        line(c,420f,514f,480f,157f,18f,color("#BBD8E5"));line(c,581f,514f,524f,157f,18f,color("#EEF6FE"))
        line(c,500f,97f,500f,530f,10f,color("#C8E6F3"))
        for(i in 0..10){val y=185f+i*29f;val half=28f+i*5f;line(c,500f-half,y,500f+half,y,6f,color("#83B3D2"))}
        round(c,395f,207f,605f,242f,13f,color("#C8E7F0"))
        for(i in 0..4)circle(c,425f+i*37f,228f,5f,if(night)color("#FDE8B4") else color("#88C1DE"))
    }
    private fun eiffel(c:Canvas,night:Boolean){
        val metal=if(night)color("#E5B882") else color("#C79678")
        line(c,333f,527f,475f,169f,26f,metal)
        line(c,668f,527f,528f,169f,26f,metal)
        line(c,473f,178f,528f,178f,18f,metal)
        line(c,500f,104f,500f,174f,9f,metal)
        for(i in 0..8){val y=208f+i*34f;val hw=32f+i*15f
            line(c,500f-hw,y,500f+hw,y,7f,metal)
            if(i<8)line(c,500f-hw,y,500f+hw+12f,y+32f,4f,color("#EAD1AD"))
        }
        rect(c,404f,372f,597f,392f,metal)
        rect(c,352f,505f,651f,520f,metal)
    }

    /** Scenic Bitmap is generated from current chosen city, sky condition, day/night and landmark. */
    fun widgetBitmap(scene:WeatherScene,condition:WeatherCondition,variant:Int,
        width:Int=520,height:Int=320,glassOpacity:Float=.66f,edgeLight:Float=.65f):Bitmap{
        val bm=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bm)
        val night=scene.nightLight>.4f
        val storm=condition==WeatherCondition.THUNDERSTORM
        val top=when {storm->color("#0B1632"); night->color("#10233F"); condition==WeatherCondition.SNOW->color("#577F9E");condition==WeatherCondition.RAIN->color("#1A526D");else->color("#257BB4")}
        val bottom=when {storm->color("#415879");night->color("#45638B");condition==WeatherCondition.SNOW->color("#C2DFEE");condition==WeatherCondition.RAIN->color("#7BA4B8");else->color("#91D4E6")}
        val shift=(variant % 5 - 2)*4f
        fun tinted(base:Int):Int {
            val hsv=FloatArray(3)
            AndroidColor.colorToHSV(base,hsv)
            hsv[0]=(hsv[0]+shift+360f)%360f
            return AndroidColor.HSVToColor(hsv)
        }
        val skyTop=tinted(top);val skyBottom=tinted(bottom)
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),38f,38f,paint(top).apply{
            shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),skyTop,skyBottom,Shader.TileMode.CLAMP)
        });p.shader=null
        val cx=width*.82f;val cy=height*.25f
        if(!night && !storm)circle(canvas,cx,cy,width*.065f,color("#FFE7A6"),180)
        if(night) for(i in 0..18)circle(canvas,((i*71)%width).toFloat(),((i*53)%(height/2)).toFloat(),1.0f,AndroidColor.WHITE,150)
        canvas.save();canvas.translate(width*.35f,height*.16f)
        canvas.scale(width/960f,height/820f)
        drawScene(canvas,1000f,650f,scene)
        canvas.restore()
        // Dark-to-clear foreground glass; the right side always shows the current 3D micro scene.
        p.reset();p.isAntiAlias=true;p.shader=LinearGradient(0f,0f,width*.85f,0f,
            intArrayOf(
                AndroidColor.argb((40+(1f-glassOpacity.coerceIn(0f,1f))*170).toInt(),9,30,51),
                AndroidColor.argb((25+(1f-glassOpacity.coerceIn(0f,1f))*150).toInt(),21,48,70),
                AndroidColor.argb(0,33,50,73)
            ),
            floatArrayOf(0f,.4f,1f),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),38f,38f,p);p.shader=null
        val frame=paint(AndroidColor.WHITE,(20+edgeLight.coerceIn(0f,1f)*170).toInt()).apply{style=Paint.Style.STROKE;strokeWidth=2.1f}
        canvas.drawRoundRect(2f,2f,width-2f,height-2f,38f,38f,frame);frame.style=Paint.Style.FILL
        // Specular rim and short high-light streak emulate refractive glass instead of opaque background.
        line(canvas,37f,11f,width*.62f,11f,2f,AndroidColor.WHITE,76)
        return bm
    }
}
