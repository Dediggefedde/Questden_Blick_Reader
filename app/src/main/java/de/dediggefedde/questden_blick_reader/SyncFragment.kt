package de.dediggefedde.questden_blick_reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import de.dediggefedde.questden_blick_reader.databinding.FragmentSyncBinding

class SyncFragment : Fragment() {

    private lateinit var binding: FragmentSyncBinding
    private lateinit var viewModel: DataViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sync, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(DataViewModel::class.java)

        binding = FragmentSyncBinding.bind(view)

        viewModel.setsLiveData.observe(viewLifecycleOwner) { set ->
            binding.editLoginName.setText(set.loginName)
            binding.editLoginPW.setText(set.loginPW)
            binding.cbAutologin.isChecked = set.autoLogin
        }
        viewModel.logState.observe(viewLifecycleOwner){ state->
            if(state.token=="" && state.errorCode==401){
                binding.statusText.text = getString(R.string.wrong_username_or_password)
            }else{
                if(state.promptText.isNotEmpty())Toast.makeText(requireContext(), state.promptText, Toast.LENGTH_LONG).show()
                if(state.statusText.isNotEmpty())binding.statusText.text = state.statusText
            }
            if(state.token!=""){
                binding.btnDownload.visibility=View.VISIBLE
                binding.btnUpload.visibility=View.VISIBLE
            }else{
                binding.btnDownload.visibility=View.GONE
                binding.btnUpload.visibility=View.GONE
            }
        }

        binding.btnDownload.setOnClickListener{btnDownload()}
        binding.btnUpload.setOnClickListener{btnUpload()}
        binding.btnLogin.setOnClickListener{ loginClick() }
        binding.txRegister.setOnClickListener{
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://phi.pf-control.de/tgchan/reg.php"))
            startActivity(intent)
        }

    }

    private fun btnDownload() {
        binding.btnDownload.animate()
        viewModel.downloadServer()
    }

    private fun btnUpload() {
        binding.btnUpload.animate()
        viewModel.uploadServer()
    }

    private fun loginClick() {//login button
        binding.btnLogin.animate()
        viewModel.setCredServer(binding.editLoginName.text.toString(),binding.editLoginPW.text.toString(),binding.cbAutologin.isChecked)
        viewModel.loginServer()
    }
}