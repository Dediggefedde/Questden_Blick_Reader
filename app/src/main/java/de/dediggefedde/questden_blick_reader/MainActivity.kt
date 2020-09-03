package de.dediggefedde.questden_blick_reader

//import com.squareup.picasso.Callback
//import com.squareup.picasso.Picasso
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.LinearSmoothScroller
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.*
import android.text.Html.TagHandler
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.list_item.view.*
import org.jsoup.Jsoup
import org.xml.sax.XMLReader
import java.lang.reflect.Field

/* Idea
* 1. get list of thread from frontpage (/quests/ at the moment
*   - call front, parse data using regex, save title,author,url,thumb and summary.
* 2. mark interesting quests via "watch" button.
* 3. manual/automatic check on watched threads
*   - call ID-page via url+50, or url
* 4. compare last read ID with result, count new posts and images and display result
* 5. show frontpage-list / only-watched-list with new counts
* 6. clicking an item opens page in browser. add last read to url, replace last read with newest Post ID
* */

/* Compatibility note
* API 16 supported (Jelly Bean) (99.8% used), but HTML strings only rendered at API 24 (Nougat) (74% used)
* fallback display html tags.
* */

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
    SFW_REAL, //spoiler images stay hidden. Note: Never requested
    SFW_QUESTION, //spoiler images reveal on click. Note: only request when clicked.
    NSFW //spoiler images loaded on default. Note: Immediatelly requested
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
    var isSpoiler: Boolean = false
)

/**
 * watchlist processing data
 *
 *  listOf in mainActivity, stable source of data for features
 *  alternative minimalizing: thread-ID/url instead of tgThread
 *  but: display watches would require scanning pages, while direct ID scans are already done
 */
data class Watch(
    var thread: TgThread = TgThread(),
    var lastReadId: String = "",
    var newestId: String = "",
    var newPosts: Int = 0,
    var newImg: Int = 0,
    var curReadId: String = ""
)

/**
 * settings object for later
 *  default sorting, default pages, sync-options
 *  loaded at start, saved on change
 */
data class Settings(
    var curpage: String = RequestValues.QUEST.url,
    var showOnlyPics: Boolean = false,
    var curSingle: Boolean = false,
    var sfw: SFWModes = SFWModes.SFW_QUESTION
)

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

//fun RecyclerView.smoothSnapToPosition(position: Int, snapMode: Int = LinearSmoothScroller.SNAP_TO_START) {
//    val smoothScroller = object : LinearSmoothScroller(this.context) {
//        override fun getVerticalSnapPreference(): Int = snapMode
//        override fun getHorizontalSnapPreference(): Int = snapMode
//    }
//    smoothScroller.targetPosition = position
//    layoutManager?.startSmoothScroll(smoothScroller)
//}

/**
 * Jsoup parse HTML code.
 * @param trimLength for trimming content after parsing
 */
fun getHTMLtext(str: String, trimLength: Int): String {
    val doc = Jsoup.parse(str)
    val tex = doc.text()
    return if (tex.length > trimLength) tex.substring(0, trimLength) else tex
}

/**
 * RecyclerView custom adapter to display tgthread correctly
 *  currently has copy of tgthread, perhaps index/reference to external list better
 *  context given for image click zoom capabilities
 *  planned to have alternative compact layout
 *  need investigation for memory management
 */
