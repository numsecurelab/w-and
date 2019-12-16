package io.horizontalsystems.bankwallet.modules.transactions

import io.horizontalsystems.bankwallet.entities.Wallet

class TransactionMetadataDataSource {

    private val lastBlockHeights = mutableMapOf<Wallet, Int>()
    private val thresholds = mutableMapOf<Wallet, Int>()

    fun setLastBlockHeight(lastBlockHeight: Int, wallet: Wallet) {
        lastBlockHeights[wallet] = lastBlockHeight
    }

    fun getLastBlockHeight(wallet: Wallet): Int? {
        return lastBlockHeights[wallet]
    }

    fun setConfirmationThreshold(confirmationThreshold: Int, wallet: Wallet) {
        thresholds[wallet] = confirmationThreshold
    }

    fun getConfirmationThreshold(wallet: Wallet): Int =
            thresholds[wallet] ?: 1

}
