package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {

    // ── RPC Functions ────────────────────────────────────────────
    @POST("rest/v1/rpc/fn_generate_batch_code")
    suspend fun generateBatchCode(
        @Body request: BatchCodeRequest,
    ): Response<String>

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
        @Query("select") select: String = "id,name,unit,active",
        @Query("order") order: String = "name.asc",
    ): Response<List<IngredientDto>>

    @POST("rest/v1/recipe_ingredients")
    suspend fun insertIngredient(
        @Body request: CreateIngredientRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<IngredientDto>>

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
        @Header("Prefer") prefer: String = "return=minimal",
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
}
