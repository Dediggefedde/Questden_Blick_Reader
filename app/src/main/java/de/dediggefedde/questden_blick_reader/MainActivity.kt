package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import android.util.AttributeSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.io.File

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
 * Main activity
 * So far only activity
 * user interaction. Trying to implement MVVM Model
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: DataViewModel
    private var chronic = mutableListOf<Navis>()
    private var mainMenu: Menu? = null
    private lateinit var binding: ActivityMainBinding
//    private lateinit var bindingMainFrag: FragmentMainBinding
    private var currentFragment :Fragment?=null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        backpressed()
    }

    private fun backpressed() {//TODO test & rework
        if (chronic.size == 0) return
        chronic.removeAt(chronic.lastIndex)
        if (chronic.size == 0) return

        val nav = chronic[chronic.lastIndex]
        when (nav.operation) {
            NavOperation.LINK -> {
                if (nav.navStat != null)
                    (currentFragment as? MainFragment)?.backFromLink(nav.navStat)
            }
            NavOperation.PAGE -> {
                if (nav.prop.isNotEmpty() && viewModel.sets.curURL != nav.prop)
                    viewModel.loadThread(nav.prop, ThrdItemTyps.THREAD)
            }

            NavOperation.THREAD -> {
            }
        }
    }

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

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Optional: Zum Back-Stack hinzufügen
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val offlFolder = File(applicationContext.filesDir, "offline")
        if (!offlFolder.exists()) offlFolder.mkdirs()

        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this).get(DataViewModel::class.java)

        setContentView(binding.root)

        currentFragment=MainFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, currentFragment as MainFragment)
            .commit()

        addObservers()
        navigationStuff()
        loadData()

        onBackPressedDispatcher.addCallback(this) {
            backpressed()
        }

        AppUpdater(this)
            .setUpdateFrom(UpdateFrom.JSON)
            // .setGitHubUserAndRepo("Dediggefedde", "Questden_Blick_Reader")
            .setUpdateJSON("""https://raw.githubusercontent.com/Dediggefedde/Questden_Blick_Reader/WIP/app/version.json""") //TODO WIP to master
            .start()


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_sorting, menu)
        mainMenu = menu
        return true
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

            R.id.menu_reader_sync -> {
                currentFragment=SyncFragment()
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
    fun viewImage(mtg:TgPost){
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.viewImage(mtg)
    }
    fun repeatScroll(){
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.repeatScroll()
    }
    fun scrollHighlight(i:Int){
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.scrollHighlight(i)
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
                    handleError(this.applicationContext,"Backup error",
                        e.message?:"Unknown error at exporting backup",
                        e.stackTraceToString(), viewModel)
                }
            }

            3 -> {
                //load backup file dialog choose file return
                try {
                    importFile(data?.data)
                } catch (e: IOException) {
                    handleError(this.applicationContext,"Backup error",
                        e.message?:"Unknown error at loading backup",
                        e.stackTraceToString(), viewModel)
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainFragment
        fragment?.listAdapt?.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
        fragment?.listAdapt?.notifyDataSetChanged()
        viewModel.loadData() //calls loadBoard with current settings
    }

    private fun storeData() {
        viewModel.storeData()
    }
    private fun addObservers(){
        viewModel.displayList.observe(this) { list ->
            list?.let {
                binding.toolbar.title = viewModel.sets.curTitle
            }
        }
        viewModel.showListDemand.observe(this){ state->
            if(state && currentFragment !is MainFragment){
                currentFragment=MainFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, currentFragment as MainFragment)
                    .commit()
            }
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