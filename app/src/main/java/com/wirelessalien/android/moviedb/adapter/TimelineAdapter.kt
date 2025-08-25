package com.wirelessalien.android.moviedb.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.ItemTimelineBinding
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TimelineAdapter(private val credits: List<JSONObject>) :
    RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimelineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val credit = credits[position]
        holder.bind(credit)
    }

    override fun getItemCount(): Int {
        return credits.size
    }

    class ViewHolder(private val binding: ItemTimelineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(credit: JSONObject) {
            binding.itemTimelineTitle.text = credit.optString("title", "") ?: credit.optString("name", "")
            val releaseDateStr = credit.optString("release_date", "") ?: credit.optString("first_air_date", "")
            val formattedDate = if (releaseDateStr.isNotEmpty()) {
                try {
                    val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(releaseDateStr)
                    DateFormat.getDateInstance(DateFormat.DEFAULT).format(parsedDate ?: "")
                } catch (e: Exception) {
                    "Date"
                }
            } else {
                "Date"
            }
            binding.itemTimelineReleaseDate.text = formattedDate

            val mediaType = credit.optString("media_type", "")
            binding.itemTimelineMediaType.text = mediaType.replaceFirstChar { it.uppercase() }
            binding.itemTimelineJob.text = if (credit.has("job")) credit.optString("job") else "Actor"

            itemView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, DetailActivity::class.java).apply {
                    putExtra("movieObject", credit.toString())
                    if (mediaType == "movie") {
                        putExtra("isMovie", true)
                    }
                }
                context.startActivity(intent)
            }
        }
    }
}
