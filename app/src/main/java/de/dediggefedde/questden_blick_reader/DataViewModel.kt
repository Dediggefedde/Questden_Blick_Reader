package de.dediggefedde.questden_blick_reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

/** Data storage of app to preserve view during shutoff using SharedPreferences*/
class DataRepository(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
    fun <T> saveData(key: String, data: T) {
        val editor = sharedPreferences.edit()
        val jsonData = Gson().toJson(data)
        editor.putString(key, jsonData)
        editor.apply()
    }

    fun <T> loadData(key: String, type: Type): T? {
        val jsonData = sharedPreferences.getString(key, null)
        return if (jsonData != null) {
            Gson().fromJson<T>(jsonData, type) // Verwende hier den Type
        } else {
            null
        }
    }
}

/** Main Data View Model for MVVM approach
 * handles displayed data, inclidng the displayed item list of posts, downloaded threads, watchlist and threads
 * Also includes functions to search, access, process, load and save this data
 * Livedata to view in listadapter and imply progress*/
class DataViewModel(application: Application) : AndroidViewModel(application) {
    private var entryListRaw = listOf<TgPost>() //all entries/posts that can be displayed
    private var entryListImg = listOf<TgPost>() //only entries with images
    private var watchlist = mutableListOf<Watch>() //list of watched threads
    private var offlineList = mutableListOf<OfflineThread>() //list of offline available threads (first posts)
    val fullImgDimMap = mutableMapOf<String, Pair<Int, Int>>()

    var logState = MutableLiveData<LoginState>() //Login state life data
    private val serverURL = "https://phi.pf-control.de/tgchan/API.php" //URL for my server to synchronize threads
    var showListDemand = MutableLiveData<Boolean>() //Livedata to demand from mainActivity to show the list

    var sets: ModelSettings = ModelSettings() //Main settings object of the dataviewmodel state
    private val dataRepository = DataRepository(application) //storing data for shutdown to preserve view
    private var reqProg: ProgData = ProgData() //http requests progress
    private var downProg: ProgData = ProgData() //download progress
    private val client = OkHttpClient()

    val backActionStack = Stack<backPage>()
    val backLinkStack = Stack<String>()

    var highLightInd = -1 //number index of highlighted item
    var fromOffline = false //current thread loaded from offline data
    var fullViewImg: TgPost? = null //shows image on click when not null

    private var _displayList = MutableLiveData<List<TgPost>>() //displayed list of posts livedata.
    val displayList: LiveData<List<TgPost>> get() = _displayList
    private val _setsLiveData = MutableLiveData<ModelSettings>() //settings livedata
    val setsLiveData: LiveData<ModelSettings> get() = _setsLiveData
    private val _downProgLive = MutableLiveData<ProgData>() //download for offline view progress livedata
    val downProgLive: LiveData<ProgData> get() = _downProgLive
    private val _reqProgLive = MutableLiveData<ProgData>() //http request progress livedata
    val reqProgLive: LiveData<ProgData> get() = _reqProgLive

    init {
        _setsLiveData.value = sets
        _downProgLive.value = downProg
        _reqProgLive.value = reqProg
        logState.value = LoginState()
    }

    /** update livedata */
    fun updateSet() {
        _setsLiveData.postValue(sets.copy())
    }

    private fun updateDownPrg() {
        _downProgLive.postValue(downProg.copy())
    }

    private fun updateReqPrg() {
        _reqProgLive.postValue(reqProg.copy())
    }

    /** for error reports, replace personalized data (login name/PW) before sending*/
    fun getSafeUserSettings(): String {
        val safeSettings = sets.copy(
            loginName = "",
            loginPW = ""
        )
        return Gson().toJson(safeSettings)
    }

    fun serializeWatchList(): String = Gson().toJson(watchlist)
    fun serializeDownloadList(): String = Gson().toJson(offlineList)

    fun getDisplayListSize(): Int = _displayList.value?.size ?: 0
    fun getImageListSize(): Int = this.entryListImg.size
    fun getItemImageCnt(index: Int): Int = _displayList.value?.get(index)?.imgCounter ?: 0

    /** updates current reading ID for the given thread*/
    private fun updateCurReadInd(thread: String, postId: String) {
        if (postId == "" || thread == "") return
        sets.curReadPostID[thread] = postId
        updateSet()
    }

    /** updates current reading ID of current list to post at index*/
    fun updateCurReadInd(index: Int) {
        val currentList = _displayList.value
        if (sets.curThreadId.isNotEmpty() && currentList != null && hasIndex(index)) {
            val postId = currentList[index].postID
            sets.curReadPostID[sets.curThreadId] = postId
            updateSet()
        }
    }

    /**delete all last-reading IDs*/
    fun deleteLastRead() {
        sets.curReadPostID.clear()
        storeData()
        updateSet()
    }

    /**get index of last read post in current displaylist*/
    fun getLastReadIndex(): Int {
        val currentList = _displayList.value
        val lastid = sets.curReadPostID[sets.curThreadId]
        if (currentList.isNullOrEmpty() || lastid.isNullOrEmpty()) return 0
        return currentList.indexOfFirst { it.postID == lastid }
    }

    /** lists watched threads*/
    private fun showWatches() {
        sets.listType = ThrdItemTyps.WATCH
        updateSet()

        entryListRaw = watchlist.map {
            it.thread
        }.toList()
        entryListImg = entryListRaw
        updateDisplayList()
    }

    /** lists downloaded threads*/
    private fun showOfflines() {
        sets.listType = ThrdItemTyps.OFFLINE
        updateSet()

        entryListRaw = offlineList.map {
            it.thread
        }.toList()
        entryListImg = entryListRaw
        updateDisplayList()
    }

    /** saves data to SharedPreferences*/
    fun storeData() {
        if (!sets.autoLogin) {
            sets.loginName = ""
            sets.loginPW = ""
        }
        dataRepository.saveData("tgchanItems", entryListRaw)
        dataRepository.saveData("watchItems", watchlist)
        dataRepository.saveData("modelSettings", sets)
        dataRepository.saveData("offlineList", offlineList)
    }

