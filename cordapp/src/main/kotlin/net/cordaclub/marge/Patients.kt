package net.cordaclub.marge

import net.corda.core.identity.CordaX500Name

object Patients {
    // Tudor - how about a selection from https://github.com/moby/moby/blob/master/pkg/namesgenerator/names-generator.go
    val allPatients = listOf(
            Patient("Joan Clarke", "ab123456b"),
            Patient("Seymour Roger Cray", "ab123456c"),
            Patient("Dorothy Vaughan", "ab123456d"),
            Patient("Steve Wozniak", "ab123456e")
    )
}

object Insurers {
    val allInsurers = listOf(
            CordaX500Name("General Insurer", "Delhi", "IN"),
            CordaX500Name("Frugal Insurer", "Tokyo", "JP")
    )
}