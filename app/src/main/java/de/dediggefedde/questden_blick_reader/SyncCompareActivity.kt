package de.dediggefedde.questden_blick_reader

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.sync_compare.*


class SyncCompareActivity : AppCompatActivity() {
    private val listAdapt = SyncCompareListAdapter(emptyList(),emptyList(), this)
    private var watchlist: MutableList<Watch>? = null
    private var newWatchlist: MutableList<Watch>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sync_compare)
        setSupportActionBar(findViewById(R.id.synctoolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)


        syncList.layoutManager = LinearLayoutManager(this)
        syncList.adapter = listAdapt

        watchlist= intent.getParcelableArrayListExtra("watchlist")
        newWatchlist= intent.getParcelableArrayListExtra("newwatchlist")

        listAdapt.items_remote= newWatchlist?.map{it.thread} ?: emptyList()
        listAdapt.items_local=watchlist?.map{it.thread} ?: emptyList()
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

    override fun onBackPressed() { //not working...
        super.onBackPressed()
        returnVals()
    }
}