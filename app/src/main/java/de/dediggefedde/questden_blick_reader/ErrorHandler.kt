package de.dediggefedde.questden_blick_reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

fun getCurrentStackTrace(): String {
    // Hole den Stacktrace des aktuellen Threads
    val stackTraceElements = Thread.currentThread().stackTrace
    // Konvertiere die Stacktrace-Elemente in einen String
    return stackTraceElements.joinToString("\n") { it.toString() }
}
fun handleError(
    context: Context,
    errorType: String,
    errorMessage: String,
    stackTrace: String,
    dataViewModel: DataViewModel?
) {
    saveErrorToFile(context, errorType, errorMessage, stackTrace)

    AlertDialog.Builder(context).apply {
        setTitle("An error occured")
        setMessage("$errorType: $errorMessage")

        if(dataViewModel!==null) {
            setPositiveButton("Send with data") { _, _ ->
                val userSettingsJson = dataViewModel.getSafeUserSettings()
                val watchListJson = dataViewModel.serializeWatchList()
                val downloadListJson = dataViewModel.serializeDownloadList()
                sendErrorReport(context, errorType, errorMessage, stackTrace, userSettingsJson, watchListJson, downloadListJson)
            }
        }
        setNeutralButton("Send only error") { _, _ ->
            sendErrorReport(context, errorType,errorMessage, stackTrace, "", "", "")
        }
        setNegativeButton("Cancel", null)
        setCancelable(true)
    }.show()
}

fun saveErrorToFile(context: Context, errorType: String, errorMessage: String, stackTrace: String) {
    val fileName = "error_log.txt"
    val errorFile = File(context.filesDir, fileName)
    try {
        FileWriter(errorFile, true).use { writer ->
            writer.appendLine("Error type: $errorType")
            writer.appendLine("Error message: $errorMessage")
            writer.appendLine("Stacktrace:")
            writer.appendLine(stackTrace)
            writer.appendLine("---")
        }
        Toast.makeText(
            context,
            "Report saved as: ${errorFile.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
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
                Toast.makeText(context, "Fehler beim Senden des Berichts", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                // Hier wird die Antwort des Servers ausgelesen
                val responseBody = response.body?.string() ?: "Keine Antwort vom Server"

                // Optional: Wenn die Antwort ein JSON oder Text ist, kannst du es weiter parsen
                // Wenn es ein JSON-Text ist, könnte das z.B. so aussehen:
                try {
                    val jsonResponse = JSONObject(responseBody)
                    val message = jsonResponse.optString("message", "Erfolgreich gesendet")

                    // Erfolgreiche Nachricht vom Server
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    // Fehler beim Parsen der JSON-Antwort
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Antwort konnte nicht verarbeitet werden", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Falls die Antwort nicht erfolgreich war, wird der Fehlertext angezeigt
                val errmsg = response.body?.string() ?: "Unbekannter Fehler"

                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Fehler beim Senden des Berichts: $errmsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    })

}