    /** loads data from SharedPreferences and updates view*/
    fun loadData() {
        entryListRaw = dataRepository.loadData("tgchanItems", object : TypeToken<List<TgPost>>() {}.type) ?: entryListRaw
        watchlist = dataRepository.loadData("watchItems", object : TypeToken<MutableList<Watch>>() {}.type) ?: watchlist
        offlineList = dataRepository.loadData("offlineList", object : TypeToken<MutableList<OfflineThread>>() {}.type) ?: offlineList
        sets = dataRepository.loadData("modelSettings", object : TypeToken<ModelSettings>() {}.type) ?: sets
        updateSet() //invoke lifedata update on setings

        if (entryListRaw.isEmpty()) { //default page
            loadThread(URLBoards.QUEST.url, ThrdItemTyps.BOARD)
        } else {
            postProcessRawList() //last displaylist is saved, works also offline, but no image guarantee
            updateDisplayList() //invoke lifedata update on displaylist
        }
    }

    /** toggles watchlist status of current thread*/
    fun toggleWatch(threadId: String = sets.curThreadId) {
        if (getWatched(threadId) != null) {
            removeFromWatch(threadId)
        } else {
            addToWatch(threadId)
        }
    }

    /** adds current thread to watchlist and checks for updates*/
    private fun addToWatch(threadId: String) {
        if (getWatched(threadId) != null) return
        val tg = displayList.value?.firstOrNull { it.postID == threadId }
        if (tg === null) return
        watchlist.add(Watch(tg))
        loadThread(tg.url, ThrdItemTyps.THREAD, true)
        storeData()
    }

    /** removes current thread from watchlist*/
    private fun removeFromWatch(threadId: String) {
        watchlist.removeAll(watchlist.filter { it.thread.postID == threadId })
        storeData()
    }

    /** deletes all watchlist entries*/
    fun deleteWatchData() {
        watchlist.clear()
        storeData()
        updateWatchlist()
    }

    /** returns watched thread or null*/
    fun getWatched(threadId: String = sets.curThreadId): Watch? = watchlist.firstOrNull { it.thread.postID == threadId }

    /** returns all data as list for backup*/
    fun exportSetting(): List<Any> = listOf(entryListRaw, watchlist, offlineList, sets)

    /** returns index of post with given ID*/
    fun getPositionById(postID: String): Int = displayList.value?.indexOfFirst { it.postID == postID } ?: -1

    /** imports data from backup*/
    fun importSettings(entryList: List<TgPost>, watlist: MutableList<Watch>, offList: MutableList<OfflineThread>, set: ModelSettings) {
        entryListRaw = entryList
        watchlist = watlist
        offlineList = offList
        sets = set
        storeData()
        updateSet()
        loadCurThread()
    }

    /** imports watchlist from backup*/
    private fun importWatchlist(watlist: MutableList<Watch>) {
        watchlist = watlist
        storeData()
        updateWatchlist() //fetches images and summaries
    }

    /** loads current thread from offline data and returns false if not found*/
    private fun loadFromOffline(): Boolean {
        val htmlFile = File(getApplication<Application>().filesDir, "offline/${sets.curThreadId}.html")
        //html-file is actually JSON as post-list

        if (!htmlFile.exists()) return false
        val gson = Gson()
        val json = htmlFile.readText()
        val listType = object : TypeToken<List<TgPost>>() {}.type
        entryListRaw = gson.fromJson(json, listType)

        //update dataview and settings
        postProcessRawList()
        sets.curTitle = entryListRaw.first().title
        sets.curURL = entryListRaw.first().url
        sets.curThreadId = Regex("""(\d+).html""").find(entryListRaw.first().url)?.groupValues?.get(1) ?: ""

        fromOffline = true //offline flag
        updateSet()

        return true
    }

    /** clean up after requests: watch/offline/board: show list, otherwise show thread, upadte lifedata*/
    private fun afterUpdateReq() {
        reqProg.pos = 0
        reqProg.max = 0
        reqProg.status = ProgStatus.DONE
        updateReqPrg()
        updateSet()

        when (sets.listType) {
            ThrdItemTyps.WATCH -> {
                showWatches()
            }

            ThrdItemTyps.OFFLINE -> {
                showOfflines()
            }

            else -> {
                if (!fromOffline) preloadThumbnails()
                updateDisplayList()
            }
        }

    }

    /** deletes all offline data, keep offline-folder, refresh displayed list*/
    fun deleteOfflineData() {
        val offImgPath = File(getApplication<Application>().filesDir, "offline")
        deleteDirectory(offImgPath)
        offImgPath.mkdirs()
        offlineList.clear()

        storeData()
        updateSet()
        loadCurThread()
    }

    /** deletes single offline thread*/
    fun deleteOffline(threadId: String = sets.curThreadId) {
        val htmlFile = File(getApplication<Application>().filesDir, "offline/${threadId}.html")
        htmlFile.delete() //remove html file

        //remove image folder
        val offImgPath = File(getApplication<Application>().filesDir, "offline/${threadId}_img")
        if (offImgPath.exists()) deleteDirectory(offImgPath)

        //remove entry
        val index = offlineList.indexOfFirst { it.thread.postID == threadId }
        if (index >= 0) offlineList.removeAt(index)
    }

