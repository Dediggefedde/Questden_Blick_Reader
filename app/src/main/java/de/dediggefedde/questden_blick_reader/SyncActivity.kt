package de.dediggefedde.questden_blick_reader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import kotlinx.android.synthetic.main.sync.*
import java.net.CookieHandler
import java.net.CookieManager


class SyncActivity : AppCompatActivity() {
    private var sets:Settings? = null
    private var loggedIn=false
//    private var watchResp=""
    private var watchlist: MutableList<Watch>? = null
    private var newWatchlist: MutableList<Watch>? = null
    private lateinit var cache:DiskBasedCache
    private lateinit var network:BasicNetwork
    private lateinit var requestQueue:RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sync)
        setSupportActionBar(findViewById(R.id.synctoolbar))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        watchlist= intent.getParcelableArrayListExtra("watchlist")
        sets = intent.extras?.get("sets") as Settings?

        editTextTextPersonName.setText(sets?.user)
        editTextTextPassword.setText(sets?.pw)

        val manager = CookieManager()
        CookieHandler.setDefault(manager)

        cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap
        network = BasicNetwork(HurlStack())
        requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        button3.visibility=View.INVISIBLE
    }
    private fun returnVals(){
        val data = Intent()
        data.putExtra("sets", sets)
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

    fun btnDownload(view: View) {
        if(!loggedIn) {
            textView.text = getString(R.string.NotLoggedIn)
            return
        }
        view.animate() //button click handler crashes without view in parameters. kotlin warns if parameter view not used.

        val params2 = HashMap<String, String>()
        params2["blobmode"] = "1" //0: layout sets?.userscript, 1: watchbar sets?.userscript
        params2["readacc"] = "''" //"readacc" and "writeacc"
        params2["blob"] = ""

        postreq("https://phi.pf-control.de/tgchan/interface.php?settings",params2) { response ->
            textView.text = response
            val lis = response.split(7.toChar()) //("itemoptions","externlistspeicher","lastviews","watchids","version");//
            // watchids = new watchbar, char(11) split each thread
            // thread information char(12) split: board, id, title, author
            if (lis[3] != "") {
                val lis2 = lis[3].split(11.toChar()).map {
                    val tg = TgThread()
                    val inf = it.split(12.toChar())
                    val url = "/kusaba/${inf[0]}/res/${inf[1]}.html"
                    //tg.postID=inf[1]
                    tg.url = url
                    Watch(tg)
                }
                newWatchlist = lis2.toMutableList()

                val inte = Intent(this, SyncActivity::class.java)
                inte.putParcelableArrayListExtra("watchlist",ArrayList(watchlist!!))
                inte.putParcelableArrayListExtra("newWatchlist",ArrayList(newWatchlist!!))
                // If an instance of this Activity already exists, then it will be moved to the front. If an instance does NOT exist, a new instance will be created
                startActivityForResult(inte, 2)

            }
            //textView.text="currentW:${watchlist?.size}, remoteW:${newWatchlist?.size}"
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 2) {
            watchlist = data?.getParcelableArrayListExtra("watchlist")
            textView.text="Watchlist Updated!"
        }else{
            textView.text="Error Updating Watchlist!"
        }
    }

    fun btnClick(view: View) {//login button
        if(!loggedIn) {
            sets?.user = editTextTextPersonName.text.toString()
            sets?.pw = editTextTextPassword.text.toString()

            view.animate()

            if (sets?.user == "") return
            login(sets!!.user, sets!!.pw)
        }else{
            loggedIn=false
            sets?.pw=""
            button3.visibility=View.INVISIBLE
        }
    }

    private fun login(name: String, pw: String) {
        loggedIn = false
        button3.visibility=View.INVISIBLE
        val params2 = HashMap<String, String>()
        params2["uname"] = name
        params2["upass"] = pw
        params2["bot"] = ""

        postreq("https://phi.pf-control.de/tgchan/interface.php?login",
            params2
        ) { response ->
            when (response) {
                "n:1" -> {
                    textView.text = getString(R.string.LoggedIn)
                    button.text = getString(R.string.LogOut)
                    button3.visibility=View.VISIBLE
                    loggedIn = true
                }
                "n:2" -> textView.text = getString(R.string.NotVerified)
                "n:0" -> textView.text = getString(R.string.LoginError)
            }
        }
    }
    private fun postreq(url: String, param: HashMap<String, String>, callback: Response.Listener<String>) {
//        val queue = Volley.newRequestQueue (this)
        val stringRequest = object : StringRequest(
            Method.POST, url,
            callback,
            Response.ErrorListener {
                textView.text = getString(R.string.ErrorReachingServer,url,param.map{ (a,b)->"$a=$b"}.joinToString { "&" })
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Host"] = "phi.pf-control.de"
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded"
            }

            override fun getParams(): MutableMap<String, String>? {
                return param
            }

        }
        requestQueue.add(stringRequest)
    }
}