package de.dediggefedde.questden_blick_reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
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
    private var watchlist: MutableList<Watch>? = null
    private var newWatchlist: MutableList<Watch>? = null
    private var newWatchUrl:MutableList<String>?=null
    private var newWatchPos:MutableList<String>?=null
    private var remoteData=mutableListOf<String>()
    private lateinit var cache:DiskBasedCache
    private lateinit var network:BasicNetwork
    private lateinit var requestQueue:RequestQueue

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

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
        data.putParcelableArrayListExtra("watchlist",ArrayList(watchlist?: emptyList()))
        data.putStringArrayListExtra("watchlistUrl",ArrayList(newWatchUrl?: emptyList()))
        data.putStringArrayListExtra("watchlistPos",ArrayList(newWatchPos?: emptyList()))
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
    private fun downloadRemote(){
        if(!loggedIn) {
            textView.text = getString(R.string.NotLoggedIn)
            return
        }

        val params2 = HashMap<String, String>()
        params2["blobmode"] = "1" //0: layout sets?.userscript, 1: watchbar sets?.userscript
        params2["readacc"] = "''" //"readacc" and "writeacc"
        params2["blob"] = ""

        postreq("https://phi.pf-control.de/tgchan/interface.php?settings",params2) { response ->
            //textView.text = response
            remoteData=response.split(7.toChar()).toMutableList()
            val lis = response.split(7.toChar()) //("itemoptions","externlistspeicher","lastviews","watchids","version");//
            // watchids = new watchbar, char(11) split each thread
            // thread information char(12) split: board, id, title, author
            //lastview: thread+"_"+id+chr(11)+ref+chr(11)+count , count >50?1:0, join by ch(12)
            if (lis[3] != "") {
                //get list of watches
                val lis2 = lis[3].split(11.toChar()).map {
                    val tg = TgThread()
                    val inf = it.split(12.toChar())
                    val url = "/kusaba/${inf[0]}/res/${inf[1]}.html"
                    //tg.postID=inf[1]
                    tg.url = url
                    Watch(tg)
                }
                newWatchlist = lis2.toMutableList()

                //get lastview
                lis[2].split(12.toChar()).forEach{ el ->
                    val inf = el.split(11.toChar())
                    val urlp=inf[0].split("_")
                    val url = "/kusaba/${urlp[0]}/res/${urlp[1]}.html"
                    newWatchlist?.firstOrNull { it.thread.url == url }?.newestId=inf[1]
                }
                syncRemStatus.text=getString(R.string.localRmoteCompare, watchlist?.size.toString(), newWatchlist?.size.toString())


                /* val inte = Intent(this, SyncCompareActivity::class.java)
                 inte.putParcelableArrayListExtra("watchlist",ArrayList(watchlist!!))
                 inte.putParcelableArrayListExtra("newWatchlist",ArrayList(newWatchlist!!))
                 // If an instance of this Activity already exists, then it will be moved to the front. If an instance does NOT exist, a new instance will be created
                 startActivityForResult(inte, 2)

                 */

            }
        }
    }
    //upload code snippets from userscript
    // var blob=[];
    // var liste2=["itemoptions","externlistspeicher","lastviews","watchids","version"];
    // blob[i]=await GM.getValue(liste2[i],"").join(String.fromCharCode(7))

    // intersettings(mode=1,blob,writeaccess=true,callback=function(f){
    // 	if(f=="no"){alert("You disabled synchronizing the watchbar!");}
    // 	if(f=="n:0"){
    // 		// alert("Watchbar data already up to date!");
    // 	}
    // 	//fertig
    // });

    // async function intersettings(mode, blob, writeaccess,callback){ //mode: 0 settings, 1 wbar
    // 	GM.xmlHttpRequest({
    // 		method: 'POST',
    // 		url: "http://phi.pf-control.de/tgchan/interface.php?settings",
    // 		data: "blobmode="+mode+"&"+(writeaccess?"write":"read")+"acc=''&blob="+encodeURIComponent(blob),
    // 		headers:{
    // 			"Host": "phi.pf-control.de",
    // 			"Content-Type": "application/x-www-form-urlencoded"
    // 		},
    // 		onload: function(response) {
    // 			console.log(response)
    // 			setTimeout(function(){callback(response.responseText);},0);
    // 		}
    // 	});
    // }

    // watchids=(await GM.getValue("watchids","")).split(String.fromCharCode(11));
    private fun uploadRemote(){

        if(!loggedIn) {
            textView.text = getString(R.string.NotLoggedIn)
            return
        }
        if( remoteData.isEmpty()){
            textView.text = getString(R.string.firstCreateEntry)
            return
        }

        val params2 = HashMap<String, String>()
        params2["blobmode"] = "1" //0: layout sets?.userscript, 1: watchbar sets?.userscript
        params2["writeacc"] = "''" //"readacc" and "writeacc"
        params2["blob"] = ""
        //("itemoptions","externlistspeicher","lastviews","watchids","version");// sep chr(7) //only watchids used here currently.
        // watchids = new watchbar, char(11) split each thread
        // thread information char(12) split: board, id, title, author
        //lastview: thread+"_"+id+chr(11)+ref+chr(11)+count , count >50?1:0, join by ch(12)
      // var watchids=""//remoteData[3]
        remoteData[3]= watchlist?.joinToString(separator = 11.toChar().toString())  {
            //board, id, title, author
            val urlinf=Regex("""kusaba/(.*?)/res/(\d+).html""",RegexOption.DOT_MATCHES_ALL).find(it.thread.url)?.groupValues
            var retval=(urlinf?.get(1) ?: "" )+ 12.toChar().toString()
            retval+=(urlinf?.get(2) ?: "") + 12.toChar().toString()
            retval+=it.thread.title+ 12.toChar().toString()
            retval+=it.thread.author
            retval
        }?: ""

        remoteData[2]=watchlist?.joinToString(separator = 12.toChar().toString())  {
            //  thread+"_"+id+chr(11)+ref+chr(11)+count
            val urlinf=Regex("""kusaba/(.*?)/res/(\d+).*""",RegexOption.DOT_MATCHES_ALL).find(it.thread.url)?.groupValues
            var retval=urlinf?.get(1)+ "_" + urlinf?.get(2) + 11.toChar().toString()
            retval+=it.newestId + 11.toChar().toString()
            retval+="0"
            retval
        }?: ""

        params2["blob"]=remoteData.joinToString(separator = 7.toChar().toString())

        postreq("https://phi.pf-control.de/tgchan/interface.php?settings",params2) { response ->
            if(response=="no"){textView.text=("You disabled synchronizing the watchbar!")}
            returnVals()
        }
    }
    fun btnDownload(view: View) {
        view.animate() //button click handler crashes without view in parameters. kotlin warns if parameter view not used.
        watchlist=newWatchlist
        newWatchPos=newWatchlist?.map { it.newestId }?.toMutableList()
        newWatchUrl=newWatchlist?.map { it.thread.url }?.toMutableList()
        returnVals()
    }
    fun btnUpload(view: View) {
        view.animate()
        uploadRemote()
    }
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == Activity.RESULT_OK && requestCode == 2) {
//            watchlist = data?.getParcelableArrayListExtra("watchlist")
//            textView.text="Watchlist Updated!"
//        }else{
//            textView.text="Error Updating Watchlist!"
//        }
//    }

    fun btnClick(view: View) {//login button
        if(!loggedIn) {
            sets?.user = editTextTextPersonName.text.toString()
            sets?.pw = editTextTextPassword.text.toString()

            view.animate()

            if (sets?.user == "") return
            login(sets!!.user, sets!!.pw)
            view.hideKeyboard()
        }else{
            loggedIn=false
            sets?.pw=""
            textView.text = getString(R.string.Loggedout)
            button.text = getString(R.string.SyncLogin)
            button3.visibility=View.INVISIBLE
            btnUpload.visibility=View.INVISIBLE
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
                    btnUpload.visibility=View.VISIBLE
                    loggedIn = true
                    downloadRemote()
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

            override fun getParams(): MutableMap<String, String> {
                return param
            }

        }
        requestQueue.add(stringRequest)
    }
}