class QuestDenListAdapter(var items: List<TgThread>, var mContext: Context) :
    RecyclerView.Adapter<QuestDenListAdapter.ViewHolder>() {

    var txsize = 16


    /**
     * custom viewholder for one tgthread object
     */
//    inner class ViewHolder(inflater: LayoutInflater, parent: ViewGroup, itemView: View) :
    inner class FullViewHolder(itemView: View) : ViewHolder(itemView) {
//        init {
//        }
    }

    //for alternative view layout
//    inner class CompactViewHolder(itemView: View) : ViewHolder(itemView) {
//        init {
//            mTitleView = itemView.findViewById(R.id.tx_comp_title)
//            mSummaryView = null//itemView.findViewById(R.id.tx_comp_Summary)
//            mAuthorView = itemView.findViewById(R.id.tx_comp_author)
//            mImgView = itemView.findViewById(R.id.img_comp_url)
//            mWatchBut = itemView.findViewById(R.id.tx_comp_watch)
//            mPostsNew = itemView.findViewById(R.id.tx_comp_newPosts)
//            mImgNew = itemView.findViewById(R.id.tx_comp_newImg)
//            mNoView = itemView.findViewById(R.id.tx_comp_postID)
//            mDate = itemView.findViewById(R.id.tx_comp_date)
//
//            setListener()
//        }
//
//        override fun updateWatchState() {
//            if (mMain.isWatched(mtg.url)) {
//                val w: Watch = mMain.getWatch(mtg.url)
//
//                mWatchBut?.setTextColor(Color.parseColor("#FF37A523"))
////                mWatchBut?.text = mContext.getString(R.string.wBut_watched)
//                mPostsNew?.visibility = View.VISIBLE
//                mImgNew?.visibility = View.VISIBLE
//                mPostsNew?.text = mContext.getString(R.string.NewCompPosts, w.newPosts)
//                mImgNew?.text = mContext.getString(R.string.NewCOMPImg, w.newImg)
//            } else {
//                mWatchBut?.setTextColor(Color.parseColor("#A52B23"))
//                // mWatchBut?.text = mContext.getString(R.string.wBut_watch)
//                mPostsNew?.visibility = View.GONE
//                mImgNew?.visibility = View.GONE
//            }
//        }
//    }
    /**
     * Viewholder item. made abstract to offer multiple layouts. currently only one used
     */
    abstract inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private var mTitleView: TextView? = null
        private var mSummaryView: TextView? = null
        private var mAuthorView: TextView? = null
        private var mImgView: ImageView? = null
        private var mWatchBut: TextView? = null
        private var mPostsNew: TextView? = null
        private var mImgNew: TextView? = null
        private var mNoView: TextView? = null
        private var mDate: TextView? = null
        private val mMain: MainActivity = (mContext as MainActivity)
        private var mtg = TgThread()

        init {
            //object access
            mTitleView = itemView.findViewById(R.id.tx_title)
            mSummaryView = itemView.findViewById(R.id.tx_Summary)
            mAuthorView = itemView.findViewById(R.id.tx_author)
            mImgView = itemView.findViewById(R.id.img_url)
            mWatchBut = itemView.findViewById(R.id.tx_watch)
            mPostsNew = itemView.findViewById(R.id.tx_newPosts)
            mImgNew = itemView.findViewById(R.id.tx_newImg)
            mNoView = itemView.findViewById(R.id.tx_postID)
            mDate = itemView.findViewById(R.id.tx_date)
            setListener()
            //event listeners
        }

        private fun setListener() {
            val evThreadTitleClick = View.OnClickListener {
                if (mtg.isThread)
                    mMain.displayThread(mtg.url, false)
            }
            mTitleView?.setOnClickListener(evThreadTitleClick)
            mAuthorView?.setOnClickListener(evThreadTitleClick)
            mImgView?.setOnClickListener {
                mMain.progressBar.visibility = View.VISIBLE
                mMain.imageZoom.visibility = View.VISIBLE
                mMain.tx_img_path.visibility = View.VISIBLE
                var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
                if (mtg.isSpoiler && mMain.sets.sfw == SFWModes.SFW_REAL) str = "https://questden.org/kusaba/spoiler.png"

                Glide.with(mMain.imageZoom)
                    .load(str)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            mMain.progressBar.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                        ): Boolean {
//                            Log.d("onResReady", "mm")
                            mMain.progressBar.visibility = View.GONE
                            return false
                        }
                    })
                    .into(mMain.imageZoom)
                mMain.tx_img_path.text = str

            }
            mWatchBut?.setOnClickListener {
                if (mMain.isWatched(mtg.url)) {
                    mMain.removeFromWatch(mtg.url)
                } else {
                    mMain.addToWatch(mtg)
                }
                updateWatchState()
                notifyDataSetChanged()
            }
            mNoView?.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                val threadurl = mtg.url.replace(Regex("#\\d+"), "")
                openURL.data = Uri.parse("https://questden.org$threadurl#${mtg.postID}")
                it.context.startActivity(openURL)
            }
            mSummaryView?.setOnClickListener {
                if (mtg.isThread) mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                else {
                    if (it.tag == "clickableClick") {
                        it.tag = ""
                    } else {
                        if (mMain.toolbar.visibility == View.GONE) {
                            mMain.toolbar.visibility = View.VISIBLE
                            mMain.bottom_navigation.visibility = View.VISIBLE
                        } else {
                            mMain.toolbar.visibility = View.GONE
                            mMain.bottom_navigation.visibility = View.GONE
                        }
                    }
                }
            }
            updateWatchState()
        }

        /**
         * adapts item to current watch-state (new posts, imgs, iswatched etc
         */
        open fun updateWatchState() {
            if (mMain.isWatched(mtg.url)) {
                val w: Watch = mMain.getWatch(mtg.url)

                mWatchBut?.setTextColor(Color.parseColor("#FF37A523"))
                mWatchBut?.text = mContext.getString(R.string.wBut_watched)
                mPostsNew?.visibility = View.VISIBLE
                mImgNew?.visibility = View.VISIBLE
                mPostsNew?.text = mContext.getString(R.string.NewPosts, w.newPosts)
                mImgNew?.text = mContext.getString(R.string.NewImg, w.newImg)
            } else {
                mWatchBut?.setTextColor(Color.parseColor("#A52B23"))
                mWatchBut?.text = mContext.getString(R.string.wBut_watch)
                mPostsNew?.visibility = View.GONE
                mImgNew?.visibility = View.GONE
            }
        }

        /**
         * binds a thread object to an item for the recycle view
         * also sets visible, parses html, updates watch-state and fetches image
         * sets internally used mtg to the thread.
         */
        @SuppressLint("Range")
        fun bind(tg: TgThread) {
            mtg = tg

            if (mtg.isThread) {
                mWatchBut?.visibility = View.VISIBLE
                mPostsNew?.visibility = View.VISIBLE
                mImgNew?.visibility = View.VISIBLE
                updateWatchState()
            } else {
                mWatchBut?.visibility = View.GONE
                mPostsNew?.visibility = View.GONE
                mImgNew?.visibility = View.GONE
            }

            mAuthorView?.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            mTitleView?.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            mImgView?.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            mSummaryView?.textSize = txsize.toFloat()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                mTitleView?.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)

                if (!mtg.isThread) mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                else mSummaryView?.text = getHTMLtext(mtg.summary, 100)

                mAuthorView?.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                mTitleView?.text = mtg.title
                mSummaryView?.text = mtg.summary
                mAuthorView?.text = mtg.author
            }

            mDate?.text = tg.date
            mNoView?.text = mtg.postID

            if (!mtg.isHighlight) {
                itemView.linearLayout?.setBackgroundColor(Color.parseColor("#F0E0D6"))
            } else {
                itemView.linearLayout?.setBackgroundColor(Color.parseColor("#F0C0B6"))
            }

            if (mtg.imgUrl != "" && mImgView != null) {
                var imgUrl = "https://questden.org" + mtg.imgUrl
                if (mtg.isSpoiler && mMain.sets.sfw != SFWModes.NSFW) imgUrl = "https://questden.org/kusaba/spoiler.png"

                //val imgwidth=(80 * getSystem().displayMetrics.density).toInt()

                Glide.with(mImgView)
                    .load(imgUrl)
                    //.apply(RequestOptions.overrideOf (imgwidth,Target.SIZE_ORIGINAL ))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(p0: GlideException?, p1: Any?, target: Target<Drawable>?, p3: Boolean): Boolean {
                            Log.d("TAG", "onLoadFailed")
                            return false
                        }
                        override fun onResourceReady(p0: Drawable?, p1: Any?, target: Target<Drawable>?, p3: DataSource?, p4: Boolean): Boolean {
                            Log.d("TAG", "onResourceReady")
                            //do something when picture already loaded
                            mImgView?.invalidate()
                            return false
                        }
                    })
                    .into(mImgView)

            }
        }

    }

