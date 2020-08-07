package de.dediggefedde.questden_blick_reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
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

// display data
// ListOf used in listview, filled from frontview, volatile, don't use as source of features
// used again in watchlist
data class TgThread(
    var title: String = "",
    var imgUrl: String = "", //all urls require https://questden.org prepended
    var url: String = "",  //used as unique identifier
    var author: String = "",
    var summary: String = "",
    var postID: String = "",
    var isThread: Boolean = false //true to be used in reader
)

// watch processing data
// listOf in mainActivity, stable source of data for features
// alternative minimalizing: thread-ID/url instead of tgThread
// but: display watches would require scanning pages, while direct ID scans are already done
data class Watch(
    var thread: TgThread = TgThread(),
    var lastReadId: String = "",
    var newestId: String = "",
    var newPosts: Int = 0,
    var newImg: Int = 0
)

data class Settings(
    var curpage: String = "https://questden.org/kusaba/quest/"
)

class IngredientsListAdapter(var items: List<TgThread>, var mContext: Context) :
    RecyclerView.Adapter<IngredientsListAdapter.ViewHolder>() {

    inner class ViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.list_item, parent, false)) {
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
//                val openURL = Intent(Intent.ACTION_VIEW)
//                openURL.data = Uri.parse("https://questden.org" + mtg.url)
                //it.context.startActivity(openURL)

                mMain.displaySingleThread(mtg)

            }
            mImgView!!.setOnClickListener {
                mMain.progressBar.visibility = View.VISIBLE
                mMain.imageZoom.visibility = View.VISIBLE
                val str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
//                mMain.button2.text = str
                Picasso.get().load(str)
                    .resize(800, 600).onlyScaleDown().centerInside()
                    .into(mMain.imageZoom, object : Callback {
                        override fun onSuccess() {
                            mMain.progressBar.visibility = View.GONE
                        }

                        override fun onError(e: Exception?) {
                            mMain.progressBar.visibility = View.GONE
                        }
                    })

            }
            mTitleView!!.setOnClickListener(evThreadTitleClick)
            mWatchBut!!.setOnClickListener {
                if (mMain.isWatched(mtg.url)) {
                    mMain.removeFromWatch(mtg)
                } else {
                    mMain.addToWatch(mtg)
                }
                updateWatchState()
                notifyDataSetChanged()
            }
            mNoView!!.setOnClickListener {
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

                mWatchBut!!.setTextColor(Color.parseColor("#FF37A523"))
                mWatchBut!!.text = mContext.getString(R.string.wBut_watched)
                mPostsNew!!.visibility = View.VISIBLE
                mImgNew!!.visibility = View.VISIBLE
                mPostsNew?.text = mContext.getString(R.string.NewPosts, w.newPosts)
                mImgNew?.text = mContext.getString(R.string.NewImg, w.newImg)
            } else {
                mWatchBut!!.setTextColor(Color.parseColor("#A52B23"))
                mWatchBut!!.text = mContext.getString(R.string.wBut_watch)
                mPostsNew!!.visibility = View.GONE
                mImgNew!!.visibility = View.GONE
            }
        }

        fun bind(tg: TgThread) {
            mtg = tg

            mWatchBut!!.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE
            mPostsNew!!.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE
            mImgNew!!.visibility = if (!mtg.isThread) View.GONE else View.VISIBLE

            mAuthorView!!.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            mTitleView!!.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            mImgView!!.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                mTitleView?.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)
                //mSummaryView?.text = Html.fromHtml(mtg.summary, Html.FROM_HTML_MODE_COMPACT)
                mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                mAuthorView?.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                mTitleView?.text = mtg.title
                mSummaryView?.text = mtg.summary
                mAuthorView?.text = mtg.author
            }
            if (mtg.isThread) {
                updateWatchState()
            }

            // mWatchImg!!.setImageResource(if(mtg.watching)R.drawable.ic_eye else R.drawable.ic_eye_closed)

            mNoView?.text = mtg.postID

            if (mtg.imgUrl != "") {
                Picasso.get().load("https://questden.org" + mtg.imgUrl)
                    .resize(800, 600).onlyScaleDown().centerInside()
                    .into(mImgView)
                Log.d("img", mtg.imgUrl)
            }
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
}


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
            val element: Any = elementField.get(xmlReader)!!
            val attsField: Field = element.javaClass.getDeclaredField("theAtts")
            attsField.isAccessible = true
            val atts: Any = attsField.get(element)!!
            val dataField: Field = atts.javaClass.getDeclaredField("data")
            dataField.isAccessible = true
