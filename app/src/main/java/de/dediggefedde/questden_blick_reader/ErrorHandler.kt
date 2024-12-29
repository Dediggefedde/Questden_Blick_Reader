package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.gson.Gson
import de.dediggefedde.questden_blick_reader.databinding.DialogErrorBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import java.io.File
import java.io.FileWriter
import java.io.IOException

fun getCurrentStackTrace(): String {
    val stackTraceElements = Thread.currentThread().stackTrace
    return stackTraceElements.joinToString("\n") { it.toString() }
}
@SuppressLint("InflateParams")
fun handleError(
    context: Context,
    errorType: String,
    errorMessage: String,
    stackTrace: String,
    dataViewModel: DataViewModel?
) {
    saveErrorToFile(context, errorType, errorMessage, stackTrace)

    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_error,null)
    val binding:DialogErrorBinding=DialogErrorBinding.bind(dialogView)

    val dialog = AlertDialog.Builder(context).apply {
        setView(dialogView)
        setCancelable(true)
    }.create()

    binding.errorTitle.text=errorType
    binding.errorMessage.text = errorMessage

    binding.sendButton.setOnClickListener {
        sendErrorReport(context, errorType,errorMessage, stackTrace, "", "", "")
        dialog.dismiss()
    }
    binding.cancelButton.setOnClickListener{
        dialog.cancel()
    }
    binding.sendWithDataButton.setOnClickListener{
        if(dataViewModel===null)return@setOnClickListener
        val userSettingsJson = dataViewModel.getSafeUserSettings()
        val watchListJson = dataViewModel.serializeWatchList()
        val downloadListJson = dataViewModel.serializeDownloadList()
        sendErrorReport(context, errorType, errorMessage, stackTrace, userSettingsJson, watchListJson, downloadListJson)
        dialog.dismiss()
    }
    dialog.show()

//    dialog.window?.setLayout(
//        ViewGroup.LayoutParams.MATCH_PARENT,
//        ViewGroup.LayoutParams.WRAP_CONTENT
//    )
//    dialog.window?.setGravity(Gravity.CENTER) // Vertikal zentrieren

}

fun saveErrorToFile(context: Context, errorType: String, errorMessage: String, stackTrace: String,pending:Boolean=false) {
    val fileName = if(pending)"tmp_error_log.txt" else "error_log.txt"
    val errorFile = File(context.filesDir, fileName)
    try {
        FileWriter(errorFile, true).use { writer ->
            writer.appendLine("Error type: $errorType")
            writer.appendLine("Error message: $errorMessage")
            writer.appendLine("Stacktrace:")
            writer.appendLine(stackTrace)
            writer.appendLine("---")
        }
        MsgHelper.showMsg(context,  "Report saved as: ${errorFile.absolutePath}")
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun sendErrorReport(
    context: Context,
    errorType: String,
    errorMessage: String,
    stackTrace: String,
    userSettings: String,
    watchList: String,
    downloadList: String
) {
    val client = OkHttpClient()

    // Kombiniere alle Daten in ein JSON-Objekt
    val report = mapOf(
        "errorType" to errorType,
        "errorMessage" to errorMessage,
        "stackTrace" to stackTrace,
        "userSettings" to userSettings,
        "watchList" to watchList,
        "downloadList" to downloadList
    )

    val gson = Gson()
    val json = gson.toJson(report)

    val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

    val request = Request.Builder()
        .url("https://phi.pf-control.de/tgchan/error_report.php")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
            // Fehler beim Senden der Anfrage, Anzeige im UI
            (context as? Activity)?.runOnUiThread {
                MsgHelper.showMsg(context,  "Error at sending the error report")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "No answer from the server"
                try {
                    (context as? Activity)?.runOnUiThread {
                        MsgHelper.showMsg(context,  responseBody)
                    }
                } catch (e: JSONException) {
                    (context as? Activity)?.runOnUiThread {
                        MsgHelper.showMsg(context,   "Error at processing answer")
                    }
                }
            } else {
                val errmsg = response.body?.string() ?: "Unknown Error"

                (context as? Activity)?.runOnUiThread {
                    MsgHelper.showMsg(context,  "Error at sending the error report: $errmsg")
                }
            }
        }
    })
}

class GlobalErrorHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val context:Context
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        saveErrorToFile(context,"Unknown Error", e.message.toString(), e.stackTraceToString(),pending = true)
        defaultHandler?.uncaughtException(t, e)
    }
}