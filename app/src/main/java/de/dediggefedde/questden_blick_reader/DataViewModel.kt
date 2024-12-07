package de.dediggefedde.questden_blick_reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
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
import java.io.PrintWriter
import java.io.StringWriter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

//import kotlin.math.log


//saving/loading data
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

class DataViewModel(application: Application) : AndroidViewModel(application) {
    private var entryListRaw = listOf<TgPost>() //all entries/posts that can be displayed
    private var entryListImg = listOf<TgPost>() //only entries with images
    private var watchlist = mutableListOf<Watch>()
    private var offlineList = mutableListOf<OfflineThread>() //list of offline available threads (first posts)

    var logState=MutableLiveData<LoginState>()
    private val serverURL = "https://phi.pf-control.de/tgchan/API.php"
    var showListDemand=MutableLiveData<Boolean>()

    var sets: ModelSettings = ModelSettings()
    private val dataRepository = DataRepository(application)
    private var reqProg: ProgData = ProgData() //http requests progress
    private var downProg: ProgData = ProgData() //download progress
    private val client = OkHttpClient()

    val backActionStack=Stack<backPage>()
    val backLinkStack=Stack<String>()

    var highLightInd = -1 //number index of highlighted item
    var fromOffline = false //current thread loaded from offline data
    var fullViewImg:TgPost?=null //shows image on click when not null

    private var _displayList = MutableLiveData<List<TgPost>>()
    val displayList: LiveData<List<TgPost>> get() = _displayList
    private val _setsLiveData = MutableLiveData<ModelSettings>()
    val setsLiveData: LiveData<ModelSettings> get() = _setsLiveData
    private val _downProgLive = MutableLiveData<ProgData>()
    val downProgLive: LiveData<ProgData> get() = _downProgLive
    private val _reqProgLive = MutableLiveData<ProgData>()
    val reqProgLive: LiveData<ProgData> get() = _reqProgLive

    init {
        _setsLiveData.value = sets
        _downProgLive.value = downProg
        _reqProgLive.value = reqProg
        logState.value=LoginState()
    }

    fun updateSet() {
        _setsLiveData.postValue(sets.copy())
    }

    private fun updateDownPrg() {
        _downProgLive.postValue(downProg.copy())
    }

    private fun updateReqPrg() {
        _reqProgLive.postValue(reqProg.copy())
    }

    fun getSafeUserSettings(): String {
        val safeSettings = sets.copy(
            loginName = "", // Leeren String setzen
            loginPW = ""    // Leeren String setzen
        )
        // Mit Gson serialisieren
       return Gson().toJson(safeSettings)
    }

    fun serializeWatchList(): String = Gson().toJson(watchlist)
    fun serializeDownloadList(): String = Gson().toJson(offlineList)

    fun getDisplayListSize(): Int = _displayList.value?.size ?: 0
    fun getImageListSize(): Int = this.entryListImg.size
    fun getItemImageCnt(index: Int): Int= _displayList.value?.get(index)?.imgCounter ?: 0

    private fun updateCurReadId(thread: String, postId: String) {
        if (postId == "" || thread == "") return
        sets.curReadPostID[thread] = postId
        updateSet()
    }

    fun updateCurReadId(index: Int) {
        val currentList = _displayList.value
        if (sets.curThreadId.isNotEmpty() && currentList != null && hasIndex(index)) {
            val postId = currentList[index].postID
            sets.curReadPostID[sets.curThreadId] = postId
            updateSet()
        }
    }

    fun deleteLastRead(){
        sets.curReadPostID.clear()
        storeData()
        updateSet()
    }
    fun getLastReadIndex(): Int {
        val currentList = _displayList.value
        val lastid = sets.curReadPostID[sets.curThreadId]
        if (currentList.isNullOrEmpty() || lastid.isNullOrEmpty()) return 0
        return currentList.indexOfFirst { it.postID == lastid }
    }

