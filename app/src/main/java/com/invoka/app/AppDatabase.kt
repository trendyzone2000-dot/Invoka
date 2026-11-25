package com.invoka.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvokaDao {
    // business
    @Query("SELECT * FROM business_profile WHERE id = 1")
    fun businessFlow(): Flow<BusinessProfile?>

    @Query("SELECT * FROM business_profile WHERE id = 1")
    suspend fun business(): BusinessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBusiness(b: BusinessProfile)

    // customers
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun customersFlow(): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomer(c: Customer): Long

    // items
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun itemsFlow(): Flow<List<CatalogItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveItem(i: CatalogItem): Long

    // invoices
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY number DESC")
    fun invoicesFlow(): Flow<List<InvoiceWithLines>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun invoice(id: Long): InvoiceWithLines?

    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM invoices")
    suspend fun nextNumber(): Int

    @Insert
    suspend fun insertInvoice(i: Invoice): Long

    @Update
    suspend fun updateInvoice(i: Invoice)

    @Insert
    suspend fun insertLines(l: List<InvoiceLine>)

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :id")
    suspend fun deleteLines(id: Long)

    @Query("UPDATE invoices SET cancelled = :c WHERE id = :id")
    suspend fun setCancelled(id: Long, c: Boolean)

    // backup / restore
    @Query("SELECT * FROM customers")
    suspend fun allCustomers(): List<Customer>

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<CatalogItem>

    @Query("SELECT * FROM invoices")
    suspend fun allInvoices(): List<Invoice>

    @Query("SELECT * FROM invoice_lines")
    suspend fun allLines(): List<InvoiceLine>

    @Insert
    suspend fun insertCustomers(l: List<Customer>)

    @Insert
    suspend fun insertItems(l: List<CatalogItem>)

    @Insert
    suspend fun insertInvoices(l: List<Invoice>)

    @Query("DELETE FROM invoice_lines")
    suspend fun deleteAllLines()

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM business_profile")
    suspend fun deleteBusiness()
}

@Database(
    entities = [BusinessProfile::class, Customer::class, CatalogItem::class, Invoice::class, InvoiceLine::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): InvokaDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "invoka.db")
                .build().also { inst = it }
        }
    }
}
