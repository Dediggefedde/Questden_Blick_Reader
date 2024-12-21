package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.chrisbanes.photoview.PhotoView
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.dediggefedde.questden_blick_reader.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

/**
 *  TODO:
 *    - Button with link to Wiki if found
 *    - onboarding images
 *    - documentation
 *    - merge into main git branch
 *  */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: DataViewModel
    private lateinit var replyModel: ReplyViewModel
    private var mainMenu: Menu? = null
    private lateinit var binding: ActivityMainBinding

    //    private lateinit var bindingMainFrag: FragmentMainBinding
    private var currentFragment: Fragment? = null

    fun toggleToolbarVisibility(toShow: Boolean? = null) {
        val show = toShow ?: (binding.toolbar.visibility == View.GONE)
        (currentFragment as? MainFragment)?.showToolbar(show)

        if (show) {
            binding.toolbar.visibility = View.VISIBLE
        } else {
            binding.toolbar.visibility = View.GONE
        }


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

        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this).get(DataViewModel::class.java)
        replyModel = ViewModelProvider(this).get(ReplyViewModel::class.java)

        currentFragment = MainFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, currentFragment as MainFragment)
            .commitNow()

        setContentView(binding.root)

        addObservers()
        navigationStuff()
        loadData()

        val offlFolder = File(applicationContext.filesDir, "offline")
        if (!offlFolder.exists()) { //first run
            intro()
            offlFolder.mkdirs()
        }

        val preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var password = preferences.getString("reply_password", null)
        if (password == null) {
            password = generateRandomPassword()
            preferences.edit().putString("reply_password", password).apply()
        }

        val errorHandler = GlobalErrorHandler(Thread.getDefaultUncaughtExceptionHandler(), this)
        Thread.setDefaultUncaughtExceptionHandler(errorHandler)

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.backLinkStack.isNotEmpty()) {
                val id = viewModel.backLinkStack.pop()
                val pos = viewModel.getPositionById(id)
                scrollHighlight(pos, backwards = true)
            } else if (viewModel.backActionStack.isNotEmpty()) {
                val state = viewModel.backActionStack.pop()
                viewModel.loadThread(state.url, state.type, backwards = true)
            } else if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                currentFragment = MainFragment()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Close App")
                    .setMessage("Do you want to close the app?")
                    .setPositiveButton("Yes") { _, _ -> finish() } // App schließen
                    .setNegativeButton("No", null) // Nichts tun
                    .show()
            }
        }
        checkForErrorFiles()

        AppUpdater(this)
            .setUpdateFrom(UpdateFrom.JSON)
            // .setGitHubUserAndRepo("Dediggefedde", "Questden_Blick_Reader")
            .setUpdateJSON("""https://raw.githubusercontent.com/Dediggefedde/Questden_Blick_Reader/WIP/app/version.json""") //TODO WIP to master
            .start()
    }
    fun generateRandomPassword(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
    private fun intro() {
        binding.viewPagerContainer.visibility = View.VISIBLE

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val images = listOf(
            R.drawable.onboarding_00_welcome1,
            R.drawable.onboarding_01_navigation,
            R.drawable.onboarding_02_boards,
            R.drawable.onboarding_03_thread,
            R.drawable.onboarding_04_toolbar,
            R.drawable.onboarding_05_imagemode,
            R.drawable.onboarding_06_reply,
            R.drawable.onboarding_07_watchlist_downloaded,
            R.drawable.onboarding_08_sync
        )

        val adapter = ImagePagerAdapter(images)
        viewPager.adapter = adapter

        val tabTitle= listOf("Welcome","Navi","Board","Thread","Tools","Image","Reply","Watch","Sync")

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            if(position in tabTitle.indices)
                tab.text=tabTitle[position]
            else
                tab.text = "${position + 1}"
        }.attach()

        binding.closeButton.setOnClickListener {
            binding.viewPagerContainer.visibility = View.GONE
        }

    }

    private fun showInfoDialog(infoText: String) {
        val textView = TextView(this).apply {
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(infoText, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(infoText)
            }

            movementMethod = LinkMovementMethod.getInstance() // Links anklickbar machen
            setPadding(32, 32, 32, 32) // Abstand
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        AlertDialog.Builder(this)
            .setTitle("About")
            .setView(scrollView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun checkForErrorFiles() {
        val errorFile = File(applicationContext.filesDir, "tmp_error_log.txt")
        if (errorFile.exists()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Restart after crash")
                .setMessage("An error report was saved for the last application crash. Do you want to process it? 'No' will delete the report.")
                .setPositiveButton("Yes") { _, _ ->
                    handleError(this, "Crash Report", errorFile.readText(), getCurrentStackTrace(), viewModel)
                    errorFile.renameTo(File(applicationContext.filesDir, "error_log.txt"))
                }
                .setNegativeButton("No") { _, _ ->
                    errorFile.delete()
                }
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_deleting, menu)
        mainMenu = menu
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val curFragIsMain = (currentFragment is MainFragment)
        menu.findItem(R.id.menu_delete_reading).setVisible(curFragIsMain)
        menu.findItem(R.id.menu_delete_offline).setVisible(curFragIsMain && viewModel.sets.listType == ThrdItemTyps.OFFLINE)
        menu.findItem(R.id.menu_delete_Watch).setVisible(curFragIsMain && viewModel.sets.listType == ThrdItemTyps.WATCH)
        menu.findItem(R.id.menu_reply).setVisible(curFragIsMain && viewModel.sets.listType == ThrdItemTyps.THREAD)
        menu.findItem(R.id.menu_clear_form).setVisible(currentFragment is ReplyFragment)

        return super.onPrepareOptionsMenu(menu)
    }

    fun openWiki(){
        currentFragment = WikiFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, currentFragment as WikiFragment)
            .addToBackStack(null)
            .commit()
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_reply -> {
                currentFragment = ReplyFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, currentFragment as ReplyFragment)
                    .addToBackStack(null)
                    .commit()
                true
            }

            R.id.menu_delete_reading -> {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Reading Status")
                    .setMessage("Do you want to delete the reading status of all your threads?")
                    .setPositiveButton("Yes") { _, _ -> viewModel.deleteLastRead() } // App schließen
                    .setNegativeButton("No", null) // Nichts tun
                    .show()
                true
            }

            R.id.menu_clear_form->{
                replyModel.clear()
                showMainList()
                true
            }

            R.id.menu_delete_offline -> {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Downloaded Data")
                    .setMessage("Do you want to delete all your downloaded threads?")
                    .setPositiveButton("Yes") { _, _ -> viewModel.deleteOfflineData() } // App schließen
                    .setNegativeButton("No", null) // Nichts tun
                    .show()
                true
            }

            R.id.menu_delete_Watch -> {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Downloaded Data")
                    .setMessage("Do you want to remove all threads from your watch list?")
                    .setPositiveButton("Yes") { _, _ -> viewModel.deleteWatchData() } // App schließen
                    .setNegativeButton("No", null) // Nichts tun
                    .show()
                true
            }

            R.id.menu_intro -> {
                intro()
                true
            }

            R.id.menu_about -> {
                val preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val password = preferences.getString("reply_password", null)?:""

                showInfoDialog(
                    """
                    <h3>Questden Blick Reader</h3>
                    <p><b>Developer</b>: Julian Bergmann</p>
                    <p><a href="https://phi.pf-control.de/tgchan/reg.php">Account registration / privacy policy</a></p>
                    <p><a href="https://github.com/Dediggefedde/Questden_Blick_Reader">GitHub: Questden Blick Reader</a>
                    <p></p>
                    <p>Reply Password: $password</p>
                    <p>Vielen Dank für die Nutzung meiner App!</p>
                """.trimIndent()
                )
                true
            }

            R.id.menu_license -> {
                showInfoDialog(
                    """
                    <h3>License</h3>
                    <p>Published using the Apache License, Version 2.0</p>
                    
                    <h3>Libraries Used:</h3>
                    <ul>
                        <li><b>Kotlin Standard Library</b> - Apache License, Version 2.0</li>
                        <li><b>AndroidX Core</b> - Apache License, Version 2.0</li>
                        <li><b>AndroidX AppCompat</b> - Apache License, Version 2.0</li>
                        <li><b>Volley</b> - Apache License, Version 2.0</li>
                        <li><b>Gson</b> - Apache License, Version 2.0</li>
                        <li><b>Glide</b> - BSD-2-Clause License</li>
                        <li><b>JSoup</b> - MIT License</li>
                        <li><b>OkHttp</b> - Apache License, Version 2.0</li>
                        <li><b>PhotoView</b> - Apache License, Version 2.0</li>
                        <li><b>AppUpdater</b> - MIT License</li>
                        <li><b>Room</b> - Apache License, Version 2.0</li>
                        <li><b>ViewPager2</b> - Apache License, Version 2.0</li>
                    </ul>
                """.trimIndent()
                )
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

    private fun navigateToBoard(url: String, type: ThrdItemTyps) {
        viewModel.loadThread(url, type)
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        viewModel.sets.boardPage = 0 //no liveview connected

        when (menuItem.itemId) {
            R.id.menu_draw -> navigateToBoard(URLBoards.DRAW.url, ThrdItemTyps.BOARD)
            R.id.menu_general -> navigateToBoard(URLBoards.MEEP.url, ThrdItemTyps.BOARD)
            R.id.menu_quest -> navigateToBoard(URLBoards.QUEST.url, ThrdItemTyps.BOARD)
            R.id.menu_questdis -> navigateToBoard(URLBoards.QUESTDIS.url, ThrdItemTyps.BOARD)
            R.id.menu_tg -> navigateToBoard(URLBoards.TG.url, ThrdItemTyps.BOARD)
            R.id.menu_watch_open -> {
                viewModel.loadThread("", ThrdItemTyps.WATCH)
            }

            R.id.menu_offline_open -> {
                navigateToBoard("", ThrdItemTyps.OFFLINE)
            }

            R.id.menu_reader_sync -> {
                currentFragment = SyncFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, currentFragment as SyncFragment)
                    .addToBackStack(null) // Backstack für Zurück-Taste
                    .commit()
            }

            R.id.menu_reader_backup -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)

                val c: Calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
                val strDate: String = sdf.format(c.time)

                intent.type = "application/json" //not needed, but maybe usefull
                intent.putExtra(Intent.EXTRA_TITLE, "questden_backup_$strDate.json") //not needed, but maybe usefull

                @Suppress("DEPRECATION")
                startActivityForResult(intent, 2)
            }

            R.id.menu_reader_restore -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "application/json" //not needed, but maybe usefull

                @Suppress("DEPRECATION")
                startActivityForResult(intent, 3)
            }

        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun viewImage(mtg: TgPost) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.viewImage(mtg)
    }

    fun repeatScroll() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.repeatScroll()
    }

    fun scrollHighlight(i: Int, backwards: Boolean = false) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.scrollHighlight(i, backwards)
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
            MsgHelper.showMsg(this, "Done")
        } catch (e: IOException) {
            e.printStackTrace()
            MsgHelper.showMsg(this, "Failed")
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
                MsgHelper.showMsg(this, "Wrong format")
                return
            }

            val entryList = gson.fromJson<List<TgPost>>(gson.toJson(li[0]), object : TypeToken<List<TgPost>>() {}.type)
            val watchList = gson.fromJson<MutableList<Watch>>(gson.toJson(li[1]), object : TypeToken<MutableList<Watch>>() {}.type)
            val offList = gson.fromJson<MutableList<OfflineThread>>(gson.toJson(li[2]), object : TypeToken<MutableList<OfflineThread>>() {}.type)
            val modsets = gson.fromJson<ModelSettings>(gson.toJson(li[3]), object : TypeToken<ModelSettings>() {}.type)

            viewModel.importSettings(entryList, watchList, offList, modsets)

            MsgHelper.showMsg(this, "Done")
            viewModel.loadCurThread() // (sets.curpage, sets.curSingle)
        } catch (e: IOException) {
            e.printStackTrace()
            MsgHelper.showMsg(this, "Failed")
        }
    }

    @Deprecated("for minsdk 16")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            2 -> {//Export Backup
                //save backup file dialog choose file return
                try {
                    exportFile(data?.data)
                } catch (e: IOException) {
                    handleError(
                        this, "Backup error",
                        e.message ?: "Unknown error at exporting backup",
                        e.stackTraceToString(), viewModel
                    )
                }
            }

            3 -> {
                //load backup file dialog choose file return
                try {
                    importFile(data?.data)
                } catch (e: IOException) {
                    handleError(
                        this, "Backup error",
                        e.message ?: "Unknown error at loading backup",
                        e.stackTraceToString(), viewModel
                    )
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.listAdapt.let {
            fragment?.listAdapt?.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
            fragment?.listAdapt?.notifyDataSetChanged()
        }
        viewModel.loadData() //calls loadBoard with current settings
    }

    private fun storeData() {
        viewModel.storeData()
    }

    private fun addObservers() {
        viewModel.displayList.observe(this) { list ->
            list?.let {
                binding.toolbar.title = viewModel.sets.curTitle
            }
        }
        viewModel.showListDemand.observe(this) { state ->
            if (state ) showMainList()
        }
    }
    fun showMainList(){
        if(currentFragment is MainFragment)return
        currentFragment = MainFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, currentFragment as MainFragment)
            .commitNow()
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

class ImagePagerAdapter(private val images: List<Int>) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {
    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: PhotoView = itemView.findViewById(R.id.sliderImg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.slider_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.imageView.setImageResource(images[position])
    }

    override fun getItemCount(): Int = images.size
}


object MsgHelper {
    private var currentToast: Toast? = null

    fun showMsg(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, message, duration).apply {
            show()
        }
    }
}