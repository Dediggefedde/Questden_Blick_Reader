package de.dediggefedde.questden_blick_reader

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * displayThread() special values
 * In principle any valid url with matching regexp page layout
 * also special links "watch" and "setting" (pending)
 */
enum class URLBoards(val url: String) {
    DRAW("/kusaba/draw/"),
    MEEP("/kusaba/meep/"),
    QUEST("/kusaba/quest/"),
    QUESTDIS("/kusaba/questdis/"),
    TG("/kusaba/tg/")
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
 */
@Parcelize
data class TgPost(
    var title: String = "", //post title
    var imgUrl: String = "", //image url
    var url: String = "", //url of post
    var author: String = "", //author name
    var summary: String = "", //post text
    var date: String = "", //date of post
    var postID: String = "", //ID of post
    var isHighlight: Boolean = false, //is currently selected
    var isSpoiler: Boolean = false, //is marked as spoiler (img)
    var thumbHeight:Int=0, //height of thumb for floating text
    var threadExtended:Boolean=false, //summary extended in board view
    var imgCounter:Int=0, //counter for images in the thread
    var postCount:Int=0 //amount of posts from overview
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
    var thread: TgPost = TgPost(),
    var lastReadId: String = "",
    var newPosts: Int = 0,
    var newImg: Int = 0,

    var highImgOnly :Boolean=true,
    var highIDs :String="",
    var highNames :String="",
    var ignoreIDs :String="",
    var ignoreNames :String=""
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
data class OfflineThread(
    var thread: TgPost = TgPost(),
    var onlyThumbs:Boolean=false,
    var lastUpdate: Long=System.currentTimeMillis()
):Parcelable

//Sorting behavior states
//enum class SORTING {
//    DATE,POSTS,IMAGES
//}

//Types of display:
// Board=list of threads, thread=a quest, watch=list of watched threads, offline= list of downloaded threads
enum class ThrdItemTyps {
    BOARD,THREAD,WATCH,OFFLINE
}

//how model renders view
@Parcelize
data class ModelSettings(
    var showOnlyPics: Boolean = false,
    var sfw: SFWModes = SFWModes.SFWQUESTION,
    var listType: ThrdItemTyps =ThrdItemTyps.BOARD,
    var curTitle:String="",
    var curURL:String=URLBoards.QUEST.url,
    var curThreadId:String="",
    var boardPage: Int=0,
    var curMaxPage:Int=0,
    val curReadPostID: MutableMap<String, String> = mutableMapOf(), //thread ids → last read pos
    var txsize: Float = 16f,
    var loginName:String="",
    var loginPW:String="",
    var autoLogin:Boolean=true,
    var numLinkMode:Int=0, //for upload, not used in app
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
enum class ScrollMode{
    ALL,IMAGES
}
//progress from viewmodel

data class ProgData(
    var pos:Int=0,
    var max:Int=0,
    var status:ProgStatus=ProgStatus.IDLE,
    var msg:String=""
)
enum class ProgStatus{
    IDLE, RUNNING,DONE,ERROR
}
