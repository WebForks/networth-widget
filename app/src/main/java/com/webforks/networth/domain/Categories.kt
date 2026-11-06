package com.webforks.networth.domain

import com.webforks.networth.data.db.AccountEntity

enum class Category(val label: String) {
    CASH("Cash"),
    INVESTMENTS("Investments"),
    RETIREMENT("Retirement"),
    CREDIT("Credit cards"),
    LOANS("Loans"),
    OTHER("Other"),
}

private val retirementSubtypes = setOf(
    "401a", "401k", "403b", "457b", "ira", "roth", "roth 401k", "sep ira", "simple ira",
    "sarsep", "pension", "profit sharing plan", "retirement", "keogh", "tsp", "thrift savings plan",
    "lira", "lrif", "lrsp", "prif", "rdsp", "resp", "rlif", "rrif", "rrsp", "tfsa", "non-taxable brokerage account",
)

val AccountEntity.category: Category
    get() = when (type) {
        "depository" -> Category.CASH
        "credit" -> Category.CREDIT
        "loan" -> Category.LOANS
        "investment", "brokerage" ->
            if (subtype?.lowercase() in retirementSubtypes) Category.RETIREMENT else Category.INVESTMENTS
        else -> Category.OTHER
    }

/** "Roth IRA", "401k", "Checking" — a readable account kind for list rows. */
val AccountEntity.kindLabel: String
    get() = when (val s = subtype?.lowercase()) {
        null -> type.replaceFirstChar { it.uppercase() }
        "ira" -> "IRA"
        "roth" -> "Roth IRA"
        "roth 401k" -> "Roth 401(k)"
        "401k" -> "401(k)"
        "403b" -> "403(b)"
        "457b" -> "457(b)"
        "hsa" -> "HSA"
        "cd" -> "CD"
        else -> s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