    /** Helper function: deletes all files in directory and subdirectories*/
    private fun deleteDirectory(directory: File) {
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        }
    }

    /** downloads images of current thread*/
    fun downloadImages(threadId: String, onlyThumb: Boolean) {
        val offImgPath = File(getApplication<Application>().filesDir, "offline/${threadId}_img")
        if (!offImgPath.exists()) offImgPath.mkdirs() //fallback, should be there

        var downloadlist = entryListImg.map { it.imgUrl }
        if (!onlyThumb) { //if "only thumbnails" is selected, only download thread.imgUrl. Redirect of fullview-url in Glide-viewer
            downloadlist = entryListImg.flatMap { thread ->
                listOf(
                    thread.imgUrl,
                    thread.imgUrl.replace("thumb", "src").replace("s.", ".")
                    //thumbnails have format "/thumb/[...]s.[...]". Fullview path is calculated as "/src/[...].[...]"
                )
            }
        }
        downloadlist = downloadlist.filter { //only download images not already there!
            val file = File(offImgPath, it)
            !file.exists()
        }

        viewModelScope.launch { //download images, show progress in mainUI
            downloadImgList(downloadlist, offImgPath)
        }
    }

    /** downloads given images list into given folder path*/
    private suspend fun downloadImgList(downloadlist: List<String>, offImgPath: File) {
        withContext(Dispatchers.IO) {
            try {
                downProg.status = ProgStatus.RUNNING
                downProg.max = downloadlist.size
                downProg.pos = 0
                updateDownPrg()

                downloadlist.forEach { thread ->
                    try {
                        downProg.pos++
                        updateDownPrg()

                        val fileName = thread.substringAfterLast("/")
                        val file = File(offImgPath, fileName)
                        if (file.exists()) return@forEach //skip images already downloaded

                        val url = URL("https://questden.org$thread")
                        val connection = url.openConnection()
                        connection.connect()

                        val inputStream: InputStream = connection.getInputStream()
                        val outputStream = FileOutputStream(file)

                        val buffer = ByteArray(4096)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }

                        outputStream.close()
                        inputStream.close()

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                downProg.status = ProgStatus.DONE
                updateDownPrg()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** saves thread to offline data. add ID to offlineList*/
    fun writeToOffline(threadId: String, onlyThumbs: Boolean) {
        val gson = Gson()
        val cont = gson.toJson(this.entryListRaw)
        val htmlFile = File(getApplication<Application>().filesDir, "offline/${threadId}.html")

        htmlFile.writeText(cont)
        val offitem = offlineList.firstOrNull { it.thread.postID == threadId }
        if (offitem == null)
            offlineList.add(OfflineThread(entryListRaw.first(), onlyThumbs))
        else {
            offitem.thread = entryListRaw.first()
            offitem.onlyThumbs = onlyThumbs
        }
        fromOffline = true
        storeData()
    }

    /** sets highlight to given index*/
    fun setHighlight(index: Int) {
        if (_displayList.value != null && hasIndex(index)) {
            if (highLightInd >= 0 && hasIndex(highLightInd)) _displayList.value!![highLightInd].isHighlight = false
            highLightInd = index
            _displayList.value!![index].isHighlight = true
        }
    }

    /** updates settings: thumbnail from full resolution in fullwidth mode. invokes lifedata update to refresh view*/
    fun setThumbFromFull(state: Boolean) {
        sets.thumbFromFull = state
        if(state)preloadFullImages()
        updateSet()
    }

    /** returns thread if downloaded or null*/
    fun getDownload(id: String): OfflineThread? {
        return offlineList.firstOrNull { it.thread.postID == id }
    }

    /** Checks if thread is downloaded */
    fun isDownloaded(id: String = sets.curThreadId): Boolean {
        return offlineList.any { it.thread.postID == id }
    }

    /** checks if index is in range if displayed List*/
    fun hasIndex(index: Int): Boolean {
        return index >= 0 && index < (_displayList.value?.size ?: 0)
    }

    /** displaylist livedata invkoe*/
    fun updateDisplayList() {
        _displayList.postValue(if (sets.showOnlyPics) entryListImg else entryListRaw)
    }

    /** rotates through SWFModes and inkoes update*/
    fun rotateSFW() {
        sets.sfw = when (sets.sfw) {
            SFWModes.SFWQUESTION -> SFWModes.SFWREAL
            SFWModes.SFWREAL -> SFWModes.NSFW
            SFWModes.NSFW -> SFWModes.SFWQUESTION
        }
        updateSet()
    }

    /** rotates through thumbnail size modes and inkoes update*/
    fun rotateImgMode() {
        sets.imageMode = when (sets.imageMode) {
            imgMode.SMALL -> imgMode.BIG
            imgMode.BIG -> imgMode.FULL
            imgMode.FULL -> imgMode.SMALL
        }
        updateSet()
    }

    /** get next position given a scrollmode in displaylist (next button)*/
    fun getNextPos(index: Int, mode: ScrollMode): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        return if (mode == ScrollMode.IMAGES) {
            index + 1 + list.subList(index + 1, getDisplayListSize()).indexOfFirst { it.imgUrl.isNotEmpty() }
        } else {
            index + 1
        }
    }

    /** get previous position given a scrollmode in displaylist (prev button)*/
    fun getPrevPos(index: Int, mode: ScrollMode, withinCur: Boolean): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        val sameType = (mode == ScrollMode.IMAGES) == (list[index].imgUrl.isNotEmpty())

        //if half a post is in view, scroll to its top
        if (sameType && withinCur) return index
        return if (mode == ScrollMode.IMAGES) {
            list.subList(0, index).indexOfLast { it.imgUrl.isNotEmpty() }
        } else {
            index - 1
        }
    }

    /** get last position given a scrollmode in displaylist (last button)*/
    fun getLastPos(index: Int, mode: ScrollMode): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        return if (mode == ScrollMode.IMAGES) list.indexOfLast { it.imgUrl.isNotEmpty() } else list.lastIndex
    }

    /** navigate to given board page */
    fun navigatePage(page: Int, rel: Boolean = false) {
        val npage = if (rel) sets.boardPage + page else page
        if (sets.listType == ThrdItemTyps.WATCH || npage < 0 || npage > sets.curMaxPage) return
        sets.boardPage = npage
        updateSet()
        loadCurThread()
    }

    /** Extracts ID from URL string */
    private fun getIdbyUrl(url: String): String = Regex("""(\d+).html""").find(url)?.groupValues?.get(1) ?: ""

    /** reloads current thread or updates watchlist  */
    fun loadCurThread() {
        if (sets.listType == ThrdItemTyps.WATCH) updateWatchlist()
        else loadThread(sets.curURL, sets.listType)
    }

    /**
     * fills dataviewmodel with data from requests
     * handles all different thread modes, watchlist update checks or update downloaded threads
     * access do downloaded data is redirected to storage. Internet access is checked
     * requires relative URL, should work with absolute URLs as well (tgchan.org and questden.org)
     * */
    fun loadThread(url: String, mode: ThrdItemTyps, onlyCheckWatch: Boolean = false, updOffline: Boolean = false, backwards: Boolean = false) {
        var murl = url
        val fet = murl.indexOf("#")
        if (fet >= 0) murl = murl.substring(0, fet) //remove highlight #

        //fallback if mode is board, but unknown board is requested
        if (url != "" && mode == ThrdItemTyps.BOARD && !URLBoards.entries.any { it.url == url }) {
            loadThread(url, ThrdItemTyps.THREAD, onlyCheckWatch, updOffline)
            return
        }

        val threadId = getIdbyUrl(murl)
        val watchedItem: Watch? = getWatched(threadId)

        //Internet connection check
        val context = getApplication<Application>().applicationContext
        if (!isInternetAvailable(context)) {
            //only allow watchlist, downloaded, sync, backup and showing downloaded threads
            //do not allow boards, online threads or updating downloaded threads
            if ((mode == ThrdItemTyps.THREAD && !isDownloaded(threadId)) || updOffline ||
                mode == ThrdItemTyps.BOARD
            ) {
                MsgHelper.showMsg(context, "You are offline!\nOnly downloaded threads can be displayed.")
                return
            }
        }

        //back button for navigation
        if (!onlyCheckWatch && !updOffline && !backwards)
            backActionStack.add(backPage(sets.listType, sets.curURL))

        //progress lifedata
        reqProg.max++
        reqProg.status = ProgStatus.RUNNING
        fromOffline = false
        updateReqPrg()

        //not just checking status: update sets.
        if (!onlyCheckWatch) {
            sets.listType = mode
            sets.curURL = murl
            showListDemand.postValue(true)//demand mainfragment display of list
        }

        when (mode) {
            ThrdItemTyps.BOARD -> { //fetch board list
                if (sets.boardPage > 0)
                    murl = "$murl${sets.boardPage}.html"
            }

            ThrdItemTyps.THREAD -> { //fetch a quest
                if (!onlyCheckWatch) { //fetch and display thread
                    sets.curThreadId = threadId

                    if (!updOffline && loadFromOffline()) {//load from offline files, if downloaded
                        afterUpdateReq()
                        return
                    }
                } else { //check for new posts in watch
                    if (watchedItem == null) {
                        reqProg.pos++
                        updateReqPrg()
                        if (reqProg.pos == reqProg.max) afterUpdateReq()
                        return
                    }
                }
            }

            ThrdItemTyps.OFFLINE -> { //fetch downloaded quests
                sets.curTitle = "Downloaded"
                sets.curThreadId = ""
                sets.curMaxPage = 0
                sets.boardPage = 0
                showOfflines() //display list
                storeData()
                afterUpdateReq()
                return
            }

            ThrdItemTyps.WATCH -> { //list watched quests
                sets.curTitle = "Watch list"
                sets.curThreadId = ""
                sets.curMaxPage = 0
                sets.boardPage = 0
                showWatches() //display list
                storeData()
                afterUpdateReq()
                return
            }
        }
        viewModelScope.launch {
            try {
                makeNetRequest(murl, mode, onlyCheckWatch, updOffline)
            } catch (e: Exception) {
                reqProg.pos = 0
                reqProg.max = 0
                reqProg.status = ProgStatus.ERROR

                val errorMessage = e.message ?: "Unknown Error"
                reqProg.msg = "Creating Download Coroutine: $errorMessage"
                updateReqPrg()
            }
        }
    }

    /** Actual Network request to questden.org */
    private suspend fun makeNetRequest(murl: String, mode: ThrdItemTyps, onlyCheckWatch: Boolean, updOffline: Boolean): Unit = withContext(Dispatchers.IO) {
        val watchedItem = getWatched(getIdbyUrl(murl))
        val newestId = if (onlyCheckWatch) watchedItem?.lastReadId else null //update watchlist after reading status
        val request = //handle absolute/relative URLs
            if (murl.startsWith("https://"))
                Request.Builder().url(murl).build()
            else
                Request.Builder().url("https://questden.org$murl").build()


        client.newCall(request).execute().use { response ->
            try {
                reqProg.pos += 1
                if (!response.isSuccessful) throw Exception("Request to $murl failed.")

                val resp = response.body?.string() ?: throw Exception("Empty response body.")
                val li = when (mode) {
                    ThrdItemTyps.THREAD -> parseThreadMode(resp, newestId)
                    ThrdItemTyps.BOARD -> parseBoardMode(resp)
                    ThrdItemTyps.WATCH -> throw Exception("WATCH mode not supported in MakeNetRequest") //failsave, should not be reached
                    ThrdItemTyps.OFFLINE -> throw Exception("OFFLINE mode not supported in MakeNetRequest") //failsave, should not be reached
                }

                processParsedData(li, murl, mode, watchedItem, onlyCheckWatch) //sets storage to results
                if (updOffline) updateOfflineMode() //updates offline data
                storeData() //stores current view and settings

                if (reqProg.pos == reqProg.max) afterUpdateReq()
            } catch (e: Exception) {
                reqProg.status = ProgStatus.ERROR

                val errorMessage = e.message ?: "Unknown Error"
                reqProg.msg = "Creating Net Request: $errorMessage"

                updateReqPrg()
                afterUpdateReq()
            }
        }
    }

    /** Handles Threads/Quests. Accepts HTMLstring and newest ID for update watchlist*/
    private fun parseThreadMode(resp: String, newestId: String?): MutableList<TgPost> {
        if (newestId != null) { // Neuste Beiträge anzeigen
            val startIdx = resp.indexOf("""id="reply$newestId"""")
            var trimmedResp = if (startIdx > 0) resp.substring(startIdx) else resp
            trimmedResp=StringUtils.preserveHTMLPreBreaks(trimmedResp)
            val doc = Jsoup.parse(trimmedResp)

            val posts = doc.select("table").map { parseJSoupToTgThread(it) }
                .filter { it.postID.isNotEmpty() }
                .toMutableList()

            // Thread-Informationen abrufen und hinzufügen
            val infoSectionStart = resp.indexOf("<form id=\"delform\"")
            val infoSectionEnd = resp.indexOf("</blockquote", infoSectionStart) + 20
            val infoDoc = Jsoup.parse(resp.substring(infoSectionStart, infoSectionEnd))

            val threadInfo = infoDoc.select("form").map { parseJSoupToTgThread(it) }
                .firstOrNull { it.postID.isNotEmpty() }
            if (threadInfo !== null) {
                if (threadInfo.title.isEmpty()) threadInfo.title = "Untitled"
                threadInfo.postCount = StringUtils.countOccurrences(resp, "<blockquote>")
            }

            if (posts.isEmpty()) { //nothing new
                return if (threadInfo == null)
                    mutableListOf() // Leere Liste, falls keine Info-Einträge gefunden
                else
                    mutableListOf(threadInfo)
            } else {
                threadInfo?.let { posts.add(it) }
                return posts
            }
        } else {// Thread anzeigen, wenn keine neue ID vorhanden
            val presResp = StringUtils.preserveHTMLPreBreaks(resp)
            val doc = Jsoup.parse(presResp)

            val tmpLi = doc.select("#delform,#delform>table").map { parseJSoupToTgThread(it) }
                .filter { it.postID.isNotEmpty() }
                .toMutableList()
            if (tmpLi.first().title.isEmpty()) tmpLi.first().title = "Untitled"
            return tmpLi
        }
    }

    /** Helper function to parse BOARD mode using regexp*/
    private fun parseBoardMode(resp: String): MutableList<TgPost> {
        val rexSec = Regex("<div id=\"thread.*?>(.*?)<blockquote>(.*?)</blockquote>(?:.*?<span.*?class=\"omittedposts\">.*?(\\d+).*?posts.*?omitted)?", RegexOption.DOT_MATCHES_ALL)
        val rexTitle = Regex("<span.*?class=\"filetitle\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val rexAuthor = Regex("<span.*?class=\"postername\".*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val rexImg = Regex("<img.*?src=\"(.*?)\"[^>]*class=\"thumb\"", RegexOption.DOT_MATCHES_ALL)
        val rexRef = Regex("class=\"reflink\".*?a href=\"(.*?(\\d+))\"", RegexOption.DOT_MATCHES_ALL)
        val rexSpoilerImg = Regex("firstChild.src='(.*?)'", RegexOption.DOT_MATCHES_ALL)
        val rexMaxPage = Regex("""<a href="/kusaba/.*?/(\d+)\.html">\d+</a>""", RegexOption.DOT_MATCHES_ALL)


        fun extractFirstGroup(regex: Regex, text: String): String =
            regex.find(text)?.groupValues?.get(1)?.replace("\n", "") ?: ""

        val threads = rexSec.findAll(resp).map { match ->
            val threadHTML = match.groupValues[1]
            val th = TgPost(
                title = extractFirstGroup(rexTitle, threadHTML),
                author = extractFirstGroup(rexAuthor, threadHTML),
                imgUrl = extractFirstGroup(rexSpoilerImg, threadHTML).ifEmpty {
                    extractFirstGroup(rexImg, threadHTML)
                },
                url = extractFirstGroup(rexRef, threadHTML),
                postID = rexRef.find(threadHTML)?.groupValues?.get(2) ?: "",
                summary = match.groupValues[2],
                postCount = match.groupValues[3].ifEmpty { "-6" }.toInt() + 6
            )
            th
        }.filter { it.url.isNotEmpty() }.toMutableList()

        rexMaxPage.findAll(resp).lastOrNull()?.let { maxPageMatch ->
            threads.add(TgPost("thread_info", "", "", "", maxPageMatch.groupValues.last()))
        }

        return threads
    }

    /** Handles processed list of Posts */
    private fun processParsedData(
        li: MutableList<TgPost>, murl: String, mode: ThrdItemTyps, watchedItem: Watch?, onlyCheckWatch: Boolean
    ) {
        when (mode) {
            ThrdItemTyps.THREAD -> {
                if (onlyCheckWatch) {
                    handleWatchOnlyMode(li, watchedItem)
                } else {
                    handleThreadMode(li, murl, watchedItem)
                }
            }

            ThrdItemTyps.BOARD -> {
                handleBoardMode(li)
            }

            ThrdItemTyps.WATCH -> throw Exception("WATCH not supported in processParsedData") //failsave, should not be reached
            ThrdItemTyps.OFFLINE -> throw Exception("OFFLINE not supported in processParsedData")//failsave, should not be reached
        }
    }

    fun preloadFullImages(){
        val context = getApplication<Application>().applicationContext
        fullImgDimMap.clear()

        entryListImg.forEach { entry ->
            val fullImageUrl = entry.imgUrl.replace("thumb", "src").replace("s.", ".")
            Glide.with(context)
                .load(fullImageUrl)  // Liste der URLs für die Thumbnails
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(p0: GlideException?, p1: Any?, target: Target<Drawable>?, p3: Boolean): Boolean {
                        return false
                    }
                    override fun onResourceReady(p0: Drawable?, p1: Any?, target: Target<Drawable>?, p3: DataSource?, p4: Boolean): Boolean {
                        p0?.let {
                            val width = it.intrinsicWidth
                            val height = it.intrinsicHeight
                            fullImgDimMap[fullImageUrl] = Pair(width, height)
                        }
                        return false
                    }
                })
                .preload()
        }
    }

    /** Preloads all thumbnails for faster view while scrolling */
    private fun preloadThumbnails() {
        val context = getApplication<Application>().applicationContext
        Glide.with(context)
            .load(entryListImg.map { it.imgUrl })  // Liste der URLs für die Thumbnails
            .preload()  // Bilder werden im Hintergrund vorab geladen
        if(sets.thumbFromFull)preloadFullImages()
    }

    /** Handles Watchlist Mode, updating thread counters. Posts before lastreadID are removed beforehand*/
    private fun handleWatchOnlyMode(li: MutableList<TgPost>, watchedItem: Watch?) {
        if (li.size == 0) return
        val inf = li.removeAt(li.lastIndex) //last item is artificial added with meta data
        val newPosts = li.count { it.postID.isNotEmpty() }
        val newImgs = li.count { it.imgUrl.isNotEmpty() }

        // Falls der watchedItem-Eintrag leer ist, initialisieren
        watchedItem?.let {
            if (it.thread.title.isEmpty()) {
                it.thread = inf
            }
            val oldw = watchlist.firstOrNull { watch -> watch.thread.url == it.thread.url }
            oldw?.apply {
                it.thread = inf
                it.newImg = newImgs
                it.newPosts = newPosts
            }
        }
    }

    /** Handles Thread Mode, updating dataviewmodel settings and list */
    private fun handleThreadMode(li: MutableList<TgPost>, murl: String, watchedItem: Watch?) {
        sets.curTitle = li.firstOrNull()?.title.orEmpty()
        sets.curThreadId = Regex("""(\d+).html""").find(murl)?.groupValues?.get(1).orEmpty()
        entryListRaw = li
        postProcessRawList()

        watchedItem?.apply {
            lastReadId = li.lastOrNull()?.postID.orEmpty()
            newImg = 0
            newPosts = 0
        }
    }

    /** Processes rawlist, adding an image counter and creating the listimg objekt*/
    private fun postProcessRawList() {
        var imageCount = 0
        entryListRaw.forEach { thread ->
            if (thread.imgUrl.isNotEmpty()) {
                imageCount++
            }
            thread.imgCounter = imageCount  // Bildzähler in jedem Eintrag speichern
            thread.threadExtended = false //preview extended in overview
        }
        entryListImg = entryListRaw.filter { it.imgUrl.isNotEmpty() } //only items with images
    }

    /** Handles Board Mode, updating dataviewmodel settings and list */
    private fun handleBoardMode(li: MutableList<TgPost>) {
        sets.curTitle = sets.curURL
        sets.curThreadId = ""

        val inf = li.removeAt(li.lastIndex)
        sets.curMaxPage = inf.summary.toIntOrNull() ?: 0

        entryListRaw = li
        entryListImg = entryListRaw // Board Mode: Keine Filterung auf Bilder
    }


    /** updates downloaded threads*/
    private fun updateOfflineMode() {
        val onlyThumb = offlineList.firstOrNull { it.thread.postID == sets.curThreadId }?.onlyThumbs ?: false
        writeToOffline(sets.curThreadId, onlyThumb)
        downloadImages(sets.curThreadId, onlyThumb)
    }

    /** updates watchlist, iterating through items*/
    private fun updateWatchlist() {
        for (w in watchlist) {
            loadThread(w.thread.url, ThrdItemTyps.THREAD, onlyCheckWatch = true)
        }
        if (watchlist.size == 0) afterUpdateReq()
    }

    /** checks for  internet connection using getSystemService as ConnectivityManager*/
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo //deprecated, minsdk 16 support
        return networkInfo != null && networkInfo.isConnected //deprecated, minsdk 16 support
    }

