package com.borgeiz.meutcc2026.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.borgeiz.meutcc2026.R
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.transactions.EditTransactionActivity

class TransactionAdapter(
    private val list: List<Transaction>
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle:  TextView = itemView.findViewById(R.id.tvTitle)
        val tvInfo:   TextView = itemView.findViewById(R.id.tvInfo)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = list[position]
        val ctx  = holder.itemView.context

        holder.tvTitle.text = item.title
        holder.tvInfo.text  = "${item.category}  ·  ${item.date}"

        if (item.type == "receita") {
            holder.tvAmount.text = "+R$ %.2f".format(item.amount)
            holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.income))
        } else {
            holder.tvAmount.text = "-R$ %.2f".format(item.amount)
            holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.expense))
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, EditTransactionActivity::class.java).apply {
                putExtra("id",          item.id)
                putExtra("type",        item.type)
                putExtra("title",       item.title)
                putExtra("amount",      item.amount)
                putExtra("category",    item.category)
                putExtra("date",        item.date)
                putExtra("description", item.description)
                putExtra("paymentMethod", item.paymentMethod)
            }
            ctx.startActivity(intent)
        }
    }
}
