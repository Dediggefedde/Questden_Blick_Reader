package de.dediggefedde.questden_blick_reader

//import com.squareup.picasso.Callback
//import com.squareup.picasso.Picasso
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
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
//import androidx.recyclerview.widget.DiffUtil
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
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
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
 * requestpage() acceptable values.
 * In principle any valid url with matching regexp page layout
 * also special links "watch" and "setting" (pending)
 */
object RequestValues {
    const val DRAW = "https://questden.org/kusaba/draw/"
    const val MEEP = "https://questden.org/kusaba/meep/"
    const val QUEST = "https://questden.org/kusaba/quest/"
    const val QUESTDIS = "https://questden.org/kusaba/questdis/"
    const val TG = "https://questden.org/kusaba/tg/"
    const val WATCH = "watch"
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
    var postID: String = "",
    var isThread: Boolean = false
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
    var newImg: Int = 0
)

/**
 * settings object for later
 *  default sorting, default pages, sync-options
 *  loaded at start, saved on change
 */
data class Settings(
    var curpage: String = RequestValues.QUEST
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

/**
 * RecyclerView custom adapter to display tgthread correctly
 *  currently has copy of tgthread, perhaps index/reference to external list better
 *  context given for image click zoom capabilities
 *  planned to have alternative compact layout
 *  need investigation for memory management
 */
class QuestDenListAdapter(var items: List<TgThread>, var mContext: Context) :
    RecyclerView.Adapter<QuestDenListAdapter.ViewHolder>() {

    /**
     * custom viewholder for one tgthread object
     */
//    inner class ViewHolder(inflater: LayoutInflater, parent: ViewGroup, itemView: View) :
        inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private var mTitleView: TextView? = null
        private var mSummaryView: TextView? = null
        private var mAuthorView: TextView? = null
        private var mImgView: ImageView? = null
        private var mWatchBut: TextView? = null
        private var mPostsNew: TextView? = null
        private var mImgNew: TextView? = null
        private var mNoView: TextView? = null
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

            //event listeners
            val evThreadTitleClick = View.OnClickListener {
                mMain.displaySingleThread(mtg.url)
            }
            mTitleView?.setOnClickListener(evThreadTitleClick)
            mImgView?.setOnClickListener {
                mMain.progressBar.visibility = View.VISIBLE
                mMain.imageZoom.visibility = View.VISIBLE
                val str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")

                Glide.with(mMain.imageZoom)
                    .load(str)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            mMain.progressBar.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?, model: Any?, target: com.bumptech.glide.request.target.Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                        ): Boolean {
                            Log.d("onResReady", "mm")
                            mMain.progressBar.visibility = View.GONE
                            return false
                        }
                    })
                    .into(mMain.imageZoom)
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

            //default layout
            updateWatchState()

        }

        private fun updateWatchState() {
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
        fun bind(tg: TgThread) {
            mtg = tg

            mWatchBut?.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE
            mPostsNew?.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE
            mImgNew?.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE

            mAuthorView?.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            mTitleView?.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            mImgView?.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                mTitleView?.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)
                //mSummaryView?.text = Html.fromHtml(mtg.summary, Html.FROM_HTML_MODE_COMPACT)
                if(mSummaryView!=null)mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                mAuthorView?.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                mTitleView?.text = mtg.title
                mSummaryView?.text = mtg.summary
                mAuthorView?.text = mtg.author
            }
            if (mtg.isThread) {
                updateWatchState()
            }

            mNoView?.text = mtg.postID

            if (mtg.imgUrl != "") {

                Glide.with(mImgView)
                    .load("https://questden.org" + mtg.imgUrl)
                    .into(mImgView)

                Log.d("img", mtg.imgUrl)
            }
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val comView=inflater.inflate(R.layout.list_item, parent, false)
        return ViewHolder(comView)
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
            if(data!=null)
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
        if (tag.equals("CQuote", ignoreCase = true)) {
            if (opening) startQuote = output.length
            if (!opening) output.setSpan(ForegroundColorSpan(Color.parseColor("#789922")), startQuote, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                output.setSpan(Clickabl(URLSpan(curURL), spoiled, Color.BLUE, mContext), startURL, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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

        if (span !=null && rexTag.matches(span!!.url)) {
                val main = mContext as MainActivity
                val pos = main.listAdapt.items.indexOfFirst { it.postID == rexTag.find(span!!.url)?.groupValues?.get(1) ?: 0 }

                if (main.chronic.size > 0 && main.chronic[main.chronic.lastIndex].prop != pos.toString())
                    main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))

                main.ingredients_list.smoothScrollToPosition(pos)
                main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))
        }
        spoiled = !spoiled
        view.invalidate()
    }

    override fun updateDrawState(ds: TextPaint) {
        if (spoiler) {
            ds.color = if (spoiled) colUnSpoil else Color.BLACK
            ds.bgColor = Color.BLACK
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
    var displayDataList = listOf<TgThread>()
    private var watchlist = mutableListOf<Watch>()
    private var sets: Settings = Settings()
    var chronic = mutableListOf<Navis>()

    private val lastVisibleItemPosition: Int
        get() = (ingredients_list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

    private lateinit var scrollListener: RecyclerView.OnScrollListener

    override fun onBackPressed() {
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
        Log.d("pageback", "${sets.curpage} _ ${nav.prop} _ ${nav.operation}")
        when (nav.operation) {
            NavOperation.LINK -> {
                if (nav.navStat != null)
                    ingredients_list.layoutManager?.onRestoreInstanceState(nav.navStat)
            }
            NavOperation.PAGE -> {
                if (nav.prop != "" && sets.curpage != nav.prop)
                    requestPage(nav.prop)
            }
            NavOperation.THREAD -> {
                if (nav.prop != "" && sets.curpage != nav.prop)
                    requestThread(nav.prop)
            }
        }
    }

    private fun setRecyclerViewScrollListener() {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                if (totalItemCount == lastVisibleItemPosition + 1) {
                    ingredients_list.removeOnScrollListener(scrollListener)
                    var nmax=listAdapt.items.size+10
                    if(nmax>displayDataList.size)
                        nmax=displayDataList.size
                    listAdapt.items=displayDataList.take(nmax)
                    listAdapt.notifyDataSetChanged()
                    setRecyclerViewScrollListener()
                }
            }
        }
        ingredients_list.addOnScrollListener(scrollListener)
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

//        listAdapt.submitList()
        //event handlers
        imageZoom.setOnClickListener {
            imageZoom.visibility = View.GONE
        }

        //start doing things with data
        loadData()
        setRecyclerViewScrollListener()
    }

    private fun convertToCustomTags(str: String?): String {
        if(str==null)return ""
        var ret = str

        val rexQuote = Regex("<span[^>]*?class=\"unkfunc\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        ret = rexQuote.replace(ret) {
            "<CQuote>" + it.groupValues[1] + "</CQuote>"
        }
        val rexSpoil = Regex("<span[^>]*?class=\"spoiler\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        ret = rexSpoil.replace(ret) {
            "<CSpoil>" + it.groupValues[1] + "</CSpoil>"
        }
        val rexLinks = Regex("<a[^>]*href=\"(.*?)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        ret = rexLinks.replace(ret) {
            "<CLink href='" + it.groupValues[2] + "'>" + it.groupValues[2] + "</CLink>"
        }
        return ret
    }

    /**
     * sets up fromhtml to work on view if phone is higher version than N
     */
    fun setTextViewHTML(text: TextView, html: String?) {
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
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_sorting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sort_date -> {
                listAdapt.items = listAdapt.items.sortedBy { it.url }
            }
            R.id.menu_sort_img -> {
                listAdapt.items = listAdapt.items.sortedByDescending {
                    if (isWatched(it.url)) {
                        getWatch(it.url).newImg
                    } else {
                        0
                    }
                }
            }
            R.id.menu_sort_posts -> {
                listAdapt.items = listAdapt.items.sortedByDescending {
                    if (isWatched(it.url)) {
                        getWatch(it.url).newPosts
                    } else {
                        0
                    }
                }
            }
            R.id.menu_sort_jump -> {
                ingredients_list.scrollToPosition(36)
            }
        }
        listAdapt.notifyDataSetChanged()
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_draw -> requestPage(RequestValues.DRAW)
            R.id.menu_general -> requestPage(RequestValues.MEEP)
            R.id.menu_quest -> requestPage(RequestValues.QUEST)
            R.id.menu_questdis -> requestPage(RequestValues.QUESTDIS)
            R.id.menu_tg -> requestPage(RequestValues.TG)
            R.id.menu_watch -> requestPage(RequestValues.WATCH)
            R.id.menu_settings -> {

            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showWatches() {
        sets.curpage = RequestValues.WATCH

        displayDataList=watchlist.map {
                it.thread.isThread = true
                it.thread
            }.toList()
        displayThreadList()
    }

    /**
     * requests https://questden.org + relative url, expecting it to be a single thread
     * regex is used to parse this into displayDataList
     * calls displayThreadList() then to refresh recycleViewer
     * watchlist count update if watched
     * storedata to open again on start +watchlist save)
     */
    fun displaySingleThread(url: String) {
        progressBar.visibility = View.VISIBLE
        chronic.add(Navis(NavOperation.THREAD, url))
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(Request.Method.GET, "https://questden.org$url",
            Response.Listener<String> { response ->

//                var scrollpos=0
                val rexSec = Regex("<div [^>]*? class=\"postwidth\">(.*?)</div>.*?<blockquote.*?>(.*?)</blockquote>", RegexOption.DOT_MATCHES_ALL)
                val rexTitle = Regex("<span.*?class=\"filetitle\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                val rexAuthor = Regex("<span.*?class=\"postername\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                val rexImg = Regex("<img.*?src=\"(.*?)\"[^>]*class=\"thumb\"", RegexOption.DOT_MATCHES_ALL) // /kusaba/quest/thumb/159280999777s.png
                val rexRef = Regex("class=\"reflink\".*?a href=\"(.*?(\\d+))\"", RegexOption.DOT_MATCHES_ALL)// /kusaba/quest/res/957117.html#957117
                val rexSpoilerImg = Regex("firstChild.src='(.*?)'", RegexOption.DOT_MATCHES_ALL)

                val posts = rexSec.findAll(response).map {
                    val th = TgThread()
                    val header = it.groupValues[1]
                    val content = it.groupValues[2]
                    if (rexTitle.containsMatchIn(header)) th.title = rexTitle.find(header)?.groupValues?.get(1)?.replace("\n", "") ?: ""
                    if (rexAuthor.containsMatchIn(header)) th.author = rexAuthor.find(header)?.groupValues?.get(1)?.replace("\n", "")?: ""
                    if (rexImg.containsMatchIn(header)) th.imgUrl = rexImg.find(header)?.groupValues?.get(1)?:""
                    if (rexSpoilerImg.containsMatchIn(header)) th.imgUrl =  rexSpoilerImg.find(header)?.groupValues?.get(1)?:""
                    if (rexRef.containsMatchIn(header)) {
                        th.url = rexRef.find(header)?.groupValues?.get(1)?:""
                        th.postID = rexRef.find(header)?.groupValues?.get(2)?:""
                    }
                    th.summary = content
                    th.isThread = false
                    th
                }
                displayDataList=posts.toList()
                displayThreadList()

                if (isWatched(url)) {
                    val w: Watch = getWatch(url) //copy returned? then w.(...)=... will not do anything
                    val scrollpos = listAdapt.items.indexOfFirst { it.postID == w.lastReadId }
                    ingredients_list.scrollToPosition(scrollpos)
                    w.lastReadId = w.newestId
                    w.newImg = 0
                    w.newPosts = 0
                }
                storeData()
                progressBar.visibility = View.GONE
            },
            Response.ErrorListener {
                listAdapt.items =
                    listOf(TgThread("There was an error loading the Thread https://questden.org$url"))
                listAdapt.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        )
        queue.add(stringRequest)
    }

    private fun requestPage(url: String) {
        sets.curpage = url
        chronic.add(Navis(NavOperation.PAGE, url))

        if (url == RequestValues.WATCH) {
            showWatches()
            return
        }

        progressBar.visibility = View.VISIBLE
        val queue = Volley.newRequestQueue(this)

        val stringRequest = StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                parseHTMLThread(response)
                storeData()
                progressBar.visibility = View.GONE
            },
            Response.ErrorListener {
                listAdapt.items = listOf(TgThread("There was an error loading quests"))
                listAdapt.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        )
        queue.add(stringRequest)
    }

    private fun storeData() {
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
        val gson = Gson()
        val jsonstring = gson.toJson(listAdapt.items)
        val jsonstring2 = gson.toJson(watchlist)
        with(sharedPref.edit()) {
            putString("tgchanItems", jsonstring)
            putString("watchItems", jsonstring2)
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
            listAdapt.items = gson.fromJson<List<TgThread>>(jsonString, itemType)
        } else {
            firstStart = true
        }
        jsonString = sharedPref.getString("watchItems", "")
        if (jsonString != "") {
            val itemType2 = object : TypeToken<MutableList<Watch>>() {}.type
            watchlist = gson.fromJson<MutableList<Watch>>(jsonString, itemType2)
        }

        if (firstStart) {
            requestPage(RequestValues.QUEST)
        } else {
            listAdapt.notifyDataSetChanged()
        }
    }

    /**
     * adds thread to watchlist, updates counts and saves data
     */
    fun addToWatch(tg: TgThread) {
        if (isWatched(tg.url)) return
        watchlist.add(Watch(tg))
        requestThread(watchlist.last().thread.url)
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
        return watchlist.first { it.thread.url == url }
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
        listAdapt.notifyDataSetChanged()
        storeData()
    }

    private fun requestThread(url: String) {
        progressBar.visibility = View.VISIBLE
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(Request.Method.GET, "https://questden.org$url",
            Response.Listener<String> { response ->

                val rexLastPos = Regex(
                    ".*class=\"reflink\".*?a href=\"(.*?(\\d+))\"",
                    RegexOption.DOT_MATCHES_ALL
                )// /kusaba/quest/res/957117.html#957117
                val curW: Watch = getWatch(url)
//                curW.lastReadId
                var newRespPart: String

                if (curW.lastReadId != "" && response.indexOf(curW.lastReadId) > 0) {
                    newRespPart = response.substring(response.indexOf(curW.lastReadId))
                    newRespPart =
                        newRespPart.substring(newRespPart.indexOf("<blockquote>")) //avoid img found in last post always new
                } else {
                    newRespPart = response
                }

                val newReadId = rexLastPos.find(response)?.groupValues?.get(2)?:""
                val newPosts = newRespPart.split("class=\"reflink\"").size - 1
                val newImgs = newRespPart.split("class=\"thumb\"").size - 1

                if (rexLastPos.containsMatchIn(response)) {
                    val newW = Watch(TgThread("", "", url), curW.lastReadId, newReadId, newPosts, newImgs)
                    updateWatch(newW)
                }
                progressBar.visibility = View.GONE

                storeData()
            },
            Response.ErrorListener {
                listAdapt.items =
                    listOf(TgThread("There was an error loading the Thread"))
                listAdapt.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        )
        queue.add(stringRequest)
    }

    private fun updateWatchlist() {
        for (w in watchlist) {
            requestThread(w.thread.url)
        }
    }

    /**
     * button click event handler
     * view is needed even not used, otherwise crash
     * updates current page list and all watched thread numbers
     */
    fun btnUpdateButton(view: View) {
        updateWatchlist()
        requestPage(sets.curpage)
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
                    th.title = rexTitle.find(threadHTML)?.groupValues?.get(1)?.replace("\n", "")?:""
                }
                if (rexAuthor.containsMatchIn(threadHTML)) {
                    th.author = rexAuthor.find(threadHTML)?.groupValues?.get(1)?.replace("\n", "")?:""
                }
                if (rexImg.containsMatchIn(threadHTML)) {
                    th.imgUrl = rexImg.find(threadHTML)?.groupValues?.get(1)?:""
                }
                if (rexSpoilerImg.containsMatchIn(threadHTML)) { //spoiler image
                    th.imgUrl = rexSpoilerImg.find(threadHTML)?.groupValues?.get(1)?:""
                }

                if (rexRef.containsMatchIn(threadHTML)) {
                    th.url = rexRef.find(threadHTML)?.groupValues?.get(1)?:""
                    th.postID = rexRef.find(threadHTML)?.groupValues?.get(2)?:""
                }
                th.summary = it.groupValues[2]
                th.isThread = true
                th
            }.filter { el -> el.url != "" }
        displayDataList=titles.toList()
        displayThreadList()
    }

    private fun displayThreadList() {
        listAdapt.items = displayDataList.take(10)
        listAdapt.notifyDataSetChanged()
        ingredients_list.scrollToPosition(0)
    }

}