//Server interaction section
    /** Update credentials for server synchronization*/
    fun setCredServer(name: String = sets.loginName, pw: String = sets.loginPW, saveLogin: Boolean = sets.autoLogin) {
        sets.loginName = name
        sets.loginPW = pw
        sets.autoLogin = saveLogin
    }

    /** Helper function to format dates */
    private fun formatDate(unixTimestampS: Long = System.currentTimeMillis() / 1000): String {
        val date = Date(unixTimestampS * 1000)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(date)
    }

    /** Login to server, updating logState livedata and token */
    fun loginServer() {
        val jsons = JSONObject().apply {
            put("username", sets.loginName)
            put("password", sets.loginPW)
            put("type", "test")
        }
        makePostRequest(jsons.toString(), onSuccess = { jsonstr ->
            if (jsonstr.has("token")) {
                logState.value?.token = jsonstr.getString("token")
                logState.value?.accessDate = jsonstr.optLong("access", 0)
                sets.loginName = jsonstr.getString("name")

                logState.value?.errorCode = 0
                logState.value?.promptText = ("Hello ${sets.loginName}!")
                logState.value?.statusText = (if (logState.value == null || logState.value?.accessDate == 0L) "No data yet" else "Data from ${formatDate(logState.value!!.accessDate)}")
            } else {
                logState.value?.errorCode = 2
                logState.value?.token = ""
                logState.value?.accessDate = 0
                logState.value?.promptText = "Login failed(#2)!"
                logState.value?.statusText = "Login failed(#2)!"
            }
            logState.postValue(logState.value)//trigger observe
        })
    }

    /** Logout from server, updating logState livedata and token */
    fun logoutServer() {
        logState.value?.token=""
        logState.value?.accessDate = 0
        logState.value?.errorCode = 0
        logState.value?.promptText = "Logged out!"
        logState.value?.statusText = "Logged out!"
        logState.postValue(logState.value)//trigger observe
    }

    /** clears logstate without invoke. Called after logstate is handled to prevent double message */
    fun clearLoginStatus(){
        logState.value?.errorCode = 0
        logState.value?.promptText = ""
        logState.value?.statusText = ""
    }

    /** Download watchlist from server using token */
    fun downloadServer() {
        val jsons = JSONObject().apply {
            put("token", logState.value?.token)
            put("type", "download")
            put("obj", "watchbar") //only watchbar data, not editor/sidebar settings
        }
        makePostRequest(jsons.toString(), onSuccess = { json ->
            val itemList = mutableListOf<Watch>()
            if (json.has("threads")) {

                sets.numLinkMode = json.optInt("numLinkMode", 0)

                val entriesArray = json.getJSONObject("threads").getJSONArray("value")
                for (i in 0 until entriesArray.length()) {
                    val post = TgPost()
                    val wat = Watch()
                    val entry = entriesArray.getJSONArray(i)
                    val key = entry.getString(0)   // Erstes Element des Arrays (key)
                    val value = entry.getJSONObject(1) // Zweites Element des Arrays (value)

                    post.postID = key
                    if (post.postID == "") continue

                    post.title = value.optString("label", "Untitled")
                    post.url = "/kusaba/${value.optString("section", "quest")}/res/${key}.html"
                    post.author = value.optString("author", "")

                    wat.thread = post
                    wat.lastReadId = value.optString("lastReadId", "")

                    //unused in app, stored for upload
                    wat.highImgOnly = value.optBoolean("highImgOnly", true)
                    wat.highIDs = value.optJSONArray("highIDs")?.let { jsonArray ->
                        List(jsonArray.length()) { jsonArray.getString(it) }
                    } ?: emptyList()
                    wat.highNames = value.optJSONArray("highNames")?.let { jsonArray ->
                        List(jsonArray.length()) { jsonArray.getString(it) }
                    } ?: emptyList()
                    wat.ignoreIDs = value.optJSONArray("ignoreIDs")?.let { jsonArray ->
                        List(jsonArray.length()) { jsonArray.getString(it) }
                    } ?: emptyList()
                    wat.ignoreNames = value.optJSONArray("ignoreNames")?.let { jsonArray ->
                        List(jsonArray.length()) { jsonArray.getString(it) }
                    } ?: emptyList()

                    itemList.add(wat)
                    updateCurReadInd(key, value.optString("currentReadId", ""))
                }
                importWatchlist(itemList)

                logState.value?.errorCode = 0
                logState.value?.promptText = ("Download successfull!")
                logState.value?.statusText = (getApplication<Application>().getString(R.string.watched_threads_imported, itemList.size))
                logState.postValue(logState.value)//trigger observe
            } else {
                logState.value?.errorCode = 3
                logState.value?.token = ""
                logState.value?.accessDate = 0
                logState.value?.promptText = "Download failed(#3)!"
                logState.value?.statusText = "Download failed(#3)!"
            }
        })
    }

    data class ThreadData(
        val label: String,
        val author: String,
        val section: String,
        val highImgOnly: Boolean,
        val highIDs: List<String>,
        val highNames: List<String>,
        val ignoreIDs: List<String>,
        val ignoreNames: List<String>,
        val lastReadId: String,
        val currentReadId: String,
        val newEntrCnt: Int,
        val totalEntrCnt: Int
    )
    data class Threads(
        val type: String = "Map",
        val value: List<Pair<String, ThreadData>>
    )
    data class Data(
        val numLinkMode: Int,
        val threads: Threads,
        val default: ThreadData
    )

    /** Upload watchlist to server using token */
    fun uploadServer() {

        val data = JSONObject()
        data.put("numLinkMode", sets.numLinkMode)

        val threads = JSONObject()
        threads.put("_type", "Map")

        val threadArray = JSONArray()
        watchlist.forEach {
            val threadData = JSONObject()
            threadData.put("label", it.thread.title)
            threadData.put("author", it.thread.author)
            threadData.put("section", it.thread.url.split("/")[2])
            threadData.put("highImgOnly", it.highImgOnly)
            threadData.put("highIDs", JSONArray(it.highIDs))
            threadData.put("highNames", JSONArray(it.highNames))
            threadData.put("ignoreIDs", JSONArray(it.ignoreIDs))
            threadData.put("ignoreNames", JSONArray(it.ignoreNames))
            threadData.put("lastReadId", it.lastReadId)
            threadData.put("currentReadId", sets.curReadPostID[it.thread.postID] ?: "")
            threadData.put("newEntrCnt", it.newPosts)
            threadData.put("totalEntrCnt", it.thread.postCount)

            val threadEntry = JSONArray()
            threadEntry.put(it.thread.postID)
            threadEntry.put(threadData)

            threadArray.put(threadEntry)
        }

        threads.put("value", threadArray)
        data.put("threads", threads)

        val default = JSONObject()
        default.put("label", "Default")
        default.put("author", "none")
        default.put("section", "general")
        default.put("highImgOnly", true)
        default.put("highIDs", JSONArray())
        default.put("highNames", JSONArray())
        default.put("ignoreIDs", JSONArray())
        default.put("ignoreNames", JSONArray())
        default.put("lastReadId", "")
        default.put("currentReadId", "")
        default.put("newEntrCnt", 0)
        default.put("totalEntrCnt", 0)

        data.put("default", default)

        val jsonData = data.toString()

        // Server JSON format:
        //{"0":"\"","1":"\"","numLinkMode":0,"threads":{"_type":"Map","value":[["1098850",{"label":"Why did you do that?","author":"tippler","section":"quest","highImgOnly":true,"highIDs":[],"highNames":[],"ignoreIDs":[],"ignoreNames":[],"lastReadId":"1101457","currentReadId":"1098852","newEntrCnt":0,"totalEntrCnt":283}]]},"default":{"label":"Default","author":"none","section":"general","highImgOnly":true,"highIDs":[],"highNames":[],"ignoreIDs":[],"ignoreNames":[],"lastReadId":"","currentReadId":"","newEntrCnt":0,"totalEntrCnt":0}}
//        var data = """{"numLinkMode":${sets.numLinkMode},"threads":{"_type":"Map","value":["""
//        data += watchlist.joinToString {
//            val currentReadId = sets.curReadPostID[it.thread.postID] ?: ""
//
//            """["${it.thread.postID}",{"label":"${it.thread.title}","author":"${it.thread.author}","section":"${it.thread.url.split("/")[2]}",
//                |"highImgOnly":${it.highImgOnly},"highIDs":${it.highIDs},"highNames":${it.highNames},"ignoreIDs":${it.ignoreIDs},
//                |"ignoreNames":${it.ignoreNames},"lastReadId":"${it.lastReadId}","currentReadId":"$currentReadId",
//                |"newEntrCnt":${it.newPosts},"totalEntrCnt":${it.thread.postCount}}]""".trimMargin()
//        }
//        data += """]}}"""

        val jsonDataObject = JSONObject(jsonData)

        val jsons = JSONObject().apply {
            put("token", logState.value?.token)
            put("type", "upload")
            put("data", jsonDataObject) // Fügt den JSON-Inhalt direkt ein
            put("obj", "watchbar") //only watchbar data, not editor/sidebar settings
        }

        makePostRequest(jsons.toString(), onSuccess = { json ->
            if (json.has("access")) {
                logState.value?.errorCode = 0
                logState.value?.accessDate = json.optLong("access", 0)
                logState.value?.promptText = ("Uploaded complete!")
                logState.value?.statusText = (if (logState.value == null || logState.value?.accessDate == 0L) "Error fetching date" else "Data from ${formatDate(logState.value!!.accessDate)}")
                logState.postValue(logState.value)//trigger observe
            }
        })
    }

    /** Helper function to make a POST request to the server and updates logState*/
    private fun makePostRequest(jsonBody: String, onSuccess: (JSONObject) -> Unit) {

        val client = OkHttpClient()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverURL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

                logState.value?.promptText = ("Request failed: ${e.message}")
                logState.value?.statusText = ("Failed: ${e.message}")
                logState.value?.errorCode = 1
                logState.postValue(logState.value)//trigger observe
                logState.value?.token=""
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val jsonResponse = JSONObject(responseBody) // JSON-Daten verarbeiten
                        onSuccess(jsonResponse)
                    }
                } else {
                    logState.value?.errorCode = response.code
                    logState.value?.promptText = ("Request unsuccessfull: ${response.code}")
                    logState.value?.statusText = ("Error: ${response.code}")
                    logState.postValue(logState.value)//trigger observe
                    logState.value?.token=""
                }
            }
        })
    }
}


