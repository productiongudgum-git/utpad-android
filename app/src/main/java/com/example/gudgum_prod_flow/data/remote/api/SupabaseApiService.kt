package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {

    // ── RPC Functions ────────────────────────────────────────────
    @POST("rest/v1/rpc/fn_fifo_allocate")
    suspend fun fifoAllocate(
        @Body request: FifoAllocationRequest,
    ): Response<List<FifoAllocationLine>>

    // ── Flavors / SKUs ───────────────────────────────────────────
    @GET("rest/v1/flavor_definitions")
    suspend fun getFlavors(
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,name,code,active,recipe_id,yield_threshold,shelf_life_days",
        @Query("order") order: String = "name.asc",
    ): Response<List<FlavorDto>>

    // ── Recipe Lines (BOM) ───────────────────────────────────────
    @GET("rest/v1/recipe_lines")
    suspend fun getRecipeLines(
        @Query("recipe_id") recipeId: String, // e.g. "eq.rcp-spearmint"
        @Query("select") select: String = "recipe_id,ingredient_id,qty,ingredient:recipe_ingredients(id,name,unit,active)",
    ): Response<List<RecipeLineDto>>

    // ── Ingredients ──────────────────────────────────────────────
    @GET("rest/v1/recipe_ingredients")
    suspend fun getIngredients(
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,name,unit,active,default_supplier_id,default_supplier_name",
        @Query("order") order: String = "name.asc",
    ): Response<List<IngredientDto>>

    @POST("rest/v1/recipe_ingredients")
    suspend fun insertIngredient(
        @Body request: CreateIngredientRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<IngredientDto>>

    @POST("rest/v1/ingredient_suppliers")
    suspend fun insertIngredientSupplierLink(
        @Body request: CreateIngredientSupplierLinkRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Suppliers ────────────────────────────────────────────────
    @GET("rest/v1/suppliers")
    suspend fun getSuppliers(
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,name,contact,active",
        @Query("order") order: String = "name.asc",
    ): Response<List<SupplierDto>>

    @POST("rest/v1/suppliers")
    suspend fun insertSupplier(
        @Body request: CreateSupplierRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<SupplierDto>>

    // ── Production Batches ───────────────────────────────────────
    @POST("rest/v1/production_batches")
    suspend fun insertProductionBatch(
        @Body request: SubmitProductionBatchRequest,
        @Header("Prefer") prefer: String = "return=minimal,resolution=merge-duplicates",
    ): Response<Unit>

    @DELETE("rest/v1/production_batch_ingredients")
    suspend fun deleteBatchIngredients(
        @Query("batch_code") batchCode: String, // e.g. "eq.BI0326-001"
    ): Response<Unit>

    @POST("rest/v1/production_batch_ingredients")
    suspend fun insertBatchIngredients(
        @Body request: List<SubmitBatchIngredientRequest>,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @GET("rest/v1/production_batches")
    suspend fun getOpenBatches(
        @Query("status") status: String = "eq.open",
        @Query("select") select: String = "batch_code,sku_id,production_date,planned_yield,actual_yield,status,flavor:flavor_definitions(name)",
        @Query("order") order: String = "production_date.asc",
    ): Response<List<ProductionBatchDto>>

    @GET("rest/v1/production_batches")
    suspend fun getPackedBatches(
        @Query("status") status: String = "eq.packed",
        @Query("select") select: String = "batch_code,sku_id,production_date,planned_yield,actual_yield,status,flavor:flavor_definitions(name,code)",
        @Query("order") order: String = "production_date.asc",
    ): Response<List<ProductionBatchDto>>

    // ── Packing Sessions ─────────────────────────────────────────
    @POST("rest/v1/packing_sessions")
    suspend fun insertPackingSession(
        @Body request: SubmitPackingSessionRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Dispatch Events ──────────────────────────────────────────
    @POST("rest/v1/dispatch_events")
    suspend fun insertDispatchEvent(
        @Body request: SubmitDispatchEventRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Returns Events ───────────────────────────────────────────
    @POST("rest/v1/returns_events")
    suspend fun insertReturnEvent(
        @Body request: SubmitReturnEventRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Inward Events ────────────────────────────────────────────
    @POST("rest/v1/inward_events")
    suspend fun insertInwardEvent(
        @Body request: SubmitInwardEventRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Dispatched Batches (for Returns selector) ────────────────
    @GET("rest/v1/dispatch_events")
    suspend fun getDispatchedBatches(
        @Query("select") select: String = "batch_code,sku_id,boxes_dispatched,dispatch_date,flavor:flavor_definitions(name)",
        @Query("order") order: String = "dispatch_date.desc",
        @Query("limit") limit: Int = 50,
    ): Response<List<DispatchedBatchDto>>

    // ── Inventory (for Dispatch stock check) ────────────────────
    @GET("rest/v1/inventory_finished_goods")
    suspend fun getFinishedGoodsStock(
        @Query("sku_id") skuId: String, // e.g. "eq.flv-mnt"
        @Query("select") select: String = "sku_id,batch_code,boxes_available,boxes_returned",
        @Query("boxes_available") boxesFilter: String = "gt.0",
    ): Response<List<Map<String, Any>>>

    // ── Gud Gum Tables (gg_ prefix) ─────────────────────────────
    @GET("rest/v1/gg_users")
    suspend fun getGgUserByPhone(
        @Query("mobile_number") mobileNumber: String, // "eq.{phone}"
        @Query("role") role: String = "eq.worker",
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,mobile_number,name,role,modules,active",
    ): Response<List<GgUserDto>>

    @GET("rest/v1/gg_customers")
    suspend fun getGgCustomers(
        @Query("select") select: String = "id,name,contact_person,phone",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgCustomerDto>>

    @GET("rest/v1/gg_batches")
    suspend fun getGgBatchByCode(
        @Query("batch_code") batchCode: String, // "eq.{code}"
        @Query("select") select: String = "id,batch_code,status",
    ): Response<List<GgBatchDto>>

    @POST("rest/v1/gg_packing")
    suspend fun insertGgPacking(
        @Body request: GgPackingRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @POST("rest/v1/gg_dispatch")
    suspend fun insertGgDispatch(
        @Body request: GgDispatchRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Gud Gum Production (gg_ tables) ─────────────────────────────
    @GET("rest/v1/gg_flavors")
    suspend fun getGgFlavors(
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,name,code,active",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgFlavorDto>>

    @GET("rest/v1/gg_recipes")
    suspend fun getGgRecipe(
        @Query("flavor_id") flavorId: String, // "eq.{uuid}"
        @Query("select") select: String = "id,flavor_id,batch_size_kg,ingredients",
    ): Response<List<GgRecipeDto>>

    @GET("rest/v1/gg_ingredients")
    suspend fun getGgIngredients(
        @Query("select") select: String = "id,name,default_unit",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgIngredientDto>>

    @GET("rest/v1/gg_vendors")
    suspend fun getGgVendors(
        @Query("select") select: String = "id,name,phone",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgVendorDto>>

    @POST("rest/v1/gg_batches")
    suspend fun insertGgBatch(
        @Body request: GgBatchInsertRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<GgBatchDto>>

    @POST("rest/v1/gg_production")
    suspend fun insertGgProductionRecords(
        @Body request: List<GgProductionRecordRequest>,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @POST("rest/v1/gg_inwarding")
    suspend fun insertGgInwarding(
        @Body request: GgInwardingRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @POST("rest/v1/gg_returns")
    suspend fun insertGgReturn(
        @Body request: GgReturnRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @POST("rest/v1/gg_vendors")
    suspend fun insertGgVendor(
        @Body request: GgVendorInsertRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<GgVendorDto>>

    @POST("rest/v1/gg_ingredients")
    suspend fun insertGgIngredient(
        @Body request: GgIngredientInsertRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<GgIngredientDto>>

    @GET("rest/v1/gg_batches")
    suspend fun getGgBatches(
        @Query("select") select: String = "id,batch_code,status",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30,
    ): Response<List<GgBatchDto>>

    @GET("rest/v1/gg_dispatch")
    suspend fun getGgDispatchedBatches(
        @Query("select") select: String = "id,batch_id,dispatch_date,quantity_dispatched,batch:gg_batches(id,batch_code,status)",
        @Query("order") order: String = "dispatch_date.desc",
        @Query("limit") limit: Int = 50,
    ): Response<List<GgDispatchSummaryDto>>
}
