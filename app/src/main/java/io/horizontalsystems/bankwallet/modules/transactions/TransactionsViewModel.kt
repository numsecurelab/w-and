package io.horizontalsystems.bankwallet.modules.transactions

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bankwallet.SingleLiveEvent
import io.horizontalsystems.bankwallet.entities.Wallet

class TransactionsViewModel : ViewModel(), TransactionsModule.IView, TransactionsModule.IRouter {

    lateinit var delegate: TransactionsModule.IViewDelegate

    val filterItems = MutableLiveData<List<Wallet?>>()
    val itemsLiveData = MutableLiveData<List<TransactionViewItem>>()
    val transactionViewItemLiveEvent = SingleLiveEvent<TransactionViewItem>()

    fun init() {
        TransactionsModule.initModule(this, this)
        delegate.viewDidLoad()
    }

    override fun showFilters(filters: List<Wallet?>) {
        filterItems.postValue(filters)
    }

    override fun setItems(items: List<TransactionViewItem>) {
        itemsLiveData.postValue(items)
    }

    override fun openTransactionInfo(transactionViewItem: TransactionViewItem) {
        transactionViewItemLiveEvent.postValue(transactionViewItem)
    }

    override fun onCleared() {
        delegate.onClear()
    }
}