/** Viewmodel for reply form, preserving inputs during navigation
 * No Livedata, since no Observer
 * */
class ReplyViewModel : ViewModel() {
    var messageText: String = ""
    var author: String = ""
    var subject: String = ""
    var email: String = ""
    var cursorPosition: Int = 0
    var uploadFile: RequestBody? = null
    var uploadName: String = ""

    fun clear() {
        messageText = ""
        cursorPosition = 0
        author = ""
        subject = ""
        email = ""
        uploadFile = null
        uploadName = ""
    }

    /**Inser at cursor, mostly for references >># */
    fun insertAtCur(newText: String) {
        val updatedText = StringBuilder(messageText).apply {
            insert(cursorPosition, newText)
        }.toString()

        messageText = updatedText
        cursorPosition += newText.length
    }
}

/** Helper class for wiki links*/
data class LinkItem(val text: String, val url: String)

/** Viewmodel for Chapter List*/
class WikiViewModel : ViewModel() {
    private val _linksLiveData = MutableLiveData<List<LinkItem>>() //actual List
    val linksLiveData: LiveData<List<LinkItem>> = _linksLiveData

    private val _reqProgLive = MutableLiveData<ProgStatus>() //progress status
    val reqProgLive: LiveData<ProgStatus> get() = _reqProgLive
    //default is IDLE, request set it to RUNNING, afterwards its DONE until processed, then again IDLE

