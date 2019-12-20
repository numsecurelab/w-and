package io.horizontalsystems.bankwallet.modules.transactions

import io.horizontalsystems.bankwallet.core.factories.TransactionViewItemFactory
import io.horizontalsystems.bankwallet.entities.Coin
import io.horizontalsystems.bankwallet.entities.Currency
import io.horizontalsystems.bankwallet.entities.TransactionRecord
import io.horizontalsystems.bankwallet.entities.Wallet
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TransactionsPresenter(
        private val interactor: TransactionsModule.IInteractor,
        private val router: TransactionsModule.IRouter,
        private val factory: TransactionViewItemFactory,
        private val dataSource: TransactionRecordDataSource,
        private val metadataDataSource: TransactionMetadataDataSource)
    : TransactionsModule.IViewDelegate, TransactionsModule.IInteractorDelegate {

    var view: TransactionsModule.IView? = null

    private var loading: AtomicBoolean = AtomicBoolean(false)
    private var viewItems = mutableListOf<TransactionViewItem>()
    private val viewItemsCopy: List<TransactionViewItem>
        get() = viewItems.map { it.copy() }

    private val executor = Executors.newSingleThreadExecutor()

    override fun viewDidLoad() {
        executor.submit {
            interactor.initialFetch()
        }
    }

    override fun onVisible() {
        executor.submit {
            resetViewItems()
        }
    }

    override fun onTransactionItemClick(transaction: TransactionViewItem) {
        router.openTransactionInfo(transaction)
    }

    override fun onBindTransactionItem(transaction: TransactionViewItem) {
        executor.submit {
            if (transaction.rate == null) {
                transaction.date?.let {
                    interactor.getRateFromApi(transaction.wallet.coin, it.time / 1000)
                }
            }
        }
    }

    override fun onFilterSelect(wallet: Wallet?) {
        executor.submit {
            interactor.setSelectedWallets(wallet?.let { listOf(wallet) } ?: listOf())
        }
    }

    override fun onClear() {
        interactor.clear()
    }

    override val itemsCount: Int
        get() = dataSource.itemsCount

    override fun onBottomReached() {
        executor.submit {
            loadNextPage(false)
        }

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

        dataSource.handleUpdatedWallets(wallets)
        viewItems.clear()
        loadNextPage(true)
    }

    override fun onUpdateSelectedWallets(selectedWallets: List<Wallet>) {
        dataSource.setWallets(selectedWallets)
        viewItems.clear()
        loadNextPage(true)
    }

    override fun didFetchRecords(records: Map<Wallet, List<TransactionRecord>>, initial: Boolean) {
        dataSource.handleNextRecords(records)

        val currentItemsCount = dataSource.itemsCount
        val insertedCount = dataSource.increasePage()
        if (insertedCount > 0) {
            val toInsert = List(insertedCount) {
                viewItemForIndex(currentItemsCount + it)
            }
            viewItems.addAll(toInsert)
            view?.setItems(viewItemsCopy)
        } else if (initial) {
            view?.setItems(listOf())
        }
        loading.set(false)
    }

    override fun onUpdateLastBlockHeight(wallet: Wallet, lastBlockHeight: Int) {
        val oldBlockHeight = metadataDataSource.getLastBlockHeight(wallet)

        metadataDataSource.setLastBlockHeight(lastBlockHeight, wallet)

        if (oldBlockHeight == null) {
            resetViewItems()
        } else {
            val threshold = metadataDataSource.getConfirmationThreshold(wallet)

            resetViewItemsByIndexes(dataSource.itemIndexesForPending(wallet, oldBlockHeight - threshold))
        }
    }

    override fun onUpdateBaseCurrency() {
        resetViewItems()
    }

    override fun didFetchRate(rateValue: BigDecimal, coin: Coin, currency: Currency, timestamp: Long) {
        resetViewItemsByIndexes(dataSource.itemIndexesForTimestamp(coin, timestamp))
    }

    override fun didUpdateRecords(records: List<TransactionRecord>, wallet: Wallet) {
        if (dataSource.handleUpdatedRecords(records, wallet)) {
            resetViewItems()
        }
    }

    override fun onConnectionRestore() {
        resetViewItems()
    }

    private fun viewItemForIndex(index: Int): TransactionViewItem {
        val transactionItem = dataSource.itemForIndex(index)
        val wallet = transactionItem.wallet
        val lastBlockHeight = metadataDataSource.getLastBlockHeight(wallet)
        val threshold = metadataDataSource.getConfirmationThreshold(wallet)
        val rate = interactor.getRate(wallet.coin, transactionItem.record.timestamp)

        return factory.item(wallet, transactionItem, lastBlockHeight, threshold, rate)
    }

    private fun resetViewItems() {
        viewItems = MutableList(itemsCount) { viewItemForIndex(it) }
        view?.setItems(viewItemsCopy)
    }

    private fun resetViewItemsByIndexes(indexes: List<Int>) {
        if (indexes.isEmpty()) return

        indexes.forEach {
            viewItems[it] = viewItemForIndex(it)
        }

        view?.setItems(viewItemsCopy)
    }

    private fun loadNextPage(initial: Boolean) {
        if (loading.get()) return
        loading.set(true)

        interactor.fetchRecords(dataSource.getFetchDataList(), initial)
    }
}
