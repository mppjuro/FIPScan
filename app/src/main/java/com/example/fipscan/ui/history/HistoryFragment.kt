package com.example.fipscan.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fipscan.AppDatabase
import com.example.fipscan.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.fipscan.ExtractData
import com.google.gson.Gson
import androidx.fragment.app.activityViewModels
import com.example.fipscan.SharedResultViewModel

class HistoryFragment : Fragment() {
    private val sharedViewModel: SharedResultViewModel by activityViewModels()
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val results = AppDatabase.getDatabase(requireContext()).resultDao().getAllResults()

            withContext(Dispatchers.Main) {
                binding.recyclerView.adapter = HistoryAdapter(results) { result ->

                    result.rawDataJson?.let { json ->
                        val map = Gson().fromJson(json, Map::class.java) as? Map<String, Any>
                        if (map != null) {
                            ExtractData.lastExtracted = map
                        }
                    }

                    val action = HistoryFragmentDirections.actionNavigationHistoryToNavigationHome(result)
                    findNavController().navigate(action)
                }
            }
        }

        return binding.root
    }
}
