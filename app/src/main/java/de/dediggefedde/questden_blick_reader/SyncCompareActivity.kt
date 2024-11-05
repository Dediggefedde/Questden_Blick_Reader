package de.dediggefedde.questden_blick_reader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import de.dediggefedde.questden_blick_reader.databinding.SyncBinding
import de.dediggefedde.questden_blick_reader.databinding.SyncCompareBinding


class SyncCompareActivity : AppCompatActivity() {
    private val listAdapt = SyncCompareListAdapter(emptyList(),emptyList())
    private var watchlist: MutableList<Watch>? = null
    private var newWatchlist: MutableList<Watch>? = null
    private lateinit var syncComBinding: SyncCompareBinding
    private lateinit var syncBinding: SyncBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncComBinding = SyncCompareBinding.inflate(layoutInflater)
        syncBinding = SyncBinding.inflate(layoutInflater)

        setContentView(syncComBinding.root)
        setSupportActionBar(syncBinding.synctoolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        syncComBinding.syncList.layoutManager = LinearLayoutManager(this)
        syncComBinding.syncList.adapter = listAdapt

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            watchlist= intent.getParcelableArrayListExtra("watchlist", Watch::class.java)?: arrayListOf()
            newWatchlist = intent.getParcelableArrayListExtra("newwatchlist", Watch::class.java)?: arrayListOf()
        } else {
            watchlist= intent.getParcelableArrayListExtra("watchlist")?: arrayListOf()
            newWatchlist = intent.getParcelableArrayListExtra("newwatchlist")?: arrayListOf()
        }

        listAdapt.itemsRemote= newWatchlist?.map{it.thread} ?: emptyList()
        listAdapt.itemsLocal=watchlist?.map{it.thread} ?: emptyList()

        onBackPressedDispatcher.addCallback(this){
            returnVals()
        }
    }

    private fun returnVals(){
        val data = Intent()
        data.putParcelableArrayListExtra("watchlist",ArrayList(watchlist!!))
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                returnVals()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { //not working...
        super.onBackPressed()
        returnVals()
    }
}