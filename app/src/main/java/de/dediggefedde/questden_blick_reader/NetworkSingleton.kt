package de.dediggefedde.questden_blick_reader

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.HurlStack
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.UnsupportedEncodingException
import java.net.CookieHandler
import java.net.CookieManager
import java.nio.charset.Charset

fun parseJSoupToTgThread(it: Element): TgThread {
    val tg = TgThread()
    var lis = it.select("span.filetitle")
    if (lis.isNotEmpty()) tg.title = lis.first().text()
    lis = it.select("span.postername")
    if (lis.isNotEmpty()) tg.author = lis.first().text()
    lis = it.select("img.thumb")
    if (lis.isNotEmpty()) tg.imgUrl = lis.first().attr("src")
    if (tg.imgUrl.contains("spoiler.png")) {
        tg.imgUrl = lis.first().attr("onmouseover")
        val indfirstCh = tg.imgUrl.indexOf("firstChild.src='")
        if (indfirstCh == -1) {
            tg.imgUrl = ""
        } else {
            tg.imgUrl = tg.imgUrl.substring(tg.imgUrl.indexOf("firstChild.src='") + 16, tg.imgUrl.length - 1)
            tg.isSpoiler = true
        }
    }
    lis = it.select("div.postwidth label")
    if (lis.isNotEmpty()) {
        tg.date = lis.first().html()
        tg.date = tg.date.substring(tg.date.lastIndexOf("</span>") + 10)//-year
    }

    lis = it.select("span.reflink a")
    if (lis.isNotEmpty()) tg.url = lis.first().attr("href") //postID missing

    tg.postID =
        it.select("div.postwidth input[type=checkbox][name='post[]']").attr("value")// Regex("""(\d+)\.html""").find(tg.url)?.groupValues?.get(1) ?: "NAN"//tg.url.substring(tg.url.indexOf("#") + 1)

    if (tg.url.indexOf("#") >= 0) tg.url = tg.url.substring(0, tg.url.indexOf("#"))

    lis = it.select("blockquote")
    if (lis.isNotEmpty()) tg.summary = lis.first().html()
    tg.isThread = false

    return tg
}

class ThreadRequest(
    url: String, private val viewSingle: Boolean, private val lastReadId: String?,
    private val headers: MutableMap<String, String>?,
    private val listener: Response.Listener<MutableList<TgThread>>,
    errorListener: Response.ErrorListener
) : Request<MutableList<TgThread>>(Method.GET, url, errorListener) {

    override fun getHeaders(): MutableMap<String, String> = headers ?: super.getHeaders()

    override fun deliverResponse(response: MutableList<TgThread>?) = listener.onResponse(response)

//TODO request to update title/author, perhaps transmit first item as last like header
    override fun parseNetworkResponse(response: NetworkResponse?): Response<MutableList<TgThread>> {
        return try {
            var li: MutableList<TgThread>
            if (response == null) {
                Response.error<MutableList<TgThread>>(VolleyError("Response empty"))
            } else {
                var resp = String(response.data, Charset.forName(HttpHeaderParser.parseCharset(response.headers)))

                //Log.d("check", "$viewSingle, $lastReadId")
                if (viewSingle) { //single requests
                    if (lastReadId != null) { //check for new posts/images
//                        Log.d("loadCheck","single,lastread $url")
                        //with lastreadid returns list of new entries with first cut off tag <td> of last read element
                        val str = """id="reply$lastReadId"""
                        val ind = resp.indexOf(str)
                        if (ind > 0) resp = resp.substring(ind)
                        val doc = Jsoup.parse(resp)
                        li = doc.select("table").map {
                            parseJSoupToTgThread(it)
                        }.filter { it.postID != "" }.toMutableList()
                        if(li.isEmpty()){
                            li= mutableListOf(TgThread())
                        }

                    } else { //display thread
//                        Log.d("loadCheck","single,!lastread $url")
                        val doc = Jsoup.parse(resp)
                        li = doc.select("#delform,#delform>table").map {
                            parseJSoupToTgThread(it)
                        }.filter { it.postID != "" }.toMutableList()
                    }
                } else { //board overview
//                    Log.d("loadCheck","overview $url")
                    val rexSec = Regex("<div id=\"thread.*?>(.*?)<blockquote>(.*?)</blockquote", RegexOption.DOT_MATCHES_ALL)
                    val rexTitle = Regex("<span.*?class=\"filetitle\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    val rexAuthor = Regex("<span.*?class=\"postername\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    val rexImg = Regex("<img.*?src=\"(.*?)\"[^>]*class=\"thumb\"", RegexOption.DOT_MATCHES_ALL) // /kusaba/quest/thumb/159280999777s.png
                    val rexRef = Regex("class=\"reflink\".*?a href=\"(.*?(\\d+))\"", RegexOption.DOT_MATCHES_ALL)// /kusaba/quest/res/957117.html#957117
                    val rexSpoilerImg = Regex("firstChild.src='(.*?)'", RegexOption.DOT_MATCHES_ALL)
                    val rexMaxPage=Regex("""<a href="/kusaba/.*?/(\d+)\.html">\d+</a>""", RegexOption.DOT_MATCHES_ALL)

                    val rexSecRes = rexSec.findAll(resp)
                    li = rexSecRes.map {
                        val th = TgThread()
                        val threadHTML = it.groupValues[1]
                        if (rexTitle.containsMatchIn(threadHTML)) {
                            th.title = rexTitle.find(threadHTML)?.groupValues?.get(1)?.replace("\n", "") ?: ""
                        }
                        if (rexAuthor.containsMatchIn(threadHTML)) {
                            th.author = rexAuthor.find(threadHTML)?.groupValues?.get(1)?.replace("\n", "") ?: ""
                        }
                        if (rexImg.containsMatchIn(threadHTML)) {
                            th.imgUrl = rexImg.find(threadHTML)?.groupValues?.get(1) ?: ""
                        }
                        if (rexSpoilerImg.containsMatchIn(threadHTML)) { //spoiler image
                            th.imgUrl = rexSpoilerImg.find(threadHTML)?.groupValues?.get(1) ?: ""
                        }

                        if (rexRef.containsMatchIn(threadHTML)) {
                            th.url = rexRef.find(threadHTML)?.groupValues?.get(1) ?: ""
                            th.postID = rexRef.find(threadHTML)?.groupValues?.get(2) ?: ""
                        }
                        th.summary = it.groupValues[2]
                        th.isThread = true
                        th
                    }.filter { el -> el.url != "" }.toMutableList()

                    val retn=rexMaxPage.findAll(resp)
                        if(retn.count()>0)
                            li.add(TgThread("thread_info","","","",retn.last().groupValues.last()))
                }
                Response.success(
                    li,
                    HttpHeaderParser.parseCacheHeaders(response)
                )
            }
        } catch (e: UnsupportedEncodingException) {
            Response.error(ParseError(e))
        } catch (e: Exception) {
            Response.error(ParseError(e))
        }
    }

}


class MySingleton constructor(context: Context) {
    private lateinit var cache: DiskBasedCache
    private lateinit var network: BasicNetwork

    companion object {
        @Volatile
        private var INSTANCE: MySingleton? = null
        fun getInstance(context: Context) =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MySingleton(context).also {
                    INSTANCE = it
                }
            }
    }

    private val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        val manager = CookieManager()
        CookieHandler.setDefault(manager)
        cache = DiskBasedCache(context.cacheDir, 1024 * 1024) // 1MB cap
        network = BasicNetwork(HurlStack())
        RequestQueue(cache, network).apply {
            start()
        }

    }

    fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
    }
}
