import json, pathlib
root=pathlib.Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets/landmarks';assets.mkdir(exist_ok=True,parents=True)
# Canonical official county labels; each settlement can expose several distinct landmarks.
# Entries without separately verified geometry are catalog candidates, not 3D assets.
counties=[
('臺北市','臺北', ['臺北101','國立故宮博物院','中正紀念堂','臺北表演藝術中心','北門']),
('新北市','新北',['淡江大橋','淡水紅毛城','野柳女王頭','九份老街','十三行博物館']),
('桃園市','桃園',['大溪老街','小烏來天空步道','桃園國際機場','慈湖紀念雕塑公園']),
('臺中市','臺中',['宮原眼科','臺中國家歌劇院','臺中車站','高美濕地','臺中公園湖心亭']),
('臺南市','臺南',['赤崁樓','安平古堡','臺南美術館二館','新臺南車站（規劃／工程狀態待核實）','奇美博物館']),
('高雄市','高雄',['夢時代','85大樓','高雄流行音樂中心','高雄車站','龍虎塔','衛武營國家藝術文化中心']),
('基隆市','基隆',['基隆港','基隆嶼','正濱漁港彩色屋','和平島地質公園']),
('新竹市','新竹市',['新竹東門城','新竹城隍廟','新竹火車站','十七公里海岸']),
('嘉義市','嘉義市',['檜意森活村','嘉義市噴水圓環','嘉義火車站','嘉義市立美術館']),
('新竹縣','新竹縣',['內灣吊橋','北埔老街','新竹縣政府文化局','小叮噹科學主題樂園']),
('苗栗縣','苗栗',['龍騰斷橋','勝興車站','三義木雕街','通霄神社']),
('彰化縣','彰化',['八卦山大佛','鹿港天后宮','扇形車庫','王功燈塔']),
('南投縣','南投',['日月潭向山遊客中心','日月潭慈恩塔','清境農場','埔里地理中心碑']),
('雲林縣','雲林',['北港朝天宮','西螺大橋','雲林布袋戲館','虎尾糖廠']),
('嘉義縣','嘉義縣',['阿里山森林鐵道','北回歸線太陽館','故宮南院','奮起湖車站']),
('屏東縣','屏東',['鵝鑾鼻燈塔','勝利星村','小琉球花瓶岩','東港東隆宮','海生館']),
('宜蘭縣','宜蘭',['龜山島','蘭陽博物館','羅東林業文化園區','礁溪溫泉公園']),
('花蓮縣','花蓮',['太魯閣長春祠','七星潭','松園別館','花蓮文化創意產業園區']),
('臺東縣','臺東',['三仙台跨海步橋','蘭嶼八代灣','綠島睡美人岩','鐵花村']),
('澎湖縣','澎湖',['澎湖跨海大橋','雙心石滬','澎湖天后宮','吉貝沙尾','桶盤嶼柱狀玄武岩']),
('金門縣','金門',['莒光樓','金門大橋','翟山坑道','烈嶼雙口海灘','烏坵燈塔']),
('連江縣','馬祖',['東引燈塔','芹壁聚落','北海坑道','東莒燈塔','大坵島梅花鹿']),
]
landmarks=[]
def slug(s):
 import hashlib
 return hashlib.sha1(s.encode()).hexdigest()[:10]
# First release prototypes: independently authored 3D shapes with simple textures, NOT official surveyed geometry.
models={
 '臺北101':'taipei101', '85大樓':'kaohsiung85','夢時代':'dreammall',
 '淡江大橋':'tamkang_bridge','高雄流行音樂中心':'music_center',
 '宮原眼科':'miyahara','赤崁樓':'chihkan','小琉球花瓶岩':'xiaoliuqiu_flower',
 '蘭嶼八代灣':'lanyu_bay','龜山島':'guishan_island',
 '新竹東門城':'hsinchu_gate','東引燈塔':'dongyin_lighthouse'
}
# Distinguish: approximate locations are for discovery only; DO NOT use as legal jurisdiction polygons.
locs={'臺北101':(25.0336,121.5646),'淡水紅毛城':(25.1700,121.4275),'龜山島':(24.8562,121.9510),
 '宮原眼科':(24.1368,120.6842),'夢時代':(22.5953,120.3062),'85大樓':(22.6116,120.3003),
 '小琉球花瓶岩':(22.3510,120.3812),'蘭嶼八代灣':(22.0378,121.5657),'赤崁樓':(22.9979,120.2021)}
