package io.horizontalsystems.bankwallet.core.adapters

import android.content.Context
import io.horizontalsystems.bankwallet.core.*
import io.horizontalsystems.bankwallet.core.utils.AddressParser
import io.horizontalsystems.bankwallet.entities.AccountType
import io.horizontalsystems.bankwallet.entities.SyncMode
import io.horizontalsystems.bankwallet.entities.TransactionRecord
import io.horizontalsystems.bankwallet.entities.Wallet
import io.horizontalsystems.bankwallet.viewHelpers.DateHelper
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.dashkit.DashKit
import io.horizontalsystems.dashkit.DashKit.NetworkType
import io.horizontalsystems.dashkit.models.DashTransactionInfo
import io.reactivex.Single
import java.math.BigDecimal
import java.util.*

class DashAdapter(override val kit: DashKit, addressParser: AddressParser, private val feeRateProvider: IFeeRateProvider) :
        BitcoinBaseAdapter(kit, addressParser), DashKit.Listener {

    constructor(wallet: Wallet, testMode: Boolean, feeRateProvider: IFeeRateProvider) :
            this(createKit(wallet, testMode), AddressParser("dash", true), feeRateProvider)

    init {
        kit.listener = this
    }

    override fun getFeeRate(feeRatePriority: FeeRatePriority): Long {
        return feeRateProvider.dashFeeRate(feeRatePriority)
    }

    //
    // BitcoinBaseAdapter
    //

    override val satoshisInBitcoin: BigDecimal = BigDecimal.valueOf(Math.pow(10.0, decimal.toDouble()))

    //
    // DashKit Listener
    //

    override fun onBalanceUpdate(balance: Long) {
        balanceUpdatedSubject.onNext(Unit)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        lastBlockHeightUpdatedSubject.onNext(Unit)
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        when (state) {
            is BitcoinCore.KitState.Synced -> {
                if (this.state !is AdapterState.Synced) {
                    this.state = AdapterState.Synced
                }
            }
            is BitcoinCore.KitState.NotSynced -> {
                if (this.state !is AdapterState.NotSynced) {
                    this.state = AdapterState.NotSynced
                }
            }
            is BitcoinCore.KitState.Syncing -> {
                this.state.let { currentState ->
                    val newProgress = (state.progress * 100).toInt()
                    val newDate = kit.lastBlockInfo?.timestamp?.let { Date(it * 1000) }

                    if (currentState is AdapterState.Syncing && currentState.progress == newProgress) {
                        val currentDate = currentState.lastBlockDate
                        if (newDate != null && currentDate != null && DateHelper.isSameDay(newDate, currentDate)) {
                            return
                        }
                    }

                    this.state = AdapterState.Syncing(newProgress, newDate)
                }
            }
        }
    }

    override fun onTransactionsUpdate(inserted: List<DashTransactionInfo>, updated: List<DashTransactionInfo>) {
        val records = mutableListOf<TransactionRecord>()

        for (info in inserted) {
            records.add(transactionRecord(info))
        }

        for (info in updated) {
            records.add(transactionRecord(info))
        }

        transactionRecordsSubject.onNext(records)
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        // ignored for now
    }

    override fun getTransactions(from: Pair<String, Int>?, limit: Int): Single<List<TransactionRecord>> {
        return kit.transactions(from?.first, limit).map { it.map { tx -> transactionRecord(tx) } }
    }

    companion object {

        private fun getNetworkType(testMode: Boolean) =
                if (testMode) NetworkType.TestNet else NetworkType.MainNet

        private fun createKit(wallet: Wallet, testMode: Boolean): DashKit {
            val account = wallet.account
            if (account.type is AccountType.Mnemonic) {
                return DashKit(App.instance, account.type.words, account.id, syncMode = SyncMode.fromSyncMode(account.defaultSyncMode), networkType = getNetworkType(testMode))
            }

            throw UnsupportedAccountException()
        }

        fun clear(context: Context, walletId: String, testMode: Boolean) {
            DashKit.clear(context, getNetworkType(testMode), walletId)
        }
    }
}