//    override fun getItemViewType(position: Int): Int {
//        return 0 // if(items[position].isThread) 1 else 0
//    }

    override fun getItemCount(): Int = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
//        if (viewType == 0) {
        val comView = inflater.inflate(R.layout.list_item, parent, false)
        return FullViewHolder(comView)
//        } else {
//            val comView = inflater.inflate(R.layout.list_item_compact, parent, false)
//            return CompactViewHolder(comView)
//        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
}

/**
 * HTML tag handler for fromHTML, parsing special html tags
 *  Needed to have custom reactions on links and special css behavior
 *  realized by replacing regex with custom tags.
 *  More specifically:
 *   >>123 link jumps to questid,
 *   <span> with spoiler blackened until clicked
 *   redirect http-links to browser
 */
class HTMLTagHandler(private var mContext: Context) : TagHandler {
    private var startQuote = 0
    private var startSpoil = 0
    private var startSmall = 0
    private var startAA = 0
    private var startCode = 0
    private var startURL = 0
    private var curURL = ""
    private var spoiled = false
    private val attributes = HashMap<String, String>()

    private fun processAttributes(xmlReader: XMLReader) {
        try {
            val elementField: Field = xmlReader.javaClass.getDeclaredField("theNewElement")
            elementField.isAccessible = true
            val element: Any? = elementField.get(xmlReader)
            val attsField: Field? = element?.javaClass?.getDeclaredField("theAtts")
            attsField?.isAccessible = true
            val atts: Any? = attsField?.get(element)
            val dataField: Field? = atts?.javaClass?.getDeclaredField("data")
            dataField?.isAccessible = true

            val data = (dataField?.get(atts) as? Array<*>)?.filterIsInstance<String>()
            val lengthField: Field? = atts?.javaClass?.getDeclaredField("length")
            lengthField?.isAccessible = true
            val len = lengthField?.get(atts) as Int
            if (data != null)
                for (i in 0 until len)
                    attributes[data[i * 5 + 1]] = data[i * 5 + 4]
        } catch (e: java.lang.Exception) {
            Log.d("TAG", "Exception: $e")
        }
    }

