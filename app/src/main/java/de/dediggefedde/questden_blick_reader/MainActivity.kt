package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
//import android.view.GestureDetector
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
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
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.dediggefedde.questden_blick_reader.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearSmoothScroller
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import kotlin.math.abs

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
 * user interaction. Trying to implement MVVM Model
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: DataViewModel
    private val listAdapt: QuestDenListAdapter = QuestDenListAdapter(this)
    private var curViewedInd = 0 //index of current view item (top) of displayDataList

    private var chronic = mutableListOf<Navis>()
    private var mainMenu: Menu? = null
    private var scrollMode = ScrollMode.IMAGES //next/prev got to next img or post
//    private lateinit var gestureDetector: GestureDetector

    lateinit var binding: ActivityMainBinding
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        backpressed()
    }

    private fun backpressed() {
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
        when (nav.operation) {
            NavOperation.LINK -> {
                if (nav.navStat != null)
                    binding.postListRecView.layoutManager?.onRestoreInstanceState(nav.navStat)
            }

            NavOperation.PAGE -> { //TODO rework
                if (nav.prop.isNotEmpty() && viewModel.sets.curURL != nav.prop)
                    viewModel.loadThread(nav.prop, ThrdItemTyps.THREAD)
            }

            NavOperation.THREAD -> {
            }
        }
    }

    var autoscroll = false
    private fun setRecyclerViewScrollListener() {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                curViewedInd = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                updatePositionDisplay()
                autoscroll = false
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!autoscroll) curViewedInd = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                updatePositionDisplay()
            }
        }
        binding.postListRecView.addOnScrollListener(scrollListener)
    }

    /**
     *
     */
    fun toggleToolbarVisibility(toShow: Boolean? = null) {
        val show = toShow ?: (binding.toolbar.visibility == View.GONE)

        if (show) {
            binding.toolbar.visibility = View.VISIBLE
            binding.bottomNavigation.visibility = View.VISIBLE
//            binding.groupNavBut.visibility = View.VISIBLE
        } else {
            binding.toolbar.visibility = View.GONE
            binding.bottomNavigation.visibility = View.GONE
            binding.toolDropout.visibility = View.GONE
//            binding.groupNavBut.visibility = View.GONE
        }
    }

    fun repeatScroll() {
        if (!autoscroll) return
//        val pos = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val smoothScroller = TopSnappingScroller(binding.postListRecView.context)
        smoothScroller.targetPosition = curViewedInd
        (binding.postListRecView.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScroller)
    }

    /**
     * scrolls to position, aligns top and sets isHighlight on thread-object
     * also remove isHighlight on others.
     * also adds chronic event (LINK) and updates position display
     */
    fun scrollHighlight(pos: Int) {
        if (!viewModel.hasIndex(pos)) return

        // Unmark previous highlights

        val lasthighInd = viewModel.highLightInd
        viewModel.setHighlight(pos)

        var vholder = binding.postListRecView.findViewHolderForAdapterPosition(lasthighInd)
        vholder?.itemView?.setBackgroundColor(ContextCompat.getColor(this, R.color.color_list_bg))

        autoscroll = true
        val smoothScroller = TopSnappingScroller(binding.postListRecView.context)
        smoothScroller.targetPosition = pos

        if (abs(curViewedInd - pos) < 10)
            (binding.postListRecView.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScroller)
        else {
            (binding.postListRecView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
        }
        curViewedInd = pos
        updatePositionDisplay()


        // Mark the new highlight
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            vholder = binding.postListRecView.findViewHolderForAdapterPosition(pos)
            vholder?.itemView?.setBackgroundColor(ContextCompat.getColor(this, R.color.color_list_high))
        }, 250)

        // Back button
        chronic.add(Navis(NavOperation.LINK, pos.toString(), binding.postListRecView.layoutManager?.onSaveInstanceState()))

    }

    override fun onStop() {
        storeData()
        super.onStop()
    }

    override fun onDestroy() {
        storeData()
        super.onDestroy()
    }

    //fullview image
    fun viewImage(mtg: TgPost) {
        binding.progressBarUndet.visibility = View.VISIBLE
        binding.imageZoom.visibility = View.VISIBLE
        binding.txImgPath.visibility = View.VISIBLE
        var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
        if (mtg.isSpoiler && viewModel.sets.sfw == SFWModes.SFWREAL) str = "https://questden.org/kusaba/spoiler.png"

        val imgNam = mtg.imgUrl.substringAfterLast("/").replace("thumb", "src").replace("s.", ".")
        val offImgPath = File(applicationContext.filesDir, "offline/${viewModel.sets.curThreadId}_img")
        if (offImgPath.exists() && File(offImgPath, imgNam).exists()) str = "${applicationContext.filesDir}/offline/${viewModel.sets.curThreadId}_img/$imgNam"

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

        val offlFolder = File(applicationContext.filesDir, "offline")
        if (!offlFolder.exists()) offlFolder.mkdirs()

        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this).get(DataViewModel::class.java)

        setContentView(binding.root)

