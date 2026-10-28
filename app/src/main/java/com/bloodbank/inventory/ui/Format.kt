package com.bloodbank.inventory.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تنسيق التاريخ والوقت بأرقام لاتينية واضحة. */
object Format {
    private val dateTime = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)
    private val date = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val time = SimpleDateFormat("HH:mm", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun dateTime(ms: Long?): String = if (ms == null) "-" else dateTime.format(Date(ms))
    fun date(ms: Long?): String = if (ms == null) "-" else date.format(Date(ms))
    fun time(ms: Long?): String = if (ms == null) "-" else time.format(Date(ms))
    fun fileStamp(ms: Long = System.currentTimeMillis()): String = fileStamp.format(Date(ms))

    fun signed(n: Int): String = if (n > 0) "+$n" else n.toString()
}