    override fun handleTag(
        opening: Boolean, tag: String, output: Editable,
        xmlReader: XMLReader
    ) {
//        Log.d("ttag", "$tag - $opening - ${output.length}")
        if (tag.equals("mybr", ignoreCase = true)) {
//            output.setspan(TextAppearanceSpan("\n",))
            if (!opening)
                output.setSpan(AbsoluteSizeSpan((mContext as MainActivity).listAdapt.txsize * 4 / 16, true), output.length - 1, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CQuote", ignoreCase = true)) {
            if (opening) startQuote = output.length
            if (!opening) output.setSpan(ForegroundColorSpan(Color.parseColor("#789922")), startQuote, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CSmall", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startSmall = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan((mContext as MainActivity).listAdapt.txsize * 10 / 16, true), startSmall, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("Caafont", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startAA = output.length
            if (!opening) output.setSpan(TypefaceSpan("serif"), startAA, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CCode", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startCode = output.length
            if (!opening) output.setSpan(TypefaceSpan("monospace"), startCode, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CSpoil", ignoreCase = true)) {

            if (opening) {
                startSpoil = output.length
                spoiled = true
            } else {
                output.setSpan(Clickabl(URLSpan(""), true, Color.WHITE, mContext), startSpoil, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spoiled = false
            }
        }
        if (tag.equals("CLink", ignoreCase = true)) {
//            Log.d("tags", "$tag:$opening - $output")
            if (opening) {
                processAttributes(xmlReader)
                curURL = attributes["href"].toString()
                startURL = output.length
            } else {
                var endp = output.substring(startURL).indexOf("</CLink>")
                if (endp == -1) endp = output.length - startURL
                output.setSpan(Clickabl(URLSpan(curURL), spoiled, Color.BLUE, mContext), startURL, endp + startURL, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//                Log.d("setasClick", output.toString())
            }
        }
    }
}

/**
 * Clickable text created by fromHTML and inserted by HTMLTagHandler
 *  spoiler (blackened) toggle readable
 *  http links opening browsers (to be done)
 */
class Clickabl(
    private var span: URLSpan?,
    private var spoiler: Boolean,
    private var colUnSpoil: Int,
    private var mContext: Context
) : ClickableSpan() {
    private var spoiled = false
    override fun onClick(view: View) {
        val rexTag = Regex(">>(\\d+)$")
        view.tag = "clickableClick"

        if (span != null && rexTag.matches(span!!.url)) {
            val main = mContext as MainActivity
            val pos = main.listAdapt.items.indexOfFirst { it.postID == rexTag.find(span!!.url)?.groupValues?.get(1) ?: 0 }

            if (main.listAdapt.items.size < pos || pos == -1) return

            if (main.chronic.size > 0 && main.chronic[main.chronic.lastIndex].prop != pos.toString())
                main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))

            main.scrollHighlight(pos)
//            main.ingredients_list.smoothScrollToPosition(pos)
            main.listAdapt.items.forEach { it.isHighlight = false }
            main.listAdapt.items[pos].isHighlight = true
            main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))
            main.listAdapt.notifyDataSetChanged()

//            if (main.toolbar.visibility == View.GONE) {
//                main.toolbar.visibility = View.VISIBLE
//                main.linearLayout2.visibility= View.VISIBLE
//            } else {
//                main.toolbar.visibility = View.GONE
//                main.linearLayout2.visibility= View.GONE
//            }
        }
        spoiled = !spoiled
        view.invalidate()
    }

    override fun updateDrawState(ds: TextPaint) {
        if (spoiler) {
            if((mContext as MainActivity).sets.sfw!=SFWModes.NSFW) {
                ds.color = if (spoiled) colUnSpoil else Color.BLACK
                ds.bgColor = Color.BLACK
            }else{
                ds.color = Color.BLACK
                ds.bgColor = Color.LTGRAY
            }
        } else {
            ds.color = Color.BLUE
            ds.bgColor = Color.TRANSPARENT
        }
    }
}

/**
 * Main activity
 * So far only activity
 * sets up all layouts, requests html, parses, fills data, manages back-click/menus etc.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    val listAdapt = QuestDenListAdapter(emptyList(), this)
    private var displayDataList = listOf<TgThread>()
    private var watchlist = mutableListOf<Watch>()
    var sets: Settings = Settings()
    private var totcnt = 0
    private var curcnt = 0

    //    private var curpos = 0
    private var curpage = 0
    var chronic = mutableListOf<Navis>()
//    private var lastlastpos=0

//    private val lastVisibleItemPosition: Int
//        get() = (ingredients_list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

    private lateinit var scrollListener: RecyclerView.OnScrollListener

    override fun onBackPressed() {
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
//        Log.d("pageback", "${sets.curpage} _ ${nav.prop} _ ${nav.operation}")
        when (nav.operation) {
            NavOperation.LINK -> {
                if (nav.navStat != null)
                    ingredients_list.layoutManager?.onRestoreInstanceState(nav.navStat)
            }
            NavOperation.PAGE -> {
                if (nav.prop != "" && sets.curpage != nav.prop)
                    displayThread(nav.prop)
            }
            NavOperation.THREAD -> {
//                if (nav.prop != "" && sets.curpage != nav.prop)
//                    updateThreadInfo(nav.prop)
            }
        }
    }

    private fun setRecyclerViewScrollListener() {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                updatePositionDisplay()

//                var lastvis=(ingredients_list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
//                var firstvis=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
//                var firstcompvis=(ingredients_list.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
//                var lastcompvis=(ingredients_list.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
//
//                if(curpos==displayDataList.size-1 && displayDataList.first().isThread && sets.curpage!=RequestValues.WATCH.url){
//                    curpage+=1
//                    var curboard=enumValues<RequestValues>().first{sets.curpage.indexOf(it.url)==0}
//                   displayThread("${curboard.url}${curpage}.html")
//
//
//                    //to do: load next (1.HTML, 2.HTML): keep track of page!
//                }

            }
        }
        ingredients_list.addOnScrollListener(scrollListener)
    }

    /**
     * scrolls to position, aligns top and sets isHighlight on thread-object
     * also remove isHighlight on others.
     * also adds chronic event (LINK) and updates position display
     */
    fun scrollHighlight(pos: Int) {
        if (listAdapt.items.size < pos || pos < 0) return

        (ingredients_list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0) //actuall scroll to position

        listAdapt.items.forEach { it.isHighlight = false } //unmark highlighted
        listAdapt.items[pos].isHighlight = true //mark scrolled to

        //back button
        chronic.add(Navis(NavOperation.LINK, pos.toString(), ingredients_list.layoutManager?.onSaveInstanceState()))

        //highlights changed
        listAdapt.notifyDataSetChanged()

        updatePositionDisplay(pos)
    }

    override fun onStop() {
        storeData()
        super.onStop()
    }

    override fun onDestroy() {
        storeData()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //layout
        setContentView(R.layout.activity_main)
        navigationStuff()
        ingredients_list.layoutManager = LinearLayoutManager(this)
        ingredients_list.adapter = listAdapt
        progressBar.visibility = View.GONE
        imageZoom.visibility = View.GONE
        tx_img_path.visibility = View.GONE

        //event handlers
        imageZoom.setOnClickListener {
            imageZoom.visibility = View.GONE
            tx_img_path.visibility = View.GONE
        }

        //start doing things with data
        loadData()
        setRecyclerViewScrollListener()
    }

    private fun convertToCustomTags(str: String?): String {
        if (str == null) return ""
        var ret = str

        var rex = Regex("""<span style="font-size:small;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span></span><CSmall>" + it.groupValues[1] + "</CSmall>"
        }
        rex = Regex("""<span style="font-family: Mona,'MS PGothic' !important;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span></span><Caafont>" + it.groupValues[1] + "</Caafont>" //span needed for leading tags being recocnized
        }
        rex = Regex("""<span style="white-space: pre-wrap !important; font-family: monospace, monospace !important;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span></span><CCode>" + it.groupValues[1] + "</CCode>" //span needed for leading tags being recocnized
        }
        rex = Regex("<span[^>]*?class=\"unkfunc\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span></span><CQuote>" + it.groupValues[1] + "</CQuote>"
        }
        rex = Regex("<span[^>]*?class=\"spoiler\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span></span><CSpoil>" + it.groupValues[1] + "</CSpoil>"
        }
        rex = Regex("<a[^>]*?href=\"(.*?)\"[^>]*?>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret) {
            "<span><CLink href='" + it.groupValues[2] + "'>" + it.groupValues[2] + "</CLink></span>"
        }


        rex = Regex("""<div[^>]*?>\s*?</div>\s*""")
        ret = rex.replace(ret, "")
        rex = Regex("""^\s*<br>""", RegexOption.DOT_MATCHES_ALL)
        ret = rex.replace(ret, "")
//        rex=Regex("""<br>\s*?<br>""",RegexOption.DOT_MATCHES_ALL)
//        ret = rex.replace(ret,"<br /><mybr><br /></mybr>")
        ret = ret.replace("<br>", "<br /><mybr><br /></mybr>")

//        Log.d("repll",ret)

        return ret
    }

    /**
     * sets up fromhtml to work on view if phone is higher version than N
     */
    fun setTextViewHTML(text: TextView?, html: String?) {
        if (text == null) return
        val imgGet = GlideImageGetter(text)
        val tagHandler = HTMLTagHandler(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
            val sequence: CharSequence = Html.fromHtml(convertToCustomTags(html), Html.FROM_HTML_MODE_COMPACT, imgGet, tagHandler)
            val strBuilder = SpannableStringBuilder(sequence)
            text.text = strBuilder
            text.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun navigationStuff() {
        navigationView.setNavigationItemSelectedListener(this)
        setSupportActionBar(toolbar)
        val menuDrawerToggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar,
            R.string.open_menu, R.string.closesMenu
        ).apply {
            drawer_layout.addDrawerListener(this)
            this.syncState()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        menuDrawerToggle.isDrawerIndicatorEnabled = true
        menuDrawerToggle.toolbarNavigationClickListener = null
        menuDrawerToggle.syncState()

        navigationView.itemIconTintList = null

//        val navDrawerToggle = ActionBarDrawerToggle(
//            this, tool_dropout, button2,
//            R.string.open_menu, R.string.closesMenu
//        ).apply {
//            drawer_layout.addDrawerListener(this)
//            this.syncState()
//        }

    }

//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
////        val inflater: MenuInflater = menuInflater
////        inflater.inflate(R.menu.menu_sorting, menu)
////        return super.onCreateOptionsMenu(menu)
////    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.menu_sort_date -> {
//                listAdapt.items = listAdapt.items.sortedBy { it.url }
//            }
//            R.id.menu_sort_img -> {
//                listAdapt.items = listAdapt.items.sortedByDescending {
//                    if (isWatched(it.url)) {
//                        getWatch(it.url).newImg
//                    } else {
//                        0
//                    }
//                }
//            }
//            R.id.menu_sort_posts -> {
//                listAdapt.items = listAdapt.items.sortedByDescending {
//                    if (isWatched(it.url)) {
//                        getWatch(it.url).newPosts
//                    } else {
//                        0
//                    }
//                }
//            }
//            R.id.menu_sort_jump -> {
//                scrollHighlight(36)
//            }
//        }
//        listAdapt.notifyDataSetChanged()
//        return super.onOptionsItemSelected(item)
//    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        curpage = 0
        when (menuItem.itemId) {
            R.id.menu_draw -> displayThread(RequestValues.DRAW.url)
            R.id.menu_general -> displayThread(RequestValues.MEEP.url)
            R.id.menu_quest -> displayThread(RequestValues.QUEST.url)
            R.id.menu_questdis -> displayThread(RequestValues.QUESTDIS.url)
            R.id.menu_tg -> displayThread(RequestValues.TG.url)
            R.id.menu_watch_open -> displayThread(RequestValues.WATCH.url)
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showWatches() {
        sets.curpage = RequestValues.WATCH.url

        displayDataList = watchlist.map {
            it.thread.isThread = true
            it.thread
        }.toList()
        displayThreadList(0)
    }


    /**
     * requests https://questden.org + relative url, expecting it to be a single thread
     * regex is used to parse this into displayDataList
     * calls displayThreadList() then to refresh recycleViewer
     * watchlist count update if watched
     * storedata to open again on start +watchlist save)
     *
     * complex method since none of these parts is repeated somewhere else.
     */

    fun displayThread(murl: String, onlyCheckWatch: Boolean = false) {

        if (!onlyCheckWatch) sets.curpage = murl

        if (murl == RequestValues.WATCH.url) {
            showWatches()
            storeData()
            return
        }

        val viewSingle = !enumValues<RequestValues>().any {
            murl == it.url
        }

        if (onlyCheckWatch && !isWatched(murl)) {
            return
        }
        progressBar.visibility = View.VISIBLE

        if (viewSingle)
            chronic.add(Navis(NavOperation.THREAD, murl))
        else
            chronic.add(Navis(NavOperation.PAGE, murl))

        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(
            Request.Method.GET, "https://questden.org$murl",
            Response.Listener<String> { response ->

                if (viewSingle) {
                    if (onlyCheckWatch) {
                        val rexLastPos = Regex(
                            ".*class=\"reflink\".*?a href=\"(.*?(\\d+))\"",
                            RegexOption.DOT_MATCHES_ALL
                        )
                        val curW: Watch = getWatch(murl)
                        var newRespPart: String

                        if (curW.lastReadId != "" && response.indexOf(curW.lastReadId) > 0) {
                            newRespPart = response.substring(response.indexOf(curW.lastReadId))
                            newRespPart =
                                newRespPart.substring(newRespPart.indexOf("<blockquote>")) //avoid img found in last post always new
                        } else {
                            newRespPart = response
                        }

                        val newReadId = rexLastPos.find(response)?.groupValues?.get(2) ?: ""
                        val newPosts = newRespPart.split("class=\"reflink\"").size - 1
                        val newImgs = newRespPart.split("class=\"thumb\"").size - 1

                        if (rexLastPos.containsMatchIn(response)) {
                            val newW = Watch(TgThread("", "", murl), curW.lastReadId, newReadId, newPosts, newImgs)
                            updateWatch(newW)
                        }
                    } else {
                        sets.curSingle = true
                        val doc = Jsoup.parse(response)

//                        Log.d("requestThread display", "a")

                        val res = doc.select("#delform,#delform>table").map {
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
                            tg.postID = tg.url.substring(tg.url.indexOf("#") + 1)

                            lis = it.select("blockquote")
                            if (lis.isNotEmpty()) tg.summary = lis.first().html()
                            tg.isThread = false

                            tg
                        }
                        displayDataList = res.toList()
                        displayThreadList(0)

                        if (isWatched(murl)) {
                            val w: Watch = getWatch(murl) //copy returned? then w.(...)=... will not do anything
                            if (w.curReadId == "") w.curReadId = w.lastReadId
                            val scrollpos = listAdapt.items.indexOfFirst { it.postID == w.curReadId }
//                            Log.d("position","${scrollpos}-${ w.curReadId}-${w.thread.url},${w.curReadId}")
                            scrollHighlight(scrollpos)
                            w.lastReadId = w.newestId
                            w.newImg = 0
                            w.newPosts = 0
                            setWatch(w)
                        }
                    }
                } else {
                    sets.curSingle = false
                    parseHTMLThread(response)
                }
                storeData()
                progressBar.visibility = View.GONE
            },
            Response.ErrorListener {
                listAdapt.items =
                    listOf(TgThread("There was an error loading the Thread"))
                listAdapt.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            })
        queue.add(stringRequest)

    }

    private fun storeData() {
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
        val gson = Gson()
        val jsonstring = gson.toJson(displayDataList)
        val jsonstring2 = gson.toJson(watchlist)
        val jsonstring3 = gson.toJson(sets)
        with(sharedPref.edit()) {
            putString("tgchanItems", jsonstring)
            putString("watchItems", jsonstring2)
            putString("sets", jsonstring3)
            commit()
        }
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
        val gson = Gson()
        var firstStart = false

        var jsonString = sharedPref.getString("tgchanItems", "")
        if (jsonString != "") {
            val itemType = object : TypeToken<List<TgThread>>() {}.type
            displayDataList = gson.fromJson<List<TgThread>>(jsonString, itemType)
        } else {
            firstStart = true
        }
        jsonString = sharedPref.getString("watchItems", "")
        if (jsonString != "") {
            val itemType2 = object : TypeToken<MutableList<Watch>>() {}.type
            watchlist = gson.fromJson<MutableList<Watch>>(jsonString, itemType2)
        }

        jsonString = sharedPref.getString("sets", "")
        if (jsonString != "") {
            val itemType3 = object : TypeToken<Settings>() {}.type
            sets = gson.fromJson<Settings>(jsonString, itemType3)
        }

        if (firstStart) {
            displayThread(RequestValues.QUEST.url)
        } else {
            if (!sets.curSingle) {
                displayThreadList(0)
            } else {
                val w = getWatch(displayDataList.first().url)
                val ind = displayDataList.indexOfFirst { w.curReadId == it.postID }
                displayThreadList()
                scrollHighlight(ind)
            }
        }
    }

    /**
     * adds thread to watchlist, updates counts and saves data
     */
    fun addToWatch(tg: TgThread) {
        if (isWatched(tg.url)) return
        watchlist.add(Watch(tg))
        displayThread(watchlist.last().thread.url, true)
        storeData()
    }

    /**
     * remove watch entry by thread-url
     */
    fun removeFromWatch(url: String) {
        watchlist.removeAll(watchlist.filter { it.thread.url == url })
        storeData()
    }

    /**
     * get watch object (copy) by url
     */
    fun getWatch(url: String): Watch {
        return watchlist.firstOrNull { it.thread.url == url } ?: return Watch()
    }

    /**
     * set watch object (copy) by url
     */
    private fun setWatch(w: Watch) {
        val ind = watchlist.indexOfFirst { it.thread.url == w.thread.url }
        if (ind == -1) return
        watchlist[ind] = w
    }

    /**
     * thread with url in watchlist?
     */
    fun isWatched(url: String): Boolean {
        return watchlist.any { it.thread.url == url }
    }

    private fun updateWatch(w: Watch) { //thread might only contain url

        val oldw: Watch = watchlist.first { it.thread.url == w.thread.url }
        oldw.lastReadId = w.lastReadId
        oldw.newImg = w.newImg
        oldw.newPosts = w.newPosts
        oldw.newestId = w.newestId

        watchlist.sortedWith(compareBy({ -it.newImg }, { -it.newPosts }))

        listAdapt.notifyDataSetChanged()
        storeData()
    }

    /**
     * updates scroll position display at bottom
     */
    fun updatePositionDisplay(position: Int = -1) {
        var pos = position + 1
        if (position == -1) pos =
            1 + (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()//(ingredients_list.layoutManager as LinearLayoutManager).getPosition(ingredients_list)
        totcnt = listAdapt.items.filter { it.imgUrl != "" }.size
        curcnt = listAdapt.items.take(pos).filter { it.imgUrl != "" }.size

//
//        var lastvis=(ingredients_list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
//        var firstvis=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
//        var firstcompvis=(ingredients_list.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
//        var lastcompvis=(ingredients_list.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()

        if (sets.curSingle && listAdapt.items.size > pos) { //update last position if single thread is displayed, not board/watchlist
            val w = getWatch(listAdapt.items.first().url)
            w.curReadId = listAdapt.items[pos].postID
            setWatch(w)
        }

        tx_position.text = getString(R.string.CurPos, curcnt, totcnt)
    }

//    private fun updateThreadInfo(url: String) {
//        progressBar.visibility = View.VISIBLE
//
//        val queue = Volley.newRequestQueue(this)
//        val stringRequest = StringRequest(Request.Method.GET, "https://questden.org$url",
//            Response.Listener<String> { response ->
//
//                val rexLastPos = Regex(
//                    ".*class=\"reflink\".*?a href=\"(.*?(\\d+))\"",
//                    RegexOption.DOT_MATCHES_ALL
//                )// /kusaba/quest/res/957117.html#957117
//                val curW: Watch = getWatch(url)
////                curW.lastReadId
//                var newRespPart: String
//
//                if (curW.lastReadId != "" && response.indexOf(curW.lastReadId) > 0) {
//                    newRespPart = response.substring(response.indexOf(curW.lastReadId))
//                    newRespPart =
//                        newRespPart.substring(newRespPart.indexOf("<blockquote>")) //avoid img found in last post always new
//                } else {
//                    newRespPart = response
//                }
//
//                val newReadId = rexLastPos.find(response)?.groupValues?.get(2) ?: ""
//                val newPosts = newRespPart.split("class=\"reflink\"").size - 1
//                val newImgs = newRespPart.split("class=\"thumb\"").size - 1
//
//                if (rexLastPos.containsMatchIn(response)) {
//                    val newW = Watch(TgThread("", "", url), curW.lastReadId, newReadId, newPosts, newImgs)
//                    updateWatch(newW)
//                }
//                progressBar.visibility = View.GONE
//
//                storeData()
//            },
//            Response.ErrorListener {
//                listAdapt.items =
//                    listOf(TgThread("There was an error loading the Thread"))
//                listAdapt.notifyDataSetChanged()
//                progressBar.visibility = View.GONE
//            }
//        )
//    }

    private fun updateWatchlist() {
        for (w in watchlist) {
            displayThread(w.thread.url, true)
        }
    }

    /*
     * button click event handler
     * view is needed even not used, otherwise crash
     * updates current page list and all watched thread numbers
     */

    /**
     * path text of fullview-image. Opens image link in browser
     */
    fun btnimgZoomPath(view: View) {
        val openURL = Intent(Intent.ACTION_VIEW)

        val targeturl = (view as TextView).text
        openURL.data = Uri.parse(targeturl.toString())
        startActivity(openURL)

        view.animate()//for sake of using view, so git would ignore the warning
    }

    private fun showSFWModeText(){ //set text to current mode
        when (sets.sfw) {
            SFWModes.SFW_QUESTION -> {
                btn_toggleSFW.text = getString(R.string.SFWQuestion)
            }
            SFWModes.SFW_REAL -> {
                btn_toggleSFW.text = getString(R.string.SFW)
            }
            SFWModes.NSFW -> {
                btn_toggleSFW.text = getString(R.string.NSFW)
            }
        }
    }
    /**
     * button toggle sfw mode
     * autoload spoiler images (yes,no, question = only on click)
     * click order SFW?→SFW→NSFW→SFW?
     */
    fun btnTglSFW(view: View) {
        when (sets.sfw) {
            SFWModes.SFW_QUESTION -> {
                sets.sfw = SFWModes.SFW_REAL
            }
            SFWModes.SFW_REAL -> {
                sets.sfw = SFWModes.NSFW
            }
            SFWModes.NSFW -> {
                sets.sfw = SFWModes.SFW_QUESTION
            }
            //for sake of using view, so git would ignore the warning
        }
        showSFWModeText()
        listAdapt.notifyDataSetChanged()

        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * opens/closes navigation tool sections
     */
    fun btnOpenTools(view: View) {
//        if(tool_dropout.isDrawerOpen(GravityCompat.START)){
        if (tool_dropout.visibility != View.GONE) {
            tool_dropout.visibility = View.GONE
//            tool_dropout.closeDrawer(GravityCompat.START)
        } else {
            tool_dropout.visibility = View.VISIBLE
//            tool_dropout.openDrawer(GravityCompat.START)
        }
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * button event for change font size
     */
    fun btnIncFont(view: View) {
        listAdapt.txsize = listAdapt.txsize + 1
        listAdapt.notifyDataSetChanged()
        view.animate()//for sake of using view, so git would ignore the warning

    }

    /**
     * button event for change font size
     */
    fun btnDecFont(view: View) {
        listAdapt.txsize = listAdapt.txsize - 1
        listAdapt.notifyDataSetChanged()
        view.animate()//for sake of using view, so git would ignore the warning

    }

    /**
     * update button, refreshes page, fetches updates when on watchlist
     */
    fun btnUpdateButton(view: View) {
        if (sets.curpage == RequestValues.WATCH.url) updateWatchlist()
        else displayThread(sets.curpage)
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * picture btn click
     * toggles showing all vs only posts with pictures
     */
    fun btnToggleOnlyPictures(view: View) {
        sets.showOnlyPics = !sets.showOnlyPics
        displayThreadList()
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * next image button
     * jumps and highlight to target
     */
    fun btnNextButton(view: View) {
        var pos = (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        pos += 1 + listAdapt.items.takeLast(listAdapt.items.size - pos - 1).indexOfFirst { it.imgUrl != "" }
//        ingredients_list.smoothSnapToPosition(pos)
        scrollHighlight(pos)
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * previous image button
     */
    fun btnPrevButton(view: View) {
        var pos = (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        pos = listAdapt.items.take(pos).indexOfLast { it.imgUrl != "" }
//        ingredients_list.smoothSnapToPosition(pos)
        scrollHighlight(pos)
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * first image button
     */
    fun btnFirstButton(view: View) {
        //var pos=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val pos = listAdapt.items.indexOfFirst { it.imgUrl != "" }
        scrollHighlight(pos)
        view.animate()//for sake of using view, so git would ignore the warning
    }

    /**
     * last image button
     */
    fun btnLastButton(view: View) {
        //var pos=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val pos = listAdapt.items.indexOfLast { it.imgUrl != "" }
        scrollHighlight(pos)
        view.animate()//for sake of using view, so git would ignore the warning
    }

    private fun parseHTMLThread(tex: String) {
        val rexSec = Regex("<div id=\"thread.*?>(.*?)<blockquote>(.*?)</blockquote", RegexOption.DOT_MATCHES_ALL)
        val rexTitle = Regex("<span.*?class=\"filetitle\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val rexAuthor = Regex("<span.*?class=\"postername\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val rexImg = Regex("<img.*?src=\"(.*?)\"[^>]*class=\"thumb\"", RegexOption.DOT_MATCHES_ALL) // /kusaba/quest/thumb/159280999777s.png
        val rexRef = Regex("class=\"reflink\".*?a href=\"(.*?(\\d+))\"", RegexOption.DOT_MATCHES_ALL)// /kusaba/quest/res/957117.html#957117
        val rexSpoilerImg = Regex("firstChild.src='(.*?)'", RegexOption.DOT_MATCHES_ALL)

        val rexSecRes = rexSec.findAll(tex)
        val titles =
            rexSecRes.map {
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
            }.filter { el -> el.url != "" }
        displayDataList = titles.toList()
        displayThreadList(0)
    }

    private fun displayThreadList(pos: Int = -1) {
        if (sets.showOnlyPics)
            listAdapt.items = displayDataList.filter { it.imgUrl != "" }
        else
            listAdapt.items = displayDataList//.take(10)
        listAdapt.notifyDataSetChanged()
        if (pos >= 0) {
            (ingredients_list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
//            updatePositionDisplay(pos)
        }
    }

}