package io.horizontalsystems.bankwallet.modules.transactions

import io.horizontalsystems.bankwallet.entities.Coin
import io.horizontalsystems.bankwallet.entities.TransactionRecord
import io.horizontalsystems.bankwallet.entities.Wallet

class TransactionsLoader(private val dataSource: TransactionRecordDataSource) {

    interface Delegate {
        fun didInsertData(fromIndex: Int, count: Int)
        fun fetchRecords(fetchDataList: List<TransactionsModule.FetchData>)
    }

    var delegate: Delegate? = null

    val itemsCount: Int
        get() = dataSource.itemsCount

    var loading: Boolean = false

    fun itemForIndex(index: Int) =
            dataSource.itemForIndex(index)

    fun setWallets(coins: List<Wallet>) {
        dataSource.setWallets(coins)
    }

    fun handleUpdate(wallets: List<Wallet>) {
        dataSource.handleUpdatedWallets(wallets)
    }

    fun loadNext() {
        if (loading || dataSource.allShown) return
        loading = true

        val fetchDataList = dataSource.getFetchDataList()
        if (fetchDataList.isEmpty()) {
            nextPage()
        } else {
            delegate?.fetchRecords(fetchDataList)
        }
    }

    fun didFetchRecords(records: Map<Wallet, List<TransactionRecord>>) {
        dataSource.handleNextRecords(records)

        nextPage()
    }

    private fun nextPage() {
        val currentItemsCount = dataSource.itemsCount
        val insertedCount = dataSource.increasePage()
        if (insertedCount > 0) {
            delegate?.didInsertData(currentItemsCount, insertedCount)
        }
        loading = false
    }

    fun itemIndexesForTimestamp(coin: Coin, timestamp: Long): List<Int> {
        return dataSource.itemIndexesForTimestamp(coin, timestamp)
    }

    fun itemIndexesForPending(wallet: Wallet, thresholdBlockHeight: Int): List<Int> {
        return dataSource.itemIndexesForPending(wallet, thresholdBlockHeight)
    }

    fun didUpdateRecords(records: List<TransactionRecord>, wallet: Wallet): Boolean {
        return dataSource.handleUpdatedRecords(records, wallet)
    }

}
