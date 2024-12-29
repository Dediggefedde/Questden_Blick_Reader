package de.dediggefedde.questden_blick_reader

import org.jsoup.nodes.Element
fun parseJSoupToTgThread(it: Element): TgPost {
    val tg = TgPost()
    var lis = it.select("span.filetitle")
    tg.title = lis.first()?.text() ?: ""
    lis = it.select("span.postername")
    tg.author = lis.first()?.text() ?: ""
    lis = it.select("img.thumb")
    if (lis.isNotEmpty()) tg.imgUrl = lis.first()?.attr("src")?:""
    if (tg.imgUrl.contains("spoiler.png")) {
        tg.imgUrl = lis.first()?.attr("onmouseover")?:""
        val indfirstCh = tg.imgUrl.indexOf("firstChild.src='")
        if (indfirstCh == -1) {
            tg.imgUrl = ""
        } else {
            tg.imgUrl = tg.imgUrl.substring(tg.imgUrl.indexOf("firstChild.src='") + 16, tg.imgUrl.length - 1)
            tg.isSpoiler = true
        }
    }
    lis = it.select("div.postwidth label")
    if (lis.isNotEmpty() && lis.first()!=null) {
        tg.date = lis.first()!!.html()?:""
        tg.date = tg.date.substring(tg.date.lastIndexOf("</span>") + 10)//-year
    }

    lis = it.select("span.reflink a")
    tg.url = lis.first()?.attr("href")?:"" //postID missing

    tg.postID =
        it.select("div.postwidth input[type=checkbox][name='post[]']").attr("value")// Regex("""(\d+)\.html""").find(tg.url)?.groupValues?.get(1) ?: "NAN"//tg.url.substring(tg.url.indexOf("#") + 1)

    if (tg.url.indexOf("#") >= 0) tg.url = tg.url.substring(0, tg.url.indexOf("#"))

    lis = it.select("blockquote")
    if (lis.isNotEmpty()) tg.summary = lis.first()?.html()?:""
    return tg
}
