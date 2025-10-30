package com.example.cekpicklist.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R

/**
 * Karung Summary Modal Fragment
 * Menduplikasi KarungSummaryModal dari web app
 */
class KarungSummaryModalFragment : DialogFragment() {

    companion object {
        private const val ARG_EXPEDITION = "expedition"
        private const val ARG_DATE = "date"
        private const val ARG_SUMMARY_DATA = "summary_data"

        fun newInstance(
            expedition: String,
            date: String,
            summaryData: List<KarungSummaryItem>
        ): KarungSummaryModalFragment {
            val fragment = KarungSummaryModalFragment()
            val args = Bundle()
            args.putString(ARG_EXPEDITION, expedition)
            args.putString(ARG_DATE, date)
            args.putSerializable(ARG_SUMMARY_DATA, ArrayList(summaryData))
            fragment.arguments = args
            return fragment
        }
    }

    data class KarungSummaryItem(
        val karungNumber: String,
        val quantity: Int
    )

    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var rvKarungList: RecyclerView
    private lateinit var btnClose: Button
    private lateinit var karungAdapter: KarungSummaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_karung_summary_modal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        loadData()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDescription = view.findViewById(R.id.tvDescription)
        rvKarungList = view.findViewById(R.id.rvKarungList)
        btnClose = view.findViewById(R.id.btnClose)
    }

    private fun setupRecyclerView() {
        karungAdapter = KarungSummaryAdapter()
        rvKarungList.layoutManager = LinearLayoutManager(requireContext())
        rvKarungList.adapter = karungAdapter
    }

    private fun loadData() {
        val expedition = arguments?.getString(ARG_EXPEDITION) ?: ""
        val date = arguments?.getString(ARG_DATE) ?: ""
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val summaryData = arguments?.getSerializable(ARG_SUMMARY_DATA) as? ArrayList<KarungSummaryItem> ?: arrayListOf()

        tvTitle.text = "Ringkasan Karung"
        tvDescription.text = "Detail karung untuk ekspedisi $expedition pada tanggal $date."
        
        karungAdapter.updateData(summaryData)
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

/**
 * Adapter for Karung Summary List
 */
class KarungSummaryAdapter : RecyclerView.Adapter<KarungSummaryAdapter.KarungViewHolder>() {

    private var karungList = listOf<KarungSummaryModalFragment.KarungSummaryItem>()

    fun updateData(newList: List<KarungSummaryModalFragment.KarungSummaryItem>) {
        karungList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KarungViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_karung_summary, parent, false)
        return KarungViewHolder(view)
    }

    override fun onBindViewHolder(holder: KarungViewHolder, position: Int) {
        holder.bind(karungList[position])
    }

    override fun getItemCount(): Int = karungList.size

    class KarungViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvKarungNumber: TextView = itemView.findViewById(R.id.tvKarungNumber)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)

        fun bind(item: KarungSummaryModalFragment.KarungSummaryItem) {
            tvKarungNumber.text = item.karungNumber
            tvQuantity.text = item.quantity.toString()
        }
    }
}
