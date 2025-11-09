package com.example.fipscan.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.fipscan.R
import com.example.fipscan.ResultEntity
import com.example.fipscan.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val results: List<ResultEntity>,
    private val onItemClick: (ResultEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        val context = holder.itemView.context

        holder.binding.textPatientName.text = result.patientName
        holder.binding.textAge.text = result.age

        val dateText = if (!result.collectionDate.isNullOrBlank()) {
            context.getString(R.string.history_date_label, result.collectionDate)
        } else {
            ""
        }

        holder.binding.textDate.text = dateText

        holder.itemView.setOnClickListener {
            onItemClick(result)
        }
    }

    override fun getItemCount(): Int = results.size
}