//        gestureDetector = GestureDetector(this, SwipeGestureListener())

        addObservers()
        addListviewEvents()

        //initial layout
        navigationStuff()
        binding.postListRecView.layoutManager = LinearLayoutManager(this)
        binding.postListRecView.adapter = listAdapt
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
//        binding.postListRecView.setOnTouchListener { view, event ->
//            val result = gestureDetector.onTouchEvent(event)
//
//            if (event.action == MotionEvent.ACTION_UP && !result) {
//                view.performClick()
//            }
//            //result
//            false
//        }

        //start doing things with data
        loadData()
        setRecyclerViewScrollListener()

        onBackPressedDispatcher.addCallback(this) {
            backpressed()
        }

        AppUpdater(this)
            .setUpdateFrom(UpdateFrom.JSON)
            // .setGitHubUserAndRepo("Dediggefedde", "Questden_Blick_Reader")
            .setUpdateJSON("""https://raw.githubusercontent.com/Dediggefedde/Questden_Blick_Reader/WIP/app/version.json""") //TODO WIP to master
            .start()
    }

    private fun addListviewEvents() {
        listAdapt.itemAction = object : QuestDenListAdapter.ItemActionListener {
           override fun openThread(url: String) {
                viewModel.loadThread(url,ThrdItemTyps.THREAD)
            }
            override fun toggleWatch(mtg: TgPost) {
                viewModel.toggleWatch(mtg)
            }
            override fun getWatched(url: String): Watch? {
                return viewModel.getWatched(url)
            }
            override fun getDownload(postID: String): OfflineThread? {
                return viewModel.getDownload(postID)
            }
            override fun getIndexById(id: String): Int {
                return listAdapt.currentList.indexOfFirst { it.postID == id }
            }
            override fun getSFWState(): SFWModes {
               return viewModel.sets.sfw
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_sorting, menu)
        mainMenu = menu
        return true
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        // Handle item selection
//        return when (item.itemId) {
//            R.id.menu_sort_date -> {
//                sortingmode = SORTING.DATE
//                sortDisplay()
//                true
//            }
//
//            R.id.menu_sort_img -> {
//                sortingmode = SORTING.IMAGES
//                sortDisplay()
//                true
//            }
//
//            R.id.menu_sort_posts -> {
//                sortingmode = SORTING.POSTS
//                sortDisplay()
//                true
//            }
//
//            else -> super.onOptionsItemSelected(item)
//        }
//    }

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
        viewModel.sets.boardPage = 0 //no liveview connected

        when (menuItem.itemId) {
            R.id.menu_draw -> viewModel.loadThread(URLBoards.DRAW.url, ThrdItemTyps.BOARD)
            R.id.menu_general -> viewModel.loadThread(URLBoards.MEEP.url, ThrdItemTyps.BOARD)
            R.id.menu_quest -> viewModel.loadThread(URLBoards.QUEST.url, ThrdItemTyps.BOARD)
            R.id.menu_questdis -> viewModel.loadThread(URLBoards.QUESTDIS.url, ThrdItemTyps.BOARD)
            R.id.menu_tg -> viewModel.loadThread(URLBoards.TG.url, ThrdItemTyps.BOARD)
            R.id.menu_watch_open -> {
                viewModel.loadThread("", ThrdItemTyps.WATCH)
            }

            R.id.menu_offline_open -> {
                viewModel.loadThread("", ThrdItemTyps.OFFLINE)
            }

            R.id.menu_reader_sync -> { //TODO reimplement

                val intent = Intent(this, SyncActivity::class.java)
                startActivity(intent)

//                val inte = Intent(this, SyncActivity::class.java)
//                inte.putExtra("sets", sets)
//                inte.putParcelableArrayListExtra("watchlist", ArrayList(watchlist))
//                // If an instance of this Activity already exists, then it will be moved to the front. If an instance does NOT exist, a new instance will be created
//                startActivityForResult(inte, 1)
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
                intent.type = "text/json" //not needed, but maybe usefull

                startActivityForResult(intent, 3)
            }

        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun exportFile(shName: Uri?) {
        try {
            val gson = Gson()
            val li = viewModel.exportSetting() //entryListRaw, watchlist, offlinelist, (module)sets
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

    private fun importFile(shName: Uri?) {
        try {
            val inf = shName?.let { contentResolver.openInputStream(it) }
            val content = inf!!.bufferedReader().use(BufferedReader::readText)

            val gson = Gson()

            val itemType = object : TypeToken<List<Any>>() {}.type
            val li: List<Any> = gson.fromJson(content, itemType)

            if (li.size < 3) {
                Toast.makeText(this, "Wrong format", Toast.LENGTH_SHORT).show()
                return
            }

            val entryList = gson.fromJson<List<TgPost>>(gson.toJson(li[0]), object : TypeToken<List<TgPost>>() {}.type)
            val watchList = gson.fromJson<MutableList<Watch>>(gson.toJson(li[1]), object : TypeToken<MutableList<Watch>>() {}.type)
            val offList = gson.fromJson<MutableList<OfflineThread>>(gson.toJson(li[2]), object : TypeToken<MutableList<OfflineThread>>() {}.type)
            val modsets = gson.fromJson<ModelSettings>(gson.toJson(li[3]), object : TypeToken<ModelSettings>() {}.type)

            viewModel.importSettings(entryList, watchList, offList, modsets)

            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
            viewModel.loadCurThread() // (sets.curpage, sets.curSingle)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            2 -> {//Export Backup
                //save backup file dialog choose file return
                try {
                    exportFile(data?.data)
                } catch (e: IOException) {
                    Toast.makeText(this.applicationContext, "Error", Toast.LENGTH_SHORT).show()
                }
            }

            3 -> {
                //load backup file dialog choose file return
                try {
                    importFile(data?.data)
                } catch (e: IOException) {
                    Toast.makeText(this.applicationContext, "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun showOfflineConfirmDialog(htmlFile: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm Action")
        if (htmlFile.exists()) {
            builder.setMessage("Do you want to delete or update local data?")
            builder.setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteOffline(htmlFile)
                viewModel.loadCurThread() //update thread
                dialog.dismiss()
            }
            builder.setNeutralButton("Update") { dialog, _ ->
                viewModel.loadThread(viewModel.sets.curURL, mode = ThrdItemTyps.THREAD, updOffline = true) //triggers update after refresh
                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        } else {
            builder.setMessage("Do you want to download the thread and ${viewModel.getImageListSize()} images?")
            builder.setPositiveButton("Download") { dialog, _ ->
                viewModel.writeToOffline(htmlFile, false)
                viewModel.downloadImages(false)
                dialog.dismiss()
            }
            builder.setNeutralButton("Only thumbnails") { dialog, _ ->
                viewModel.writeToOffline(htmlFile, true)
                viewModel.downloadImages(true)
                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }
        builder.create().show()
    }

    fun btnTglOffline(@Suppress("UNUSED_PARAMETER") view: View?) {
        val htmlFile = File(applicationContext.filesDir, "offline/${viewModel.sets.curThreadId}.html")
        showOfflineConfirmDialog(htmlFile)
    }

    private fun storeData() {
        viewModel.storeData()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        listAdapt.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
        listAdapt.notifyDataSetChanged()
        viewModel.loadData() //calls loadBoard with current settings
    }

    /**
     * updates scroll position display at bottom
     */
    fun updatePositionDisplay() {
        var posMod = "Page: "
        val curPos: Int
        var maxPos: Int

        if (!viewModel.hasIndex(curViewedInd)) return //something wrong

        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            if (scrollMode == ScrollMode.IMAGES || viewModel.sets.showOnlyPics) {
                maxPos = viewModel.getImageListSize()
                curPos = viewModel.getItemImageCnt(curViewedInd) //displayDataList.take(curViewedInd).filter { it.imgUrl != "" }.size
                posMod = "Image:\n"
            } else {
                maxPos = viewModel.getDisplayListSize()
                curPos = curViewedInd + 1
                posMod = "Post:\n"
            }

            viewModel.updateCurReadId(curViewedInd)
        } else {
            maxPos = viewModel.sets.curMaxPage + 1
            curPos = viewModel.sets.boardPage + 1
            if (maxPos < curPos) maxPos = curPos //current page = maxpage, link missing
        }

        binding.txPosition.text = getString(R.string.CurPos, posMod, curPos, maxPos)
    }

    private fun addObservers() {
        val scrolling = {
            val scrollPos = viewModel.getLastReadIndex() //0 if not on thread or unknown
            if (scrollPos > 0) scrollHighlight(scrollPos)
            else (binding.postListRecView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
            updatePositionDisplay()
        }

        viewModel.displayList.observe(this) { list ->
            list?.let {
                binding.toolbar.title = viewModel.sets.curTitle
                listAdapt.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
                listAdapt.submitList(it, scrolling)
                updateOfflineImg()
                if (viewModel.fromOffline) Toast.makeText(this, "Loaded offline data from storage for ${viewModel.sets.curThreadId}.html", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.setsLiveData.observe(this) { set ->

            listAdapt.updateDisplaySetting(set, txtSize = viewModel.sets.txsize)

            binding.btnToggleSFW.text = when (set.sfw) {
                SFWModes.SFWQUESTION -> getString(R.string.SFWQuestion)
                SFWModes.SFWREAL -> getString(R.string.SFW)
                SFWModes.NSFW -> getString(R.string.NSFW)
            }

            //Scrollmode image
            val imgid = if (set.showOnlyPics) R.drawable.ic_exclnonimg else R.drawable.ic_inclnonimg
            binding.btnOnlyPics.setImageDrawable(ContextCompat.getDrawable(this, imgid))


            //setup buttons/menus for different views
            when(viewModel.sets.listType){ //TODO not working?
                ThrdItemTyps.BOARD, ThrdItemTyps.WATCH,ThrdItemTyps.OFFLINE -> {
                    binding.btnOnlyPics.visibility=View.GONE
                    binding.btnToggleSFW.visibility=View.GONE
                    binding.btnOffline.visibility=View.GONE
                }
                ThrdItemTyps.THREAD -> {
                    binding.btnOnlyPics.visibility=View.VISIBLE
                    binding.btnToggleSFW.visibility=View.VISIBLE
                    binding.btnOffline.visibility=View.VISIBLE
                }
            }
        }

        viewModel.reqProgLive.observe(this) { prog ->
            when (prog.status) {
                ProgStatus.RUNNING -> {
                    val perc = (prog.pos * 100f / prog.max).toInt()
                    binding.progressBarUndet.visibility = View.VISIBLE
                    binding.progressBarDet.visibility = View.VISIBLE
                    binding.progressBarDet.progress = perc
                }

                ProgStatus.DONE -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.ERROR -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0
                    Toast.makeText(applicationContext, "There was an error: ${prog.msg}", Toast.LENGTH_SHORT).show()
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.IDLE -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0
                }
            }
        }

        viewModel.downProgLive.observe(this) { prog ->
            when (prog.status) {
                ProgStatus.RUNNING -> {
                    val perc = (prog.pos * 100f / prog.max).toInt()
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressText.visibility = View.VISIBLE
                    binding.progressBar.progress = perc
                    binding.progressText.text = getString(R.string.downloaded_of_images, prog.pos, prog.max)
                }

                ProgStatus.DONE -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                    Toast.makeText(applicationContext, getString(R.string.all_images_downloaded), Toast.LENGTH_SHORT).show()
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                    Toast.makeText(applicationContext, getString(R.string.there_was_an_error, prog.msg), Toast.LENGTH_SHORT).show()
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.IDLE -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                }
            }
        }
    }

    private fun updateOfflineImg() {
        if (viewModel.isDownloaded()) {
            binding.btnOffline.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.online))
        } else {
            binding.btnOffline.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.offline))
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
     * Only changes display
     */
    fun btnSkipModeChange(@Suppress("UNUSED_PARAMETER") view: View) {
        scrollMode = if (scrollMode == ScrollMode.ALL) ScrollMode.IMAGES else ScrollMode.ALL
        updatePositionDisplay()
    }

    /**
     * button toggle sfw mode
     * autoload spoiler images (yes,no, question = only on click)
     * click order SFW?→SFW→NSFW→SFW?
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnTglSFW(@Suppress("UNUSED_PARAMETER") view: View) {
        viewModel.rotateSFW()
        listAdapt.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
        listAdapt.notifyDataSetChanged()
        Toast.makeText(this, "SFW mode set to ${viewModel.sets.sfw}", Toast.LENGTH_SHORT).show()
    }

    /**
     * opens/closes navigation tool sections
     */
    fun btnOpenTools(@Suppress("UNUSED_PARAMETER") view: View) {
        if (binding.toolDropout.visibility != View.GONE) {
            binding.toolDropout.visibility = View.GONE
        } else {
            binding.toolDropout.visibility = View.VISIBLE
        }
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnIncFont(@Suppress("UNUSED_PARAMETER") view: View) {
        viewModel.sets.txsize += 1f
        listAdapt.updateDisplaySetting(null, txtSize = viewModel.sets.txsize)
        listAdapt.notifyDataSetChanged()
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnDecFont(@Suppress("UNUSED_PARAMETER") view: View) {
        viewModel.sets.txsize -= 1f
        listAdapt.updateDisplaySetting(null, txtSize = viewModel.sets.txsize)
        listAdapt.notifyDataSetChanged()
    }

    /**
     * update button, refreshes page, fetches updates when on watchlist
     */
    fun btnUpdateButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (viewModel.fromOffline) //if offline data, call to delete/update/cancel
            btnTglOffline(null)
        else
            viewModel.loadCurThread()
    }

    /**
     * picture btn click
     * toggles showing all vs only posts with pictures
     */
    fun btnToggleOnlyPictures(@Suppress("UNUSED_PARAMETER") view: View) {
        val newMode = !viewModel.sets.showOnlyPics
        viewModel.sets.showOnlyPics = newMode
        viewModel.updateSet()
        viewModel.updateDisplayList()

        if (newMode)
            Toast.makeText(this, "Only Posts with images", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(this, "Showing all Posts", Toast.LENGTH_SHORT).show()
    }

    /**
     * next image button
     * jumps and highlight to target
     */
    fun btnNextButton(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {//single thread
            val newPos = viewModel.getNextPos(curViewedInd, scrollMode)
            if (newPos == -1) return
            scrollHighlight(newPos)
        } else { //board
            viewModel.navigatePage(+1, rel = true)
        }
    }

    /**
     * previous image button
     */
    fun btnPrevButton(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {//single thread
            if (curViewedInd <= 0) return
            val layoutManag = (binding.postListRecView.layoutManager as LinearLayoutManager)
            val curView = layoutManag.findViewByPosition(curViewedInd) //null during scrolling, used to scroll to top of curview if in view

            val withinCur = layoutManag.findFirstVisibleItemPosition() == curViewedInd && curView != null && curView.top != 0

            val newPos = viewModel.getPrevPos(curViewedInd, scrollMode, withinCur)
            if (newPos == -1) return

            scrollHighlight(newPos)
        } else { //board
            viewModel.navigatePage(-1, rel = true)
        }
    }

    /**
     * first image button
     */
    fun btnFirstButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            scrollHighlight(0)//quests must start with image anyway
        } else {
            viewModel.navigatePage(0, rel = false)
        }
    }

    /**
     * last image button
     */
    fun btnLastButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            val newPos = viewModel.getLastPos(curViewedInd, scrollMode)
            if (newPos == -1) return
            scrollHighlight(newPos)
        } else {
            viewModel.navigatePage(viewModel.sets.curMaxPage, rel = false)
        }
    }

//    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
//        private val swipeTreash = 100
//        private val swipeVel = 100
//
//        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
//            // Implementiere die Logik für einen Tap hier
//            return true
//        }
//
//        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
//            if (e1 == null) return false
//            val diffX = e2.x - e1.x
//            val diffY = e2.y - e1.y
//
//            return if (abs(diffX) > abs(diffY)) {
//                // Horizontaler Swipe erkannt
//                if (abs(diffX) > swipeTreash && abs(velocityX) > swipeVel) {
//                    if (diffX > 0) {
//                        btnPrevButton(null)
//                    } else {
//                        btnNextButton(null)
//                    }
//                    true
//                } else {
//                    false
//                }
//            } else {
//                false
//            }
//        }
//    }

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