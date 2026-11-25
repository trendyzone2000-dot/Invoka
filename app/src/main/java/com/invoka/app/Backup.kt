package com.invoka.app

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Backup {
    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    suspend fun export(ctx: Context, dao: InvokaDao): String {
        val root = JSONObject()
        root.put("app", "Invoka")
        root.put("schema", 1)
        val b = dao.business() ?: BusinessProfile()
        root.put(
            "business", JSONObject()
                .put("name", b.name).put("phone", b.phone).put("address", b.address)
                .put("taxNumber", b.taxNumber).put("defaultTaxRate", b.defaultTaxRate)
                .put("defaultPaper", b.defaultPaper)
        )
        val cs = JSONArray()
        dao.allCustomers().forEach { c ->
            cs.put(
                JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone)
                    .put("address", c.address).put("archived", c.archived).put("createdAt", c.createdAt)
            )
        }
        root.put("customers", cs)
        val its = JSONArray()
        dao.allItems().forEach { i ->
            its.put(
                JSONObject().put("id", i.id).put("name", i.name).put("price", i.price)
                    .put("archived", i.archived).put("createdAt", i.createdAt)
            )
        }
        root.put("items", its)
        val invs = JSONArray()
        dao.allInvoices().forEach { i ->
            invs.put(
                JSONObject().put("id", i.id).put("number", i.number).put("dateMillis", i.dateMillis)
                    .put("customerId", i.customerId ?: JSONObject.NULL)
                    .put("customerName", i.customerName).put("customerPhone", i.customerPhone)
                    .put("customerAddress", i.customerAddress).put("delivery", i.delivery)
                    .put("otherName", i.otherName).put("otherAmount", i.otherAmount)
                    .put("discount", i.discount).put("taxRate", i.taxRate).put("paid", i.paid)
                    .put("notes", i.notes).put("cancelled", i.cancelled).put("createdAt", i.createdAt)
            )
        }
        root.put("invoices", invs)
        val ls = JSONArray()
        dao.allLines().forEach { l ->
            ls.put(
                JSONObject().put("id", l.id).put("invoiceId", l.invoiceId).put("position", l.position)
                    .put("name", l.name).put("qty", l.qty).put("price", l.price).put("discount", l.discount)
            )
        }
        root.put("lines", ls)
        val logo = File(ctx.filesDir, "logo.png")
        if (logo.exists()) root.put("logo", Base64.encodeToString(logo.readBytes(), Base64.NO_WRAP))
        return root.toString()
    }

    suspend fun restore(ctx: Context, db: AppDatabase, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "Invoka") { "not an Invoka backup" }
        val bo = root.optJSONObject("business")
        val business = BusinessProfile(
            name = bo?.optString("name", "") ?: "",
            phone = bo?.optString("phone", "") ?: "",
            address = bo?.optString("address", "") ?: "",
            taxNumber = bo?.optString("taxNumber", "") ?: "",
            defaultTaxRate = bo?.optDouble("defaultTaxRate", 0.0) ?: 0.0,
            defaultPaper = bo?.optString("defaultPaper", "A4") ?: "A4"
        )
        val customers = (root.optJSONArray("customers") ?: JSONArray()).objs().map {
            Customer(
                id = it.getLong("id"), name = it.getString("name"), phone = it.optString("phone", ""),
                address = it.optString("address", ""), archived = it.optBoolean("archived", false),
                createdAt = it.optLong("createdAt", System.currentTimeMillis())
            )
        }
        val items = (root.optJSONArray("items") ?: JSONArray()).objs().map {
            CatalogItem(
                id = it.getLong("id"), name = it.getString("name"), price = it.optDouble("price", 0.0),
                archived = it.optBoolean("archived", false),
                createdAt = it.optLong("createdAt", System.currentTimeMillis())
            )
        }
        val invoices = (root.optJSONArray("invoices") ?: JSONArray()).objs().map {
            Invoice(
                id = it.getLong("id"), number = it.getInt("number"), dateMillis = it.getLong("dateMillis"),
                customerId = if (it.isNull("customerId")) null else it.getLong("customerId"),
                customerName = it.optString("customerName", ""), customerPhone = it.optString("customerPhone", ""),
                customerAddress = it.optString("customerAddress", ""), delivery = it.optDouble("delivery", 0.0),
                otherName = it.optString("otherName", ""), otherAmount = it.optDouble("otherAmount", 0.0),
                discount = it.optDouble("discount", 0.0), taxRate = it.optDouble("taxRate", 0.0),
                paid = it.optDouble("paid", 0.0), notes = it.optString("notes", ""),
                cancelled = it.optBoolean("cancelled", false),
                createdAt = it.optLong("createdAt", System.currentTimeMillis())
            )
        }
        val lines = (root.optJSONArray("lines") ?: JSONArray()).objs().map {
            InvoiceLine(
                id = it.getLong("id"), invoiceId = it.getLong("invoiceId"), position = it.getInt("position"),
                name = it.optString("name", ""), qty = it.optDouble("qty", 1.0), price = it.optDouble("price", 0.0),
                discount = it.optDouble("discount", 0.0)
            )
        }
        val dao = db.dao()
        db.withTransaction {
            dao.deleteAllLines(); dao.deleteAllInvoices(); dao.deleteAllCustomers()
            dao.deleteAllItems(); dao.deleteBusiness()
            dao.saveBusiness(business)
            dao.insertCustomers(customers)
            dao.insertItems(items)
            dao.insertInvoices(invoices)
            dao.insertLines(lines)
        }
        val logo = File(ctx.filesDir, "logo.png")
        val b64 = root.optString("logo", "")
        if (b64.isNotEmpty()) logo.writeBytes(Base64.decode(b64, Base64.NO_WRAP)) else logo.delete()
    }
}
