package de.dediggefedde.questden_blick_reader

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import de.dediggefedde.questden_blick_reader.databinding.SyncBinding
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SyncActivity : AppCompatActivity() {
    private lateinit var syncBinding: SyncBinding
    private lateinit var viewModel: DataViewModel

//    private fun View.hideKeyboard() {
//        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.hideSoftInputFromWindow(windowToken, 0)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncBinding = SyncBinding.inflate(layoutInflater)
        setContentView(syncBinding.root)

        viewModel = ViewModelProvider(this).get(DataViewModel::class.java)

//        setSupportActionBar(syncBinding.synctoolbar)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setDisplayShowHomeEnabled(true)

        viewModel.setsLiveData.observe(this) { set ->
            syncBinding.editLoginName.setText(viewModel.sets.loginName)
            syncBinding.editLoginPW.setText(viewModel.sets.loginPW)
            syncBinding.cbAutologin.isChecked = viewModel.sets.autoLogin
        }
        viewModel.promptLoginMessage.observe(this){msg->Toast.makeText(this@SyncActivity, msg, Toast.LENGTH_LONG).show()}
        viewModel.statusLoginMessage.observe(this){msg->syncBinding.statusText.text =msg}

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { //not working...
        super.onBackPressed()
        finish()
    }

    fun btnDownload(view: View) {
        view.animate()
        viewModel.download()
    }

    fun btnUpload(view: View) {
        view.animate()
        viewModel.upload()
    }

    fun loginClick(view: View) {//login button
        viewModel.setCred(syncBinding.editLoginName.text.toString(),syncBinding.editLoginPW.text.toString(),syncBinding.cbAutologin.isChecked)
        viewModel.login()
    }
}