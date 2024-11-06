package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.dediggefedde.questden_blick_reader.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.LinearSmoothScroller
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/* Behavior
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

/* TODO
* - Theme light/dark
* - update channel beta/stable
* */

/**
 * Main activity
 * So far only activity
 * sets up all layouts, requests html, parses, fills data, manages back-click/menus etc.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    val listAdapt:QuestDenListAdapter = QuestDenListAdapter(this)
    private var displayDataList = listOf<TgThread>()
    private var watchlist = mutableListOf<Watch>()
    var sets: Settings = Settings() //current app settings
    private var totcnt = 0 //max position in navigations
    private var curcnt = 0 //current position in navigation
    private var reqCnt = 0 //max position in progressbar
    private var reqDone = 0 //current position in progressbar
    private var curWatch: Watch? =null //currently opened thread if watched
    var chronic = mutableListOf<Navis>()
    private var sortingmode=SORTING.NATIVE
    private var mainMenu:Menu?=null
    private var scrollMode=ScrollMode.IMAGES
    private lateinit var gestureDetector: GestureDetector

    private lateinit var binding: ActivityMainBinding
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
       backpressed()
    }
    private fun backpressed(){
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
        when (nav.operation) {
            NavOperation.LINK -> {
                if (nav.navStat != null)
                    binding.ingredientsList.layoutManager?.onRestoreInstanceState(nav.navStat)
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
        binding.ingredientsList.addOnScrollListener(scrollListener)
    }

    /**
     *
     */
    fun toggleToolbarVisibility(toShow:Boolean?=null) {
        val show = toShow ?: (binding.toolbar.visibility == View.GONE)

        if (show) {
            binding.toolbar.visibility = View.VISIBLE
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.groupNavBut.visibility = View.VISIBLE
        } else {
            binding.toolbar.visibility = View.GONE
            binding.bottomNavigation.visibility = View.GONE
            binding.toolDropout.visibility = View.GONE
            binding.groupNavBut.visibility = View.GONE
        }
    }
    /**
     * scrolls to position, aligns top and sets isHighlight on thread-object
     * also remove isHighlight on others.
     * also adds chronic event (LINK) and updates position display
     */
    fun scrollHighlight(pos: Int) {
        if (displayDataList.size < pos || pos < 0 || !sets.curSingle) return

        // Unmark previous highlights
        displayDataList.forEachIndexed { _, el ->
            if (el.isHighlight) {
                el.isHighlight = false
            }
        }
        // Mark the new highlight
        displayDataList[pos].isHighlight = true
        binding.ingredientsList.post {
            // Hier kannst du die Logik für das Highlighting anpassen
            listAdapt.notifyItemChanged(pos) // Notify only the highlighted item
        }

        val curPos=(binding.ingredientsList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val smoothScroller = TopSnappingScroller(binding.ingredientsList.context)
        smoothScroller.targetPosition = pos
        if(abs(curPos-pos)<10)
            (binding.ingredientsList.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScroller)
        else
            (binding.ingredientsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos,0)

        // Back button
        chronic.add(Navis(NavOperation.LINK, pos.toString(), binding.ingredientsList.layoutManager?.onSaveInstanceState()))

    }

    override fun onStop() {
        storeData()
        super.onStop()
    }

    override fun onDestroy() {
        storeData()
        super.onDestroy()
    }

    fun viewImage(mtg:TgThread){
        binding.progressBarUndet.visibility = View.VISIBLE
        binding.imageZoom.visibility = View.VISIBLE
        binding.txImgPath.visibility = View.VISIBLE
        var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
        if (mtg.isSpoiler && sets.sfw == SFWModes.SFWREAL) str = "https://questden.org/kusaba/spoiler.png"

        Glide.with(binding.imageZoom)
            .asDrawable()
            .load(str)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    binding.progressBarUndet.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                ): Boolean {
//                    binding.imageZoom.setImageDrawable(resource)
                    // Manuell die Größe des ImageViews festlegen
                    binding.progressBarUndet.visibility = View.GONE
                    return false
                }
            })
            .into(binding.imageZoom)
        binding.txImgPath.text = str
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gestureDetector = GestureDetector(this, SwipeGestureListener())

        //layout
        navigationStuff()
        binding.ingredientsList.layoutManager = LinearLayoutManager(this)
        binding.ingredientsList.adapter = listAdapt
        binding.progressBarDet.visibility = View.GONE
        binding.progressBarUndet.visibility = View.GONE
        binding.imageZoom.visibility = View.GONE
        binding.txImgPath.visibility = View.GONE

        //event handlers
        binding.imageZoom.setOnClickListener {
            binding.imageZoom.visibility = View.GONE
            binding.txImgPath.visibility = View.GONE
        }
        // Setze den OnTouchListener für das ScrollView
        binding.ingredientsList.setOnTouchListener{view, event ->
            val result = gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP && !result) {
                view.performClick()
            }

            //result
            false
        }

        //start doing things with data
        loadData()
        setRecyclerViewScrollListener()


        onBackPressedDispatcher.addCallback(this){
            backpressed()
        }

        AppUpdater(this)
            .setUpdateFrom(UpdateFrom.JSON)
            // .setGitHubUserAndRepo("Dediggefedde", "Questden_Blick_Reader")
            .setUpdateJSON("""https://raw.githubusercontent.com/Dediggefedde/Questden_Blick_Reader/WIP/app/version.json""") //TODO WIP to master
            .start()
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
        val tagHandler = HTMLTagHandler(this, binding)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
            val sequence: CharSequence = Html.fromHtml(convertToCustomTags(html), Html.FROM_HTML_MODE_COMPACT, imgGet, tagHandler)
            val strBuilder = SpannableStringBuilder(sequence)
            text.text = strBuilder
            text.movementMethod = LinkMovementMethod.getInstance()
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_sorting, menu)
        mainMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.menu_sort_date -> {
                sortingmode=SORTING.DATE
                sortDisplay()
                true
            }
            R.id.menu_sort_img -> {
                sortingmode=SORTING.IMAGES
                sortDisplay()
                true
            }
            R.id.menu_sort_posts -> {
                sortingmode=SORTING.POSTS
                sortDisplay()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigationStuff() {
        binding.navigationView.setNavigationItemSelectedListener(this)
        setSupportActionBar(binding.toolbar)

        val menuDrawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.open_menu, R.string.closesMenu
        ).apply {
            binding.drawerLayout.addDrawerListener(this)
            this.syncState()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        menuDrawerToggle.isDrawerIndicatorEnabled = true
        menuDrawerToggle.toolbarNavigationClickListener = null
        menuDrawerToggle.syncState()


        binding.navigationView.itemIconTintList = null

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        sets.boardPage=0

        when (menuItem.itemId) {
            R.id.menu_draw -> displayThread(RequestValues.DRAW.url, false)
            R.id.menu_general -> displayThread(RequestValues.MEEP.url, false)
            R.id.menu_quest -> displayThread(RequestValues.QUEST.url, false)
            R.id.menu_questdis -> displayThread(RequestValues.QUESTDIS.url, false)
            R.id.menu_tg -> displayThread(RequestValues.TG.url, false)
            R.id.menu_watch_open -> {
                displayThread(RequestValues.WATCH.url, false)
            }
            R.id.menu_reader_sync -> {
                val inte = Intent(this, SyncActivity::class.java)
                inte.putExtra("sets", sets)
                inte.putParcelableArrayListExtra("watchlist", ArrayList(watchlist))
                // If an instance of this Activity already exists, then it will be moved to the front. If an instance does NOT exist, a new instance will be created
                startActivityForResult(inte, 1)
            }
            R.id.menu_reader_backup -> {

                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)

                val c: Calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
                val strDate: String = sdf.format(c.time)

                intent.type = "text/json" //not needed, but maybe usefull
                intent.putExtra(Intent.EXTRA_TITLE, "questden_backup_$strDate.json") //not needed, but maybe usefull

                startActivityForResult(intent, 2)
            }

            R.id.menu_reader_restore -> {

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)

//                val c: Calendar = Calendar.getInstance()
//                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ENGLISH)
//                val strDate: String = sdf.format(c.time)

                intent.type = "text/json" //not needed, but maybe usefull
//                intent.putExtra(Intent.EXTRA_TITLE, "questden_backup_$strDate.json") //not needed, but maybe usefull

                startActivityForResult(intent, 3)
            }

        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun exportSH(shName: Uri?) {
        try {
            val gson = Gson()
            val li = listOf(displayDataList, watchlist, sets)
            val cont = gson.toJson(li)

            shName?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(cont.toByteArray())
                }
            }
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importSH(shName: Uri?) {
        try {

            val inf= shName?.let { contentResolver.openInputStream(it) }
            val content = inf!!.bufferedReader().use(BufferedReader::readText)

            val gson = Gson()

            val itemType = object : TypeToken<List<Any>>() {}.type
            val li:List<Any> = gson.fromJson(content, itemType)

            if(li.size<3){
                Toast.makeText(this, "Wrong format", Toast.LENGTH_SHORT).show()
                return
            }

            var zwi=gson.toJson(li[0])
            displayDataList =gson.fromJson(zwi, object : TypeToken<List<TgThread>>() {}.type)
            zwi=gson.toJson(li[1])
            watchlist = gson.fromJson(zwi, object : TypeToken<MutableList<Watch>>() {}.type)
            zwi=gson.toJson(li[2])
            sets= gson.fromJson(zwi, object : TypeToken<Settings>() {}.type)


            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()

            displayThread(sets.curpage, sets.curSingle)
           // btnUpdateButton(btn_update)

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode != Activity.RESULT_OK)return

        if (requestCode == 1) {

            @Suppress("DEPRECATION")

            sets= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("sets", Settings::class.java)
            }else{
               data?.extras?.get("sets") as Settings
            }?: Settings()

            val remWatchUrl=data?.getStringArrayListExtra("watchlistUrl")
            val remWatchPos=data?.getStringArrayListExtra("watchlistPos")

            if(remWatchUrl!=null && remWatchUrl.size  >0) {//download
                watchlist.clear()

                try {
                    for (i in 1..remWatchUrl.size) {
                        val td = TgThread()
                        td.url = remWatchUrl[i - 1]
                        addToWatch(td,true)
                        watchlist.last().newestId = remWatchPos?.get(i - 1) ?: ""
                    }
                    showWatches()
                    Toast.makeText(this.applicationContext, "Download finished", Toast.LENGTH_SHORT).show()

                    updateWatchlist()
                    storeData()
                    Toast.makeText(this.applicationContext, "Import Complete", Toast.LENGTH_SHORT).show()
                }catch(e:Exception){
                    Toast.makeText(this.applicationContext, "Error", Toast.LENGTH_SHORT).show()
                }
            }else{//upload complete
                Toast.makeText(this.applicationContext, "Upload complete", Toast.LENGTH_SHORT).show()
            }
        }else if(requestCode == 2){
            //save backup file dialog choose file return
            try {
                exportSH(data?.data)
            } catch (e: IOException) {
                Toast.makeText(this.applicationContext, "Error", Toast.LENGTH_SHORT).show()
            }
        }else if(requestCode ==3)  {
            //load backup file dialog choose file return
            try {
                importSH(data?.data)
            } catch (e: IOException) {
                Toast.makeText(this.applicationContext, "Error", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun sortDisplay(){
        when(sortingmode){
            SORTING.POSTS->
                displayDataList=displayDataList.sortedWith(compareBy({ -it.newPosts }, { -it.newImg }))
            SORTING.IMAGES->
                displayDataList=displayDataList.sortedWith(compareBy({ -it.newImg }, { -it.newPosts }))
            SORTING.DATE->
                displayDataList=displayDataList.sortedWith(compareBy({it.date},{ -it.newImg }, { -it.newPosts }))
            else -> {}
        }
        listAdapt.submitList(displayDataList) { binding.ingredientsList.scrollToPosition(0) }
    }
    private fun showWatches() {
        sets.curpage = RequestValues.WATCH.url
        displayDataList = watchlist.map {
            it.thread.isThread = true
            it.thread.newImg=it.newImg
            it.thread.newPosts=it.newPosts
            it.thread
        }.toList()
        displayThreadList(0)
        sortingmode=SORTING.IMAGES
        sortDisplay()
        mainMenu?.setGroupVisible(0,true)
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


        if (!onlyCheckWatch) sets.curpage = murl

        if (murl == RequestValues.WATCH.url) {
            sets.curTitle="Watch list"
            sets.curSingle = false
            sets.curThreadId = ""
            showWatches()
            storeData()
            mainMenu?.setGroupVisible(0,true)
            return
        }else{
            sortingmode=SORTING.NATIVE
            mainMenu?.setGroupVisible(0,false)
        }
        if (!sets.curSingle && sets.boardPage > 0 && !onlyCheckWatch) murl = "$murl${sets.boardPage}.html"


        if (onlyCheckWatch && !isWatched(murl)) {
            reqDone += 1
            return
        }
        binding.progressBarUndet.visibility = View.VISIBLE

        if (viewSingle)
            chronic.add(Navis(NavOperation.THREAD, murl))
        else
            chronic.add(Navis(NavOperation.PAGE, murl))

        val queue = MySingleton.getInstance(this.applicationContext)
        val curW: Watch = getWatchByUrl(murl)

        curWatch=null

        val tr = ThreadRequest("https://questden.org$murl", viewSingle, if (onlyCheckWatch) curW.newestId else null, null, { response ->
            reqDone += 1
            if (onlyCheckWatch) {
                if (response.isEmpty()) return@ThreadRequest
                val inf = response.removeAt(response.lastIndex)
                val newPosts = response.filter { it.postID != "" }.size
                val newImgs = response.filter { it.imgUrl != "" }.size
                if (curW.thread.title == "") {
                    curW.thread = inf
                }
               // if (newPosts > 0) {
                  //  val newestId = response.last().postID
//                   val newW = Watch(curW.thread, curW.newestId, newPosts, newImgs, curW.lastReadId)

                   val oldw: Watch? = watchlist.firstOrNull { it.thread.url == curW.thread.url }
                    if (oldw != null) {
                        oldw.thread=inf
                        oldw.newImg =newImgs
                        oldw.newPosts = newPosts
                    }
           //     }
            } else {
                sets.curSingle = viewSingle
                if (viewSingle) {
                    sets.curTitle = response.first().title
                    sets.curThreadId = Regex("""(\d+).html""").find(murl)?.groupValues?.get(1) ?: ""
                    displayDataList = response
                    displayThreadList()
                } else {
                    sets.curTitle = sets.curpage
                    sets.curThreadId = ""
                    val inf = response.last()
                    response.remove(response.last())
                    sets.curMaxPage = inf.summary.toInt()
                    displayDataList = response
                    displayThreadList(0)
                }


                if (isWatched(murl)) {
                    val w: Watch = getWatchByUrl(murl) //copy returned? then w.(...)=... will not do anything
                    // if (w.curReadId == "") w.curReadId = w.lastReadId
                    //val scrollpos = listAdapt.currentList.indexOfFirst { it.postID == w.curReadId }
                    //scrollHighlight(scrollpos)
                    w.newestId = response.last().postID
                    //w.lastReadId = w.newestId
                    w.newImg = 0
                    w.newPosts = 0
                    setWatch(w)
                    curWatch=w
                }else{
                    curWatch=null
                }


            }
            storeData()
            var perc = 0

            if (reqCnt > 0) {
                perc = (reqDone * 100f / reqCnt).toInt()
                binding.progressBarUndet.visibility = View.GONE
                binding.progressBarDet.visibility = View.VISIBLE
            }
            binding.progressBarDet.progress = perc

            if ((reqDone == reqCnt && reqCnt > 0) || reqCnt == 0)
                afterUpdateReq()
        }, {
            reqDone += 1
            if (reqCnt > 0) {
                binding.progressBarDet.progress = reqDone * 100 / reqCnt
                binding.progressBarUndet.progress = reqDone * 100 / reqCnt
            }
            afterUpdateReq()

            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
//            val exceptionAsString = sw.toString()
//
//            listAdapt.currentList =
//                listOf(TgThread("There was an error loading the Thread:<br/>${it.message}<br/><br/>StackTrace:<br/>$exceptionAsString"))
//            listAdapt.notifyDataSetChanged()
            //listAdapt.notifyItemRemoved(0)

            Toast.makeText(this, "There was an error loading the Thread:\n${it.message}", Toast.LENGTH_SHORT).show()
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

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
        val gson = Gson()
        var firstStart = false
        reqCnt = 0

        var jsonString = sharedPref.getString("tgchanItems", "")
        if (jsonString != "") {
            val itemType = object : TypeToken<List<TgThread>>() {}.type
            displayDataList = gson.fromJson(jsonString, itemType)
        } else {
            firstStart = true
        }
        jsonString = sharedPref.getString("watchItems", "")
        if (jsonString != "") {
            val itemType2 = object : TypeToken<MutableList<Watch>>() {}.type
            watchlist = gson.fromJson(jsonString, itemType2)
        }

        jsonString = sharedPref.getString("sets", "")
        if (jsonString != "") {
            val itemType3 = object : TypeToken<Settings>() {}.type
            sets = gson.fromJson(jsonString, itemType3)
        }
        listAdapt.notifyDataSetChanged()
//        displayThread(RequestValues.QUEST.url, false)
//        firstStart=true;
        //TODO check prev version
        if (firstStart) {
            displayThread(RequestValues.QUEST.url, false)
        } else {
            if (!sets.curSingle) {
                displayThreadList(0)
            } else {
                //val w = getWatch(displayDataList.first().url)
                // val ind = displayDataList.indexOfFirst { w.curReadId == it.postID }
                displayThreadList()
                // scrollHighlight(ind)
            }
        }
        showSetsInButtons()
    }

    /**
     * adds thread to watchlist, updates counts and saves data
     */
    fun addToWatch(tg: TgThread, silent:Boolean=false) {
        if (isWatched(tg.url)) return
        watchlist.add(Watch(tg))
        if(!silent) {
            displayThread(watchlist.last().thread.url, viewSingle = true, onlyCheckWatch = true)
        }
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
    fun getWatchByUrl(url: String): Watch {
        return watchlist.firstOrNull { it.thread.url == url } ?: return Watch()
    }
    /**
     * get watch object (copy) by url
     */
//    fun getWatchById(id: String): Watch {
//        return watchlist.firstOrNull { it.thread.postID == id } ?: return Watch()
//    }


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


    /**
     * updates scroll position display at bottom
     */
    fun updatePositionDisplay(position: Int = -1) {
        var pos = position
        var posMod="Page: "
        if (sets.curSingle && displayDataList.size > pos) {
            if (position == -1) {
                pos = 1 + (binding.ingredientsList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            }
            if (displayDataList.size < pos) pos = 0
            if(scrollMode==ScrollMode.IMAGES||sets.showOnlyPics) {
                totcnt = displayDataList.filter { it.imgUrl != "" }.size
                curcnt = displayDataList.take(pos).filter { it.imgUrl != "" }.size
                posMod="Image: "
            }else{
                totcnt = displayDataList.size
                curcnt = pos
                posMod="Post: "
            }

            if (sets.curThreadId != "" && displayDataList.size>pos) {
                sets.lastReadIDs[sets.curThreadId] = displayDataList[pos].postID
            }
            if(curWatch!=null){
                curWatch!!.lastReadId=displayDataList[pos].postID
            }

        } else if (!sets.curSingle) {
            totcnt = sets.curMaxPage
            curcnt = sets.boardPage
            if(totcnt<curcnt)totcnt=curcnt //current page = maxpage, link missing
        }

        binding.txPosition.text = getString(R.string.CurPos, posMod,curcnt, totcnt)
    }

    private fun showSetsInButtons() { //set text to current mode
        when (sets.sfw) {
            SFWModes.SFWQUESTION -> {
                binding.btnToggleSFW.text = getString(R.string.SFWQuestion)
            }
            SFWModes.SFWREAL -> {
                binding.btnToggleSFW.text = getString(R.string.SFW)
            }
            SFWModes.NSFW -> {
                binding.btnToggleSFW.text = getString(R.string.NSFW)
            }
        }

        val imgid = if (sets.showOnlyPics) R.drawable.ic_exclnonimg else R.drawable.ic_inclnonimg

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            binding.btnOnlyPics.setImageDrawable(ContextCompat.getDrawable(this, imgid))
        } else {
            binding.btnOnlyPics.setImageDrawable(VectorDrawableCompat.create(resources, imgid, theme))
        }
    }

    private fun afterUpdateReq() {
        binding.progressBarUndet.visibility = View.GONE
        binding.progressBarDet.visibility = View.GONE
        binding.progressBarUndet.progress = 0
        binding.progressBarDet.progress = 0
        reqCnt = 0
        reqDone = 0

        if(sets.curpage==RequestValues.WATCH.url) { //update watchlist
            displayThreadList(0)
            sortingmode=SORTING.IMAGES
            showWatches()

            Toast.makeText(this, "Updating Done", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWatchlist() {
        reqCnt = watchlist.size
        reqDone = 0
        binding.progressBarDet.max = 100
        binding.progressBarDet.progress = 0
        binding.progressBarDet.visibility = View.VISIBLE
        for (w in watchlist) {
            displayThread(w.thread.url, viewSingle = true, onlyCheckWatch = true)
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
    fun btnimgZoomPath(view: View) {
        val openURL = Intent(Intent.ACTION_VIEW)

        val targeturl = (view as TextView).text
        openURL.data = Uri.parse(targeturl.toString())
        startActivity(openURL)
    }
    /**
     * changes mode display and skip for next/prev
     */

    fun btnSkipModeChange(@Suppress("UNUSED_PARAMETER") view: View){
        scrollMode = if (scrollMode==ScrollMode.ALL) ScrollMode.IMAGES else ScrollMode.ALL
        updatePositionDisplay()
    }

    /**
     * button toggle sfw mode
     * autoload spoiler images (yes,no, question = only on click)
     * click order SFW?→SFW→NSFW→SFW?
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnTglSFW(@Suppress("UNUSED_PARAMETER") view: View) {
        when (sets.sfw) {
            SFWModes.SFWQUESTION -> {
                sets.sfw = SFWModes.SFWREAL
            }
            SFWModes.SFWREAL -> {
                sets.sfw = SFWModes.NSFW
            }
            SFWModes.NSFW -> {
                sets.sfw = SFWModes.SFWQUESTION
            }
            //for sake of using view, so git would ignore the warning
        }
        showSetsInButtons()
        listAdapt.notifyDataSetChanged()

        Toast.makeText(this, "SFW mode set to ${sets.sfw}", Toast.LENGTH_SHORT).show()
    }

    /**
     * opens/closes navigation tool sections
     */
    fun btnOpenTools(@Suppress("UNUSED_PARAMETER") view: View) {
//        if(tool_dropout.isDrawerOpen(GravityCompat.START)){
        if (binding.toolDropout.visibility != View.GONE) {
            binding.toolDropout.visibility = View.GONE
//            tool_dropout.closeDrawer(GravityCompat.START)
        } else {
            binding.toolDropout.visibility = View.VISIBLE
//            tool_dropout.openDrawer(GravityCompat.START)
        }
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnIncFont(@Suppress("UNUSED_PARAMETER") view: View) {
        sets.txsize += 1f
        listAdapt.notifyDataSetChanged()
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnDecFont(@Suppress("UNUSED_PARAMETER") view: View) {
        sets.txsize -= 1f
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
        if( sets.showOnlyPics)
            Toast.makeText(this, "Only Posts with images", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(this, "Showing all Posts", Toast.LENGTH_SHORT).show()
    }

    private fun navigatePage(page: Int) {
        if (sets.curpage == RequestValues.WATCH.url || page <0 || page > sets.curMaxPage) return
        sets.boardPage = page
        displayThread(sets.curpage, viewSingle = false, onlyCheckWatch = false)
    }

    /**
     * next image button
     * jumps and highlight to target
     */
    fun btnNextButton(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (sets.curSingle) {//single thread
            var pos = (binding.ingredientsList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

            if(scrollMode==ScrollMode.IMAGES) {
                pos += displayDataList.takeLast(displayDataList.size - pos - 1).indexOfFirst { it.imgUrl != "" }
            }
            pos +=1

            scrollHighlight(pos)
        } else { //board
            navigatePage(sets.boardPage + 1)
        }
    }

    /**
     * previous image button
     */
    fun btnPrevButton(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (sets.curSingle) {//single thread
            var pos = (binding.ingredientsList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            pos =if(scrollMode==ScrollMode.IMAGES)  displayDataList.take(pos).indexOfLast { it.imgUrl != "" }else displayDataList.take(pos).lastIndex

            scrollHighlight(pos)
        } else { //board
            navigatePage(sets.boardPage - 1)
        }
    }

    /**
     * first image button
     */
    fun btnFirstButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (sets.curSingle) {
            val pos=if(scrollMode==ScrollMode.IMAGES) displayDataList.indexOfFirst { it.imgUrl != "" } else 0
            scrollHighlight(pos)
        } else {
            navigatePage(0)
        }
    }

    /**
     * last image button
     */
    fun btnLastButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (sets.curSingle) {
            val pos =if(scrollMode==ScrollMode.IMAGES) displayDataList.indexOfLast { it.imgUrl != "" } else displayDataList.lastIndex
            scrollHighlight(pos)
        } else {
            navigatePage(sets.curMaxPage)
        }
    }
//    private fun updateVisible(){
//
//        val man=(ingredientsList.layoutManager as LinearLayoutManager)
//        val first=man.findFirstVisibleItemPosition()
//        val last=man.findLastVisibleItemPosition()
//        listAdapt.notifyItemRangeChanged(first,last-first)
//    }

    private fun displayThreadList(pos: Int = -1) {
        binding.toolbar.title=sets.curTitle

        val scrolling = {
            if (pos >= 0) {
                (binding.ingredientsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
                updatePositionDisplay(pos)
            } else {
                if (sets.curThreadId != "") {
                    if (sets.lastReadIDs[sets.curThreadId] != null) {
                        scrollHighlight(displayDataList.indexOfFirst { it.postID == sets.lastReadIDs[sets.curThreadId] })
                    } else {
                        sets.lastReadIDs[sets.curThreadId] = displayDataList.first().postID
                        scrollHighlight(0)
                    }
                }else
                    updatePositionDisplay(pos)
            }
        }

        if (sets.showOnlyPics)
            listAdapt.submitList(displayDataList.filter { it.imgUrl != "" },scrolling)//listAdapt.currentList = displayDataList.filter { it.imgUrl != "" }
        else
            listAdapt.submitList(displayDataList,scrolling)//listAdapt.currentList = displayDataList//.take(10)

    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeTreash = 100
        private val swipeVel = 100

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Implementiere die Logik für einen Tap hier
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            return if (abs(diffX) > abs(diffY)) {
                // Horizontaler Swipe erkannt
                if (abs(diffX) > swipeTreash && abs(velocityX) > swipeVel) {
                    if (diffX > 0) {
                        btnPrevButton(null)
                    } else {
                        btnNextButton(null)
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }
    inner class TopSnappingScroller(context: Context) : LinearSmoothScroller(context) {
        override fun getVerticalSnapPreference(): Int {
            return SNAP_TO_START // Setze den Snap-Preference auf den oberen Rand
        }
        override fun calculateTimeForScrolling(dx: Int): Int {
            val time = super.calculateTimeForScrolling(dx)
            return time * 2 // Verdopple die Scrollzeit
        }
    }
}

class CustomRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}