package com.bloodbank.inventory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloodbank.inventory.BloodBankApp
import com.bloodbank.inventory.data.AuditLogEntity
import com.bloodbank.inventory.data.ClassificationEntity
import com.bloodbank.inventory.data.MovementEntity
import com.bloodbank.inventory.data.ReceivingDraft
import com.bloodbank.inventory.data.ReportData
import com.bloodbank.inventory.data.ShiftEntity
import com.bloodbank.inventory.domain.BalanceRow
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.InventoryException
import com.bloodbank.inventory.domain.MovementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as BloodBankApp).repository

    val openShift: StateFlow<ShiftEntity?> =
        repo.observeOpenShift().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val classifications: StateFlow<List<ClassificationEntity>> =
        repo.observeClassifications().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val balances: StateFlow<List<BalanceRow>> = openShift
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.observeBalances(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val movements: StateFlow<List<MovementEntity>> = openShift
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.observeMovements(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val closedShifts: StateFlow<List<ShiftEntity>> =
        repo.observeClosedShifts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val auditLog: StateFlow<List<AuditLogEntity>> =
        repo.observeAudit().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hospitalName: StateFlow<String?> =
        repo.observeHospitalName().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** يشغّل عملية ويعيد النتيجة: null عند النجاح أو رسالة الخطأ. */
    private fun <T> exec(block: suspend () -> T, onDone: (Result<T>) -> Unit) {
        viewModelScope.launch {
            val result = try {
                Result.success(block())
            } catch (e: InventoryException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception("حدث خطأ غير متوقع: ${e.message}"))
            }
            onDone(result)
        }
    }

    fun prepareReceiving(onDone: (Result<ReceivingDraft>) -> Unit) = exec({ repo.prepareReceiving() }, onDone)

    fun confirmReceiving(
        shiftNumber: String, receiver: String, deliverer: String,
        actual: Map<Long, Int>, note: String, onDone: (Result<Long>) -> Unit
    ) = exec({ repo.confirmReceiving(shiftNumber, receiver, deliverer, actual, note) }, onDone)

    fun addMovement(
        classificationId: Long, type: MovementType, quantity: Int, note: String, requestId: String,
        onDone: (Result<Boolean>) -> Unit
    ) {
        val shift = openShift.value ?: return onDone(Result.failure(Exception("لا توجد خفارة مفتوحة")))
        exec({ repo.addMovement(shift.id, classificationId, type, quantity, note, requestId) }, onDone)
    }

    fun voidMovement(id: Long, reason: String, onDone: (Result<Unit>) -> Unit) =
        exec({ repo.voidMovement(id, reason) }, onDone)

    fun closeShift(handoverTo: String, notes: String, onDone: (Result<Long>) -> Unit) {
        val shift = openShift.value ?: return onDone(Result.failure(Exception("لا توجد خفارة مفتوحة")))
        exec({ repo.closeShift(shift.id, handoverTo, notes); shift.id }, onDone)
    }

    fun loadReport(shiftId: Long, onDone: (Result<ReportData>) -> Unit) = exec({ repo.getReport(shiftId) }, onDone)

    fun setHospitalName(name: String, onDone: (Result<Unit>) -> Unit) = exec({ repo.setHospitalName(name) }, onDone)
    fun addClassification(category: Category, name: String, onDone: (Result<Unit>) -> Unit) =
        exec({ repo.addClassification(category, name) }, onDone)
    fun renameClassification(id: Long, name: String, onDone: (Result<Unit>) -> Unit) =
        exec({ repo.renameClassification(id, name) }, onDone)
    fun setClassificationActive(id: Long, active: Boolean) = exec({ repo.setClassificationActive(id, active) }) {}
    fun moveClassification(id: Long, direction: Int) = exec({ repo.moveClassification(id, direction) }) {}

    fun audit(action: String, details: String) = exec({ repo.audit(openShift.value?.id, action, details) }) {}
}
