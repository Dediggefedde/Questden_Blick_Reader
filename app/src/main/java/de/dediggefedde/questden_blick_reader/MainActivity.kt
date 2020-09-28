package de.dediggefedde.questden_blick_reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.android.volley.Response
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*

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
 * Main activity
 * So far only activity
 * sets up all layouts, requests html, parses, fills data, manages back-click/menus etc.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    val listAdapt = QuestDenListAdapter(emptyList(), this)
    private var displayDataList = listOf<TgThread>()
    private var watchlist = mutableListOf<Watch>()
    var sets: Settings = Settings() //current app settings
    private var totcnt = 0 //max position in navigations
    private var curcnt = 0 //current position in navigation
    private var reqCnt = 0 //max position in progressbar
    private var reqDone = 0 //current position in progressbar
    var chronic = mutableListOf<Navis>()

    private lateinit var scrollListener: RecyclerView.OnScrollListener

    override fun onBackPressed() {
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
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
            }
        }
    }

    private fun setRecyclerViewScrollListener() {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                updatePositionDisplay()
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
        ret = ret.replace(Regex("""<br>[\n\r\s]*<br>""", RegexOption.DOT_MATCHES_ALL), "<br /><mybr2><br /></mybr2>")
        ret = ret.replace("<br>", "<br /><mybr><br /></mybr>")

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

    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_draw -> displayThread(RequestValues.DRAW.url, false)
            R.id.menu_general -> displayThread(RequestValues.MEEP.url, false)
            R.id.menu_quest -> displayThread(RequestValues.QUEST.url, false)
            R.id.menu_questdis -> displayThread(RequestValues.QUESTDIS.url, false)
            R.id.menu_tg -> displayThread(RequestValues.TG.url, false)
            R.id.menu_watch_open -> displayThread(RequestValues.WATCH.url, false)
            R.id.menu_reader_sync -> {
                val inte = Intent(this, SyncActivity::class.java)
                inte.putExtra("user", sets.user)
                inte.putExtra("pw", sets.pw)
                // If an instance of this Activity already exists, then it will be moved to the front. If an instance does NOT exist, a new instance will be created
                startActivityForResult(inte, 123)
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 123) {
            val watchResp = data?.getStringExtra("response")
            sets.user = data?.getStringExtra("user") ?: sets.user
            sets.pw = data?.getStringExtra("pw") ?: sets.pw

            // watchids = new watchbar, char(11) split each thread
            // thread information char(12) split: board, id, title, author
            if (watchResp != "") {
                val lis = watchResp?.split(11.toChar())?.map {
                    val tg = TgThread()
                    val inf = it.split(12.toChar())
                    val url = "/kusaba/${inf[0]}/res/${inf[1]}.html"
                    //tg.postID=inf[1]
                    tg.url = url
                    val w = Watch(tg)
                    w
                }
                if (lis != null) {
                    watchlist = lis.toMutableList()
                }
                updateWatchlist() //fill missing thread and watch information
            }

            storeData()
        }
    }

    private fun showWatches() {
        sets.curpage = RequestValues.WATCH.url


        displayDataList = watchlist.sortedWith(compareBy({ -it.newImg }, { -it.newPosts })).map {
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

    fun displayThread(url: String, viewSingle: Boolean = false, onlyCheckWatch: Boolean = false) {
        var murl = url
        val fet = murl.indexOf("#")
        if (fet >= 0) murl = murl.substring(0, fet)

        Log.d("displayThread", url)

        if (!onlyCheckWatch) sets.curpage = murl

        if (murl == RequestValues.WATCH.url) {
            showWatches()
            storeData()
            return
        }


        if (onlyCheckWatch && !isWatched(murl)) {
            reqDone += 1
            Log.d("wrong Request", murl)
            Log.d("Watchlist", watchlist.joinToString(",") { it.thread.url })
            return
        }
        progressBar.visibility = View.VISIBLE

        if (viewSingle)
            chronic.add(Navis(NavOperation.THREAD, murl))
        else
            chronic.add(Navis(NavOperation.PAGE, murl))

        val queue = MySingleton.getInstance(this.applicationContext)
        val curW: Watch = getWatch(murl)

        val tr = ThreadRequest("https://questden.org$murl", viewSingle, if (onlyCheckWatch) curW.lastReadId else null, null, Response.Listener { response ->
            reqDone += 1
            if (onlyCheckWatch) {
                val newPosts = response.filter { it.postID != "" }.size
                val newImgs = response.filter { it.imgUrl != "" }.size
                val newestId = response.last().postID

                val newW = Watch(curW.thread, curW.lastReadId, newestId, newPosts, newImgs)
                updateWatch(newW)
            } else {
                sets.curSingle = viewSingle
                displayDataList = response
                displayThreadList(0)

                if (isWatched(murl)) {
                    val w: Watch = getWatch(murl) //copy returned? then w.(...)=... will not do anything
                    if (w.curReadId == "") w.curReadId = w.lastReadId
                    val scrollpos = listAdapt.items.indexOfFirst { it.postID == w.curReadId }
                    scrollHighlight(scrollpos)
                    w.lastReadId = w.newestId
                    w.newImg = 0
                    w.newPosts = 0
                    setWatch(w)
                }
            }
            storeData()
            var perc = 0

            if (reqCnt > 0)
                perc = (reqDone * 100f / reqCnt).toInt()
            progressBar.progress = perc

            if ((reqDone == reqCnt && reqCnt > 0) || reqCnt == 0)
                afterUpdateReq()
        }, Response.ErrorListener {
            reqDone += 1
            if (reqCnt > 0)
                progressBar.progress = reqDone * 100 / reqCnt
            afterUpdateReq()

            listAdapt.items =
                listOf(TgThread("There was an error loading the Thread\n${it}"))
            listAdapt.notifyDataSetChanged()
        }
        )
        queue.addToRequestQueue(tr)
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
        reqCnt = 0

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
            displayThread(RequestValues.QUEST.url, false)
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
        showSetsInButtons()
    }

    /**
     * adds thread to watchlist, updates counts and saves data
     */
    fun addToWatch(tg: TgThread) {
        if (isWatched(tg.url)) return
        watchlist.add(Watch(tg))
        displayThread(watchlist.last().thread.url, false, true)
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

        val oldw: Watch? = watchlist.firstOrNull { it.thread.url == w.thread.url }
        if (oldw == null) {
            val compid = Regex("""(\d+).html""").find(w.thread.url)?.groupValues?.get(1)
            val renWatch = watchlist.firstOrNull {
                compid == Regex("""(\d+).html""").find(it.thread.url)?.groupValues?.get(1)
            }
            if (renWatch == null) {
                addToWatch(w.thread)
            } else {
                w.thread.url = renWatch.thread.url
                updateWatch(w)
            }
            return
        }
        if (w.lastReadId != "") oldw.lastReadId = w.lastReadId
        oldw.newImg = w.newImg
        oldw.newPosts = w.newPosts
        if (w.newestId != "") oldw.newestId = w.newestId

        if (oldw.thread.postID == "") {
            oldw.thread = w.thread
        }

        watchlist.sortedWith(compareBy({ -it.newImg }, { -it.newPosts }))

        listAdapt.notifyDataSetChanged()
        storeData()
    }

    /**
     * updates scroll position display at bottom
     */
    fun updatePositionDisplay(position: Int = -1) {
        var pos = position + 1
        if (sets.curSingle && listAdapt.items.size > pos) {
                if (position == -1) pos =
                1 + (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()//(ingredients_list.layoutManager as LinearLayoutManager).getPosition(ingredients_list)
            totcnt = listAdapt.items.filter { it.imgUrl != "" }.size
            curcnt = listAdapt.items.take(pos).filter { it.imgUrl != "" }.size

            val w = getWatch(listAdapt.items.first().url)
            w.curReadId = listAdapt.items[pos].postID
            setWatch(w)
        }else if(!sets.curSingle){
            totcnt=0
            curcnt=sets.boardPage
        }

        tx_position.text = getString(R.string.CurPos, curcnt, totcnt)
    }

    private fun showSetsInButtons() { //set text to current mode
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

        val imgid = if (sets.showOnlyPics) R.drawable.ic_exclnonimg else R.drawable.ic_inclnonimg

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            btn_only_pics.setImageDrawable(ContextCompat.getDrawable(this, imgid))
        } else {
            btn_only_pics.setImageDrawable(VectorDrawableCompat.create(resources, imgid, theme))
        }
    }

    private fun afterUpdateReq() {
        listAdapt.notifyDataSetChanged()
        progressBar.visibility = View.GONE
        progressBar.progress = 0
        reqCnt = 0
        reqDone = 0

    }

    private fun updateWatchlist() {
        reqCnt = watchlist.size
        reqDone = 0
        progressBar.max = 100
        progressBar.progress = 0
        for (w in watchlist) {
            displayThread(w.thread.url, false, true)
        }
    }

    /*
     * button click event handlers below
     * view is needed even not used, otherwise crash
     * updates current page list and all watched thread numbers
     */

    /**
     * path text of fullview-image. Opens image link in browser
     */
    fun btnimgZoomPath(@Suppress("UNUSED_PARAMETER") view: View) {
        val openURL = Intent(Intent.ACTION_VIEW)

        val targeturl = (view as TextView).text
        openURL.data = Uri.parse(targeturl.toString())
        startActivity(openURL)
    }

    /**
     * button toggle sfw mode
     * autoload spoiler images (yes,no, question = only on click)
     * click order SFW?→SFW→NSFW→SFW?
     */
    fun btnTglSFW(@Suppress("UNUSED_PARAMETER") view: View) {
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
        showSetsInButtons()
        listAdapt.notifyDataSetChanged()

    }

    /**
     * opens/closes navigation tool sections
     */
    fun btnOpenTools(@Suppress("UNUSED_PARAMETER") view: View) {
//        if(tool_dropout.isDrawerOpen(GravityCompat.START)){
        if (tool_dropout.visibility != View.GONE) {
            tool_dropout.visibility = View.GONE
//            tool_dropout.closeDrawer(GravityCompat.START)
        } else {
            tool_dropout.visibility = View.VISIBLE
//            tool_dropout.openDrawer(GravityCompat.START)
        }
    }

    /**
     * button event for change font size
     */
    fun btnIncFont(@Suppress("UNUSED_PARAMETER") view: View) {
        sets.txsize = sets.txsize + 1f
        listAdapt.notifyDataSetChanged()

    }

    /**
     * button event for change font size
     */
    fun btnDecFont(@Suppress("UNUSED_PARAMETER") view: View) {
        sets.txsize = sets.txsize - 1f
        listAdapt.notifyDataSetChanged()
    }

    /**
     * update button, refreshes page, fetches updates when on watchlist
     */
    fun btnUpdateButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (sets.curpage == RequestValues.WATCH.url) updateWatchlist()
        else displayThread(sets.curpage, sets.curSingle)
    }

    /**
     * picture btn click
     * toggles showing all vs only posts with pictures
     */
    fun btnToggleOnlyPictures(@Suppress("UNUSED_PARAMETER") view: View) {
        sets.showOnlyPics = !sets.showOnlyPics
        showSetsInButtons()
        displayThreadList()
    }

    /**
     * next image button
     * jumps and highlight to target
     */
    fun btnNextButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (sets.curSingle) {//single thread
            var pos = (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            pos += 1 + listAdapt.items.takeLast(listAdapt.items.size - pos - 1).indexOfFirst { it.imgUrl != "" }
            scrollHighlight(pos)
        } else { //board
            val curUrl = Regex("""(^.*\/)(?:(\d+).html)?""").find(sets.curpage)?.groupValues
            val baseUrl = curUrl?.get(1)
            val basePageStr = (curUrl?.get(2) ?: "") //error or empty group
            Log.d("nextButton", basePageStr)
            val basePage = if (basePageStr == "") 0 else basePageStr.toInt()
            sets.boardPage=basePage+1
            displayThread(baseUrl + (basePage + 1).toString() + ".html", false)
        }
    }

    /**
     * previous image button
     */
    fun btnPrevButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (sets.curSingle) {//single thread
            var pos = (ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            pos = listAdapt.items.take(pos).indexOfLast { it.imgUrl != "" }
            scrollHighlight(pos)
        } else { //board
            val curUrl = Regex("""(^.*\/)(?:(\d+).html)?""").find(sets.curpage)?.groupValues
            val baseUrl = curUrl?.get(1)
            val basePageStr = (curUrl?.get(2) ?: "") //error or empty group
            Log.d("nextButton", basePageStr)
            val basePage = if (basePageStr == "") 0 else basePageStr.toInt()

            if (basePage > 1) {
                sets.boardPage=basePage-1
                displayThread(baseUrl + (basePage - 1).toString() + ".html", false)
            }else if (basePage ==1) {
                sets.boardPage=0
                displayThread(baseUrl!!, false)
            }
        }
    }

    /**
     * first image button
     */
    fun btnFirstButton(@Suppress("UNUSED_PARAMETER") view: View) {
        //var pos=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val pos = listAdapt.items.indexOfFirst { it.imgUrl != "" }
        scrollHighlight(pos)
    }

    /**
     * last image button
     */
    fun btnLastButton(@Suppress("UNUSED_PARAMETER") view: View) {
        //var pos=(ingredients_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val pos = listAdapt.items.indexOfLast { it.imgUrl != "" }
        scrollHighlight(pos)
    }

    private fun displayThreadList(pos: Int = -1) {
        if (sets.showOnlyPics)
            listAdapt.items = displayDataList.filter { it.imgUrl != "" }
        else
            listAdapt.items = displayDataList//.take(10)
        listAdapt.notifyDataSetChanged()
        if (pos >= 0) {
            (ingredients_list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
        }
        updatePositionDisplay()
    }

}