    private val _errorLiveData = MutableLiveData<String>() //error message
    val errorLiveData: LiveData<String> = _errorLiveData

    init {
        setIdle()
    }

    fun setIdle() {
        _reqProgLive.value = ProgStatus.IDLE
    }

    /** fetch links from a wiki page and update progress
     * 1. search for thread ID in wiki search and extract URL of thread
     * 2. request wiki page of thread and extract infobox.
     * 3. extract links from infobox  */
    fun loadTableData(threadId: String) {
        _reqProgLive.postValue(ProgStatus.RUNNING)
        viewModelScope.launch {
            try {
                val searchUrl = "https://questden.org/w/index.php?search=$threadId" //search wiki
                val wikiUrl = fetchWikiUrl(searchUrl) //fetch wiki thread url
                if (wikiUrl != null) {
                    val links = fetchLinksFromWiki("https://questden.org$wikiUrl") //visit wiki thread and extract infobox links
                    if (links.isNotEmpty()) {
                        _linksLiveData.postValue(links)
                        _reqProgLive.postValue(ProgStatus.DONE)
                    } else {
                        _errorLiveData.postValue("No chapters found.")
                        _reqProgLive.postValue(ProgStatus.ERROR)
                    }
                } else {
                    _errorLiveData.postValue("No thread found.")
                    _reqProgLive.postValue(ProgStatus.ERROR)
                }
            } catch (e: Exception) {
                _errorLiveData.postValue("Error: ${e.message}")
                _reqProgLive.postValue(ProgStatus.ERROR)
            }
        }
    }