    private fun showWatches() {
        sets.listType = ThrdItemTyps.WATCH
        updateSet()

        entryListRaw = watchlist.map {
            it.thread
        }.toList()
        entryListImg = entryListRaw
        updateDisplayList()
    }

    private fun showOfflines() {
        sets.listType = ThrdItemTyps.OFFLINE
        updateSet()

        entryListRaw = offlineList.map {
            it.thread
        }.toList()
        entryListImg = entryListRaw
        updateDisplayList()
    }

    fun storeData() {
        if(!sets.autoLogin){
            sets.loginName=""
            sets.loginPW=""
        }
        dataRepository.saveData("tgchanItems", entryListRaw)
        dataRepository.saveData("watchItems", watchlist)
        dataRepository.saveData("modelSettings", sets)
        dataRepository.saveData("offlineList", offlineList)
    }

    fun loadData() {
        entryListRaw = dataRepository.loadData("tgchanItems", object : TypeToken<List<TgPost>>() {}.type) ?: entryListRaw
        watchlist = dataRepository.loadData("watchItems", object : TypeToken<MutableList<Watch>>() {}.type) ?: watchlist
        offlineList = dataRepository.loadData("offlineList", object : TypeToken<MutableList<OfflineThread>>() {}.type) ?: offlineList
        sets = dataRepository.loadData("modelSettings", object : TypeToken<ModelSettings>() {}.type) ?: sets
        updateSet()
        if(entryListRaw.isEmpty()){ //default page
            loadThread(URLBoards.QUEST.url, ThrdItemTyps.BOARD)
        }else {
            postProcessRawList()
            updateDisplayList()
        }
    }

    fun toggleWatch(threadId: String=sets.curThreadId) {
        if (getWatched(threadId) != null) {
            removeFromWatch(threadId)
        } else {
            addToWatch(threadId)
        }
    }

    private fun addToWatch(threadId: String) {
        if (getWatched(threadId) != null) return
        val tg=displayList.value?.firstOrNull { it.postID==threadId }
        if(tg===null) return
        watchlist.add(Watch(tg))
        loadThread(tg.url,ThrdItemTyps.THREAD,true)
        storeData()
    }

    private fun removeFromWatch(threadId: String) {
        watchlist.removeAll(watchlist.filter { it.thread.postID == threadId })
        storeData()
    }
    fun deleteWatchData(){
        watchlist.clear()
        storeData()
        updateWatchlist()
    }

    fun getWatched(threadId: String=sets.curThreadId): Watch? = watchlist.firstOrNull { it.thread.postID == threadId }
    fun exportSetting(): List<Any> = listOf(entryListRaw, watchlist, offlineList, sets)

    fun getPositionById(postID: String):Int = displayList.value?.indexOfFirst{ it.postID==postID }?:-1

    fun importSettings(entryList: List<TgPost>, watlist: MutableList<Watch>, offList: MutableList<OfflineThread>, set: ModelSettings) {
        entryListRaw = entryList
        watchlist = watlist
        offlineList = offList
        sets = set
        storeData()
        updateSet()
        loadCurThread()
    }

    private fun importWatchlist(watlist: MutableList<Watch>) {
        watchlist = watlist
        storeData()
        updateWatchlist() //fetches images and summaries
    }

    private fun loadFromOffline(): Boolean { //checks current sets to exist in download
        val htmlFile = File(getApplication<Application>().filesDir, "offline/${sets.curThreadId}.html")

        if (!htmlFile.exists()) return false
        val gson = Gson()
        val json = htmlFile.readText()
        val listType = object : TypeToken<List<TgPost>>() {}.type
        entryListRaw = gson.fromJson(json, listType)

        postProcessRawList()
        sets.curTitle = entryListRaw.first().title
        sets.curURL = entryListRaw.first().url
        sets.curThreadId = Regex("""(\d+).html""").find(entryListRaw.first().url)?.groupValues?.get(1) ?: ""

        fromOffline = true
        updateSet()

        return true
    }

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

