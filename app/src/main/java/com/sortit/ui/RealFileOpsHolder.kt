package com.sortit.ui

import com.sortit.repo.RealFileOps

// Holder singleton sederhana untuk FileOps di UI (monitor). DI nyata via Hilt/Koin di v2.
object RealFileOpsHolder {
    val ops = RealFileOps()
}