for county,display,sites in counties:
 for name in sites:
  mid=models.get(name)
  landmarks.append(dict(id=mid or slug(county+name),country='TW',county=county,display_region=display,
   island=None,name=name,latitude=locs.get(name,(None,None))[0],longitude=locs.get(name,(None,None))[1],
   gps_approximate=True,model=f'landmarks/individual/{mid}.glb' if mid else None,
   preview=f'landmarks/individual/previews/{mid}.png' if mid else None,
   geometry_status='original_prototype' if mid else 'not_modelled',
   license='original_app_project' if mid else None,
   verification='needs_visual_and_location_review', priority=1 if mid else 2))
# Island-specific collection; exact island names prioritized before broad county routing.
islands=[
 ('小琉球','屏東縣','屏東',22.344,120.375,7,['小琉球花瓶岩','美人洞','厚石裙礁','落日亭']),
 ('蘭嶼','臺東縣','臺東',22.053,121.546,17,['蘭嶼八代灣','蘭嶼燈塔','青青草原','東清灣']),
 ('綠島','臺東縣','臺東',22.660,121.488,13,['綠島睡美人岩','朝日溫泉','綠島燈塔']),
 ('龜山島','宜蘭縣','宜蘭',24.843,121.952,6,['龜山島','龜山島牛奶海','龜山島401高地']),
 ('基隆嶼','基隆市','基隆',25.192,121.787,4,['基隆嶼','基隆嶼燈塔']),
 ('七美','澎湖縣','澎湖',23.211,119.426,8,['雙心石滬','小臺灣']),
 ('望安','澎湖縣','澎湖',23.367,119.503,8,['望安花宅聚落','天台山']),
 ('吉貝','澎湖縣','澎湖',23.739,119.603,6,['吉貝沙尾','吉貝石滬']),
 ('虎井','澎湖縣','澎湖',23.490,119.518,5,['虎井嶼','虎井嶼北回歸線']),
 ('桶盤','澎湖縣','澎湖',23.514,119.523,4,['桶盤嶼柱狀玄武岩']),
 ('西嶼','澎湖縣','澎湖',23.591,119.509,12,['西嶼燈塔','二崁聚落']),
 ('白沙','澎湖縣','澎湖',23.666,119.595,12,['澎湖跨海大橋','通梁古榕']),
 ('烈嶼','金門縣','金門',24.433,118.241,9,['烈嶼雙口海灘','九宮坑道']),
 ('烏坵','金門縣','金門',24.995,119.456,6,['烏坵燈塔']),
 ('南竿','連江縣','馬祖',26.151,119.951,8,['北海坑道','馬祖天后宮']),
 ('北竿','連江縣','馬祖',26.223,119.997,7,['芹壁聚落','壁山觀景台']),
 ('東莒','連江縣','馬祖',25.967,119.971,6,['東莒燈塔','福正聚落']),
 ('西莒','連江縣','馬祖',25.974,119.929,5,['坤坵沙灘']),
 ('東引','連江縣','馬祖',26.365,120.482,8,['東引燈塔','安東坑道']),
 ('大坵','連江縣','馬祖',26.228,119.960,3,['大坵島梅花鹿']),
]
for island,county,display,lat,lon,radius,names in islands:
 for name in names:
  cur=next((x for x in landmarks if x['name']==name and x['county']==county and x['island'] is None),None)
  if cur:cur['island']=island; continue
  mid=models.get(name)
  landmarks.append(dict(id=mid or slug(county+island+name), country='TW', county=county,display_region=display,island=island,
   name=name,latitude=None,longitude=None,gps_approximate=True,
   model=f'landmarks/individual/{mid}.glb' if mid else None,
   preview=f'landmarks/individual/previews/{mid}.png' if mid else None,
   geometry_status='original_prototype' if mid else 'not_modelled',
   license='original_app_project' if mid else None,verification='needs_visual_and_location_review',priority=1 if mid else 2))
foreign=[('東京','東京晴空塔'),('京都','清水寺'),('首爾','南山首爾塔'),('新加坡','濱海灣金沙'),('巴黎','艾菲爾鐵塔'),('倫敦','大笨鐘'),('紐約','自由女神像'),('舊金山','金門大橋')]
for city,name in foreign:
 landmarks.append(dict(id=slug(city+name),country='INTL',county=city,display_region=city,island=None,name=name,latitude=None,longitude=None,gps_approximate=True,model=None,preview=None,geometry_status='not_modelled',license=None,verification='needs_visual_and_location_review',priority=3))