    fun deleteOfflineData(){
        val offImgPath = File(getApplication<Application>().filesDir, "offline")
        deleteDirectory(offImgPath)
        offImgPath.mkdirs()
        offlineList.clear()
        storeData()
        updateSet()
        loadCurThread()
    }

    fun deleteOffline(threadId:String=sets.curThreadId) {

        val htmlFile = File(getApplication<Application>().filesDir, "offline/${threadId}.html")
        htmlFile.delete() //remove html file

        //remove image folder
        val offImgPath = File(getApplication<Application>().filesDir, "offline/${threadId}_img")
        if (offImgPath.exists()) deleteDirectory(offImgPath)

        //remove entry
        val index = offlineList.indexOfFirst { it.thread.postID == threadId }
        if (index >= 0) offlineList.removeAt(index)
    }

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

    fun downloadImages(threadId: String, onlyThumb: Boolean) {
        val offImgPath = File(getApplication<Application>().filesDir, "offline/${threadId}_img")
        if (!offImgPath.exists()) offImgPath.mkdirs()


        var downloadlist = entryListImg.map { it.imgUrl }
        if (!onlyThumb) {
            downloadlist = entryListImg.flatMap { thread ->
                listOf(
                    thread.imgUrl,
                    thread.imgUrl.replace("thumb", "src").replace("s.", ".")
                )
            }
        }
        downloadlist = downloadlist.filter {
            val file = File(offImgPath, it)
            !file.exists()
        }

        viewModelScope.launch {
            downloadImgList(downloadlist, offImgPath)
        }
    }

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
                        if(file.exists())return@forEach //skip images already downloaded

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

    fun setHighlight(index: Int) {
        if (_displayList.value != null && hasIndex(index)) {
            if (highLightInd >= 0 && hasIndex(highLightInd)) _displayList.value!![highLightInd].isHighlight = false
            highLightInd = index
            _displayList.value!![index].isHighlight = true
        }
    }

    fun getDownload(id: String): OfflineThread? {
        return offlineList.firstOrNull { it.thread.postID == id }
    }

    fun isDownloaded(id: String = sets.curThreadId): Boolean {
        return offlineList.any { it.thread.postID == id }
    }

    fun hasIndex(index: Int): Boolean {
        return index >= 0 && index < (_displayList.value?.size ?: 0)
    }

    fun updateDisplayList() {
        _displayList.postValue(if (sets.showOnlyPics) entryListImg else entryListRaw)
    }

    fun rotateSFW() {
        sets.sfw = when (sets.sfw) {
            SFWModes.SFWQUESTION -> SFWModes.SFWREAL
            SFWModes.SFWREAL -> SFWModes.NSFW
            SFWModes.NSFW -> SFWModes.SFWQUESTION
        }
        updateSet()
    }
    fun rotateImgMode(){
        sets.imageMode= when (sets.imageMode) {
            imgMode.SMALL -> imgMode.BIG
            imgMode.BIG -> imgMode.FULL
            imgMode.FULL -> imgMode.SMALL
        }
        updateSet()
    }


