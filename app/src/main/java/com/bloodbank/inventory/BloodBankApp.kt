package com.bloodbank.inventory

import android.app.Application
import com.bloodbank.inventory.data.AppDatabase
import com.bloodbank.inventory.data.InventoryRepository

class BloodBankApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: InventoryRepository by lazy { InventoryRepository(database) }
}