manifest={'schema_version':2,'notes':'Places without original geometry are discovery candidates only. Original prototypes are stylized, not architectural surveys. Coordinates approximate; use reverse geocoding for administrative determination.','county_count':22,
 'island_regions':[dict(name=n,county=c,display_region=d,latitude=lat,longitude=lon,radius_km=rad) for n,c,d,lat,lon,rad,_ in islands],
 'landmarks':landmarks}
(assets/'catalog_v2.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
# Kotlin data model, with metadata and formatters; dynamically parse Android asset JSON to avoid duplicate source of truth.
kt=root/'app/src/main/java/io/atmos/weather/domain/visual/LandmarkRegistry.kt'
kt.write_text('''package io.atmos.weather.domain.visual

import android.content.Context
import org.json.JSONObject
import io.atmos.weather.domain.model.GeoPoint
import kotlin.math.*

/** V2 content catalog; a pending model must never be rendered as if a verified 3D landmark. */
data class LandmarkPlace(
    val id:String, val county:String, val displayRegion:String, val island:String?,
    val name:String, val model:String?, val preview:String?, val geometryStatus:String
) {
    val caption:String get() = "$displayRegion、$name"
    val hasModel:Boolean get() = geometryStatus == "original_prototype" && model != null && preview != null
}

data class IslandRegion(val name:String, val county:String, val centerLat:Double,
    val centerLon:Double, val radiusKm:Double)

class LandmarkRegistry private constructor(val places:List<LandmarkPlace>, val islands:List<IslandRegion>) {
    companion object {
        fun load(context:Context):LandmarkRegistry = context.assets.open("landmarks/catalog_v2.json").bufferedReader().use { reader ->
            val json=JSONObject(reader.readText())
            val sites=json.getJSONArray("landmarks")
            val places=buildList {
                for(i in 0 until sites.length()) {
                    val p=sites.getJSONObject(i)
                    add(LandmarkPlace(p.getString("id"),p.getString("county"),p.getString("display_region"),
                        p.optString("island").takeIf { it.isNotBlank() && it!="null" },
                        p.getString("name"),p.optString("model").takeIf { it.isNotBlank() && it!="null" },
                        p.optString("preview").takeIf { it.isNotBlank() && it!="null" },
                        p.getString("geometry_status")))
                }
            }
            val jIslands=json.getJSONArray("island_regions")
            val islands=buildList {
                for(i in 0 until jIslands.length()){
                    val x=jIslands.getJSONObject(i)
                    add(IslandRegion(x.getString("name"),x.getString("county"),
                        x.getDouble("latitude"),x.getDouble("longitude"),x.getDouble("radius_km")))
                }
            }
            LandmarkRegistry(places,islands)
        }
    }

    /** Island first, then county. Restrict island proximity to avoid placing visitors at sea. */
    fun islandFor(point:GeoPoint,label:String=""):IslandRegion? {
        val closest=islands.filter { distanceKm(point.latitude,point.longitude,it.centerLat,it.centerLon) <= it.radiusKm }
            .minByOrNull { distanceKm(point.latitude,point.longitude,it.centerLat,it.centerLon) }
        if(closest!=null) return closest
        // Label-only island fallback is never applied if coordinates are far away.
        return null
    }
    fun inRegion(county:String,island:String?):List<LandmarkPlace> {
        val scoped=places.filter { it.county==county && it.island==island }
        return if(scoped.isNotEmpty()) scoped else places.filter { it.county==county && it.island==null }
    }
    fun forLocation(point:GeoPoint,label:String):List<LandmarkPlace> {
        val island=islandFor(point,label)
        val county=island?.county ?: TaiwanLandmarks.countyFor(label,point)
        if(county!=null) return inRegion(county,island?.name)
        val name=label.substringAfterLast('·').trim()
        return places.filter { it.county==name && it.countryCodeCompatible() }
    }
    private fun LandmarkPlace.countryCodeCompatible():Boolean = true

    /** Choose locally modelled first; retain unmodelled candidates for a transparent catalog. */
    fun playable(point:GeoPoint,label:String):List<LandmarkPlace> = forLocation(point,label)
        .filter { it.hasModel }.ifEmpty { forLocation(point,label) }

    fun distanceKm(a:Double,b:Double,c:Double,d:Double):Double {
        val x=Math.toRadians(c-a)
        val y=Math.toRadians(d-b)
        val h=sin(x/2)*sin(x/2)+cos(Math.toRadians(a))*cos(Math.toRadians(c))*sin(y/2)*sin(y/2)
        return 6371.0*2*atan2(sqrt(h),sqrt((1.0-h).coerceAtLeast(0.0)))
    }
}
''',encoding='utf8')
print('Landmarks:',len(landmarks),'island regions',len(islands),'models prototypes',len(models),'file:',kt)
