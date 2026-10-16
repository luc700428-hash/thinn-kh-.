package io.atmos.weather.domain.visual

/** Exactly 22 county-level original stylised GLB models.
 * Previews are 2D PNGs suitable for weather hero and Android Widgets.
 * These are decorative art, not verified real-world 3D building geometry.
 */
object Landmark3dCatalog {
    fun file(county:String):String? = TaiwanLandmarks.counties.indexOf(county)
        .takeIf{it>=0}?.let { "landmarks/tw_${it.toString().padStart(2,'0')}.glb" }
    fun preview(county:String):String? = TaiwanLandmarks.counties.indexOf(county)
        .takeIf{it>=0}?.let { "landmarks/previews/tw_${it.toString().padStart(2,'0')}.png" }
}