//            val data = dataField.get(atts) as Array<String>
            val data = (dataField.get(atts) as? Array<*>)!!.filterIsInstance<String>()
            val lengthField: Field = atts.javaClass.getDeclaredField("length")
            lengthField.isAccessible = true
            val len = lengthField.get(atts) as Int
            for (i in 0 until len) attributes[data[i * 5 + 1]] = data[i * 5 + 4]
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
//            Log.d("tags", "$tag:$opening - $output")
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

class Clickabl(private var span: URLSpan?,
               private var spoiler: Boolean,
               private var colUnSpoil: Int,
               private var mContext: Context
) : ClickableSpan() {
    private var spoiled = false
    override fun onClick(view: View) {
        val rexTag = Regex(">>(\\d+)$")
//        Log.d("tag", span!!.url+" . "+rexTag.matches(span!!.url).toString())
        if (rexTag.matches(span!!.url)) {
            val main = mContext as MainActivity
            val pos = main.listAdapt.items.indexOfFirst { it.postID == rexTag.find(span!!.url)!!.groupValues[1] }
            main.ingredients_list.smoothScrollToPosition(pos)
            main.recylerViewState = main.ingredients_list.layoutManager!!.onSaveInstanceState()
            main.oldPos=(main.ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    val listAdapt = IngredientsListAdapter(emptyList(), this)
    private var watchlist = mutableListOf<Watch>()
    private var sets: Settings = Settings()
    private var lastUrl="https://questden.org/kusaba/quest/"
    var oldPos=0
    var recylerViewState:Parcelable?=null

    override fun onBackPressed() {
        /*case back is url switch
        Log.d("back",lastUrl + " . "+sets.curpage)
        if(sets.curpage!=lastUrl)
            requestPage(lastUrl)
         */
        if(recylerViewState!=null)
            ingredients_list.layoutManager!!.onRestoreInstanceState(recylerViewState)
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

        //event handlers
        imageZoom.setOnClickListener {
            imageZoom.visibility = View.GONE
        }

        //start doing things with data
        loadData()
    }

    private fun convertToCustomTags(str: String?): String {
        var ret = str
        val rexQuote = Regex("<span[^>]*?class=\"unkfunc\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        ret = rexQuote.replace(ret!!) {
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
//        Log.d("html", ret)
        return ret
    }

    fun setTextViewHTML(text: TextView, html: String?) {
        val imgGet = PicassoImageGetter2(Picasso.get(), text, this.resources)
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
            R.id.menu_draw -> requestPage("https://questden.org/kusaba/draw/")
            R.id.menu_general -> requestPage("https://questden.org/kusaba/meep/")
            R.id.menu_quest -> requestPage("https://questden.org/kusaba/quest/")
            R.id.menu_questdis -> requestPage("https://questden.org/kusaba/questdis/")
            R.id.menu_tg -> requestPage("https://questden.org/kusaba/tg/")
            R.id.menu_watch -> requestPage("watch")
            R.id.menu_settings -> {

            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showWatches() {
        sets.curpage = "watch"
        displayThreadList(
            watchlist.map {
                it.thread.isThread = true
                it.thread
            }
        )
    }

    fun displaySingleThread(tg: TgThread) {
        progressBar.visibility = View.VISIBLE
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(Request.Method.GET, "https://questden.org" + tg.url,
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
                    if (rexTitle.containsMatchIn(header)) th.title = rexTitle.find(header)!!.groupValues[1].replace("\n", "")
                    if (rexAuthor.containsMatchIn(header)) th.author = rexAuthor.find(header)!!.groupValues[1].replace("\n", "")
                    if (rexImg.containsMatchIn(header)) th.imgUrl = rexImg.find(header)!!.groupValues[1]
                    if (rexSpoilerImg.containsMatchIn(header)) th.imgUrl = rexSpoilerImg.find(header)!!.groupValues[1]
                    if (rexRef.containsMatchIn(header)) {
                        th.url = rexRef.find(header)!!.groupValues[1]
                        th.postID = rexRef.find(header)!!.groupValues[2]
                    }
                    th.summary = content
                    th.isThread = false
                    th
                }
//                if(isWatched(tg.url)){
//                    var w=getWatch(tg)
//                    scrollpos=posts.indexOfFirst { it.postID== w.lastReadId}
//                }
                displayThreadList(posts.toList())

                if (isWatched(tg.url)) {
                    val w: Watch = getWatch(tg.url)
                    val scrollpos = listAdapt.items.indexOfFirst { it.postID == w.lastReadId }
                    ingredients_list.scrollToPosition(scrollpos)
                    w.lastReadId = w.newestId
                    w.newImg = 0
                    w.newPosts = 0
                }
                storeData()

                //ingredients_list.scrollToPosition(scrollpos)
                progressBar.visibility = View.GONE
            },
            Response.ErrorListener {
                listAdapt.items =
                    listOf(TgThread("There was an error loading the Thread " + "https://questden.org" + tg.url))
                listAdapt.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        )
        queue.add(stringRequest)
    }

    /** Called when the user taps the Send button */
    private fun requestPage(url: String) {
        // Do something in response to button
        lastUrl=sets.curpage
        sets.curpage = url

        if(url=="watch"){
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
            requestPage("https://questden.org/kusaba/quest/")
        } else {
            listAdapt.notifyDataSetChanged()
        }
    }

    fun addToWatch(tg: TgThread) {
        if (isWatched(tg.url)) return
        watchlist.add(Watch(tg))
        requestThread(watchlist.last().thread.url)
        storeData()
    }

    fun removeFromWatch(tg: TgThread) {
        watchlist.removeAll(watchlist.filter { it.thread.url == tg.url })
        //watchlist.removeIf{it.thread.url==tg.url} API 24
        storeData()
    }

    fun getWatch(url: String): Watch {
        return watchlist.first { it.thread.url == url }
    }

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
        val stringRequest = StringRequest(Request.Method.GET,  "https://questden.org"+url,
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

                val newReadId = rexLastPos.find(response)!!.groupValues[2]
                val newPosts = newRespPart.split("class=\"reflink\"").size - 1
                val newImgs = newRespPart.split("class=\"thumb\"").size - 1

                if (rexLastPos.containsMatchIn(response)) {
                    val newW = Watch(TgThread("","",url), curW.lastReadId, newReadId, newPosts, newImgs)
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

    fun btnUpdateButton(view: View) { //view is needed even not used, otherwise crash
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
                    th.title = rexTitle.find(threadHTML)!!.groupValues[1].replace("\n", "")
                }
                if (rexAuthor.containsMatchIn(threadHTML)) {
                    th.author = rexAuthor.find(threadHTML)!!.groupValues[1].replace("\n", "")
                }
                if (rexImg.containsMatchIn(threadHTML)) {
                    th.imgUrl = rexImg.find(threadHTML)!!.groupValues[1]
                }
                if (rexSpoilerImg.containsMatchIn(threadHTML)) { //spoiler image
                    th.imgUrl = rexSpoilerImg.find(threadHTML)!!.groupValues[1]
                }

                if (rexRef.containsMatchIn(threadHTML)) {
                    th.url = rexRef.find(threadHTML)!!.groupValues[1]
                    th.postID = rexRef.find(threadHTML)!!.groupValues[2]
                }
                th.summary = it.groupValues[2]
                th.isThread = true
                th
            }.filter { el -> el.url != "" }
        displayThreadList(titles.toList())
    }

    private fun displayThreadList(li: List<TgThread>) {
        listAdapt.items = li //.joinToString("\n")
        listAdapt.notifyDataSetChanged()
        ingredients_list.scrollToPosition(0)
    }

}