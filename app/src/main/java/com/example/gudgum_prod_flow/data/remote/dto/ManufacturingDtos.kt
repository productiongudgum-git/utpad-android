package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Flavors / SKUs ──────────────────────────────────────────────
@Serializable
data class FlavorDto(
    val id: String,
    val name: String,
    val code: String,
    val active: Boolean = true,
    @SerialName("recipe_id") val recipeId: String? = null,
    @SerialName("yield_threshold") val yieldThreshold: Double? = null,
    @SerialName("shelf_life_days") val shelfLifeDays: Int? = null,
)

// ── Recipe Ingredients ──────────────────────────────────────────
@Serializable
data class IngredientDto(
    val id: String,
    val name: String,
    val unit: String,
    val active: Boolean = true,
    @SerialName("default_supplier_id") val defaultSupplierId: String? = null,
    @SerialName("default_supplier_name") val defaultSupplierName: String? = null,
)

// ── Recipe Lines (BOM) ──────────────────────────────────────────
@Serializable
data class RecipeLineDto(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("ingredient_id") val ingredientId: String,
    val qty: Double,
    // Joined fields from recipe_ingredients
    val ingredient: IngredientDto? = null,
)

// ── Production ──────────────────────────────────────────────────
@Serializable
data class SubmitProductionBatchRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("planned_yield") val plannedYield: Double? = null,
)

@Serializable
data class SubmitBatchIngredientRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("ingredient_id") val ingredientId: String,
    @SerialName("planned_qty") val plannedQty: Double,
    @SerialName("actual_qty") val actualQty: Double,
)

@Serializable
data class ProductionBatchDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("worker_id") val workerId: String,
    val status: String = "open",
    @SerialName("planned_yield") val plannedYield: Double? = null,
    @SerialName("actual_yield") val actualYield: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Joined
    @SerialName("sku_name") val skuName: String? = null,
)

// ── Packing ─────────────────────────────────────────────────────
@Serializable
data class SubmitPackingSessionRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("boxes_packed") val boxesPacked: Int,
)

@Serializable
data class OpenBatchDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("planned_yield") val plannedYield: Double? = null,
    @SerialName("actual_yield") val actualYield: Double? = null,
    val status: String = "open",
    // Computed locally from packing_sessions
    var totalPacked: Int = 0,
    var hasOverdueAlert: Boolean = false,
    // Joined flavor name
    @SerialName("sku_name") val skuName: String? = null,
)

// ── Dispatch / FIFO ─────────────────────────────────────────────
@Serializable
data class FifoAllocationRequest(
    @SerialName("p_sku_id") val skuId: String,
    @SerialName("p_qty") val qty: Int,
)

@Serializable
data class FifoAllocationLine(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("boxes_available") val boxesAvailable: Int,
    @SerialName("boxes_to_take") val boxesToTake: Int,
)

@Serializable
data class SubmitDispatchEventRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("boxes_dispatched") val boxesDispatched: Int,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("dispatch_date") val dispatchDate: String,
    @SerialName("worker_id") val workerId: String,
)

@Serializable
data class SkuStockDto(
    @SerialName("sku_id") val skuId: String,
    @SerialName("sku_name") val skuName: String,
    @SerialName("sku_code") val skuCode: String,
    @SerialName("boxes_available") val boxesAvailable: Int,
)

// ── Inwarding ───────────────────────────────────────────────────
@Serializable
data class SubmitInwardEventRequest(
    @SerialName("ingredient_id") val ingredientId: String,
    val qty: Double,
    val unit: String,
    @SerialName("inward_date") val inwardDate: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("lot_ref") val lotRef: String? = null,
    val supplier: String? = null,
    @SerialName("vendor_id") val vendorId: String? = null,
    @SerialName("worker_id") val workerId: String,
)

// ── Suppliers ────────────────────────────────────────────────────
@Serializable
data class SupplierDto(
    val id: String,
    val name: String,
    val contact: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateSupplierRequest(
    val id: String,
    val name: String,
    val contact: String? = null,
    val active: Boolean = true,
)

@Serializable
data class CreateIngredientRequest(
    val id: String,
    val name: String,
    val unit: String,
    val active: Boolean = true,
    @SerialName("default_supplier_id") val defaultSupplierId: String? = null,
    @SerialName("default_supplier_name") val defaultSupplierName: String? = null,
)

@Serializable
data class CreateIngredientSupplierLinkRequest(
    val id: String,
    @SerialName("ingredient_id") val ingredientId: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("is_default") val isDefault: Boolean = true,
)

// ── Returns ─────────────────────────────────────────────────────
@Serializable
data class SubmitReturnEventRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("qty_returned") val qtyReturned: Int,
    val reason: String? = null,
    @SerialName("return_date") val returnDate: String,
    @SerialName("worker_id") val workerId: String,
)

