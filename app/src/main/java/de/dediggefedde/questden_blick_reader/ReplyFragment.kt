package de.dediggefedde.questden_blick_reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.dediggefedde.questden_blick_reader.databinding.FragmentReplyBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReplyFragment : Fragment() {

    private lateinit var replyViewModel: ReplyViewModel
    private lateinit var binding: FragmentReplyBinding
    private lateinit var viewModel: DataViewModel
    private var uploadFile:RequestBody?=null
    private var uploadName:String=""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reply, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(DataViewModel::class.java)
        binding = FragmentReplyBinding.bind(view)
        replyViewModel=ViewModelProvider(requireActivity()).get(ReplyViewModel::class.java)

        binding.buttonUploadFile.setOnClickListener {
            openFileChooser()
        }
        binding.buttonSubmit.setOnClickListener {
            submitForm()
        }
        binding.buttonClose.setOnClickListener {
            (requireActivity() as MainActivity).showMainList()
        }

        binding.inputMessage.setText(replyViewModel.messageText.value)
        binding.inputMessage.setSelection(replyViewModel.cursorPosition.value?:0)
        //no observers required, closing the form is destroying it

        binding.inputMessage.addTextChangedListener {
            replyViewModel.updateMessageText(it.toString())
            replyViewModel.updateCursorPosition(binding.inputMessage.selectionStart)
        }
    }

    @Deprecated("for minsdk 16")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                binding.filename.text=""
                val inputStream = try{context?.contentResolver?.openInputStream(uri)}catch(e:Exception){
                    e.printStackTrace()
                    null
                }
                if (inputStream == null) {
                    MsgHelper.showMsg(requireContext(),  "Failed to open file.")
                    return
                }
                val cursor = context?.contentResolver?.query(uri, null, null, null, null)
               val fileName = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) it.getString(nameIndex) else null
                    } else null
                } ?: "default_file_name"

                binding.filename.text=fileName
                val fileBytes = inputStream.readBytes()

                uploadFile= fileBytes.toRequestBody("multipart/form-data".toMediaTypeOrNull()).also { uploadName=fileName }
            }
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"

        @Suppress("DEPRECATION")
        startActivityForResult(intent, 3)
    }
    private fun submitForm() {
        val name = binding.inputName.text.toString()
        val email = binding.inputEmail.text.toString()
        val subject = binding.inputSubject.text.toString()
        val message = binding.inputMessage.text.toString()
        val spoiler = if (binding.cbSpoiler.isChecked) "on" else ""

        // POST-Anfrage senden
        sendPostRequest(
            name = name,
            email = email,
            subject = subject,
            message = message,
            spoiler = spoiler
        )

        //MsgHelper.showMsg(requireContext(),  "Form submitted: Name=$name, Email=$email, Subject=$subject, Message=$message" )
}

    private fun sendPostRequest(name: String, email: String, subject: String, message: String, spoiler: String) {
        val url="https://questden.org/kusaba/board.php"
        val board=Regex("/kusaba/(\\w+)/").find(viewModel.sets.curURL)?.groupValues?.get(1)?:"quest"

        val preferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var password = preferences.getString("reply_password", null)
        if (password == null) {
            password = (requireActivity()as MainActivity).generateRandomPassword()
            preferences.edit().putString("reply_password", password).apply()
        }

        val formBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("board", board)
            .addFormDataPart("replythread", viewModel.sets.curThreadId)
            .addFormDataPart("name", name)
            .addFormDataPart("em", email)
            .addFormDataPart("subject", subject)
            .addFormDataPart("message", message)
            .addFormDataPart("spoiler", spoiler)
            .addFormDataPart("MAX_FILE_SIZE", "26214400")
            .addFormDataPart("email", "")
            .addFormDataPart("postpassword", password) //TODO: password management, website: cookie for 1 year expiration

        val reqF=uploadFile
        if (reqF!=null && uploadName.isNotEmpty()) {
            formBodyBuilder//.addFormDataPart("imagefile", file.name, fileRequestBody)
                .addFormDataPart("imagefile", uploadName, reqF) // Dateinamen optional anpassen

        }

        val formBody = formBodyBuilder.build()
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        activity?.runOnUiThread {
            MsgHelper.showMsg(requireContext(),  "Sending Reply..." )
        }

        val client= OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Verbindungstimeout
            .readTimeout(30, TimeUnit.SECONDS)    // Lese-Timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Schreib-Timeout
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                // Fehler-Feedback
                activity?.runOnUiThread {
                    MsgHelper.showMsg(requireContext(),  "Request failed: ${e.message}" )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        // Fehler-Feedback
                        activity?.runOnUiThread {
                            MsgHelper.showMsg(requireContext(),  "Request failed: ${it.message}" )
                        }
                        return
                    }

                    // Erfolgsmeldung
                    activity?.runOnUiThread {
                        MsgHelper.showMsg(requireContext(),  "Post submitted successfully!")
                        (requireActivity() as MainActivity).showMainList()
                        replyViewModel.updateMessageText("")
                        viewModel.loadCurThread()
                    }
                }
            }
        })
    }
}

