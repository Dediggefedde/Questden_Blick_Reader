package de.dediggefedde.questden_blick_reader

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize


/**
 * displayThread() special values
 * In principle any valid url with matching regexp page layout
 * also special links "watch" and "setting" (pending)
 */
enum class RequestValues(val url: String) {
    DRAW("/kusaba/draw/"),
    MEEP("/kusaba/meep/"),
    QUEST("/kusaba/quest/"),
    QUESTDIS("/kusaba/questdis/"),
    TG("/kusaba/tg/"),
    WATCH("watch")//,
//    SETTING("/kusaba/quest/res/954051.html")
}

/**
 * auto show/hide mode for spoiler images and texts
 */
enum class SFWModes {
    @SerializedName("SFWREAL") SFWREAL, //spoiler images stay hidden. Note: Never requested
    @SerializedName("SFWQUESTION") SFWQUESTION, //spoiler images reveal on click. Note: only request when clicked.
    @SerializedName("NSFW") NSFW //spoiler images loaded on default. Note: Immediatelly requested
}

/**
 * display data
 *
 *  ListOf used in listview, filled from frontview, volatile, don't use as source of features
 *  used again in watchlist
 * @property imgUrl all urls require https://questden.org prepended
 * @property isThread true=overview, false=single-thread entry
 * @property url used as unique identifier to thread (first post is also thread-url)
 * @property postID also unique identifier to single post
 */
@Parcelize
data class TgThread(
    var title: String = "",
    var imgUrl: String = "",
    var url: String = "",
    var author: String = "",
    var summary: String = "",
    var date: String = "",
    var postID: String = "",
    var isThread: Boolean = false,
    var isHighlight: Boolean = false,
    var isSpoiler: Boolean = false,
    var newPosts: Int = 0,
    var newImg: Int = 0
):Parcelable

/**
 * watchlist processing data
 *
 *  listOf in mainActivity, stable source of data for features
 *  alternative minimalizing: thread-ID/url instead of tgThread
 *  but: display watches would require scanning pages, while direct ID scans are already done
 *  lastread: last reading position
 *  newestId: position after which posts are "new"
 */
@Parcelize
data class Watch(
    var thread: TgThread = TgThread(),
    var newestId: String = "",
    var newPosts: Int = 0,
    var newImg: Int = 0,
    var lastReadId: String = ""
):Parcelable

/**
 * Sorting behavior states
 */
enum class SORTING {
    DATE,POSTS,IMAGES,NATIVE
}
/**
 * settings object for later
 *  default sorting, default pages, sync-options
 *  loaded at start, saved on change
 */
//
@Parcelize
data class Settings(
    var curpage: String = RequestValues.QUEST.url,
    var curThreadId:String="",
    var curMaxPage:Int=0,
    var showOnlyPics: Boolean = false,
    var curSingle: Boolean = false, //single thread or board/watchlist
    var boardPage:Int=0, //pagination boards
    var sfw: SFWModes = SFWModes.SFWQUESTION,
    var txsize: Float = 16f,
    var user: String = "",
    var pw: String = "",
    val lastReadIDs: MutableMap<String, String> = mutableMapOf(), //thread ids → last read pos
    var curTitle:String=""
):Parcelable
/**
 * enum for navigation object
 * page for quest/tg etc switch, link for quote-clicked, thread for thread opened
 */
enum class NavOperation { PAGE, LINK, THREAD }

/**
 * navigation object for chronic (back-button)
 */
data class Navis(
    var operation: NavOperation,
    var prop: String,
    var navStat: Parcelable? = null
)