    /** fetch wiki thread url from wiki search, first search result identified by CSS selector */
    private suspend fun fetchWikiUrl(searchUrl: String): String? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(searchUrl).build()
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            response.body?.string()?.let { html ->
                val doc = Jsoup.parse(html)
                val link = doc.selectFirst(".mw-search-results .mw-search-result-heading a") //first search result
                return@withContext link?.attr("href")
            }
        } else {
            null
        }
    }

    /** visit wiki thread and extract links from infobox by CSS selector*/
    private suspend fun fetchLinksFromWiki(wikiUrl: String): List<LinkItem> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(wikiUrl).build()
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            response.body?.string()?.let { html ->
                val doc = Jsoup.parse(html)
                return@withContext doc.select(".infobox a[href*=html]").map { //CSS selector of links to .html pages in infobox. turn absolute to relative urls.
                    LinkItem(text = it.text(), url = it.attr("href").replaceFirst("https://questden.org", "", ignoreCase = true).replaceFirst("https://tgchan.org", "", ignoreCase = true))
                }
            }
        }
        return@withContext emptyList()
    }
}

object StringUtils {
    /** counts occurrences of a string in a text */
    fun countOccurrences(text: String, search: String): Int {
        var count = 0
        var index = text.indexOf(search)

        while (index >= 0) {
            count++
            index = text.indexOf(search, index + 1)
        }

        return count
    }

