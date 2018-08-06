package net.cordaclub.marge.util

import io.cordite.commons.utils.transaction
import net.corda.core.node.ServiceHub
import net.cordaclub.marge.TreatmentState
import rx.Observable

fun ServiceHub.listenForTreatments() : Observable<List<TreatmentState>> {
    return this.transaction {
        this.vaultService.trackBy(TreatmentState::class.java).updates.map { it.produced.map { it.state.data } }
    }
}

fun ServiceHub.loadTreatments() : List<TreatmentState> {
    return this.transaction {
        this.vaultService.queryBy(TreatmentState::class.java).states.map { it.state.data }
    }
}
