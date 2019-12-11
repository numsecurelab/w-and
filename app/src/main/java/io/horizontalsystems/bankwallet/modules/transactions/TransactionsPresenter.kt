package io.horizontalsystems.bankwallet.modules.transactions

import androidx.recyclerview.widget.DiffUtil
import io.horizontalsystems.bankwallet.core.factories.TransactionViewItemFactory
import io.horizontalsystems.bankwallet.entities.Coin
import io.horizontalsystems.bankwallet.entities.Currency
import io.horizontalsystems.bankwallet.entities.TransactionRecord
import io.horizontalsystems.bankwallet.entities.Wallet
import java.math.BigDecimal

class TransactionsPresenter(
        private val interactor: TransactionsModule.IInteractor,
        private val router: TransactionsModule.IRouter,
        private val factory: TransactionViewItemFactory,
        private val loader: TransactionsLoader,
        private val metadataDataSource: TransactionMetadataDataSource)
    : TransactionsModule.IViewDelegate, TransactionsModule.IInteractorDelegate, TransactionsLoader.Delegate {

    var view: TransactionsModule.IView? = null

    private var viewItems = mutableListOf<TransactionViewItem>()
    private val viewItemsCopy: List<TransactionViewItem>
        get() = viewItems.map { it.copy() }

    override fun viewDidLoad() {
        interactor.initialFetch()
    }

    override fun onVisible() {
        viewItems = MutableList(itemsCount) { itemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    override fun onTransactionItemClick(transaction: TransactionViewItem) {
        router.openTransactionInfo(transaction)
    }

    override fun onFilterSelect(wallet: Wallet?) {
        interactor.setSelectedWallets(wallet?.let { listOf(wallet) } ?: listOf())
    }

    override fun onClear() {
        interactor.clear()
    }

    override val itemsCount: Int
        get() = loader.itemsCount

    override fun itemForIndex(index: Int): TransactionViewItem {
        val transactionItem = loader.itemForIndex(index)
        val wallet = transactionItem.wallet
        val lastBlockHeight = metadataDataSource.getLastBlockHeight(wallet)
        val threshold = metadataDataSource.getConfirmationThreshold(wallet)
        val rate = metadataDataSource.getRate(wallet.coin, transactionItem.record.timestamp)

//        if (rate == null) {
//            interactor.fetchRate(wallet.coin, transactionItem.record.timestamp)
//        }

        return factory.item(wallet, transactionItem, lastBlockHeight, threshold, rate)
    }

    override fun onBottomReached() {
        loader.loadNext(false)
    }

    override fun onUpdateWalletsData(allWalletsData: List<Triple<Wallet, Int, Int?>>) {
        val wallets = allWalletsData.map { it.first }

        allWalletsData.forEach { (wallet, confirmationThreshold, lastBlockHeight) ->
            metadataDataSource.setConfirmationThreshold(confirmationThreshold, wallet)
            lastBlockHeight?.let {
                metadataDataSource.setLastBlockHeight(it, wallet)
            }
        }

        interactor.fetchLastBlockHeights()

        val filters = when {
            wallets.size < 2 -> listOf()
            else -> listOf(null).plus(wallets)
        }

        view?.showFilters(filters)

        loader.handleUpdate(wallets)
        viewItems.clear()
        loader.loadNext(true)
    }

    override fun onUpdateSelectedWallets(selectedWallets: List<Wallet>) {
        loader.setWallets(selectedWallets)
        viewItems.clear()
        loader.loadNext(true)
    }

    override fun didFetchRecords(records: Map<Wallet, List<TransactionRecord>>) {
        loader.didFetchRecords(records)
    }

    override fun onUpdateLastBlockHeight(wallet: Wallet, lastBlockHeight: Int) {
        val oldBlockHeight = metadataDataSource.getLastBlockHeight(wallet)
        val threshold = metadataDataSource.getConfirmationThreshold(wallet)

        metadataDataSource.setLastBlockHeight(lastBlockHeight, wallet)

        if (oldBlockHeight == null) {
            viewItems = MutableList(itemsCount) { itemForIndex(it) }
            view?.setItems(viewItemsCopy)
            return
        }

        val indexes = loader.itemIndexesForPending(wallet, oldBlockHeight - threshold)
        if (indexes.isNotEmpty()) {
            indexes.forEach {
                viewItems[it] = itemForIndex(it)
            }

            view?.setItems(viewItemsCopy)
        }
    }

    override fun onUpdateBaseCurrency() {
        metadataDataSource.clearRates()

        viewItems = MutableList(itemsCount) { itemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    override fun didFetchRate(rateValue: BigDecimal, coin: Coin, currency: Currency, timestamp: Long) {
        metadataDataSource.setRate(rateValue, coin, currency, timestamp)

        val indexes = loader.itemIndexesForTimestamp(coin, timestamp)
        if (indexes.isNotEmpty()) {
            indexes.forEach {
                viewItems[it] = itemForIndex(it)
            }

            view?.setItems(viewItemsCopy)
        }
    }

    override fun didUpdateRecords(records: List<TransactionRecord>, wallet: Wallet) {
        loader.didUpdateRecords(records, wallet)
    }

    override fun onConnectionRestore() {
        viewItems = MutableList(itemsCount) { itemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    //
    // TransactionsLoader Delegate
    //

    override fun onChange(diff: DiffUtil.DiffResult) {
        viewItems = MutableList(itemsCount) { itemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    override fun didChangeData() {
        viewItems = MutableList(itemsCount) { itemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    override fun didInsertData(fromIndex: Int, count: Int) {
        val toInsert = List(count) {
            itemForIndex(fromIndex + it)
        }
        viewItems.addAll(toInsert)

        view?.setItems(viewItemsCopy)
    }

    override fun fetchRecords(fetchDataList: List<TransactionsModule.FetchData>) {
        interactor.fetchRecords(fetchDataList)
    }

}
