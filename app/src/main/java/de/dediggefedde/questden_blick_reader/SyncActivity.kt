package de.dediggefedde.questden_blick_reader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import kotlinx.android.synthetic.main.sync.*
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore


class SyncActivity : AppCompatActivity() {
    var user = ""
    var pw = ""
    var loggedIn=false
    var watchResp=""
    lateinit var cache:DiskBasedCache
    lateinit var network:BasicNetwork
    lateinit var requestQueue:RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sync)
        setSupportActionBar(findViewById(R.id.synctoolbar))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        user = intent.getStringExtra("user") ?: ""
        pw = intent.getStringExtra("pw") ?: ""

        editTextTextPersonName.setText(user)
        editTextTextPassword.setText(pw)


        val manager = CookieManager()
        CookieHandler.setDefault(manager)

        cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap
        network = BasicNetwork(HurlStack())
        requestQueue = RequestQueue(cache, network).apply {
            start()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                val data = Intent()
                data.putExtra("user", user)
                data.putExtra("pw", pw)
                data.putExtra("response", watchResp)
                setResult(RESULT_OK, data)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() { //not working...
        super.onBackPressed()
        val data = Intent()
        data.putExtra("user", user)
        data.putExtra("pw", pw)
        data.putExtra("response", watchResp)
        setResult(RESULT_OK, data)
        finish()
    }

    fun btn_download(view: View) {
        if(!loggedIn) {
            textView.text = "Not logged in."
            return
        }

        val params2 = HashMap<String, String>()
        params2["blobmode"] = "1" //0: layout userscript, 1: watchbar userscript
        params2["readacc"] = "''" //"readacc" and "writeacc"
        params2["blob"] = ""

        postreq("https://phi.pf-control.de/tgchan/interface.php?settings",params2, Response.Listener<String> { response ->
            textView.text=response
            val lis=response.split(7.toChar()) //("itemoptions","externlistspeicher","lastviews","watchids","version");//
            // watchids = new watchbar, char(11) split each thread
            // thread information char(12) split: board, id, title, author
            watchResp=lis[3]//.split(11.toChar()).map{ el.split(12.toChar())[1]}
        })
    }

    fun btn_click(view: View) {
        user = editTextTextPersonName.text.toString()
        pw = editTextTextPassword.text.toString()

        if (user == "") return
        login(user, pw)//async
    }

    fun login(name: String, pw: String) {
        loggedIn = false
        val params2 = HashMap<String, String>()
        params2["uname"] = name
        params2["upass"] = pw
        params2["bot"] = ""

        postreq("https://phi.pf-control.de/tgchan/interface.php?login",
            params2,
            Response.Listener<String> { response ->
                when (response) {
                    "n:1" -> {
                        textView.text = "Logged in!"
                        loggedIn = true
                    }
                    "n:2" -> textView.text = "Account not verified!"
                    "n:0" -> textView.text = "Login-Error."
                }
            }
        )
    }
    fun postreq(url: String, param: HashMap<String, String>, callback: Response.Listener<String>) {
//        val queue = Volley.newRequestQueue (this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            callback,
            Response.ErrorListener {
                textView.text = "Error Reaching Server...\n${url}\n${param.map{(a,b)->"$a=$b"}.joinToString { "&" }}"
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

//        queue.add(stringRequest)
    }

}