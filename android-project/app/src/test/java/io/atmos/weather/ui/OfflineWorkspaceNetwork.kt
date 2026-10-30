package io.atmos.weather.ui

import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/** UI tests use local empty feed responses. No location or other data leaves the test JVM. */
internal fun installOfflineWorkspaceNetwork() {
    URL.setURLStreamHandlerFactory {protocol->
        if(protocol!="https"&&protocol!="http")null
        else object:URLStreamHandler() {
            override fun openConnection(url:URL):URLConnection = object:HttpsURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentType()="application/json"
                override fun getInputStream()=ByteArrayInputStream("{\"records\":{},\"data\":[]}".toByteArray())
                override fun getCipherSuite()="LOCAL_UI_FIXTURE"
                override fun getLocalCertificates():Array<Certificate>?=null
                override fun getServerCertificates():Array<Certificate> = emptyArray()
                override fun getPeerPrincipal():Principal?=null
                override fun getLocalPrincipal():Principal?=null
            }
        }
    }
}
