package com.invoka.app

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "business_profile")
data class BusinessProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val taxNumber: String = "",
    val defaultTaxRate: Double = 0.0,
    val defaultPaper: String = "A4"
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "items")
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double = 0.0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoices", indices = [Index(value = ["number"], unique = true)])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val dateMillis: Long,
    val customerId: Long? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val delivery: Double = 0.0,
    val otherName: String = "",
    val otherAmount: Double = 0.0,
    val discount: Double = 0.0,
    val taxRate: Double = 0.0,
    val paid: Double = 0.0,
    val notes: String = "",
    val cancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [ForeignKey(
        entity = Invoice::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
data class InvoiceLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val position: Int,
    val name: String,
    val qty: Double,
    val price: Double,
    val discount: Double = 0.0
)

data class InvoiceWithLines(
    @Embedded val invoice: Invoice,
    @Relation(parentColumn = "id", entityColumn = "invoiceId") val lines: List<InvoiceLine>
)
