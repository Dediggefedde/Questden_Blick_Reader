package de.dediggefedde.questden_blick_reader

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import de.dediggefedde.questden_blick_reader.databinding.FragmentWikiBinding

class WikiFragment : Fragment() {
    private lateinit var wikiModel:WikiViewModel
    private lateinit var viewModel: DataViewModel
    private lateinit var binding: FragmentWikiBinding
    private lateinit var linksAdapter: ArrayAdapter<LinkItem>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wiki, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        wikiModel = ViewModelProvider(requireActivity()).get(WikiViewModel::class.java)
        viewModel=ViewModelProvider(requireActivity()).get(DataViewModel::class.java)
        binding = FragmentWikiBinding.bind(view)

        linksAdapter = object : ArrayAdapter<LinkItem>(
            requireContext(),
            android.R.layout.simple_list_item_1,
            mutableListOf()
        ){
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val mview = super.getView(position, convertView, parent) as TextView
                val item = getItem(position)
                mview.text = item?.text

                if (item?.url?.contains(viewModel.sets.curThreadId, ignoreCase = true) == true) {
                    mview.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_list_high))
//                    mview.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark))
                } else {
                    mview.setBackgroundColor(ContextCompat.getColor(requireContext(),android.R.color.transparent))
//                  mview.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark))
                }
                return mview
            }
        }
        binding.linksListView.adapter = linksAdapter

        wikiModel.linksLiveData.observe(viewLifecycleOwner) { list ->
            linksAdapter.clear()
            linksAdapter.addAll(list)
            linksAdapter.notifyDataSetChanged()
        }

        binding.linksListView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = linksAdapter.getItem(position)
            if (selectedItem != null) {
                viewModel.loadThread(selectedItem.url,ThrdItemTyps.THREAD)
                (requireActivity() as MainActivity).showMainList()
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

}