// ── Dispatched Batches (for Returns selector) ────────────────────
@Serializable
data class DispatchedBatchDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("sku_name") val skuName: String? = null,
    @SerialName("boxes_dispatched") val boxesDispatched: Int,
    @SerialName("dispatch_date") val dispatchDate: String,
)

// ── Gud Gum Tables (gg_ prefix) ─────────────────────────────────
@Serializable
data class GgUserDto(
    val id: String,
    @SerialName("mobile_number") val mobileNumber: String,
    val name: String,
    val role: String,
    val modules: List<String> = emptyList(),
    val active: Boolean = true,
)

@Serializable
data class GgCustomerDto(
    val id: String,
    val name: String,
    @SerialName("contact_person") val contactPerson: String? = null,
    val phone: String? = null,
)

@Serializable
data class GgBatchDto(
    val id: String,
    @SerialName("batch_code") val batchCode: String,
    val status: String = "open",
)

@Serializable
data class GgPackingRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("quantity_kg") val quantityKg: Double,
    @SerialName("boxes_count") val boxesCount: Int,
    @SerialName("packing_date") val packingDate: String,
    @SerialName("recorded_by") val recordedBy: String,
)

@Serializable
data class GgDispatchRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("quantity_dispatched") val quantityDispatched: Double,
    @SerialName("dispatch_date") val dispatchDate: String,
    @SerialName("recorded_by") val recordedBy: String,
)

// ── Gud Gum Production DTOs ──────────────────────────────────────
@Serializable
data class GgFlavorDto(
    val id: String,
    val name: String,
    val code: String,
    val active: Boolean = true,
)

@Serializable
data class GgIngredientDto(
    val id: String,
    val name: String,
    @SerialName("default_unit") val defaultUnit: String,
)

@Serializable
data class GgRecipeIngredientDto(
    val unit: String,
    val quantity: Double,
    val ingredientId: String,
    val ingredientName: String,
)

@Serializable
data class GgRecipeDto(
    val id: String,
    @SerialName("flavor_id") val flavorId: String,
    @SerialName("batch_size_kg") val batchSizeKg: Double? = null,
    val ingredients: List<GgRecipeIngredientDto> = emptyList(),
)

@Serializable
data class GgVendorDto(
    val id: String,
    val name: String,
    val phone: String? = null,
)

@Serializable
data class GgBatchInsertRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("flavor_id") val flavorId: String,
    @SerialName("planned_qty_kg") val plannedQtyKg: Double? = null,
    @SerialName("production_date") val productionDate: String,
    @SerialName("created_by") val createdBy: String,
    val status: String = "open",
)

@Serializable
data class GgProductionRecordRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("ingredient_id") val ingredientId: String,
    @SerialName("planned_qty") val plannedQty: Double,
    @SerialName("actual_qty") val actualQty: Double,
    @SerialName("recorded_by") val recordedBy: String,
)

@Serializable
data class GgInwardingRequest(
    @SerialName("ingredient_id") val ingredientId: String,
    val quantity: Double,
    val unit: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("received_date") val receivedDate: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("bill_number") val billNumber: String? = null,
    @SerialName("bill_photo_url") val billPhotoUrl: String? = null,
    @SerialName("recorded_by") val recordedBy: String,
)

@Serializable
data class GgReturnRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("qty_returned") val qtyReturned: Int,
    val reason: String? = null,
    @SerialName("return_date") val returnDate: String,
    @SerialName("recorded_by") val recordedBy: String,
)

@Serializable
data class GgVendorInsertRequest(
    val name: String,
    val phone: String? = null,
)

@Serializable
data class GgIngredientInsertRequest(
    val name: String,
    @SerialName("default_unit") val defaultUnit: String,
)

@Serializable
data class GgDispatchSummaryDto(
    val id: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("dispatch_date") val dispatchDate: String,
    @SerialName("quantity_dispatched") val quantityDispatched: Double,
    val batch: GgBatchDto? = null,
)