    /** fetches innerHTML of current HTML-span-tag until matching closing span
     * if more span tags open, function will search for matching number of closing span tags.
     * 1. search for first </span
     * 2. if substring not includes <span, return substring.
     * 3. otherwise search for next </span and check substring from last </span for span then repeat 2.
     */
    fun fetchUntilClosingSpan(html:String):String{
        var spanind=html.indexOf("</span", startIndex = 0)
        if(spanind<0) return html

        var spanind2=0
        var subt=html.substring(0,spanind)

        while(subt.contains("<span")) {
            spanind2 = html.indexOf("</span", startIndex = spanind + 1)
            if(spanind2<0) break
            subt=html.substring(spanind,spanind2)
            spanind=spanind2
        }
        return html.substring(0,spanind)
    }

    /** replaces \n,\r,\r\n with <br> in HTML string within span tags with white-sprace:pre-wrap */
    fun preserveHTMLPreBreaks(html:String):String{
        var updatedHtml = html
        val ident="<span style=\"white-space: pre-wrap !important; font-family: monospace, monospace !important;\">"
        var pos= updatedHtml.indexOf(ident)

        while (pos >=0) {
            val originalText=fetchUntilClosingSpan(updatedHtml.substring(pos+ident.length)) //must be within range by definition
            val replacedText=originalText.replace(Regex("(\\r\\n|\\n|\\r)"),"<br>") //not ()+ since I like linebreak for each \n more in display
            if(originalText!=replacedText){
                updatedHtml=updatedHtml.substring(0, pos+ident.length) + replacedText + updatedHtml.substring(pos+ident.length+originalText.length)
            }
            pos = updatedHtml.indexOf(ident, pos+ident.length + originalText.length)
        }
        return updatedHtml
    }
    fun replaceTag(html: String, tag: String, startReplace: String, endReplace: String):String{
        var ret=html
        var pos = ret.indexOf(tag)
        var tagEndPos=ret.indexOf(">", pos+1)+1

        while (pos >= 0) {
            val tex = ret.substring(tagEndPos)
            var innerHtml = fetchUntilClosingSpan(tex)
            if(innerHtml.contains(tag))
                innerHtml= replaceTag(innerHtml, tag, startReplace, endReplace)
            val replacedInnerHtml = startReplace + innerHtml + endReplace
            ret=ret.substring(0,pos)+replacedInnerHtml+ret.substring(tagEndPos+innerHtml.length)
//            ret = ret.replace(tex, replacedInnerHtml)
            pos = ret.indexOf(tag, tagEndPos+innerHtml.length)
            tagEndPos=ret.indexOf(">", pos+1)+1
        }
        return ret
    }
}