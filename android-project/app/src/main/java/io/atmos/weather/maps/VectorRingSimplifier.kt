package io.atmos.weather.maps

/** Closed-ring Douglas-Peucker; never drops a small island or changes the source at deep zoom. */
internal object VectorRingSimplifier {
    fun simplify(points:FloatArray,tolerance:Float):FloatArray {
        val n=points.size/2
        if(tolerance<=0f||n<=4)return points
        val keep=BooleanArray(n);keep[0]=true;keep[n-1]=true
        val stack=IntArray(n*2);var count=0
        stack[count++]=0;stack[count++]=n-1
        val limit=tolerance*tolerance
        while(count>0) {
            val end=stack[--count];val start=stack[--count]
            val ax=points[start*2];val ay=points[start*2+1]
            val dx=points[end*2]-ax;val dy=points[end*2+1]-ay;val length=dx*dx+dy*dy
            var furthest=-1;var maximum=limit
            for(i in start+1 until end) {
                val px=points[i*2]-ax;val py=points[i*2+1]-ay
                val t=if(length>0f)((px*dx+py*dy)/length).coerceIn(0f,1f) else 0f
                val ex=px-t*dx;val ey=py-t*dy;val distance=ex*ex+ey*ey
                if(distance>maximum){maximum=distance;furthest=i}
            }
            if(furthest>=0) {
                keep[furthest]=true
                stack[count++]=start;stack[count++]=furthest
                stack[count++]=furthest;stack[count++]=end
            }
        }
        val retained=keep.count{it}
        if(retained<4)return points
        val result=FloatArray(retained*2);var at=0
        for(i in 0 until n)if(keep[i]) {result[at++]=points[i*2];result[at++]=points[i*2+1]}
        return result
    }
}
