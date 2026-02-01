package com.example.fipscan.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.fipscan.ElectrophoresisAnalyzer
import com.example.fipscan.R
import com.example.fipscan.ResultEntity
import com.example.fipscan.databinding.ItemRecentHistoryBinding
import com.google.gson.Gson
import kotlin.math.min
import com.google.gson.reflect.TypeToken

class RecentHistoryAdapter(
    private val results: List<ResultEntity>,
    private val onItemClick: (ResultEntity) -> Unit
) : RecyclerView.Adapter<RecentHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        val context = holder.itemView.context

        holder.binding.textPatientName.text = result.patientName
        holder.binding.textAge.text = result.age

        val dateText = if (!result.collectionDate.isNullOrBlank())
            context.getString(R.string.recent_history_date_format, result.collectionDate)
        else
            ""
        holder.binding.textDate.text = dateText

        var riskPercentage = 0
        try {
            result.rawDataJson?.let {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val extractedData = Gson().fromJson<Map<String, Any>>(it, type)
                if (extractedData != null) {
                    val defaultRivalta = context.resources.getStringArray(R.array.rivalta_options)[0]
                    val electroResult = ElectrophoresisAnalyzer.assessFipRisk(
                        extractedData,
                        result.rivaltaStatus ?: defaultRivalta,
                        context
                    )
                    riskPercentage = electroResult.riskPercentage
                }
            }
        } catch (e: Exception) {
            Log.e("RecentHistoryAdapter", "Error calculating risk", e)
        }

        holder.binding.textRiskIndicator.text = context.getString(R.string.risk_percentage_format, riskPercentage)
        val bgColor = calculateBackgroundColor(riskPercentage)
        holder.binding.textRiskIndicator.backgroundTintList = ColorStateList.valueOf(bgColor)

        holder.itemView.setOnClickListener {
            onItemClick(result)
        }
    }

    override fun getItemCount(): Int = results.size

    private fun calculateBackgroundColor(percentage: Int): Int {
        val r = min(255, percentage * 255 / 100)
        val g = min(255, (100 - percentage) * 255 / 100)
        val b = 0

        return Color.rgb(
            min(255, r + 150),
            min(255, g + 150),
            min(255, b + 150)
        )
    }
}