    fun getNextPos(index: Int, mode: ScrollMode): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        return if (mode == ScrollMode.IMAGES) {
            index + 1 + list.subList(index + 1, getDisplayListSize()).indexOfFirst { it.imgUrl.isNotEmpty() }
        } else {
            index + 1
        }
    }

    fun getPrevPos(index: Int, mode: ScrollMode, withinCur: Boolean): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        val sameType = (mode == ScrollMode.IMAGES) == (list[index].imgUrl.isNotEmpty())
        if (sameType && withinCur) return index
        return if (mode == ScrollMode.IMAGES) {
            list.subList(0, index).indexOfLast { it.imgUrl.isNotEmpty() }
        } else {
            index - 1
        }
    }

    fun getLastPos(index: Int, mode: ScrollMode): Int {
        val list = _displayList.value
        if (list.isNullOrEmpty() || index >= getDisplayListSize()) return -1
        return if (mode == ScrollMode.IMAGES) list.indexOfLast { it.imgUrl.isNotEmpty() } else list.lastIndex
    }

    fun navigatePage(page: Int, rel: Boolean = false) {
        val npage = if (rel) sets.boardPage + page else page
        if (sets.listType == ThrdItemTyps.WATCH || npage < 0 || npage > sets.curMaxPage) return
        sets.boardPage = npage
        updateSet()
        loadCurThread()
    }
    fun getIdbyUrl(url:String):String = Regex("""(\d+).html""").find(url)?.groupValues?.get(1) ?: ""

    /**
     * requests https://questden.org + relative url, expecting it to be a single thread
     * regex is used to parse this into displayDataList
     * calls displayThreadList() then to refresh recycleViewer
     * watchlist count update if watched
     * storedata to open again on start +watchlist save)
     *
     * complex method since none of these parts is repeated somewhere else.
     */
    fun loadCurThread() {//reload current.
        if (sets.listType == ThrdItemTyps.WATCH) updateWatchlist()
        else loadThread(sets.curURL, sets.listType)
    }

    fun loadThread(url: String, mode: ThrdItemTyps, onlyCheckWatch: Boolean = false, updOffline: Boolean = false,backwards:Boolean=false) {
        var murl = url
        val fet = murl.indexOf("#")
        if (fet >= 0) murl = murl.substring(0, fet)

        //weird bug where board has normal url -> change type
        if (url != "" && mode == ThrdItemTyps.BOARD && !URLBoards.entries.any { it.url == url }) {
            loadThread(url, ThrdItemTyps.THREAD, onlyCheckWatch, updOffline)
            return
        }

        val threadId = getIdbyUrl(murl)
        val watchedItem:Watch? = getWatched(threadId)

        if(!onlyCheckWatch && !updOffline && !backwards)
            backActionStack.add(backPage(sets.listType,sets.curURL))

        reqProg.max++
        reqProg.status = ProgStatus.RUNNING
        fromOffline = false
        updateReqPrg()

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
                showOfflines()
                storeData()
                afterUpdateReq()
                return
            }

            ThrdItemTyps.WATCH -> { //list watched quests
                sets.curTitle = "Watch list"
                sets.curThreadId = ""
                sets.curMaxPage = 0
                sets.boardPage = 0
                showWatches()
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

                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val errorMessage = e.message ?: "Unknown Error"
                reqProg.msg = "creating download Coroutine: $errorMessage\nStackTrace:\n$sw"
                updateReqPrg()
            }
        }
    }

    private suspend fun makeNetRequest(murl: String, mode: ThrdItemTyps, onlyCheckWatch: Boolean, updOffline: Boolean): Unit = withContext(Dispatchers.IO) {
        val watchedItem = getWatched(getIdbyUrl(murl))
        val newestId = if (onlyCheckWatch) watchedItem?.lastReadId else null
        val request = Request.Builder().url("https://questden.org$murl").build()

        client.newCall(request).execute().use { response ->
            try {
                reqProg.pos += 1
                if (!response.isSuccessful) throw Exception("Request to $murl failed.")

                val resp = response.body?.string() ?: throw Exception("Empty response body.")
                val li = when (mode) {
                    ThrdItemTyps.THREAD -> parseThreadMode(resp, newestId)
                    ThrdItemTyps.BOARD -> parseBoardMode(resp)
                    ThrdItemTyps.WATCH -> throw Exception("WATCH mode not supported in MakeNetRequest")
                    ThrdItemTyps.OFFLINE -> throw Exception("OFFLINE mode not supported in MakeNetRequest")
                }

                processParsedData(li, murl, mode, watchedItem, onlyCheckWatch) //sets storage to results

                if (updOffline) updateOfflineMode() //updates offline data

                storeData() //stores current view and settings

                if (reqProg.pos == reqProg.max) afterUpdateReq()
            } catch (e: Exception) {
                reqProg.status = ProgStatus.ERROR

                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val errorMessage = e.message ?: "Unknown Error"
                reqProg.msg = "Error: $errorMessage\nStackTrace:\n$sw"

                updateReqPrg()
                afterUpdateReq()
            }
        }
    }

    private fun countOccurrences(text: String, search: String): Int {
        var count = 0
        var index = text.indexOf(search)

        while (index >= 0) {
            count++
            index = text.indexOf(search, index + 1)
        }

        return count
    }

    private fun parseThreadMode(resp: String, newestId: String?): MutableList<TgPost> {
        if (newestId != null) { // Neuste Beiträge anzeigen
            val startIdx = resp.indexOf("""id="reply$newestId"""")
            val trimmedResp = if (startIdx > 0) resp.substring(startIdx) else resp
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
            if(threadInfo!==null){
                if(threadInfo.title.isEmpty())threadInfo.title="Untitled"
                threadInfo.postCount=countOccurrences(resp,"<blockquote>")
            }


            if (posts.isEmpty()) { //nothing new
                return if(threadInfo == null )
                    mutableListOf() // Leere Liste, falls keine Info-Einträge gefunden
                else
                    mutableListOf(threadInfo)
            }else {
                threadInfo?.let { posts.add(it) }
                return posts
            }
        } else {// Thread anzeigen, wenn keine neue ID vorhanden
            val doc = Jsoup.parse(resp)
            val tmpLi= doc.select("#delform,#delform>table").map { parseJSoupToTgThread(it) }
                .filter { it.postID.isNotEmpty() }
                .toMutableList()
            if(tmpLi.first().title.isEmpty())tmpLi.first().title="Untitled"
            return tmpLi
        }
    }

    // Helper function to parse BOARD mode
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

            ThrdItemTyps.WATCH -> throw Exception("WATCH not supported in processParsedData")
            ThrdItemTyps.OFFLINE -> throw Exception("OFFLINE not supported in processParsedData")
        }
    }

    private fun preloadThumbnails() {
        val context = getApplication<Application>().applicationContext
        Glide.with(context)
            .load(entryListImg.map { it.imgUrl })  // Liste der URLs für die Thumbnails
            .preload()  // Bilder werden im Hintergrund vorab geladen
    }

    private fun handleWatchOnlyMode(li: MutableList<TgPost>, watchedItem: Watch?) {
        if(li.size==0)return
        val inf = li.removeAt(li.lastIndex)
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

    private fun postProcessRawList() {
        var imageCount = 0
        entryListRaw.forEach { thread ->
            if (thread.imgUrl.isNotEmpty()) {
                imageCount++
            }
            thread.imgCounter = imageCount  // Bildzähler in jedem Eintrag speichern
            thread.threadExtended = false //preview extended in overview
        }
        entryListImg = entryListRaw.filter { it.imgUrl.isNotEmpty() }
    }

    private fun handleBoardMode(li: MutableList<TgPost>) {
        sets.curTitle = sets.curURL
        sets.curThreadId = ""

        val inf = li.removeAt(li.lastIndex)
        sets.curMaxPage = inf.summary.toIntOrNull() ?: 0

        entryListRaw = li
        entryListImg = entryListRaw // Board Mode: Keine Filterung auf Bilder
    }


    // Function to handle offline updates
    private fun updateOfflineMode() {
        val onlyThumb = offlineList.firstOrNull { it.thread.postID == sets.curThreadId }?.onlyThumbs ?: false
        writeToOffline(sets.curThreadId, onlyThumb)
        downloadImages(sets.curThreadId,onlyThumb)
    }

    private fun updateWatchlist() {
        for (w in watchlist) {
            loadThread(w.thread.url, ThrdItemTyps.THREAD, onlyCheckWatch = true)
        }
        if (watchlist.size == 0) afterUpdateReq()
    }

//Server interaction section

    fun setCredServer(name: String = sets.loginName, pw: String = sets.loginPW, saveLogin: Boolean = sets.autoLogin) {
        sets.loginName = name
        sets.loginPW = pw
        sets.autoLogin = saveLogin
    }

    private fun formatDate(unixTimestampS: Long = System.currentTimeMillis()/1000): String {
        val date = Date(unixTimestampS*1000)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(date)
    }

    fun loginServer() {
        val jsons = JSONObject().apply {
            put("username", sets.loginName)
            put("password", sets.loginPW)
            put("type", "test")
        }
        makePostRequest(jsons.toString(), onSuccess = { jsonstr ->
            if (jsonstr.has("token")) {
                logState.value?.token= jsonstr.getString("token")
                logState.value?.accessDate = jsonstr.optLong("access", 0)
                sets.loginName = jsonstr.getString("name")

                logState.value?.errorCode=0
                logState.value?.promptText=("Hello ${sets.loginName}!")
                logState.value?.statusText=(if (logState.value==null || logState.value?.accessDate == 0L) "No data yet" else "Data from ${formatDate(logState.value!!.accessDate)}")
            } else {
                logState.value?.errorCode=2
                logState.value?.token=""
                logState.value?.accessDate=0
                logState.value?.promptText="Login failed(#2)!"
                logState.value?.statusText="Login failed(#2)!"
            }
            logState.postValue(logState.value)//trigger observe
        })
    }

    fun downloadServer(){
        val jsons = JSONObject().apply {
            put("token", logState.value?.token)
            put("type", "download")
            put("obj","watchbar") //only watchbar data, not editor/sidebar settings
        }
        makePostRequest(jsons.toString(), onSuccess = { json ->
            val itemList= mutableListOf<Watch>()
            if(json.has("threads")){

                sets.numLinkMode=json.optInt("numLinkMode",0)

                val entriesArray = json.getJSONObject("threads").getJSONArray("value")
                for (i in 0 until entriesArray.length()) {
                    val post=TgPost()
                    val wat=Watch()
                    val entry = entriesArray.getJSONArray(i)
                    val key = entry.getString(0)   // Erstes Element des Arrays (key)
                    val value = entry.getJSONObject(1) // Zweites Element des Arrays (value)

                    post.postID=key
                    if(post.postID=="")continue

                    post.title=value.optString("label","Untitled")
                    post.url="/kusaba/${value.optString("section","quest")}/res/${key}.html"
                    post.author=value.optString("author","")

                    wat.thread=post
                    wat.lastReadId=value.optString("lastReadId","")

                    //unused in app, stored for upload
                    wat.highImgOnly=value.optBoolean("highImgOnly",true)
                    wat.highIDs=value.optString("highIDs","")
                    wat.highNames=value.optString("highNames","")
                    wat.ignoreIDs=value.optString("ignoreIDs","")
                    wat.ignoreNames=value.optString("ignoreNames","")

                    itemList.add(wat)
                    updateCurReadId(key,value.optString("currentReadId",""))
                }
                importWatchlist(itemList)

                logState.value?.errorCode=0
                logState.value?.promptText=("Download successfull!")
                logState.value?.statusText=(getApplication<Application>().getString(R.string.watched_threads_imported, itemList.size))
                logState.postValue(logState.value)//trigger observe
            }else{
                logState.value?.errorCode=3
                logState.value?.token=""
                logState.value?.accessDate=0
                logState.value?.promptText="Download failed(#3)!"
                logState.value?.statusText="Download failed(#3)!"
            }
            /*
            {
                "numLinkMode":0,
                "threads":{
                    "_type":"Map",
                    "value":[
                        ["1092522",
                     x       {"label":"History Unmade - Thread 3",
                     x       "author":"Silicon",
                     x       "section":"quest",
                            "highImgOnly":true,
                            "highIDs":[],
                            "highNames":[],
                            "ignoreIDs":[],
                            "ignoreNames":[],
                     x       "lastReadId":"1099138",
                     x       "currentReadId":"",
                            "newEntrCnt":0,
                            "totalEntrCnt":164}
                         ],
                  }
            }

        type threaddata = {
            label: string, //title
            author: string, //authorname
            section: string, //board string
            highImgOnly: boolean, //only highlight on new images
            highIDs: string[], //only highlight on these IDs
            highNames: string[], //only highlight on these names
            ignoreIDs: string[], //ignore posts from these IDs
            ignoreNames: string[], //ignore posts from these names
            lastReadId: string, //id of last read post
            currentReadId: string, //id of currently focues post
            newEntrCnt: number,// new posts detected at last query
            totalEntrCnt: number, //total number of posts
        }
        interface watchdataform {
            threads: Map<string, threaddata>, //id -> threaddata
            default: threaddata,
            numLinkMode: number, //0:new, 1:lastread, 2:none;
        };
	**/
        })
    }

    fun uploadServer(){
        // {"numLinkMode":0,"threads":{"_type":"Map","value":[["1092522",{"label":"History Unmade - Thread 3","author":"Silicon","section":"quest","highImgOnly":true,"highIDs":[],"highNames":[],"ignoreIDs":[],"ignoreNames":[],"lastReadId":"1099138","currentReadId":"","newEntrCnt":0,"totalEntrCnt":164}],}
        var data="""{"numLinkMode":${sets.numLinkMode},"threads":{"_type":"Map","value":["""
        watchlist.joinToString {
            val currentReadId=sets.curReadPostID[it.thread.postID]?:""

            """["${it.thread.postID}",{"label":"${it.thread.title}","author":"${it.thread.author}","section":"${it.thread.url.split("/")[2]}",
                |"highImgOnly":${it.highImgOnly},"highIDs":${it.highIDs},"highNames":${it.highNames},"ignoreIDs":${it.ignoreIDs},
                |"ignoreNames":${it.ignoreNames},"lastReadId":"${it.lastReadId}","currentReadId":"$currentReadId",
                |"newEntrCnt":${it.newPosts},"totalEntrCnt":${it.thread.postCount}}]""".trimMargin()
        }
        data+="""]}}"""

        val jsons = JSONObject().apply {
            put("token", logState.value?.token)
            put("type", "upload")
            put("data",data)
            put("obj","watchbar") //only watchbar data, not editor/sidebar settings
        }
        makePostRequest(jsons.toString(), onSuccess = { json ->
            if(json.has("access")){
                logState.value?.errorCode=0
                logState.value?.accessDate=json.optLong("access",0)
                logState.value?.promptText=("Uploaded complete!")
                logState.value?.statusText=(if (logState.value==null || logState.value?.accessDate == 0L) "Error fetching date" else "Data from ${formatDate(logState.value!!.accessDate)}")
                logState.postValue(logState.value)//trigger observe
            }
        })
    }
    private fun makePostRequest(jsonBody: String, onSuccess: (JSONObject) -> Unit) {

        val client = OkHttpClient()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverURL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

                logState.value?.promptText=( "Request failed: ${e.message}")
                logState.value?.statusText=("Failed: ${e.message}")
                logState.value?.errorCode=1
                logState.postValue(logState.value)//trigger observe
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val jsonResponse = JSONObject(responseBody) // JSON-Daten verarbeiten
                        onSuccess(jsonResponse)
                    }
                } else {
                    logState.value?.errorCode=response.code
                    logState.value?.promptText=( "Request unsuccessfull: ${response.code}")
                    logState.value?.statusText=( "Error: ${response.code}")
                    logState.postValue(logState.value)//trigger observe
                }
            }
        })
    }
}